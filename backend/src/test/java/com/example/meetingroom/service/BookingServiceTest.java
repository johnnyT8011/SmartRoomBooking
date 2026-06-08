package com.example.meetingroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.TimeRange;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.exception.MeetingRoomNotFoundException;
import com.example.meetingroom.exception.UserNotFoundException;
import com.example.meetingroom.repository.BookingRepository;
import com.example.meetingroom.repository.MeetingRoomRepository;
import com.example.meetingroom.repository.UserRepository;

/**
 * Unit tests for {@link BookingService} with all collaborators mocked. Covers the seven
 * scenarios required by the spec: happy path, conflict, 5-minute lock, cancel, user/room
 * missing, and concurrent submission (only one succeeds).
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    // 取得目前這台電腦的時區 ZoneId：處理時區的類別 systemDefault()：抓取作業系統預設時區
    private static final ZoneId ZONE = ZoneId.systemDefault();
    // 時間殘根，預設是2026/6/5 8:00
    private static final LocalDateTime NOW = LocalDateTime.of(2026, Month.JUNE, 5, 8, 0);
    // 預約殘根，預設是預約2026/6/5 10:00 到 10:30
    private static final LocalDateTime START = LocalDateTime.of(2026, Month.JUNE, 5, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, Month.JUNE, 5, 10, 30);

    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private MeetingRoomRepository meetingRoomRepository;
    @Mock private BookingRuleValidator validator;
    @Mock private NotificationService notificationService;

    private BookingService service;
    private User user;
    private MeetingRoom room;

    @BeforeEach
    void setUp() {
        // 假時鐘 2026/6/5 8點
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        // new 一個測試者
        service = new BookingService(bookingRepository, userRepository, meetingRoomRepository,
                validator, notificationService, clock);
        // new 一個 User 叫做員工甲
        user = new User("員工甲", "a@example.com");
        // 強制設他的 id 是 1L
        ReflectionTestUtils.setField(user, "id", 1L);
        // new 一個 MeetingRoom 叫會議室 A
        room = new MeetingRoom("會議室 A");
        // 強制設他的 id 是 2L
        ReflectionTestUtils.setField(room, "id", 2L);
    }
    
    // 輔助殘根使用者、會議室
    private void stubExistingUserAndRoom() {
        // 只要有人查使用者資料庫 findById，就回傳 1L 員工甲
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        // 只要有人查會議室資料庫 findByIdForUpdate，就回傳 2L 會議室 A
        given(meetingRoomRepository.findByIdForUpdate(2L)).willReturn(Optional.of(room));
    }

    // ---- 情境 1、9:「會議室 A」於今日 "10:00" 至 "10:30" 狀態為「可預約」，應接受預約----
    @Test
    void lockRoom_happyPath_createsLockingBookingWith5MinuteExpiry() {
        // 呼叫前面建好的殘根
        stubExistingUserAndRoom();
        // 設定預約狀況 findOverlapping 會議室 A 2026/6/5 10:00 到 10:30 回傳 List.of() 代表沒人預約
        given(bookingRepository.findOverlapping(eq(2L), any(), any()))
                .willReturn(List.of());
        // 後端丟什麼 Booking(any(Booking.class)) 就回傳什麼回去(inv.getArgument(0))
        given(bookingRepository.save(any(Booking.class))).willAnswer(inv -> inv.getArgument(0));

        // 幫員工甲鎖定會議室 A 的 START 到 END 期間
        Booking result = service.lockRoom(1L, 2L, new com.example.meetingroom.domain.TimeRange(START, END));
        // 檢查狀態是否是 LOCKING
        assertThat(result.getStatus()).isEqualTo(BookingStatus.LOCKING);
        // 鎖定的時間 LockedAt 要在現在時間 2026/6/5 8:00
        assertThat(result.getLockedAt()).isEqualTo(NOW);
        // 限時 5 分鐘，過期時間（LockExpiresAt）需在 2026/6/5 8:00 + 5分鐘
        assertThat(result.getLockExpiresAt()).isEqualTo(NOW.plusMinutes(5));

        // 驗證是否把 user, START, END 丟入
        verify(validator).validateAll(eq(user), any(TimeRange.class)); // [Refactor #1/#2] now a TimeRange
        // 驗證是否有把使用者 ID、任何通知文字丟入 notificationService 跑網頁通知
        verify(notificationService).sendSseEvent(eq(1L), any());
    }

    // ---- 情境 2: 預約時間與現有預約發生衝突 ----
    @Test
    void lockRoom_whenOverlap_throwsConflict_andDoesNotSave() {
        // 呼叫前面建好的殘根
        stubExistingUserAndRoom();
        // new 一筆資料，借的是 會議室 A，時間是 2026/6/5 10:00 到 10:30，且狀態是 BOOKED
        Booking existing = new Booking(user, room, new com.example.meetingroom.domain.TimeRange(START, END), BookingStatus.BOOKED);
        // 設定預約狀況 findOverlapping 會議室 A 2026/6/5 10:00 到 10:30 回傳 List.of(existing) 代表有一個 user 預約這個會議室
        given(bookingRepository.findOverlapping(eq(2L), any(), any()))
                .willReturn(List.of(existing));
        // 呼叫 service.lockRoom ，而因為有人預約了，所以形態為 BookingConflictException ，警告標語要有時段重疊
        TimeRange range = new TimeRange(START, END);
        assertThatThrownBy(() -> service.lockRoom(1L, 2L, range))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("時段重疊");
        // 驗證 bookingRepository 沒有 save 任何預約
        verify(bookingRepository, never()).save(any());
    }

    // ---- 情境 3: 同一時間預約同一個時段的會議室 ----
    @Test
    void lockRoom_concurrentRequests_onlyFirstSucceeds() {
        // 呼叫前面建好的殘根
        stubExistingUserAndRoom();
        // new 一筆資料，員工甲借會議室 A 2026/6/5 10:00 到 10:30，狀態是 LOCKING
        Booking first = new Booking(user, room, new com.example.meetingroom.domain.TimeRange(START, END), BookingStatus.LOCKING);
        // 第一次呼叫回傳空的，第二次呼叫回傳有人
        given(bookingRepository.findOverlapping(eq(2L), any(), any()))
                .willReturn(List.of())
                .willReturn(List.of(first));
        // 後端丟什麼 Booking(any(Booking.class)) 就回傳什麼回去(inv.getArgument(0))
        given(bookingRepository.save(any(Booking.class))).willAnswer(inv -> inv.getArgument(0));
        // 驗證第一個人，有成功預約(.isNotNull())
        TimeRange range = new TimeRange(START, END);
        assertThat(service.lockRoom(1L, 2L, range)).isNotNull();
        // assertThatThrownBy 必須拋出任何錯誤，並預期內部錯誤必須剛好預約衝突(.isInstanceOf(BookingConflictException.class))
        assertThatThrownBy(() -> service.lockRoom(1L, 2L, range))
                .isInstanceOf(BookingConflictException.class);
    }

    // ---- 情境: 使用者不存在 ----
    @Test
    void lockRoom_whenUserMissing_throwsUserNotFound() {
        // 設定查詢 ID 99L 時回傳 Optional.empty() 查無此人
        given(userRepository.findById(99L)).willReturn(Optional.empty());
        // assertThatThrownBy 必須拋出任何錯誤，並預期內部錯誤是 UserNotFoundException.class
        TimeRange range = new TimeRange(START, END);
        assertThatThrownBy(() -> service.lockRoom(99L, 2L, range))
                .isInstanceOf(UserNotFoundException.class);
        // 確保拋出異常中，沒有執行過任何 save()
        verify(bookingRepository, never()).save(any());
    }

    // ---- 情境: 會議室不存在 ----
    @Test
    void lockRoom_whenRoomMissing_throwsRoomNotFound() {
        // 查得到使用者
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        // 會議室查不到 ID 404L 時回傳 Optional.empty() 查無此會議室
        given(meetingRoomRepository.findByIdForUpdate(404L)).willReturn(Optional.empty());
        // assertThatThrownBy 必須拋出任何錯誤，並預期內部錯誤是 MeetingRoomNotFoundException.class
        TimeRange range = new TimeRange(START, END);
        assertThatThrownBy(() -> service.lockRoom(1L, 404L, range))
                .isInstanceOf(MeetingRoomNotFoundException.class);
        // 確保拋出異常中，沒有執行過 any save()
        verify(bookingRepository, never()).save(any());
    }

    // ---- 情境 11: 使用者取消預約 ----
    @Test
    void cancelBooking_setsCancelled_andDoesNotDelete() {
        // new 一筆狀態為 BOOKED 的預約
        Booking booking = new Booking(user, room, new com.example.meetingroom.domain.TimeRange(START, END), BookingStatus.BOOKED);
        // 設定已經存在一筆 ID 為 50 的資料
        ReflectionTestUtils.setField(booking, "id", 50L);
        // 當 findDetailedById 呼叫 ID 50 時，回傳狀態為 BOOKED 的預約
        given(bookingRepository.findDetailedById(50L)).willReturn(Optional.of(booking));
        // 接收使用者取消預約的結果
        Booking result = service.cancelBooking(50L);
        // status 必須變更為 CANCELLED
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // 確保沒有刪到資料，而是把狀態改成 CANCELLED
        verify(bookingRepository, never()).delete(any());
        verify(bookingRepository, never()).deleteById(anyLong());
        // 驗證是否有把使用者 ID、任何通知文字丟入 notificationService 跑網頁通知
        verify(notificationService).sendSseEvent(eq(1L), any());
    }

    @Test
    void cancelLocking_setsCancelled_andDoesNotDelete() {
        // new 一筆狀態為 LOCKING 的預約
        Booking booking = new Booking(user, room, new com.example.meetingroom.domain.TimeRange(START, END), BookingStatus.LOCKING);
        // 設定已經存在一筆 ID 為 50 的資料
        ReflectionTestUtils.setField(booking, "id", 50L);
        // 當 findDetailedById 呼叫 ID 50 時，回傳狀態為 LOCKING 的預約
        given(bookingRepository.findDetailedById(50L)).willReturn(Optional.of(booking));
        // 接收使用者取消預約的結果
        Booking result = service.cancelBooking(50L);
        // status 必須變更為 CANCELLED
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // 確保沒有刪到資料，而是把狀態改成 CANCELLED
        verify(bookingRepository, never()).delete(any());
        verify(bookingRepository, never()).deleteById(anyLong());
        // 驗證是否有把使用者 ID、任何通知文字丟入 notificationService 跑網頁通知
        verify(notificationService).sendSseEvent(eq(1L), any());
    }

    // ---- 情境：確認預定到確認的過渡 與 重複預約 ----
    @Test
    void confirmBooking_promotesLockingToBooked() {
        // new 一筆預約員工甲借會議室 A 2026/6/5 10:00 到 10:30，狀態是 LOCKING
        Booking booking = new Booking(user, room, new com.example.meetingroom.domain.TimeRange(START, END), BookingStatus.LOCKING);
        // 設定已經存在一筆 ID 為 60 的資料
        ReflectionTestUtils.setField(booking, "id", 60L);
        // 當 findDetailedById 呼叫 ID 60 時，回傳狀態為 LOCKING 的預約
        given(bookingRepository.findDetailedById(60L)).willReturn(Optional.of(booking));
        // 除了自己這時段有沒有其他人
        given(bookingRepository.findOverlappingExcluding(eq(2L), any(), any(), eq(60L)))

        .willReturn(List.of());
        Booking result = service.confirmBooking(60L);
        // 回傳結果狀態為 BookingStatus.BOOKED
        assertThat(result.getStatus()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void confirmBooking_whenSlotStolen_throwsConflict() {
        // new 一筆預約員工甲借會議室 A 2026/6/5 10:00 到 10:30，狀態是 LOCKING
        Booking booking = new Booking(user, room, new com.example.meetingroom.domain.TimeRange(START, END), BookingStatus.LOCKING);
        // 設定已經存在一筆 ID 為 61 的資料
        ReflectionTestUtils.setField(booking, "id", 61L);
        // new 一筆預約員工甲借會議室 A 2026/6/5 10:00 到 10:30，狀態是 BOOKED
        Booking other = new Booking(user, room, new com.example.meetingroom.domain.TimeRange(START, END), BookingStatus.BOOKED);
        // 除了自己這時段有沒有其他人，回傳有List.of(other)
        given(bookingRepository.findDetailedById(61L)).willReturn(Optional.of(booking));
        given(bookingRepository.findOverlappingExcluding(eq(2L), any(), any(), eq(61L)))
                .willReturn(List.of(other));
        // assertThatThrownBy 必須拋出任何錯誤，並預期內部錯誤是 BookingConflictException.class
        assertThatThrownBy(() -> service.confirmBooking(61L))
                .isInstanceOf(BookingConflictException.class);
        // 原本的那筆 61 狀態，維持在 LOCKING
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.LOCKING);
    }

    // ---- 情境5：報到 ----
    @Test
    void checkIn_setsCheckedInAndTimestamp() {
        // new 一筆預約員工甲借會議室 A 2026/6/5 10:00 到 10:30，狀態是 BOOKED
        Booking booking = new Booking(user, room, new com.example.meetingroom.domain.TimeRange(START, END), BookingStatus.BOOKED);
        // 設定已經存在一筆 ID 為 70 的資料
        ReflectionTestUtils.setField(booking, "id", 70L);
        // 當 findDetailedById 呼叫 ID 70 時，回傳狀態為 BOOKED 的預約
        given(bookingRepository.findDetailedById(70L)).willReturn(Optional.of(booking));

        Booking result = service.checkIn(70L);
        // 回傳結果狀態為 BookingStatus.CHECKED_IN
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        // 簽到時間欄位要等於現在時間 2026/6/5 8:00
        assertThat(result.getCheckedInAt()).isEqualTo(NOW);
    }
}

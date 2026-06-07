package com.example.meetingroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
// import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.exception.InvalidBookingException;
import com.example.meetingroom.repository.BookingRepository;

/**
 * Boundary tests for {@link BookingRuleValidator}, driven by a fixed clock at
 * 2026-06-03 09:00 (mirroring Scenario 8's system date).
 */
@ExtendWith(MockitoExtension.class)
class BookingRuleValidatorTest {

    // 取得目前這台電腦的時區 ZoneId：處理時區的類別 systemDefault()：抓取作業系統預設時區
    private static final ZoneId ZONE = ZoneId.systemDefault();
    // 時間殘根，預設是2026/6/3 9:00
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 3, 9, 0);

    // 資料庫殘根，模擬物件
    @Mock
    private BookingRepository bookingRepository;

    private BookingRuleValidator validator;
    private final User user = new User("員工甲", "a@example.com");

    // 時間殘根與系統時間測試
    @BeforeEach
    protected void setUp() {
        try {
            // 準備時間殘根
            Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
            
            // 準備資料庫殘根，注入假資料庫與時間殘根
            validator = new BookingRuleValidator(bookingRepository, clock);
            
            // 設定使用者 ID 是 1
            ReflectionTestUtils.setField(user, "id", 1L);
        }
        catch (Exception e) {
            // 發生錯誤時捕捉，印出 Log 並呼叫 fail()
            System.out.println("準備測試環境發生錯誤");
            e.printStackTrace();
            fail("setUp 初始化失敗：" + e.getMessage());
        }
    }

    // ---- validateTimeBlock 情境6 預約時間尾數只能是 00 或 30，e.g. 10:00、10:30，所以 10:15、10:45 為錯誤----
    // 案例 1：不合法的輸入資料 10:15、10:45
    @Test
    void timeBlock_rejectsNon30MinuteBoundary() {
        assertThatThrownBy(() -> validator.validateTimeBlock(
                LocalDateTime.of(2026, 6, 3, 10, 15),
                LocalDateTime.of(2026, 6, 3, 10, 45)))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("30 分鐘");
    }

    // 案例 2：合法的輸入資料 10:00、10:30
    @Test
    void timeBlock_acceptsAlignedTimes() {
        assertThatCode(() -> validator.validateTimeBlock(
                LocalDateTime.of(2026, 6, 3, 10, 0),
                LocalDateTime.of(2026, 6, 3, 10, 30)))
                .doesNotThrowAnyException();
    }

    // ---- validateDuration 情境7 預約時限最短 30 分鐘、最長 4 小時 ----
    // 案例 1：超過 4 小時(不合法)
    @Test
    void duration_rejectsOverFourHours() {
        assertThatThrownBy(() -> validator.validateDuration(
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 3, 14, 0)))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("4 小時");
    }

    // 案例 2：少於 30 分鐘(不合法)
    @Test
    void duration_rejectsUnder30Minutes() {
        assertThatThrownBy(() -> validator.validateDuration(
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 3, 9, 15)))
                .isInstanceOf(InvalidBookingException.class);
    }

    // 案例 3：結束比開始早(不合法)
    @Test
    void duration_rejectsEndBeforeStart() {
        assertThatThrownBy(() -> validator.validateDuration(
                LocalDateTime.of(2026, 6, 3, 11, 0),
                LocalDateTime.of(2026, 6, 3, 10, 0)))
                .isInstanceOf(InvalidBookingException.class);
    }

    // 案例 4：預約 4 小時(合法)
    @Test
    void duration_acceptsExactlyFourHours() {
        assertThatCode(() -> validator.validateDuration(
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 3, 13, 0)))
                .doesNotThrowAnyException();
    }

    // 案例 5：預約 30 分鐘(合法)
    @Test
    void duration_acceptsExactly30Minutes() {
        assertThatCode(() -> validator.validateDuration(
                LocalDateTime.of(2026, 6, 3, 9, 30),
                LocalDateTime.of(2026, 6, 3, 10, 0)))
                .doesNotThrowAnyException();
    }

    // ---- validateBookingWindow 情境8 預約日期僅開放預約未來 7 天內的時段 ----
    // 案例 1：2026/6/3 預約 2026/6/15 的會議室(超過 7 天) 不合法
    @Test
    void window_rejectsBeyond7Days() {
        assertThatThrownBy(() -> validator.validateBookingWindow(
                LocalDateTime.of(2026, 6, 15, 10, 0),
                LocalDateTime.of(2026, 6, 15, 11, 0)))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("7 天");
    }

    // 案例 2：2026/6/3 預約 2026/6/1 的會議室(預約過去) 不合法
    @Test
    void window_rejectsPast() {
        assertThatThrownBy(() -> validator.validateBookingWindow(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0)))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("過去");
    }

    // 案例 3：2026/6/3 預約 2026/6/5 的會議室 合法
    @Test
    void window_acceptsWithin7Days() {
        assertThatCode(() -> validator.validateBookingWindow(
                LocalDateTime.of(2026, 6, 5, 10, 0),
                LocalDateTime.of(2026, 6, 5, 11, 0)))
                .doesNotThrowAnyException();
    }

    // ---- checkUserConflict ----
    // 案例 1：
    @Test
    void userConflict_throwsWhenUserHasOverlap() {
        // 在記憶體中 new 一筆已經預約成功的紀錄：6/5 的 10:00 ~ 11:00 在會議室 A
        Booking existing = new Booking(user, new MeetingRoom("會議室 A"),
                LocalDateTime.of(2026, 6, 5, 10, 0),
                LocalDateTime.of(2026, 6, 5, 11, 0), BookingStatus.BOOKED);
        // 有人呼叫 findUserOverlapping 就回傳 existing，anyLong() 和 any() 代表任何都可以
        given(bookingRepository.findUserOverlapping(anyLong(), any(), any(), any()))
                .willReturn(List.of(existing));
        // 使用者發起新的預約：6/5 的 10:30 ~ 11:30，重疊要回覆 BookingConflictException
        assertThatThrownBy(() -> validator.checkUserConflict(user,
                LocalDateTime.of(2026, 6, 5, 10, 30),
                LocalDateTime.of(2026, 6, 5, 11, 30)))
                .isInstanceOf(BookingConflictException.class);
    }

    // 案例 2：
    @Test
    void userConflict_passesWhenNoOverlap() {
        // 有人呼叫 findUserOverlapping 就回傳 List.of()空的清單
        given(bookingRepository.findUserOverlapping(anyLong(), any(), any(), any()))
                .willReturn(List.of());
        // 使用者預約 10:00 ~ 11:00
        assertThatCode(() -> validator.checkUserConflict(user,
                LocalDateTime.of(2026, 6, 5, 10, 0),
                LocalDateTime.of(2026, 6, 5, 11, 0)))
                .doesNotThrowAnyException();
    }

    // 案例 3：
    @Test
    void slotConstants_areAsSpecified() {
        // 驗證最小預約時間是不是 30 分鐘
        assertThat(BookingRuleValidator.MIN_DURATION_MINUTES).isEqualTo(30);
        // 驗證最大預約時間是不是 4 小時( 240 分鐘)
        assertThat(BookingRuleValidator.MAX_DURATION_MINUTES).isEqualTo(240);
        // 驗證最早能預約時間是不是 7 天
        assertThat(BookingRuleValidator.BOOKING_WINDOW_DAYS).isEqualTo(7);
    }
}

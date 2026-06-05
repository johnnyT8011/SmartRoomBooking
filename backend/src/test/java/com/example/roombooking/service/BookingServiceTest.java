package com.example.roombooking.service;

import com.example.roombooking.model.Booking;
import com.example.roombooking.model.BookingStatus;
import com.example.roombooking.repo.BookingRepository;
import com.example.roombooking.repo.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BookingService 單元測試（純 Mockito，不啟動 Spring）。
 * 時間相關情境用「把預約開始時間相對於現在挪動」的方式落在/落在窗外，
 * 不依賴外部時鐘，毫秒內跑完、無 flaky 風險。
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepo;
    @Mock RoomRepository roomRepo;
    @Mock NotificationService notifications;

    BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepo, roomRepo, notifications);
    }

    private Booking booking(Long id, LocalDateTime start, BookingStatus status) {
        Booking b = new Booking();
        b.setId(id);
        b.setRoomId(1L);
        b.setBorrower("員工甲");
        b.setStartTime(start);
        b.setEndTime(start.plusHours(1));
        b.setStatus(status);
        return b;
    }

    // ---------- 報到時間窗 ----------

    @Test
    @DisplayName("報到：落在窗口內（開始時間=現在）→ 成功轉 CHECKED_IN")
    void checkIn_withinWindow_succeeds() {
        Booking b = booking(1L, LocalDateTime.now(), BookingStatus.BOOKED);
        when(bookingRepo.findById(1L)).thenReturn(Optional.of(b));
        when(bookingRepo.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = service.checkIn(1L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        verify(bookingRepo).save(b);
    }

    @Test
    @DisplayName("報到：太早（開始前超過 5 分鐘）→ 拒絕，不存檔")
    void checkIn_tooEarly_isRejected() {
        Booking b = booking(1L, LocalDateTime.now().plusMinutes(10), BookingStatus.BOOKED);
        when(bookingRepo.findById(1L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.checkIn(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("尚未開放報到");
        verify(bookingRepo, never()).save(any());
    }

    @Test
    @DisplayName("報到：太晚（開始後超過 15 分鐘）→ 拒絕，不存檔")
    void checkIn_tooLate_isRejected() {
        Booking b = booking(1L, LocalDateTime.now().minusMinutes(20), BookingStatus.BOOKED);
        when(bookingRepo.findById(1L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.checkIn(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("報到時間已過");
        verify(bookingRepo, never()).save(any());
    }

    @Test
    @DisplayName("報到：狀態非 BOOKED（已報到）→ 拒絕")
    void checkIn_whenNotBooked_isRejected() {
        Booking b = booking(1L, LocalDateTime.now(), BookingStatus.CHECKED_IN);
        when(bookingRepo.findById(1L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.checkIn(1L))
                .isInstanceOf(ConflictException.class);
        verify(bookingRepo, never()).save(any());
    }

    // ---------- 防重疊 ----------

    @Test
    @DisplayName("預約：同房同時段已有有效預約 → 丟 ConflictException")
    void createBooking_whenOverlap_throwsConflict() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = start.plusHours(1);
        when(roomRepo.existsById(1L)).thenReturn(true);
        when(bookingRepo.findOverlapping(eq(1L), anyList(), eq(start), eq(end)))
                .thenReturn(List.of(booking(99L, start, BookingStatus.BOOKED)));

        assertThatThrownBy(() -> service.createBooking(1L, "員工乙", start, end))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("時段重疊");
        verify(bookingRepo, never()).save(any());
    }

    // ---------- 自動釋放 ----------

    @Test
    @DisplayName("釋放：開始已超過寬限且未報到的 BOOKED → 轉 RELEASED")
    void releaseNoShows_releasesPastStartBooked() {
        Booking past = booking(1L, LocalDateTime.now().minusMinutes(30), BookingStatus.BOOKED);
        when(bookingRepo.findByStatus(BookingStatus.BOOKED)).thenReturn(List.of(past));
        when(bookingRepo.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        int released = service.releaseNoShows(15);

        assertThat(released).isEqualTo(1);
        assertThat(past.getStatus()).isEqualTo(BookingStatus.RELEASED);
    }
}

package com.example.roombooking.service;

import com.example.roombooking.model.Booking;
import com.example.roombooking.model.BookingStatus;
import com.example.roombooking.model.Room;
import com.example.roombooking.repo.BookingRepository;
import com.example.roombooking.repo.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    /** 報到開放時間：預約開始前幾分鐘起可報到 */
    public static final int CHECKIN_OPEN_BEFORE_MINUTES = 5;
    /** 未報到寬限：預約開始後幾分鐘仍未報到即釋放。報到窗的關門時間=這條線 */
    public static final int NO_SHOW_GRACE_MINUTES = 15;

    private final BookingRepository bookingRepo;
    private final RoomRepository roomRepo;
    private final NotificationService notifications;

    public BookingService(BookingRepository bookingRepo, RoomRepository roomRepo, NotificationService notifications) {
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
        this.notifications = notifications;
    }

    public List<Room> getRooms() {
        return roomRepo.findAll();
    }

    public List<Booking> getBookingsForDay(LocalDate day) {
        return bookingRepo.findByStartTimeGreaterThanEqualAndStartTimeLessThan(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    @Transactional
    public Booking createBooking(Long roomId, String borrower, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("結束時間必須晚於開始時間");
        }
        if (!roomRepo.existsById(roomId)) {
            throw new NotFoundException("會議室不存在：" + roomId);
        }
        List<Booking> clash = bookingRepo.findOverlapping(
                roomId, List.of(BookingStatus.BOOKED, BookingStatus.CHECKED_IN), start, end);
        if (!clash.isEmpty()) {
            throw new ConflictException("時段重疊，預約失敗");
        }
        Booking b = new Booking();
        b.setRoomId(roomId);
        b.setBorrower(borrower);
        b.setStartTime(start);
        b.setEndTime(end);
        b.setStatus(BookingStatus.BOOKED);
        b.setCreatedAt(LocalDateTime.now());
        b = bookingRepo.save(b);
        notifications.add("預約成功：" + borrower + " 預約 房間#" + roomId
                + " " + start.toLocalTime() + "-" + end.toLocalTime());
        return b;
    }

    /**
     * 報到。只允許在報到窗 [開始前 5 分鐘, 開始後 15 分鐘] 內、且狀態仍為 BOOKED 時成功。
     * 這層是真正的守門員：不管請求從哪來（即使繞過前端直接打 API）都會被檢查。
     */
    @Transactional
    public Booking checkIn(Long bookingId) {
        Booking b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("預約不存在：" + bookingId));

        if (b.getStatus() != BookingStatus.BOOKED) {
            throw new ConflictException("此預約目前無法報到（狀態：" + b.getStatus() + "）");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime openAt = b.getStartTime().minusMinutes(CHECKIN_OPEN_BEFORE_MINUTES);
        LocalDateTime closeAt = b.getStartTime().plusMinutes(NO_SHOW_GRACE_MINUTES);
        if (now.isBefore(openAt)) {
            throw new ConflictException("尚未開放報到（預約開始前 " + CHECKIN_OPEN_BEFORE_MINUTES + " 分鐘起才可報到）");
        }
        if (now.isAfter(closeAt)) {
            throw new ConflictException("報到時間已過，預約已失效");
        }

        b.setStatus(BookingStatus.CHECKED_IN);
        bookingRepo.save(b);
        notifications.add("報到成功：" + b.getBorrower() + " 房間#" + b.getRoomId());
        return b;
    }

    /**
     * 釋放未報到的預約。
     * graceMinutes = 15：正式規則（排程用）。
     * graceMinutes = 0 ：加速測試（trigger-timeout 用），立即釋放已開始但未報到者。
     */
    @Transactional
    public int releaseNoShows(int graceMinutes) {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (Booking b : bookingRepo.findByStatus(BookingStatus.BOOKED)) {
            LocalDateTime deadline = b.getStartTime().plusMinutes(graceMinutes);
            if (!now.isBefore(deadline)) {  // now >= start + grace
                b.setStatus(BookingStatus.RELEASED);
                bookingRepo.save(b);
                notifications.add("自動釋放：" + b.getBorrower()
                        + " 未報到，房間#" + b.getRoomId() + " 時段已釋放");
                count++;
            }
        }
        return count;
    }
}

package com.example.roombooking.config;

import com.example.roombooking.model.Booking;
import com.example.roombooking.model.BookingStatus;
import com.example.roombooking.model.Room;
import com.example.roombooking.repo.BookingRepository;
import com.example.roombooking.repo.RoomRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoomRepository roomRepo;
    private final BookingRepository bookingRepo;

    public DataInitializer(RoomRepository roomRepo, BookingRepository bookingRepo) {
        this.roomRepo = roomRepo;
        this.bookingRepo = bookingRepo;
    }

    @Override
    public void run(String... args) {
        if (roomRepo.count() > 0) return;

        Room a = roomRepo.save(new Room("會議室 A"));
        Room b = roomRepo.save(new Room("會議室 B"));
        Room c = roomRepo.save(new Room("會議室 C"));

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

        // 紅色待釋放：start = 現在、未報到。15 分鐘排程暫時不會動它，
        // 點 trigger-timeout (grace=0) 會立即釋放 → 紅變綠的 Demo 主角
        save(a.getId(), "示範-未報到", now, now.plusHours(1), BookingStatus.BOOKED);
        // 綠色已報到：不會被釋放
        save(b.getId(), "示範-已報到", now, now.plusHours(1), BookingStatus.CHECKED_IN);
        // 稍後的預約
        save(c.getId(), "示範-稍後", now.plusHours(2), now.plusHours(3), BookingStatus.BOOKED);
    }

    private void save(Long roomId, String who, LocalDateTime s, LocalDateTime e, BookingStatus st) {
        Booking bk = new Booking();
        bk.setRoomId(roomId);
        bk.setBorrower(who);
        bk.setStartTime(s);
        bk.setEndTime(e);
        bk.setStatus(st);
        bk.setCreatedAt(LocalDateTime.now());
        bookingRepo.save(bk);
    }
}

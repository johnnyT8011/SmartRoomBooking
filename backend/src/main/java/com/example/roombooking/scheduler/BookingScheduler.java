package com.example.roombooking.scheduler;

import com.example.roombooking.service.BookingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingScheduler {

    private final BookingService service;

    public BookingScheduler(BookingService service) {
        this.service = service;
    }

    // 每 30 秒掃描一次，釋放開始後超過寬限（15 分鐘）仍未報到的預約
    @Scheduled(fixedRate = 30000)
    public void releaseNoShows() {
        service.releaseNoShows(BookingService.NO_SHOW_GRACE_MINUTES);
    }
}

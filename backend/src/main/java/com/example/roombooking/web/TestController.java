package com.example.roombooking.web;

import com.example.roombooking.service.BookingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 加速測試端點：不必等 15 分鐘，立即觸發逾時掃描（grace = 0）。
 * Demo 時點一下，未報到的紅色區塊立刻被釋放。
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    private final BookingService service;

    public TestController(BookingService service) {
        this.service = service;
    }

    @PostMapping("/trigger-timeout")
    public Map<String, Object> triggerTimeout() {
        int released = service.releaseNoShows(0);
        return Map.of("released", released, "message", "已立即觸發逾時掃描");
    }
}

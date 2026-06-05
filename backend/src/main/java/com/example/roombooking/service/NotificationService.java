package com.example.roombooking.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 最小版通知：把事件存在記憶體，前端用 GET /api/notifications 輪詢拿。
 * 升級路徑：改成 Server-Sent Events (SSE) 即可做到後端主動推播。
 */
@Service
public class NotificationService {

    private final List<Map<String, String>> events = new CopyOnWriteArrayList<>();

    public void add(String message) {
        Map<String, String> e = new LinkedHashMap<>();
        e.put("time", LocalDateTime.now().toString());
        e.put("message", message);
        events.add(0, e);
        while (events.size() > 30) {
            events.remove(events.size() - 1);
        }
    }

    public List<Map<String, String>> recent() {
        return new ArrayList<>(events);
    }
}

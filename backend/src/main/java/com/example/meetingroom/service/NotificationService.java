package com.example.meetingroom.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events (SSE) push service.
 *
 * <p>Keeps one or more open {@link SseEmitter}s per user id. Booking events (success,
 * cancel, lock release, no-show cancel) are delivered through {@link #sendSseEvent}.
 * No Redis / message queue is involved — emitters are held in memory, which is sufficient
 * for a single-instance deployment.</p>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Emitters live for an hour before the browser must reconnect. */
    private static final long SSE_TIMEOUT_MS = 60L * 60L * 1000L;

    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    /**
     * Open an SSE channel for a user. Called by {@code GET /api/notifications/sse}.
     */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        register(userId, emitter);
        try {
            emitter.send(SseEmitter.event().name("connected").data("SSE 連線已建立"));
        } catch (IOException e) {
            log.debug("Failed to send initial SSE handshake to user {}", userId, e);
        }
        return emitter;
    }

    /**
     * Register an emitter under a user id, wiring up cleanup callbacks.
     * Package-private so unit tests can register a mock emitter and verify pushes.
     */
    void register(Long userId, SseEmitter emitter) {
        emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
    }

    /**
     * Push a message to every open channel owned by {@code userId}. Encapsulates the SSE
     * delivery logic; callers (service, scheduler) just provide the recipient and text.
     */
    public void sendSseEvent(Long userId, String message) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No active SSE channel for user {}; dropping message: {}", userId, message);
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(message));
            } catch (IOException | IllegalStateException e) {
                log.debug("Removing dead SSE emitter for user {}", userId, e);
                remove(userId, emitter);
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}

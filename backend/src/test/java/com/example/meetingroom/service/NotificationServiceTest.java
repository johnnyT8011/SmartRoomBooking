package com.example.meetingroom.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * Verifies the SSE push logic: registered emitters receive events, missing users are a
 * no-op, and a broken emitter does not break delivery.
 */
class NotificationServiceTest {

    private final NotificationService service = new NotificationService();

    @Test
    void sendSseEvent_pushesToRegisteredEmitter() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        service.register(1L, emitter);

        service.sendSseEvent(1L, "預約成功：會議室 A");

        verify(emitter).send(any(SseEventBuilder.class));
    }

    @Test
    void sendSseEvent_toUserWithoutChannel_isNoOp() {
        SseEmitter other = mock(SseEmitter.class);
        service.register(1L, other);

        // User 2 has no channel — nothing should be sent and no exception thrown.
        assertThatCode(() -> service.sendSseEvent(2L, "hi")).doesNotThrowAnyException();
    }

    @Test
    void sendSseEvent_swallowsBrokenEmitter() throws IOException {
        SseEmitter broken = mock(SseEmitter.class);
        doThrow(new IOException("client gone")).when(broken).send(any(SseEventBuilder.class));
        service.register(1L, broken);

        assertThatCode(() -> service.sendSseEvent(1L, "msg")).doesNotThrowAnyException();

        // Dead emitter is dropped; a second push finds no channel and sends nothing more.
        service.sendSseEvent(1L, "again");
        verify(broken).send(any(SseEventBuilder.class));
    }

    @Test
    void sendSseEvent_withNoRegistrations_doesNothing() {
        assertThatCode(() -> service.sendSseEvent(99L, "msg")).doesNotThrowAnyException();
    }
}

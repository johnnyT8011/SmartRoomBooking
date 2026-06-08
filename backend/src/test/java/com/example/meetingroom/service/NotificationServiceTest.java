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

    // 確定使用者有收到通知
    @Test
    void sendSseEvent_pushesToRegisteredEmitter() throws IOException {
        // 建立一個接收通知人
        SseEmitter emitter = mock(SseEmitter.class);
        // 設 ID 為 1L 
        service.register(1L, emitter);
        // 系統對 1L 發送 message
        service.sendSseEvent(1L, "預約成功：會議室 A");
        // 驗證有呼叫 send
        verify(emitter).send(any(SseEventBuilder.class));
    }

    // 確定使用者有在線上
    @Test
    void sendSseEvent_toUserWithoutChannel_isNoOp() {
        // 建立一個接收通知人
        SseEmitter other = mock(SseEmitter.class);
        // 設 ID 為 1L 
        service.register(1L, other);

        // 使用者沒有在網頁上，不傳送任何訊息
        assertThatCode(() -> service.sendSseEvent(2L, "hi")).doesNotThrowAnyException();
    }

    // 使用者原本在線上，但後端要送出訊息時，使用者突然不見
    @Test
    void sendSseEvent_swallowsBrokenEmitter() throws IOException {
        // 建立一個接收通知人
        SseEmitter broken = mock(SseEmitter.class);
        // 有人呼叫 send，就拋出異常 IOException 錯誤
        doThrow(new IOException("client gone")).when(broken).send(any(SseEventBuilder.class));
        // 設 ID 為 1L
        service.register(1L, broken);

        // 使用者斷網，不傳送任何錯誤訊息
        assertThatCode(() -> service.sendSseEvent(1L, "msg")).doesNotThrowAnyException();

        // 傳送一個 message 給使用者 1L，確定上面有抓出斷網的
        service.sendSseEvent(1L, "again");
        // 驗證有呼叫 send
        verify(broken).send(any(SseEventBuilder.class));
    }

    // 沒有對應的使用者，什麼都不用做
    @Test
    void sendSseEvent_withNoRegistrations_doesNothing() {
        assertThatCode(() -> service.sendSseEvent(99L, "msg")).doesNotThrowAnyException();
    }
}

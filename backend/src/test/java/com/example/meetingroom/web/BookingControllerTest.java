package com.example.meetingroom.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.exception.BookingNotFoundException;
import com.example.meetingroom.service.BookingService;
import com.example.meetingroom.service.NotificationService;

/**
 * Verifies the {@link BookingController} HTTP layer: correct status codes, that the
 * service is invoked with the parsed arguments, and that domain exceptions are translated
 * by {@code GlobalExceptionHandler}. The service/notification beans are mocked.
 */
@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private NotificationService notificationService;

    // 設定一筆預約，使用者是牛，會議室 A 時間是 2026/6/6 10:00~10:30，狀態可以隨著測試需求自由切換
    private Booking sampleBooking(BookingStatus status) {
        User user = new User("牛", "niu@example.com");
        MeetingRoom room = new MeetingRoom("會議室 A");
        return new Booking(user, room,
                new com.example.meetingroom.domain.TimeRange(LocalDateTime.of(2026, 6, 6, 10, 0),
                LocalDateTime.of(2026, 6, 6, 10, 30)), status);
    }

    // 測試前端的資料有準確傳到 Service
    @Test
    void lock_returns201_andCallsService() throws Exception {
        // 設定如果有人要預約 1L 2L 會議室，回傳 LOCKING
        given(bookingService.lockRoom(eq(1L), eq(2L), any()))
                .willReturn(sampleBooking(BookingStatus.LOCKING));
        // JSON 格式
        String body = """
                {"userId":1,"roomId":2,"startTime":"2026-06-06T10:00:00","endTime":"2026-06-06T10:30:00"}
                """;
        // 模擬前端用 JSON，發送一個 POST 請求到 /api/bookings/lock
        mockMvc.perform(post("/api/bookings/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                // 預期拿到 isCreated
                .andExpect(status().isCreated())
                // 預期狀態是 LOCKING
                .andExpect(jsonPath("$.status").value("LOCKING"))
                // 預期借用人是牛
                .andExpect(jsonPath("$.borrower").value("牛"))
                // 預期是會議室 A
                .andExpect(jsonPath("$.roomName").value("會議室 A"));
        // 驗證資料有準確傳入 Service
        verify(bookingService).lockRoom(eq(1L), eq(2L), any());
    }

    // 預約資料不完整
    @Test
    void lock_withMissingFields_returns400() throws Exception {
        // 模擬前端用 JSON，發送一個 POST 請求到 /api/bookings/lock
        mockMvc.perform(post("/api/bookings/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":2}"))
                // 由於只有 roomId 所以應該是 isBadRequest
                .andExpect(status().isBadRequest());
    }

    // 模擬時段重疊
    @Test
    void lock_whenOverlap_returns409_withMessage() throws Exception {
        // 設定如果有人要預約會議室，都回傳預約失敗
        given(bookingService.lockRoom(anyLong(), anyLong(), any()))
                .willThrow(new BookingConflictException("時段重疊，預約失敗"));
        // JSON 格式
        String body = """
                {"userId":1,"roomId":2,"startTime":"2026-06-06T10:00:00","endTime":"2026-06-06T10:30:00"}
                """;
        // 模擬前端用 JSON，發送一個 POST 請求到 /api/bookings/lock
        mockMvc.perform(post("/api/bookings/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                // 預期失敗
                .andExpect(status().isConflict())
                // 預期收到訊息有"時段重疊，預約失敗"
                .andExpect(jsonPath("$.message").value("時段重疊，預約失敗"));
    }

    // 模擬轉確認預約狀況
    @Test
    void confirm_returns200() throws Exception {
        // 設定如果有人要預約會議室 5L，都回 BOOKED(已確定預約)
        given(bookingService.confirmBooking(5L)).willReturn(sampleBooking(BookingStatus.BOOKED));
        // 模擬前端用 JSON，發送一個確認的狀態過去 Service，預期回傳是正常且狀態是 BOOKED
        mockMvc.perform(post("/api/bookings/5/confirm")) 
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"));
        // 驗證有沒有轉傳 BOOKED 到 Service
        verify(bookingService).confirmBooking(5L);
    }

    // 模擬取消預約狀況
    @Test
    void cancel_returns200() throws Exception {
        // 設定如果有人要預約會議室 7L，都回 CANCELLED(取消預約)
        given(bookingService.cancelBooking(7L)).willReturn(sampleBooking(BookingStatus.CANCELLED));
        // 模擬前端用 JSON，發送一個確認的狀態過去 Service，預期回傳是正常且狀態是 CANCELLED
        mockMvc.perform(post("/api/bookings/7/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        // 驗證有沒有轉傳 CANCELLED 到 Service
        verify(bookingService).cancelBooking(7L);
    }

    // 模擬報到狀況
    @Test
    void checkIn_returns200() throws Exception {
        // 設定如果有人要預約會議室 9L，都回 CHECKED_IN(報到)
        given(bookingService.checkIn(9L)).willReturn(sampleBooking(BookingStatus.CHECKED_IN));
        // 模擬前端用 JSON，發送一個確認的狀態過去 Service，預期回傳是正常且狀態是 CHECKED_IN
        mockMvc.perform(post("/api/bookings/9/checkin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));
        // 驗證有沒有轉傳 CHECKED_IN 到 Service
        verify(bookingService).checkIn(9L);
    }

    // 預約不存在
    @Test
    void confirm_whenBookingMissing_returns404() throws Exception {
        // 設定如果有人要預約會議室 9L，都回 BookingNotFoundException
        given(bookingService.confirmBooking(404L)).willThrow(new BookingNotFoundException(404L));
        // 模擬前端用 JSON，發送一個確認的狀態過去 Service，預期回傳是沒找到預約(isNotFound)
        mockMvc.perform(post("/api/bookings/404/confirm"))
                .andExpect(status().isNotFound());
    }

    // 測試 SSE 是否有正確連線
    @Test
    void sse_opensStream() throws Exception {
        // 當呼叫 subscribe(1L)，回傳 SseEmitter(一條會持續開著、之後才陸續吐資料的回應物件)
        given(notificationService.subscribe(1L)).willReturn(new SseEmitter());
        // 模擬一個 GET 請求，帶 query 參數 userId=1，當回傳 SseEmitter，啟動「非同步請求處理」，把連線留著等之後推資料，且狀態是 200
        mockMvc.perform(get("/api/notifications/sse").param("userId", "1"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());
        // 驗證有沒有轉傳給 notificationService.subscribe，參數是 1
        verify(notificationService).subscribe(1L);
    }
}

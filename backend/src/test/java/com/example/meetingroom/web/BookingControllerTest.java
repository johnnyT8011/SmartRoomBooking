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
import com.example.meetingroom.exception.UserNotFoundException;
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

    private Booking sampleBooking(BookingStatus status) {
        User user = new User("牛", "niu@example.com");
        MeetingRoom room = new MeetingRoom("會議室 A");
        return new Booking(user, room,
                LocalDateTime.of(2026, 6, 6, 10, 0),
                LocalDateTime.of(2026, 6, 6, 10, 30), status);
    }

    @Test
    void lock_returns201_andCallsService() throws Exception {
        given(bookingService.lockRoom(eq(1L), eq(2L), any(), any()))
                .willReturn(sampleBooking(BookingStatus.LOCKING));

        String body = """
                {"userId":1,"roomId":2,"startTime":"2026-06-06T10:00:00","endTime":"2026-06-06T10:30:00"}
                """;

        mockMvc.perform(post("/api/bookings/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("LOCKING"))
                .andExpect(jsonPath("$.borrower").value("牛"))
                .andExpect(jsonPath("$.roomName").value("會議室 A"));

        verify(bookingService).lockRoom(eq(1L), eq(2L),
                eq(LocalDateTime.of(2026, 6, 6, 10, 0)),
                eq(LocalDateTime.of(2026, 6, 6, 10, 30)));
    }

    @Test
    void lock_withMissingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/bookings/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lock_whenOverlap_returns409_withMessage() throws Exception {
        given(bookingService.lockRoom(anyLong(), anyLong(), any(), any()))
                .willThrow(new BookingConflictException("時段重疊，預約失敗"));

        String body = """
                {"userId":1,"roomId":2,"startTime":"2026-06-06T10:00:00","endTime":"2026-06-06T10:30:00"}
                """;

        mockMvc.perform(post("/api/bookings/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("時段重疊，預約失敗"));
    }

    @Test
    void confirm_returns200() throws Exception {
        given(bookingService.confirmBooking(5L)).willReturn(sampleBooking(BookingStatus.BOOKED));

        mockMvc.perform(post("/api/bookings/5/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"));

        verify(bookingService).confirmBooking(5L);
    }

    @Test
    void cancel_returns200() throws Exception {
        given(bookingService.cancelBooking(7L)).willReturn(sampleBooking(BookingStatus.CANCELLED));

        mockMvc.perform(post("/api/bookings/7/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(bookingService).cancelBooking(7L);
    }

    @Test
    void checkIn_returns200() throws Exception {
        given(bookingService.checkIn(9L)).willReturn(sampleBooking(BookingStatus.CHECKED_IN));

        mockMvc.perform(post("/api/bookings/9/checkin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));

        verify(bookingService).checkIn(9L);
    }

    @Test
    void confirm_whenBookingMissing_returns404() throws Exception {
        given(bookingService.confirmBooking(404L)).willThrow(new UserNotFoundException(404L));

        mockMvc.perform(post("/api/bookings/404/confirm"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sse_opensStream() throws Exception {
        given(notificationService.subscribe(1L)).willReturn(new SseEmitter());

        mockMvc.perform(get("/api/notifications/sse").param("userId", "1"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());

        verify(notificationService).subscribe(1L);
    }
}

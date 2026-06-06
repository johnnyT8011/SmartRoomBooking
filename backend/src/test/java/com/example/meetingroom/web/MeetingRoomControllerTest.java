package com.example.meetingroom.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.repository.MeetingRoomRepository;
import com.example.meetingroom.service.BookingService;

/**
 * Verifies the read-only {@link MeetingRoomController}: status codes and JSON shape for the
 * room list and the weekly schedule. Backing beans are mocked.
 */
@WebMvcTest(MeetingRoomController.class)
class MeetingRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeetingRoomRepository meetingRoomRepository;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void rooms_returnsRoomList() throws Exception {
        given(meetingRoomRepository.findAll()).willReturn(List.of(
                new MeetingRoom("會議室 A"),
                new MeetingRoom("會議室 B"),
                new MeetingRoom("會議室 C")));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].roomName").value("會議室 A"));
    }

    @Test
    void schedule_returnsWeeklyBookings() throws Exception {
        Booking booking = new Booking(
                new User("豬", "zhu@example.com"),
                new MeetingRoom("會議室 B"),
                LocalDateTime.of(2026, 6, 6, 9, 0),
                LocalDateTime.of(2026, 6, 6, 10, 30),
                BookingStatus.BOOKED);
        given(bookingService.getWeeklySchedule()).willReturn(List.of(booking));

        mockMvc.perform(get("/api/rooms/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].borrower").value("豬"))
                .andExpect(jsonPath("$[0].status").value("BOOKED"));
    }
}

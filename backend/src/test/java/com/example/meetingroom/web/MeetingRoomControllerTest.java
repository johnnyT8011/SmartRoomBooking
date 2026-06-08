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

    // 確定建立的會議室都有被抓到
    @Test
    void rooms_returnsRoomList() throws Exception {
        // 建立三個會議室
        given(meetingRoomRepository.findAll()).willReturn(List.of(
                new MeetingRoom("會議室 A"),
                new MeetingRoom("會議室 B"),
                new MeetingRoom("會議室 C")));
        // 發送請求
        mockMvc.perform(get("/api/rooms"))
                // 存在
                .andExpect(status().isOk())
                // 總共幾間
                .andExpect(jsonPath("$.length()").value(3))
                // 第一間為會議室 A
                .andExpect(jsonPath("$[0].roomName").value("會議室 A"));
    }

    // 測試每週排程有成功取回
    @Test
    void schedule_returnsWeeklyBookings() throws Exception {
        // 建立一筆預約，user是豬借會議室 B，時間2026/6/6 9:00~10:30
        Booking booking = new Booking(
                new User("豬", "zhu@example.com"),
                new MeetingRoom("會議室 B"),
                new com.example.meetingroom.domain.TimeRange(LocalDateTime.of(2026, 6, 6, 9, 0),
                LocalDateTime.of(2026, 6, 6, 10, 30)),
                BookingStatus.BOOKED);
        // 在取得每周排程時回傳 booking
        given(bookingService.getWeeklySchedule()).willReturn(List.of(booking));
        
        // 發送請求
        mockMvc.perform(get("/api/rooms/schedule"))
                // 確保 API 通
                .andExpect(status().isOk())
                // 確保是一筆
                .andExpect(jsonPath("$.length()").value(1))
                // 確保借用者是豬
                .andExpect(jsonPath("$[0].borrower").value("豬"))
                // 確保狀態是 BOOKED
                .andExpect(jsonPath("$[0].status").value("BOOKED"));
    }
}

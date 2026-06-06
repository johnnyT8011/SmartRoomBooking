package com.example.meetingroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.exception.MeetingRoomNotFoundException;
import com.example.meetingroom.exception.UserNotFoundException;
import com.example.meetingroom.repository.BookingRepository;
import com.example.meetingroom.repository.MeetingRoomRepository;
import com.example.meetingroom.repository.UserRepository;

/**
 * Unit tests for {@link BookingService} with all collaborators mocked. Covers the seven
 * scenarios required by the spec: happy path, conflict, 5-minute lock, cancel, user/room
 * missing, and concurrent submission (only one succeeds).
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 5, 8, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 5, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 6, 5, 10, 30);

    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private MeetingRoomRepository meetingRoomRepository;
    @Mock private BookingRuleValidator validator;
    @Mock private NotificationService notificationService;

    private BookingService service;
    private User user;
    private MeetingRoom room;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new BookingService(bookingRepository, userRepository, meetingRoomRepository,
                validator, notificationService, clock);
        user = new User("員工甲", "a@example.com");
        ReflectionTestUtils.setField(user, "id", 1L);
        room = new MeetingRoom("會議室 A");
        ReflectionTestUtils.setField(room, "id", 2L);
    }

    private void stubExistingUserAndRoom() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(meetingRoomRepository.findByIdForUpdate(2L)).willReturn(Optional.of(room));
    }

    // ---- Scenario 1: Happy path (also covers the 5-minute lock) ----

    @Test
    void lockRoom_happyPath_createsLockingBookingWith5MinuteExpiry() {
        stubExistingUserAndRoom();
        given(bookingRepository.findOverlapping(eq(2L), eq(START), eq(END), any()))
                .willReturn(List.of());
        given(bookingRepository.save(any(Booking.class))).willAnswer(inv -> inv.getArgument(0));

        Booking result = service.lockRoom(1L, 2L, START, END);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.LOCKING);
        assertThat(result.getLockedAt()).isEqualTo(NOW);
        assertThat(result.getLockExpiresAt()).isEqualTo(NOW.plusMinutes(5));

        verify(validator).validateAll(user, START, END);
        verify(notificationService).sendSseEvent(eq(1L), any());
    }

    // ---- Scenario 2: Slot already taken ----

    @Test
    void lockRoom_whenOverlap_throwsConflict_andDoesNotSave() {
        stubExistingUserAndRoom();
        Booking existing = new Booking(user, room, START, END, BookingStatus.BOOKED);
        given(bookingRepository.findOverlapping(eq(2L), eq(START), eq(END), any()))
                .willReturn(List.of(existing));

        assertThatThrownBy(() -> service.lockRoom(1L, 2L, START, END))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("時段重疊");

        verify(bookingRepository, never()).save(any());
    }

    // ---- Scenario 3: Concurrent submission — only one survives ----

    @Test
    void lockRoom_concurrentRequests_onlyFirstSucceeds() {
        stubExistingUserAndRoom();
        Booking first = new Booking(user, room, START, END, BookingStatus.LOCKING);
        // First check sees an empty slot; the second sees the booking the first one inserted.
        given(bookingRepository.findOverlapping(eq(2L), eq(START), eq(END), any()))
                .willReturn(List.of())
                .willReturn(List.of(first));
        given(bookingRepository.save(any(Booking.class))).willAnswer(inv -> inv.getArgument(0));

        assertThat(service.lockRoom(1L, 2L, START, END)).isNotNull();
        assertThatThrownBy(() -> service.lockRoom(1L, 2L, START, END))
                .isInstanceOf(BookingConflictException.class);
    }

    // ---- Scenario: user does not exist ----

    @Test
    void lockRoom_whenUserMissing_throwsUserNotFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.lockRoom(99L, 2L, START, END))
                .isInstanceOf(UserNotFoundException.class);

        verify(bookingRepository, never()).save(any());
    }

    // ---- Scenario: room does not exist ----

    @Test
    void lockRoom_whenRoomMissing_throwsRoomNotFound() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(meetingRoomRepository.findByIdForUpdate(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.lockRoom(1L, 404L, START, END))
                .isInstanceOf(MeetingRoomNotFoundException.class);

        verify(bookingRepository, never()).save(any());
    }

    // ---- Scenario 11: User-initiated cancel keeps the record ----

    @Test
    void cancelBooking_setsCancelled_andDoesNotDelete() {
        Booking booking = new Booking(user, room, START, END, BookingStatus.BOOKED);
        ReflectionTestUtils.setField(booking, "id", 50L);
        given(bookingRepository.findDetailedById(50L)).willReturn(Optional.of(booking));

        Booking result = service.cancelBooking(50L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository, never()).delete(any());
        verify(bookingRepository, never()).deleteById(anyLong());
        verify(notificationService).sendSseEvent(eq(1L), any());
    }

    // ---- confirmBooking transitions and re-checks overlap ----

    @Test
    void confirmBooking_promotesLockingToBooked() {
        Booking booking = new Booking(user, room, START, END, BookingStatus.LOCKING);
        ReflectionTestUtils.setField(booking, "id", 60L);
        given(bookingRepository.findDetailedById(60L)).willReturn(Optional.of(booking));
        given(bookingRepository.findOverlappingExcluding(eq(2L), eq(START), eq(END), any(), eq(60L)))
                .willReturn(List.of());

        Booking result = service.confirmBooking(60L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void confirmBooking_whenSlotStolen_throwsConflict() {
        Booking booking = new Booking(user, room, START, END, BookingStatus.LOCKING);
        ReflectionTestUtils.setField(booking, "id", 61L);
        Booking other = new Booking(user, room, START, END, BookingStatus.BOOKED);
        given(bookingRepository.findDetailedById(61L)).willReturn(Optional.of(booking));
        given(bookingRepository.findOverlappingExcluding(eq(2L), eq(START), eq(END), any(), eq(61L)))
                .willReturn(List.of(other));

        assertThatThrownBy(() -> service.confirmBooking(61L))
                .isInstanceOf(BookingConflictException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.LOCKING);
    }

    // ---- checkIn ----

    @Test
    void checkIn_setsCheckedInAndTimestamp() {
        Booking booking = new Booking(user, room, START, END, BookingStatus.BOOKED);
        ReflectionTestUtils.setField(booking, "id", 70L);
        given(bookingRepository.findDetailedById(70L)).willReturn(Optional.of(booking));

        Booking result = service.checkIn(70L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(result.getCheckedInAt()).isEqualTo(NOW);
    }
}

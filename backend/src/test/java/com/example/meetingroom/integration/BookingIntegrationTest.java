package com.example.meetingroom.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.TimeRange;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.repository.BookingRepository;
import com.example.meetingroom.repository.MeetingRoomRepository;
import com.example.meetingroom.repository.UserRepository;
import com.example.meetingroom.service.BookingService;
import com.jayway.jsonpath.JsonPath;

/**
 * 整合測試（Integration Test）── 採「由下而上（Bottom-Up）」整合策略。
 *
 * <p>有別於專案內其他「每一層都把協作者 mock 掉」的單元 / 切片測試，本檔<b>完全不 mock</b>，
 * 用 {@code @SpringBootTest} 載入整個 Spring 容器（真實 Service、Validator、Repository、
 * 真實 H2、真實 Jackson/MVC、真實 GlobalExceptionHandler），驗證「各模組接在一起」之後的行為，
 * 補上專案先前缺少的『跨層端到端整合』。</p>
 *
 * <p>由下而上分三層，依序往上疊（用 {@code @Nested} + {@code @Order} 固定執行順序）：</p>
 * <pre>
 *   L1 持久層整合：Repository ↔ 真實 H2                         （最底層，先確定資料存取正確）
 *   L2 服務層整合：Service ↔ Validator ↔ Repository ↔ DB         （把業務邏輯＋悲觀鎖疊上去）
 *   L3 端到端整合：HTTP → Controller → Service → … → DB          （最後疊上 Web 層，整條打通）
 * </pre>
 *
 * <p>策略說明：傳統 bottom-up 需為上層另寫「驅動程式 (driver)」。在 Spring 裡，
 * {@code @SpringBootTest} 直接把真實上層元件接上來，等於「以真實上層當 driver」，
 * 所以 L1→L2→L3 能無縫往上疊。</p>
 *
 * <p>並發競態（多執行緒同搶同一房間）這條情境，由獨立的
 * {@code service.BookingConcurrencyIntegrationTest} 涵蓋，與本檔互補。</p>
 *
 * <p>注意：本類別掛 {@code @Transactional}，每個測試方法跑完即 rollback，彼此隔離；
 * {@code DataSeeder} 開機種下的會議室/使用者是另外 commit 的，不受本交易影響。
 * 測試資料一律自建（名稱/email 與種子資料不同），避開 unique 限制。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("預約系統整合測試（Bottom-Up：L1 持久層 → L2 服務層 → L3 端到端）")
class BookingIntegrationTest {

    /** LockRequest 的 @JsonFormat 接受 yyyy-MM-dd'T'HH:mm[:ss]，這裡固定補到秒。 */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired private MockMvc mockMvc;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MeetingRoomRepository meetingRoomRepository;
    /** 與正式碼共用同一個 Clock bean（MutableClock，offset 0 = 真實時間）。 */
    @Autowired private Clock clock;

    private User userA;
    private User userB;
    private MeetingRoom room;
    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        // 自建測試資料（名稱/email 都跟 DataSeeder 種的不同，避開 unique 限制），用真實 Repository 寫進真實 H2
        userA = userRepository.save(new User("整合測試員工A", "it-a@test.com"));
        userB = userRepository.save(new User("整合測試員工B", "it-b@test.com"));
        room = meetingRoomRepository.save(new MeetingRoom("整合測試會議室"));

        // 時間用「明天 10:00~11:00」：未過去、在 7 天視窗內、對齊 30 分鐘、時長 60 分鐘 → 通過所有規則
        start = LocalDate.now(clock).plusDays(1).atTime(10, 0);
        end = start.plusHours(1);
    }

    private TimeRange range(LocalDateTime s, LocalDateTime e) {
        return new TimeRange(s, e);
    }

    private String lockJson(Long userId, Long roomId, LocalDateTime s, LocalDateTime e) {
        return """
                {"userId":%d,"roomId":%d,"startTime":"%s","endTime":"%s"}
                """.formatted(userId, roomId, FMT.format(s), FMT.format(e));
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // L1 ── 持久層整合：Repository ↔ 真實 H2
    //   最底層。先確定 anti-overlap 查詢透過真實 SQL/H2 真的查得對，上層才有意義。
    //   （此層另有 @DataJpaTest 版的 BookingRepositoryTest；這裡放一條代表，串成完整 bottom-up）
    // ──────────────────────────────────────────────────────────────────────────────
    @Nested
    @Order(1)
    @DisplayName("L1 持久層整合：Repository ↔ 真實 H2")
    class L1_PersistenceLayer {

        @Test
        @DisplayName("findOverlapping 透過真實 H2 抓到重疊的有效預約")
        void findOverlapping_detectsConflictThroughRealDb() {
            // 先存一筆 BOOKED：明天 10:00~11:00
            bookingRepository.save(new Booking(userA, room, range(start, end), BookingStatus.BOOKED));

            // 查 10:30~11:30 是否與既有預約重疊 → 應抓到 1 筆
            List<Booking> hits = bookingRepository.findOverlapping(
                    room.getId(),
                    range(start.plusMinutes(30), end.plusMinutes(30)),
                    BookingStatus.activeStatuses());

            assertThat(hits).hasSize(1);
        }

        @Test
        @DisplayName("邊緣相接（10:00↔11:00）不算重疊 → 真實 SQL 回 0 筆")
        void findOverlapping_touchingEdges_isNotOverlap() {
            // 既有預約 10:00~11:00
            bookingRepository.save(new Booking(userA, room, range(start, end), BookingStatus.BOOKED));

            // 緊接其後 11:00~12:00（邊緣相接）→ 半開區間定義下不重疊 → 0 筆
            List<Booking> hits = bookingRepository.findOverlapping(
                    room.getId(),
                    range(end, end.plusHours(1)),
                    BookingStatus.activeStatuses());

            assertThat(hits).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // L2 ── 服務層整合：Service ↔ Validator ↔ Repository ↔ DB（不 mock 任何協作者）
    //   把業務邏輯疊在 L1 之上：真實 BookingRuleValidator + 真實悲觀鎖 + 真實寫入。
    // ──────────────────────────────────────────────────────────────────────────────
    @Nested
    @Order(2)
    @DisplayName("L2 服務層整合：Service ↔ Validator ↔ Repository ↔ DB")
    class L2_ServiceLayer {

        @Test
        @DisplayName("lockRoom 真的把 LOCKING 預約寫進 DB，且過期時間 = 鎖定時間 + 5 分鐘")
        void lockRoom_persistsLockingThroughAllRealBeans() {
            Booking saved = bookingService.lockRoom(userA.getId(), room.getId(), range(start, end));

            // 重新從 DB 撈出來，證明「不是只回傳物件，而是真的落地」
            Booking reloaded = bookingRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.LOCKING);
            assertThat(reloaded.getLockExpiresAt()).isEqualTo(reloaded.getLockedAt().plusMinutes(5));
        }

        @Test
        @DisplayName("不同使用者搶同一間同時段：真實 overlap 偵測 → 拋房間衝突")
        void lockRoom_secondUserSameRoom_roomOverlapRejected() {
            // 甲先鎖定 10:00~11:00
            bookingService.lockRoom(userA.getId(), room.getId(), range(start, end));

            // 乙再鎖定同一間 10:30~11:30（重疊）→ 經真實 JPQL 查到甲的鎖 → 房間衝突
            assertThatThrownBy(() -> bookingService.lockRoom(
                    userB.getId(), room.getId(), range(start.plusMinutes(30), end.plusMinutes(30))))
                    .isInstanceOf(BookingConflictException.class)
                    .hasMessageContaining("時段重疊");
        }

        @Test
        @DisplayName("同一使用者跨房間重複預約：真實 validator 查 DB → 拋使用者衝突")
        void lockRoom_sameUserDifferentRoom_userConflictRejected() {
            MeetingRoom room2 = meetingRoomRepository.save(new MeetingRoom("整合測試會議室B"));

            // 甲先鎖定 A 室 10:00~11:00
            bookingService.lockRoom(userA.getId(), room.getId(), range(start, end));

            // 甲又想鎖 B 室 10:30~11:30（自己撞自己）→ validateAll 內的 checkUserConflict 先攔下
            assertThatThrownBy(() -> bookingService.lockRoom(
                    userA.getId(), room2.getId(), range(start.plusMinutes(30), end.plusMinutes(30))))
                    .isInstanceOf(BookingConflictException.class)
                    .hasMessageContaining("已有其他預約");
        }

        @Test
        @DisplayName("完整生命週期（純服務層）：lock → confirm → checkIn 真的逐步改 DB 狀態")
        void lifecycle_lockConfirmCheckIn_throughServiceBeans() {
            Booking locked = bookingService.lockRoom(userA.getId(), room.getId(), range(start, end));
            assertThat(locked.getStatus()).isEqualTo(BookingStatus.LOCKING);

            bookingService.confirmBooking(locked.getId());
            bookingService.checkIn(locked.getId());

            assertThat(bookingRepository.findById(locked.getId()).orElseThrow().getStatus())
                    .isEqualTo(BookingStatus.CHECKED_IN);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // L3 ── 端到端整合：HTTP → Controller → Service → Validator → Repository → DB
    //   最後疊上 Web 層，用 MockMvc 發真實 HTTP，整條鏈路全部真貨打通。
    // ──────────────────────────────────────────────────────────────────────────────
    @Nested
    @Order(3)
    @DisplayName("L3 端到端整合：HTTP → Controller → Service → DB")
    class L3_EndToEndApi {

        @Test
        @DisplayName("POST /api/bookings/lock → 201，且資料真的寫進 DB")
        void postLock_returns201_andPersists() throws Exception {
            mockMvc.perform(post("/api/bookings/lock")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(lockJson(userA.getId(), room.getId(), start, end)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("LOCKING"))
                    .andExpect(jsonPath("$.roomName").value("整合測試會議室"))
                    .andExpect(jsonPath("$.borrower").value("整合測試員工A"));

            // 證明確實落地（DataSeeder 不種 booking，本交易內只有這一筆）
            assertThat(bookingRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("完整生命週期：lock(201) → confirm(200 BOOKED) → checkin(200 CHECKED_IN)")
        void fullLifecycle_lockConfirmCheckIn() throws Exception {
            // 1) lock，並從回應取出 booking id
            String body = mockMvc.perform(post("/api/bookings/lock")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(lockJson(userA.getId(), room.getId(), start, end)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("LOCKING"))
                    .andReturn().getResponse().getContentAsString();
            long bookingId = ((Number) JsonPath.read(body, "$.id")).longValue();

            // 2) confirm：LOCKING → BOOKED（真實重驗 overlap 排除自身）
            mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("BOOKED"));

            // 3) checkin：BOOKED → CHECKED_IN
            mockMvc.perform(post("/api/bookings/" + bookingId + "/checkin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CHECKED_IN"));

            // DB 內最終狀態確為 CHECKED_IN
            assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                    .isEqualTo(BookingStatus.CHECKED_IN);
        }

        @Test
        @DisplayName("第二筆重疊預約 → 409，訊息為時段重疊")
        void secondOverlappingLock_returns409() throws Exception {
            // 甲鎖定成功
            mockMvc.perform(post("/api/bookings/lock")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(lockJson(userA.getId(), room.getId(), start, end)))
                    .andExpect(status().isCreated());

            // 乙搶同一間重疊時段 → 整條鏈路（含真實悲觀鎖 + overlap 查詢）→ 409
            mockMvc.perform(post("/api/bookings/lock")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(lockJson(userB.getId(), room.getId(),
                                    start.plusMinutes(30), end.plusMinutes(30))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("時段重疊，預約失敗"));
        }

        @Test
        @DisplayName("違反規則的預約（過去時間）→ 真實 validator 攔下 → 400")
        void bookingInThePast_returns400() throws Exception {
            // 昨天 10:00~11:00，仍對齊 30 分鐘、時長合法，唯一違反的是「不可預約過去」
            LocalDateTime pastStart = LocalDate.now(clock).minusDays(1).atTime(10, 0);
            LocalDateTime pastEnd = pastStart.plusHours(1);

            mockMvc.perform(post("/api/bookings/lock")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(lockJson(userA.getId(), room.getId(), pastStart, pastEnd)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("無法預約過去的時間"));
        }

        @Test
        @DisplayName("缺欄位的請求 → bean validation → 400（沒進到 Service）")
        void missingFields_returns400() throws Exception {
            mockMvc.perform(post("/api/bookings/lock")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roomId\":1}"))
                    .andExpect(status().isBadRequest());
        }
    }
}

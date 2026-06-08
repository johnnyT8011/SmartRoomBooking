package com.example.meetingroom.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.TimeRange;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.repository.BookingRepository;
import com.example.meetingroom.repository.MeetingRoomRepository;
import com.example.meetingroom.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 並發整合測試 — 系統招牌「防重疊預約」的端到端驗證。
 *
 * <p>與 {@code BookingServiceTest} 的本質差異：那支用 Mockito 把 repository 換成假的，
 * 因此 {@code findByIdForUpdate} 的悲觀鎖語意在那裡並不存在，只驗了「假設鎖有效時邏輯對不對」。
 * 本測試以 {@code @SpringBootTest} 起真 context、真 H2、真交易，讓多條執行緒同時對
 * 同一房間同一時段下手，實際把那把 row lock 跑起來，驗證「最多只有一筆有效預約存活」。</p>
 *
 * <p>刻意使用<b>不同的使用者</b>各搶一次：若用同一使用者，失敗會由 validator 的
 * checkUserConflict（自我重疊）攔下，就無法分離出「房間鎖」這條路徑。</p>
 *
 * <p>注意：本類別<b>不可</b>標 {@code @Transactional}，否則整個測試會被單一交易包住，
 * 各執行緒無法各自提交、悲觀鎖也無從觀察。資料以手動清理替代交易回滾。</p>
 */
@SpringBootTest
@DisplayName("並發下的房間鎖：同一時段只能有一筆有效預約")
class BookingConcurrencyIntegrationTest {

    private static final int THREADS = 6;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRoomRepository meetingRoomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Long roomId;
    private final List<Long> userIds = new ArrayList<>();
    private TimeRange range;

    @BeforeEach
    void setUp() {
        // 清掉任何由 DataSeeder 或前一個測試殘留的預約，確保計數基準乾淨。
        bookingRepository.deleteAll();

        roomId = meetingRoomRepository.save(new MeetingRoom("並發測試室")).getId();

        userIds.clear();
        for (int i = 0; i < THREADS; i++) {
            User u = userRepository.save(new User("user" + i, "user" + i + "@test.com"));
            userIds.add(u.getId());
        }

        // 對齊 30 分鐘、未來、長度合法（1 小時）的時段，確保唯一可能的失敗原因是房間重疊。
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(10, 0);
        range = new TimeRange(start, start.plusHours(1));
    }

    @AfterEach
    void tearDown() {
        bookingRepository.deleteAll();
    }

    @Test
    @DisplayName("六位使用者同時搶同一房間同一時段，恰好一人成功、其餘被衝突拒絕")
    void onlyOneLockSurvivesUnderConcurrentContention() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS); // 全部就緒
        CountDownLatch fire = new CountDownLatch(1);        // 同時起跑的閘門
        AtomicInteger successes = new AtomicInteger(0);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < THREADS; i++) {
            final Long uid = userIds.get(i);
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();                 // 一起等閘門打開，逼出真正的競態
                    bookingService.lockRoom(uid, roomId, range);
                    successes.incrementAndGet();
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }

        ready.await();                            // 確認六條執行緒都已停在閘門前
        fire.countDown();                         // 同時放行
        pool.shutdown();
        boolean finished = pool.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(finished).as("所有執行緒應在時限內結束").isTrue();

        // 核心不變式：同一房間同一時段，有效預約恰好一筆 —— 不論輸家拿到什麼例外，都不得雙重預約。
        assertThat(bookingRepository.findOverlapping(roomId, range, BookingStatus.activeStatuses()))
                .as("資料庫中該時段的有效預約數")
                .hasSize(1);

        // 行為面：恰好一人成功、其餘五人失敗。
        assertThat(successes.get()).as("成功數").isEqualTo(1);
        assertThat(failures).as("失敗數").hasSize(THREADS - 1);

        // 失敗原因應為重疊衝突 —— 因 row lock 將請求序列化，後到者必然先看到已提交的 LOCKING 而被 ensureNoRoomOverlap 拒絕。
        assertThat(failures)
                .as("每個失敗都應是房間重疊衝突")
                .allMatch(BookingConflictException.class::isInstance);
    }
}

package com.example.roombooking.repo;

import com.example.roombooking.model.Booking;
import com.example.roombooking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // 防重疊核心查詢：兩個區間 [s1,e1) 與 [s2,e2) 重疊 <=> s1 < e2 AND e1 > s2
    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND b.status IN :statuses " +
           "AND b.startTime < :end AND b.endTime > :start")
    List<Booking> findOverlapping(@Param("roomId") Long roomId,
                                  @Param("statuses") List<BookingStatus> statuses,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByStartTimeGreaterThanEqualAndStartTimeLessThan(LocalDateTime from, LocalDateTime to);
}

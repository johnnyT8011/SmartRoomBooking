import dayjs from 'dayjs';
import BookingPopover from '../components/BookingPopover';
import { WEEKDAY_SHORT, roomColor } from '../lib/constants';
import { ACTIVE, fmtHM } from '../lib/utils';

/**
 * Month view — a Monday-first calendar grid with booking chips per day.
 */
export default function MonthView({ rooms, bookings, baseDate, nowMs, handlers }) {
  const monthStart = baseDate.startOf('month');
  const startDow = (monthStart.day() + 6) % 7; // leading blanks (Monday-based)
  const daysInMonth = monthStart.daysInMonth();
  const todayStr = dayjs(nowMs).format('YYYY-MM-DD');
  const chipOf = (roomId) => {
    const idx = rooms.findIndex((r) => r.id === roomId);
    return roomColor(idx < 0 ? 0 : idx).chip;
  };

  return (
    <div className="timeline">
      <div className="month-head">
        {WEEKDAY_SHORT.map((d) => <div key={d}>{d}</div>)}
      </div>
      <div className="month-grid">
        {Array.from({ length: startDow }).map((_, i) => (
          <div key={`blank-${i}`} className="month-cell blank" />
        ))}
        {Array.from({ length: daysInMonth }).map((_, i) => {
          const day = monthStart.add(i, 'day');
          const dayStr = day.format('YYYY-MM-DD');
          const isToday = dayStr === todayStr;
          const dayBookings = bookings
            .filter((b) => ACTIVE.has(b.status) && dayjs(b.startTime).format('YYYY-MM-DD') === dayStr)
            .sort((a, b) => dayjs(a.startTime) - dayjs(b.startTime));
          return (
            <div key={i} className="month-cell">
              <span className={`day-num ${isToday ? 'today' : ''}`}>{day.date()}</span>
              <div className="month-chips no-scrollbar">
                {dayBookings.map((b) => (
                  <BookingPopover key={b.id} b={b} nowMs={nowMs} handlers={handlers}>
                    <div className={`month-chip ${chipOf(b.roomId)}`} style={{ cursor: 'pointer' }}>
                      <span>{fmtHM(b.startTime)} {(b.roomName || '').slice(-1)}</span>
                      <span style={{ background: 'rgba(255,255,255,0.25)', padding: '0 4px', borderRadius: 4 }}>
                        {b.borrower}
                      </span>
                    </div>
                  </BookingPopover>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

import dayjs from 'dayjs';
import BookingBlock from '../components/BookingBlock';
import { GridLines, NowLine, TimeAxis } from '../components/TimelineParts';
import { TOTAL_MINUTES, roomColor } from '../lib/constants';
import { ACTIVE, blockGeometry, nowMinutes } from '../lib/utils';

/**
 * Day view — a Gantt chart with one row per meeting room (mirrors the prototype's day view).
 */
export default function DayView({ rooms, bookings, baseDate, nowMs, handlers }) {
  const dayStr = baseDate.format('YYYY-MM-DD');
  const isToday = dayjs(nowMs).format('YYYY-MM-DD') === dayStr;
  const nowMin = nowMinutes(nowMs);

  return (
    <div className="timeline">
      <TimeAxis />
      <div style={{ position: 'relative' }}>
        {rooms.map((room, idx) => {
          const color = roomColor(idx);
          const dayBookings = bookings.filter(
            (b) => ACTIVE.has(b.status) && b.roomId === room.id
              && dayjs(b.startTime).format('YYYY-MM-DD') === dayStr,
          );
          return (
            <div key={room.id} className={`lane-row ${idx % 2 ? 'alt' : ''}`} style={{ height: 96 }}>
              <div className="row-label">
                <span><span className={`dot ${color.dot}`} />{room.roomName}</span>
              </div>
              <div className="canvas">
                <GridLines />
                {isToday && nowMin >= 0 && nowMin <= TOTAL_MINUTES && (
                  <NowLine pct={(nowMin / TOTAL_MINUTES) * 100} />
                )}
                {dayBookings.map((b) => {
                  const { left, width } = blockGeometry(b);
                  return (
                    <BookingBlock
                      key={b.id}
                      b={b}
                      colorBlock={color.block}
                      nowMs={nowMs}
                      handlers={handlers}
                      style={{ left: `${left}%`, width: `${width}%`, top: 14, height: 64 }}
                    />
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

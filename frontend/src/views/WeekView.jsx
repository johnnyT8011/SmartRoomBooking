import dayjs from 'dayjs';
import BookingBlock from '../components/BookingBlock';
import { GridLines, NowLine, TimeAxis } from '../components/TimelineParts';
import { TOTAL_MINUTES, WEEKDAYS, roomColor } from '../lib/constants';
import { ACTIVE, blockGeometry, nowMinutes, weekStart } from '../lib/utils';

const LANE_HEIGHT = 46;

/**
 * Horizontal week view — one row per weekday (Mon–Sun). Overlapping bookings within a day
 * are stacked into "lanes" using the same greedy algorithm as the prototype.
 */
export default function WeekView({ rooms, bookings, baseDate, nowMs, handlers }) {
  const start = weekStart(baseDate);
  const days = Array.from({ length: 7 }).map((_, i) => start.add(i, 'day'));
  const colorOfRoom = (roomId) => {
    const idx = rooms.findIndex((r) => r.id === roomId);
    return roomColor(idx < 0 ? 0 : idx);
  };
  const nowMin = nowMinutes(nowMs);
  const todayStr = dayjs(nowMs).format('YYYY-MM-DD');

  return (
    <div className="timeline">
      <TimeAxis />
      <div style={{ position: 'relative' }}>
        {days.map((day, dayIdx) => {
          const dayStr = day.format('YYYY-MM-DD');
          const isToday = dayStr === todayStr;

          // greedy lane assignment
          const dayBookings = bookings
            .filter((b) => ACTIVE.has(b.status) && dayjs(b.startTime).format('YYYY-MM-DD') === dayStr)
            .sort((a, b) => dayjs(a.startTime) - dayjs(b.startTime));
          const lanes = [];
          dayBookings.forEach((b) => {
            let placed = false;
            for (let i = 0; i < lanes.length; i++) {
              const last = lanes[i][lanes[i].length - 1];
              if (dayjs(last.endTime).valueOf() <= dayjs(b.startTime).valueOf()) {
                lanes[i].push(b);
                b._lane = i;
                placed = true;
                break;
              }
            }
            if (!placed) {
              b._lane = lanes.length;
              lanes.push([b]);
            }
          });
          const totalLanes = Math.max(1, lanes.length);
          const rowHeight = totalLanes * LANE_HEIGHT + 16;

          return (
            <div
              key={dayIdx}
              className={`lane-row ${isToday ? 'today' : dayIdx % 2 ? 'alt' : ''}`}
              style={{ minHeight: rowHeight }}
            >
              <div className="row-label">
                <span style={{ fontWeight: 700, color: isToday ? '#4f46e5' : undefined }}>
                  {WEEKDAYS[dayIdx]}
                </span>
                <span className="sub">{day.month() + 1}/{day.date()}</span>
              </div>
              <div className="canvas">
                <GridLines />
                {isToday && nowMin >= 0 && nowMin <= TOTAL_MINUTES && (
                  <NowLine pct={(nowMin / TOTAL_MINUTES) * 100} />
                )}
                {dayBookings.map((b) => {
                  const { left, width } = blockGeometry(b);
                  const color = colorOfRoom(b.roomId);
                  return (
                    <BookingBlock
                      key={b.id}
                      b={b}
                      colorBlock={color.block}
                      nowMs={nowMs}
                      handlers={handlers}
                      style={{
                        left: `${left}%`,
                        width: `${width}%`,
                        top: 8 + b._lane * LANE_HEIGHT,
                        height: LANE_HEIGHT - 6,
                      }}
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

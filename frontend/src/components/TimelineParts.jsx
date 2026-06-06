import { START_HOUR, END_HOUR } from '../lib/constants';

const HOURS = END_HOUR - START_HOUR;

/** The shared hour ruler shown above day/week timelines. */
export function TimeAxis() {
  return (
    <div className="time-axis">
      {Array.from({ length: HOURS + 1 }).map((_, i) => (
        <span key={i} className="tick" style={{ left: `${(i / HOURS) * 100}%` }}>
          {String(START_HOUR + i).padStart(2, '0')}:00
        </span>
      ))}
    </div>
  );
}

/** Vertical dashed hour grid lines inside a canvas. */
export function GridLines() {
  return Array.from({ length: HOURS }).map((_, i) => (
    <div key={i} className="grid-line" style={{ left: `${((i + 1) / HOURS) * 100}%` }} />
  ));
}

/** The red current-time indicator. `pct` is 0–100 across the band. */
export function NowLine({ pct }) {
  return (
    <div className="now-line" style={{ left: `${pct}%` }}>
      <div className="knob" />
    </div>
  );
}

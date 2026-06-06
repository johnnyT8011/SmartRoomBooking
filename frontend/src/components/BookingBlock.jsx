import BookingPopover from './BookingPopover';
import { displayState, fmtHM } from '../lib/utils';

/**
 * A single booking rendered as an absolutely-positioned block on a day/week timeline.
 * `style` supplies the left/width/top/height geometry computed by the parent view.
 */
export default function BookingBlock({ b, colorBlock, nowMs, style, handlers }) {
  const state = displayState(b, nowMs);

  let cls = `booking-block ${colorBlock}`;
  if (state === 'checkedin') cls = 'booking-block c-green';
  else if (state === 'locking') cls = 'booking-block c-amber is-pending';
  else if (state === 'pending') cls = `booking-block ${colorBlock} is-pending`;
  else if (state === 'finished') cls = `booking-block ${colorBlock} is-finished`;

  const shortRoom = (b.roomName || '').replace('會議室 ', '');

  return (
    <BookingPopover b={b} nowMs={nowMs} handlers={handlers}>
      <div className={cls} style={{ ...style, cursor: 'pointer' }}>
        <div className="bk-title">
          <span>{shortRoom}</span>
          <span className="tag">{b.borrower}</span>
        </div>
        <div className="bk-time">{fmtHM(b.startTime)} - {fmtHM(b.endTime)}</div>
      </div>
    </BookingPopover>
  );
}

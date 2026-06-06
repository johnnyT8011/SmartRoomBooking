import dayjs from 'dayjs';
import { START_HOUR, TOTAL_MINUTES } from './constants';

/** Backend-friendly local ISO string, e.g. 2026-06-06T10:00:00 (no timezone). */
export const isoLocal = (d) => dayjs(d).format('YYYY-MM-DDTHH:mm:ss');

export const fmtHM = (iso) => dayjs(iso).format('HH:mm');

/** Minutes from START_HOUR for a timestamp, clamped to the visible [0, TOTAL_MINUTES] band. */
export function minutesIntoBand(iso) {
  const d = dayjs(iso);
  return d.hour() * 60 + d.minute() - START_HOUR * 60;
}

/** Minutes-into-band for the current time (used to position the red "now" line). */
export function nowMinutes(nowMs) {
  const n = dayjs(nowMs);
  return n.hour() * 60 + n.minute() - START_HOUR * 60;
}

/** Horizontal geometry (percent) of a booking on the time axis. */
export function blockGeometry(b) {
  const startMin = minutesIntoBand(b.startTime);
  const durationMin = dayjs(b.endTime).diff(dayjs(b.startTime), 'minute');
  const left = Math.max(0, (startMin / TOTAL_MINUTES) * 100);
  const width = Math.min(100 - left, (durationMin / TOTAL_MINUTES) * 100);
  return { left, width };
}

/**
 * Derive a visual state from the persisted status plus the current time, so blocks update
 * live (pending check-in, finished, etc.) the way the prototype did.
 */
export function displayState(b, nowMs) {
  const start = dayjs(b.startTime).valueOf();
  const end = dayjs(b.endTime).valueOf();
  const finished = nowMs >= end;
  if (b.status === 'CHECKED_IN') return finished ? 'finished' : 'checkedin';
  if (b.status === 'LOCKING') return 'locking';
  if (b.status === 'BOOKED') {
    if (finished) return 'finished';
    if (nowMs >= start) return 'pending'; // started but not checked in → needs check-in
    return 'booked';
  }
  return 'other';
}

/** Active statuses we render on the timeline (released/cancelled ones drop out). */
export const ACTIVE = new Set(['LOCKING', 'BOOKED', 'CHECKED_IN']);

/** Monday 00:00 of the week containing `d` (dayjs). */
export function weekStart(d) {
  const offset = (d.day() + 6) % 7; // dayjs: Sunday=0 → make Monday the first day
  return d.subtract(offset, 'day').startOf('day');
}

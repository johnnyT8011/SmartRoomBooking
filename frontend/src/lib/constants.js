export const START_HOUR = 8;
export const END_HOUR = 18;
export const TOTAL_MINUTES = (END_HOUR - START_HOUR) * 60;

// Color set assigned to rooms by their order in the fetched list (matches the prototype).
const ROOM_PALETTE = [
  { block: 'c-blue', dot: 'c-blue', chip: 'b-blue' },
  { block: 'c-purple', dot: 'c-purple', chip: 'b-purple' },
  { block: 'c-teal', dot: 'c-teal', chip: 'b-teal' },
];

export function roomColor(index) {
  return ROOM_PALETTE[index % ROOM_PALETTE.length];
}

export const WEEKDAYS = ['週一', '週二', '週三', '週四', '週五', '週六', '週日'];
export const WEEKDAY_SHORT = ['一', '二', '三', '四', '五', '六', '日'];

export const STATUS_LABEL = {
  LOCKING: '鎖定中',
  BOOKED: '已預約',
  CHECKED_IN: '已報到',
  CANCELLED: '已取消',
  EXPIRED: '已釋放',
};

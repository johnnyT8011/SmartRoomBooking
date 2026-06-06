import { Button, Popover, Space, Tag } from 'antd';
import { displayState, fmtHM } from '../lib/utils';
import { STATUS_LABEL } from '../lib/constants';

/**
 * Wraps any trigger element (timeline block / month chip) and shows a click popover with
 * the booking details and the actions valid for its current state:
 *   LOCKING  → 確認 / 取消
 *   started & not checked in → 報到 / 取消
 *   future BOOKED → 取消
 */
export default function BookingPopover({ b, nowMs, handlers, children }) {
  const state = displayState(b, nowMs);

  const actions = [];
  if (state === 'locking') {
    actions.push(<Button key="c" size="small" type="primary" onClick={() => handlers.onConfirm(b)}>確認預約</Button>);
    actions.push(<Button key="x" size="small" danger onClick={() => handlers.onCancel(b)}>取消</Button>);
  } else if (state === 'pending') {
    actions.push(<Button key="i" size="small" type="primary" onClick={() => handlers.onCheckIn(b)}>報到</Button>);
    actions.push(<Button key="x" size="small" danger onClick={() => handlers.onCancel(b)}>取消</Button>);
  } else if (state === 'booked') {
    actions.push(<Button key="x" size="small" danger onClick={() => handlers.onCancel(b)}>取消預約</Button>);
  }

  const content = (
    <div style={{ minWidth: 190 }}>
      <div style={{ marginBottom: 6 }}>
        <strong>{b.roomName}</strong>{' '}
        <Tag color={state === 'pending' ? 'orange' : undefined}>{STATUS_LABEL[b.status] || b.status}</Tag>
      </div>
      <div style={{ color: '#64748b', marginBottom: 4 }}>借用人：{b.borrower}</div>
      <div style={{ color: '#64748b', marginBottom: actions.length ? 10 : 0 }}>
        {fmtHM(b.startTime)} – {fmtHM(b.endTime)}
      </div>
      {actions.length > 0 && <Space>{actions}</Space>}
    </div>
  );

  return (
    <Popover content={content} trigger="click" placement="top">
      {children}
    </Popover>
  );
}

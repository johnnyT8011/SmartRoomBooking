import { useState } from 'react';
import { Button, DatePicker, Space, Tag } from 'antd';
import dayjs from 'dayjs';
import { isoLocal } from '../lib/utils';

/**
 * 時空模擬控制台 — shifts the backend system clock so the 5-minute lock release and
 * 15-minute no-show release can be demonstrated without waiting in real time.
 */
export default function SimConsole({ nowMs, simulated, onAdvance, onJump, onReset }) {
  const [target, setTarget] = useState(null);

  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
      <span style={{ color: '#c7d2fe', fontSize: 13 }}>系統時間</span>
      <span className="clock">{dayjs(nowMs).format('YYYY/MM/DD HH:mm:ss')}</span>
      {simulated && <Tag color="gold">模擬中</Tag>}
      <Space.Compact>
        <Button size="small" onClick={() => onAdvance(-15)}>⏪ 倒退15分</Button>
        <Button size="small" onClick={() => onAdvance(15)}>⏩ 快轉15分</Button>
      </Space.Compact>
      <DatePicker
        showTime
        size="small"
        value={target}
        onChange={setTarget}
        placeholder="跳轉至…"
        style={{ width: 190 }}
      />
      <Button size="small" type="primary" disabled={!target} onClick={() => onJump(isoLocal(target))}>
        🚀 跳轉
      </Button>
      {simulated && <Button size="small" onClick={onReset}>↩ 還原真實時間</Button>}
    </div>
  );
}

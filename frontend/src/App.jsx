import { useCallback, useEffect, useRef, useState } from 'react';
import { App as AntApp, Button, Segmented, Select, Spin } from 'antd';
import dayjs from 'dayjs';

import BookingForm from './components/BookingForm';
import SimConsole from './components/SimConsole';
import DayView from './views/DayView';
import WeekView from './views/WeekView';
import MonthView from './views/MonthView';
import {
  cancelBooking, checkIn, confirmBooking, getRooms, getSchedule, getSimTime, getUsers,
  lockRoom, simAdvance, simJump, simReset, sseUrl,
} from './lib/api';
import { isoLocal, weekStart } from './lib/utils';

function rangeFor(viewMode, baseDate) {
  if (viewMode === 'day') {
    const from = baseDate.startOf('day');
    return [from, from.add(1, 'day')];
  }
  if (viewMode === 'week') {
    const from = weekStart(baseDate);
    return [from, from.add(7, 'day')];
  }
  const from = baseDate.startOf('month');
  return [from, from.add(1, 'month')];
}

export default function App() {
  const { message } = AntApp.useApp();

  const [rooms, setRooms] = useState([]);
  const [users, setUsers] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [viewMode, setViewMode] = useState('week');
  const [baseDate, setBaseDate] = useState(dayjs());
  const [nowMs, setNowMs] = useState(Date.now());
  const [simulated, setSimulated] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [currentUserId, setCurrentUserId] = useState(null);
  const [ready, setReady] = useState(false);

  // offset (ms) between the backend's (possibly simulated) clock and the browser clock,
  // so the red "now" line + time-derived block states follow the simulated system time.
  const clockOffsetRef = useRef(0);

  const applyServerTime = (resp) => {
    const serverMs = dayjs(resp.now).valueOf();
    clockOffsetRef.current = serverMs - Date.now();
    setNowMs(serverMs);
    setSimulated(resp.simulated);
  };

  // live clock ticking from the backend-synced offset
  useEffect(() => {
    const t = setInterval(() => setNowMs(Date.now() + clockOffsetRef.current), 1000);
    return () => clearInterval(t);
  }, []);

  // initial master data + system time
  useEffect(() => {
    Promise.all([getRooms(), getUsers(), getSimTime()])
      .then(([r, u, t]) => {
        setRooms(r);
        setUsers(u);
        setCurrentUserId(u[0]?.id ?? null);
        applyServerTime(t);
        setReady(true);
      })
      .catch((e) => message.error('初始化失敗：' + e.message));
  }, [message]);

  const refresh = useCallback(async () => {
    const [from, to] = rangeFor(viewMode, baseDate);
    try {
      setBookings(await getSchedule(isoLocal(from), isoLocal(to)));
    } catch (e) {
      message.error('載入預約失敗：' + e.message);
    }
  }, [viewMode, baseDate, message]);

  useEffect(() => {
    if (ready) refresh();
  }, [ready, refresh]);

  // keep the SSE handler pointed at the latest refresh without reopening the connection
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;

  // Server-Sent Events for the selected user
  useEffect(() => {
    if (!currentUserId) return undefined;
    const es = new EventSource(sseUrl(currentUserId));
    const onNotify = (ev) => {
      message.info(ev.data);
      refreshRef.current();
    };
    es.addEventListener('notification', onNotify);
    es.onerror = () => { /* browser auto-reconnects */ };
    return () => {
      es.removeEventListener('notification', onNotify);
      es.close();
    };
  }, [currentUserId, message]);

  const act = async (fn, okMsg) => {
    try {
      await fn();
      message.success(okMsg);
      refresh();
    } catch (e) {
      message.error(e.message);
    }
  };

  const handlers = {
    onConfirm: (b) => act(() => confirmBooking(b.id), '預約已確認'),
    onCancel: (b) => act(() => cancelBooking(b.id), '預約已取消，時段已釋放'),
    onCheckIn: (b) => act(() => checkIn(b.id), '報到成功'),
  };

  const onLock = async (payload) => {
    setSubmitting(true);
    try {
      await lockRoom(payload);
      message.success('已鎖定時段，請於時段方塊點擊「確認預約」完成');
      refresh();
    } catch (e) {
      message.error(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  const sim = async (fn) => {
    try {
      applyServerTime(await fn());
      refresh();
    } catch (e) {
      message.error(e.message);
    }
  };

  const navigate = (dir) => {
    if (viewMode === 'day') setBaseDate((d) => d.add(dir, 'day'));
    else if (viewMode === 'week') setBaseDate((d) => d.add(dir * 7, 'day'));
    else setBaseDate((d) => d.add(dir, 'month'));
  };

  const title = (() => {
    if (viewMode === 'day') return baseDate.format('YYYY年M月D日 dddd');
    if (viewMode === 'week') {
      const s = weekStart(baseDate);
      return `${s.format('M/D')} - ${s.add(6, 'day').format('M/D')}`;
    }
    return baseDate.format('YYYY年M月');
  })();

  const viewProps = { rooms, bookings, baseDate, nowMs, handlers };

  return (
    <div className="page">
      <header className="app-header">
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h1>🗓️ 智慧會議室預約系統</h1>
            <div style={{ marginTop: 10 }}>
              <SimConsole
                nowMs={nowMs}
                simulated={simulated}
                onAdvance={(m) => sim(() => simAdvance(m))}
                onJump={(iso) => sim(() => simJump(iso))}
                onReset={() => sim(simReset)}
              />
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ color: '#c7d2fe', fontSize: 13 }}>通知對象：</span>
            <Select
              value={currentUserId}
              onChange={setCurrentUserId}
              style={{ minWidth: 120 }}
              options={users.map((u) => ({ value: u.id, label: u.empName }))}
            />
          </div>
        </div>
      </header>

      {!ready ? (
        <div style={{ textAlign: 'center', padding: 60 }}>
          <Spin size="large" />
          <div style={{ marginTop: 12, color: '#64748b' }}>連線後端中…</div>
        </div>
      ) : (
        <>
          <BookingForm rooms={rooms} users={users} onLock={onLock} loading={submitting} />

          <div className="toolbar">
            <Segmented
              value={viewMode}
              onChange={setViewMode}
              options={[
                { label: '日視角', value: 'day' },
                { label: '週視角（橫向）', value: 'week' },
                { label: '月視角', value: 'month' },
              ]}
            />
            <div className="nav-group">
              <Button onClick={() => navigate(-1)}>◀</Button>
              <span className="nav-title">{title}</span>
              <Button onClick={() => navigate(1)}>▶</Button>
              <Button type="link" onClick={() => setBaseDate(dayjs())}>回到今日</Button>
            </div>
          </div>

          {viewMode === 'day' && <DayView {...viewProps} />}
          {viewMode === 'week' && <WeekView {...viewProps} />}
          {viewMode === 'month' && <MonthView {...viewProps} />}
        </>
      )}
    </div>
  );
}

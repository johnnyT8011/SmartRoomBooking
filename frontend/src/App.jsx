// 儲存狀態
import { useCallback, useEffect, useRef, useState } from 'react';
// UI元件庫
import { App as AntApp, Button, Segmented, Select, Spin } from 'antd';
import dayjs from 'dayjs';

// 預約表單、控制台、行事曆的畫面元件
import BookingForm from './components/BookingForm';
import SimConsole from './components/SimConsole';
import DayView from './views/DayView';
import WeekView from './views/WeekView';
import MonthView from './views/MonthView';

// 與後端溝通的API管道，若是有新增如：刪除預約，需到lib/api中新增名稱
import {
  cancelBooking, checkIn, confirmBooking, getRooms, getSchedule, getSimTime, getUsers,
  lockRoom, simAdvance, simJump, simReset, sseUrl,
} from './lib/api';
import { isoLocal, weekStart } from './lib/utils';

/*
* 根據不同的模式(日、周、月)與目前日期(baseDate)算出開始時和集結束時間
*/
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
  // Antd 彈出提示訊息（彈窗）的工具
  const { message } = AntApp.useApp();

  // 儲存所有會議室列表
  const [rooms, setRooms] = useState([]);
  // 儲存所有使用者（員工）列表
  const [users, setUsers] = useState([]);
  // 儲存撈出來的會議室預約紀錄
  const [bookings, setBookings] = useState([]);
  // 目前的視角，預設是週視角
  const [viewMode, setViewMode] = useState('week');
  // 目前行事曆停在對準哪一天，預設是今天
  const [baseDate, setBaseDate] = useState(dayjs());
  // 系統目前的「現在時間」（毫秒），會隨著時間改變
  const [nowMs, setNowMs] = useState(Date.now());
  // 後端目前是不是處於「模擬時間」狀態
  const [simulated, setSimulated] = useState(false);
  // 表單是否正在送出中（用來防止重複點擊按鈕）
  const [submitting, setSubmitting] = useState(false);

  // 目前選擇要接收通知的員工 ID
  const [currentUserId, setCurrentUserId] = useState(null);
  // 初始化資料是否都載入完畢了
  const [ready, setReady] = useState(false);

  // offset (ms) between the backend's (possibly simulated) clock and the browser clock,
  // so the red "now" line + time-derived block states follow the simulated system time.
  const clockOffsetRef = useRef(0);

  // 每當後端的時間改變，後端會回傳一個物件。用來更新前端的時間，並重新校正時差。
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

  // 初始化設定(時間、會議是、用戶)
  useEffect(() => {
    Promise.all([getRooms(), getUsers(), getSimTime()])
      .then(([r, u, t]) => {
        setRooms(r);
        setUsers(u);

        // 預設把通知對象選成第一個員工
        setCurrentUserId(u[0]?.id ?? null);
        // 同步時間
        applyServerTime(t);
        setReady(true);
      })
      .catch((e) => message.error('初始化失敗：' + e.message));
  }, [message]);

  const refresh = useCallback(async () => {
    // 先算好目前的日期範圍
    const [from, to] = rangeFor(viewMode, baseDate);

    // 去後端找該段範圍的預約紀錄
    try {
      setBookings(await getSchedule(isoLocal(from), isoLocal(to)));
    } catch (e) {
      message.error('載入預約失敗：' + e.message);
    }
  }, [viewMode, baseDate, message]);

  // 若有切換時間或是或是加減日期，利用useEffect偵測，並呼喚refresh更新資料
  useEffect(() => {
    if (ready) refresh();
  }, [ready, refresh]);

  // keep the SSE handler pointed at the latest refresh without reopening the connection
  //為了防止 SSE 頻繁中斷重連，用 refreshRef 來綁定最新的 refresh 函式。
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;

  // Server-Sent Events for the selected user
  useEffect(() => {
    if (!currentUserId) return undefined;
    // 建立一條跟後端相連的即時通道
    const es = new EventSource(sseUrl(currentUserId));
    // 有通知，則可能預約行程有改變，刷新頁面
    const onNotify = (ev) => {
      message.info(ev.data);
      refreshRef.current();
      };

    // 監聽後端的 'notification' 事件
    es.addEventListener('notification', onNotify);
    es.onerror = () => { /* browser auto-reconnects */ };

    return () => {
    // 換員工或者關閉網頁時，必須把舊的連線斷開
      es.removeEventListener('notification', onNotify);
      es.close();
    };
  }, [currentUserId, message]);

  const act = async (fn, okMsg) => {
    try {
      // 執行傳進來的後端 API 動作
      await fn();
      // 彈出成功訊息
      message.success(okMsg);
      // 更新頁面，顯示最新狀態
      refresh();
    } catch (e) {
      message.error(e.message);
    }
  };

  /*
  * 需確認預約取消功能是否需要預約已確認
  * 如果想要新增一個「延長會議時間」的功能，要在 handlers 裡面加一行：onExtend: (b) => act(() => extendBooking(b.id), '會議已延長'),
  */
  const handlers = {
    onConfirm: (b) => act(() => confirmBooking(b.id), '預約已確認'),
    onCancel: (b) => act(() => cancelBooking(b.id), '預約已取消，時段已釋放'),
    onCheckIn: (b) => act(() => checkIn(b.id), '報到成功'),
  };

  const onLock = async (payload) => {
    setSubmitting(true);
    try {
      // 呼叫後端暫時鎖定會議室時段
      await lockRoom(payload);
      message.success('已鎖定時段，請於時段方塊點擊「確認預約」完成');
      refresh();
    } catch (e) {
      message.error(e.message);
    } finally {
      // 結束鎖定動作，按鈕恢復可點擊狀態
      setSubmitting(false);
    }
  };

  const sim = async (fn) => {
    try {
      // 執行時間模擬 API，並把後端回傳的新時間同步過來
      applyServerTime(await fn());
      refresh();
    } catch (e) {
      message.error(e.message);
    }
  };

  /*
  * 不同日期模式，不同顯示(eg. 周，若是按下一周需增加七天)
  */
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

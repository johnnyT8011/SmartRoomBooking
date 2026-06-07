// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntApp } from 'antd';
import App from '../App';

// Mock 所有的後端 API 請求
vi.mock('../lib/api', () => ({
  getRooms: vi.fn(() => Promise.resolve([{ id: 'room-1', roomName: '大會議室' }])),
  getUsers: vi.fn(() => Promise.resolve([{ id: 'user-1', empName: '王小明' }])),
  getSimTime: vi.fn(() => Promise.resolve({ now: '2026-06-07T12:00:00', simulated: false })),
  getSchedule: vi.fn(() => Promise.resolve([])),
  sseUrl: vi.fn(() => 'http://localhost/mock-sse'),
  simAdvance: vi.fn(() => Promise.resolve({ now: '2026-06-07T12:15:00', simulated: true })),
  simJump: vi.fn(() => Promise.resolve({ now: '2026-06-07T12:00:00', simulated: true })),
  simReset: vi.fn(() => Promise.resolve({ now: '2026-06-07T12:00:00', simulated: false })),
}));

// Mock 瀏覽器原生的 EventSource (SSE)
class MockEventSource {
  addEventListener = vi.fn();
  removeEventListener = vi.fn();
  close = vi.fn();
}
global.EventSource = MockEventSource;

// 解決 Ant Design 專用的 matchMedia 警告
beforeEach(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// 封裝一個 Helper 函式，自動幫 App 加上 Ant 頂層 Context
const renderWithAntApp = (ui) => {
  return render(<AntApp>{ui}</AntApp>);
};

describe('App 根元件核心整合測試', () => {

  // 測試初始化載入流程
  it('初始化時應顯示載入中，API 成功後應解鎖畫面並顯示預約系統', async () => {
    renderWithAntApp(<App />);

    // 剛進網頁時，應該處於 ready = false 狀態，顯示讀取條
    expect(screen.getByText('連線後端中…')).toBeTruthy();

    // 等待 Promise.all 完成後，ready 變成 true，讀取條消失，顯示主標題
    await waitFor(() => {
      expect(screen.queryByText('連線後端中…')).toBeNull();
      expect(screen.getByText('🗓️ 智慧會議室預約系統')).toBeTruthy();
    });
  });


  // 測試切換視角與導覽日期連動
  it('切換不同的日曆視角與導覽時，標題與時間區間應正確聯動', async () => {
    renderWithAntApp(<App />);

    // 先等待初始化完畢
    await waitFor(() => {
      expect(screen.queryByText('連線後端中…')).toBeNull();
    });

    // 點擊「日視角」按鈕
    const dayViewBtn = screen.getByText('日視角');
    await userEvent.click(dayViewBtn);

    // 切換到日視角後，標題應該變成當天的格式（依據 Mock 伺服器回傳時間計算）
    await waitFor(() => {
      expect(screen.getByText(/2026年6月7日/)).toBeTruthy();
    });

    // 模擬使用者點擊下一天 "▶" 按鈕
    const nextBtn = screen.getByText('▶');
    await userEvent.click(nextBtn);

    // 標題應該往後推一天，變成 6月8日
    await waitFor(() => {
      expect(screen.getByText(/2026年6月8日/)).toBeTruthy();
    });
  });
});
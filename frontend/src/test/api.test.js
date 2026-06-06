// 📄 src/lib/api.test.js
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getRooms, getSchedule, confirmBooking, lockRoom, cancelBooking, checkIn, sseUrl, } from '../lib/api.js';

// 💡 核心技術：模擬全域的 fetch 功能
const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

describe('API 模組單元測試', () => {

  beforeEach(() => {
    mockFetch.mockReset(); // 每次跑新的測試前，把上一次的模擬狀態清空
  });

  // ==========================================
  // 核心測試區一：測試 GET 基礎資料與 handle 錯誤攔截器
  // ==========================================
  describe('基礎資料與錯誤攔截處理 (handle)', () => {
    it('當後端成功回傳房間列表時，應該正確解析 JSON 資料', async () => {
      // 1. 模擬後端回傳 200 成功與一筆房間資料
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => [{ id: 'room-A', roomName: '大會議室' }],
      });

      // 2. 呼叫我們要測試的 function
      const rooms = await getRooms();

      // 3. 斷言（驗證結果）：檢查網址對不對、資料有沒有拿到
      expect(mockFetch).toHaveBeenCalledWith('/api/rooms');
      expect(rooms).toEqual([{ id: 'room-A', roomName: '大會議室' }]);
    });

    it('當後端噴出 400 錯誤時，handle 應該要能抓住並噴出後端的錯誤訊息', async () => {
      // 1. 模擬後端爆炸，回傳錯誤訊息
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        json: async () => ({ message: '該時段已被搶先預約！' }),
      });

      // 2. 驗證程式有沒有如預期「噴出 Error」
      await expect(getRooms()).rejects.toThrow('該時段已被搶先預約！');
    });

    it('204 No Content 應回傳 null', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 204,
      });

      const result = await lockRoom({
        roomId: 'room-A',
        userId: 'user-1',
      });

      expect(result).toBeNull();
    });

    it('sseUrl 應組出正確網址', () => {
      expect(
        sseUrl('user-1')
      ).toBe(
        '/api/notifications/sse?userId=user-1'
      );
    });
  });

  // ==========================================
  // 核心測試區二：測試帶有參數的網址與 POST 請求
  // ==========================================
  describe('會議室預約行為測試', () => {
    it('getSchedule 應該要對時間參數進行安全編碼 (encodeURIComponent)', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true, status: 200, json: async () => [] });

      // 帶有特殊字元（如 + 號和冒號）的時間字串
      await getSchedule('2026-06-06T08:00:00+08:00', '2026-06-06T18:00:00+08:00');

      // 驗證發送的網址是否已經被編碼成大寫百分比字元，防止網址斷掉
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/rooms/schedule?from=2026-06-06T08%3A00%3A00%2B08%3A00&to=2026-06-06T18%3A00%3A00%2B08%3A00'
      );
    });

    it('lockRoom 應該要發送正確的 POST Header 與 JSON 字串', async () => {
      // 模擬 204 No Content 成功狀態
      mockFetch.mockResolvedValueOnce({ ok: true, status: 204 });

      const payload = { roomId: 'room-A', userId: 'user-1' };
      await lockRoom(payload);

      // 驗證 POST 的細節：Method、Header、Body 是不是都有乖乖轉換
      expect(mockFetch).toHaveBeenCalledWith('/api/bookings/lock', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
    });

    it('非 JSON 錯誤應使用預設訊息', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: async () => {
          throw new Error();
        },
      });

      await expect(getRooms())
        .rejects
        .toThrow('請求失敗 (500)');
    });

    it('沒有 message 欄位時使用預設訊息', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        json: async () => ({
          error: 'oops',
        }),
      });

      await expect(getRooms())
        .rejects
        .toThrow('請求失敗 (400)');
    });

    it('confirmBooking 應呼叫正確網址', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 204,
      });

      await confirmBooking('booking-1');

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/bookings/booking-1/confirm',
        {
          method: 'POST',
        }
      );
    });

    it('cancelBooking 應呼叫正確網址', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 204,
      });

      await cancelBooking('booking-1');

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/bookings/booking-1/cancel',
        {
          method: 'POST',
        }
      );
    });

    it('checkIn 應呼叫正確網址', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 204,
      });

      await checkIn('booking-1');

        expect(mockFetch).toHaveBeenCalledWith(
          '/api/bookings/booking-1/checkin',
          expect.objectContaining({
            method: 'POST',
        })
      );
    });
  });

});
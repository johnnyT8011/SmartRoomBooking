import { describe, it, expect, vi } from 'vitest';
import dayjs from 'dayjs';
import {
  isoLocal,
  fmtHM,
  minutesIntoBand,
  nowMinutes,
  blockGeometry,
  displayState,
  weekStart
} from '../lib/utils.js';

// 💡 核心步驟一：模擬常數檔案，這樣我們才能精準計算預期的分鐘數與百分比
vi.mock('./constants', () => ({
  START_HOUR: 8,
  TOTAL_MINUTES: 720,
}));

describe('時間工具函式單元測試', () => {

  /**
   * 1. 測試時間格式化功能
   */
  describe('時間格式化 (Formatting)', () => {
    it('isoLocal 應回傳不帶時區的後端友善 ISO 字串', () => {
      const date = new Date(2026, 5, 6, 10, 30, 0); // 2026-06-06 10:30:00
      expect(isoLocal(date)).toBe('2026-06-06T10:30:00');
    });

    it('fmtHM 應正確格式化為 小時:分鐘', () => {
      expect(fmtHM('2026-06-06T14:05:00')).toBe('14:05');
    });
  });

  /**
   * 2. 測試分鐘數計算（相對於營業開始時間）
   */
  describe('相對分鐘數計算 (Minutes into Band)', () => {
    it('minutesIntoBand 應計算出相對於 START_HOUR (8點) 的分鐘數', () => {
      // 早上 8:00 應該是第 0 分鐘
      expect(minutesIntoBand('2026-06-06T08:00:00')).toBe(0);
      // 早上 10:30 應該是 2*60 + 30 = 150 分鐘
      expect(minutesIntoBand('2026-06-06T10:30:00')).toBe(150);
    });

    it('nowMinutes 應正確計算目前時間的分鐘數', () => {
      const mockNowMs = dayjs('2026-06-06T09:15:00').valueOf();
      // 9:15 距離 8:00 是 75 分鐘
      expect(nowMinutes(mockNowMs)).toBe(75);
    });
  });

  /**
   * 3. 測試前端元件定位與寬度計算 (幾何幾何)
   */
  describe('區塊幾何幾何計算 (blockGeometry)', () => {
    it('應正確計算預約區塊的 left 與 width 百分比', () => {
      const booking = {
        // 距離 8 點是 60 分鐘
        startTime: '2026-06-06T09:00:00',
        // 總長 120 分鐘
        endTime: '2026-06-06T11:00:00',
      };

      const result = blockGeometry(booking);

      // left: (60 / 720) * 100 = 8.3333%
      expect(result.left).toBeCloseTo(8.3333, 4);
      // width: (120 / 720) * 100 = 16.6666666667%
      expect(result.width).toBeCloseTo(16.6667, 4);
    });

    it('超出邊界時應正確進行安全裁切 (Clamping)', () => {
      const edgeBooking = {
        startTime: '2026-06-06T07:00:00', // 比營業時間早
        endTime: '2026-06-06T20:00:00',   // 比營業時間晚
      };

      const result = blockGeometry(edgeBooking);

      // left 最小值必須是 0
      expect(result.left).toBe(0);
      // width 最大值不能讓右邊爆出去 (100 - left)
      expect(result.width).toBe(100);
    });
  });

  /**
   * 4. 測試視覺狀態狀態機 (displayState)
   */
  describe('視覺狀態判定 (displayState)', () => {
    const bookingBase = {
      startTime: '2026-06-06T10:00:00',
      endTime: '2026-06-06T11:00:00',
    };

    it('狀態為 LOCKING 時，一律回傳 locking', () => {
      const now = dayjs('2026-06-06T09:30:00').valueOf();
      expect(displayState({ ...bookingBase, status: 'LOCKING' }, now)).toBe('locking');
    });

    it('狀態為 CHECKED_IN 時，根據時間回傳 checkedin 或 finished', () => {
      const nowNotFinished = dayjs('2026-06-06T10:30:00').valueOf();
      const nowFinished = dayjs('2026-06-06T11:30:00').valueOf();

      expect(displayState({ ...bookingBase, status: 'CHECKED_IN' }, nowNotFinished)).toBe('checkedin');
      expect(displayState({ ...bookingBase, status: 'CHECKED_IN' }, nowFinished)).toBe('finished');
    });

    it('狀態為 BOOKED 時，應正確切換 booked / pending / finished', () => {
      const b = { ...bookingBase, status: 'BOOKED' };

      const nowBefore = dayjs('2026-06-06T09:30:00').valueOf();
      const nowDuring = dayjs('2026-06-06T10:30:00').valueOf(); // 已開始但未報到 -> pending
      const nowAfter = dayjs('2026-06-06T11:15:00').valueOf();

      expect(displayState(b, nowBefore)).toBe('booked');
      expect(displayState(b, nowDuring)).toBe('pending');
      expect(displayState(b, nowAfter)).toBe('finished');
    });
  });

  /**
   * 5. 測試周起始日計算（週一為第一天）
   */
  describe('周起始日計算 (weekStart)', () => {
    it('無論給定該周的哪一天，都應該回到該周的週一凌晨 00:00', () => {
      // 假設 2026-06-06 是星期六
      const inputDate = dayjs('2026-06-06T15:30:00');
      const startOfWeek = weekStart(inputDate);

      // 預期回到 2026-06-01 (週一) 00:00:00
      expect(startOfWeek.format('YYYY-MM-DD HH:mm:ss')).toBe('2026-06-01 00:00:00');
    });

    it('如果給定的是週日，應該回到同一個禮拜前的週一', () => {
      // 2026-06-07 是星期日
      const inputDate = dayjs('2026-06-07T10:00:00');
      const startOfWeek = weekStart(inputDate);

      // 預期回到 2026-06-01 (週一)
      expect(startOfWeek.format('YYYY-MM-DD')).toBe('2026-06-01');
    });
  });

});
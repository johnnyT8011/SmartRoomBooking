import { describe, it, expect, vi } from 'vitest';
import { roomColor, TOTAL_MINUTES } from '../lib/constants';

describe('常數與顏色工具函式測試', () => {

  // 測試常數計算是否符合預期
  it('TOTAL_MINUTES 應該要是 720 分鐘', () => {
    expect(TOTAL_MINUTES).toBe(720);
  });

  describe('roomColor 函式', () => {
    it('當 index 在範圍內時，應返回對應的顏色物件', () => {
      // index = 0 應該拿到第一個藍色
      expect(roomColor(0)).toEqual({ block: 'c-blue', dot: 'c-blue', chip: 'b-blue' });
      // index = 1 應該拿到第二個紫色
      expect(roomColor(1)).toEqual({ block: 'c-purple', dot: 'c-purple', chip: 'b-purple' });
    });

    it('當 index 超過顏色陣列長度時，應正確循環（取餘數）', () => {
      // 陣列長度是 3，所以 index = 3 應該折返回去拿 index = 0 (藍色)
      expect(roomColor(3)).toEqual({ block: 'c-blue', dot: 'c-blue', chip: 'b-blue' });

      // index = 5 應該拿到 index = 2 (綠色/teal)
      expect(roomColor(5)).toEqual({ block: 'c-teal', dot: 'c-teal', chip: 'b-teal' });
    });

    it('應能處理 index 為 0 或極大值的情況', () => {
      expect(roomColor(300)).toEqual({ block: 'c-blue', dot: 'c-blue', chip: 'b-blue' });
    });
  });
});
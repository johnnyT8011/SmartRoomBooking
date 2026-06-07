// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import dayjs from 'dayjs';

import BookingForm from '../components/BookingForm';

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
});

describe('BookingForm', () => {
  const rooms = [
    {
      id: 'room-1',
      roomName: '大會議室',
    },
  ];

  const users = [
    {
      id: 'user-1',
      empName: '王小明',
    },
  ];

  it('應正常顯示表單欄位與送出按鈕', () => {
    render(
      <BookingForm
        rooms={rooms}
        users={users}
        onLock={vi.fn()}
        loading={false}
      />
    );

    expect(screen.getByText('會議室')).toBeTruthy();
    expect(screen.getByText('借用人')).toBeTruthy();
    expect(screen.getByText('日期')).toBeTruthy();
    expect(screen.getByText('開始')).toBeTruthy();
    expect(screen.getByText('結束')).toBeTruthy();

    expect(
      screen.getByRole('button', {
        name: /\+ 送出預約/,
      })
    ).toBeTruthy();
  });

  it('loading=true 時送出按鈕應顯示 loading 狀態', () => {
    render(
      <BookingForm
        rooms={rooms}
        users={users}
        onLock={vi.fn()}
        loading={true}
      />
    );

    const submitButton = screen.getByRole('button', {
      name: /\+ 送出預約/,
    });

    expect(
      submitButton.classList.contains('ant-btn-loading')
    ).toBe(true);
  });

  it('rooms 與 users 為空時不應崩潰', () => {
    expect(() =>
      render(
        <BookingForm
          rooms={[]}
          users={[]}
          onLock={vi.fn()}
          loading={false}
        />
      )
    ).not.toThrow();
  });

  it('點擊送出後應呼叫 onLock 並傳入正確資料', async () => {
    const mockOnLock = vi.fn();

    render(
      <BookingForm
        rooms={rooms}
        users={users}
        onLock={mockOnLock}
        loading={false}
      />
    );

    const submitButton = screen.getByRole('button', {
      name: /\+ 送出預約/,
    });

    await userEvent.click(submitButton);

    const todayStr = dayjs().format('YYYY-MM-DD');

    await waitFor(() => {
      expect(mockOnLock).toHaveBeenCalledTimes(1);

      expect(mockOnLock).toHaveBeenCalledWith({
        userId: 'user-1',
        roomId: 'room-1',
        startTime: `${todayStr}T09:00:00`,
        endTime: `${todayStr}T10:00:00`,
      });
    });
  });
});
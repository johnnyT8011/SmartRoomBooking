import { Button, DatePicker, Form, Select, TimePicker } from 'antd';
import dayjs from 'dayjs';
import { isoLocal } from '../lib/utils';

/**
 * Booking request form. Submitting creates a LOCKING hold (the spec's explicit "send a
 * booking request" step); the user then confirms from the timeline block.
 */
export default function BookingForm({ rooms, users, onLock, loading }) {
  const initial = {
    roomId: rooms[0]?.id,
    userId: users[0]?.id,
    date: dayjs(),
    start: dayjs().hour(9).minute(0).second(0),
    end: dayjs().hour(10).minute(0).second(0),
  };

  const onFinish = (v) => {
    const compose = (t) => v.date.hour(t.hour()).minute(t.minute()).second(0).millisecond(0);
    onLock({
      userId: v.userId,
      roomId: v.roomId,
      startTime: isoLocal(compose(v.start)),
      endTime: isoLocal(compose(v.end)),
    });
  };

  return (
    <Form layout="inline" initialValues={initial} onFinish={onFinish} className="toolbar" style={{ rowGap: 12 }}>
      <Form.Item label="會議室" name="roomId" rules={[{ required: true }]}>
        <Select style={{ minWidth: 120 }} options={rooms.map((r) => ({ value: r.id, label: r.roomName }))} />
      </Form.Item>
      <Form.Item label="借用人" name="userId" rules={[{ required: true }]}>
        <Select style={{ minWidth: 100 }} options={users.map((u) => ({ value: u.id, label: u.empName }))} />
      </Form.Item>
      <Form.Item label="日期" name="date" rules={[{ required: true }]}>
        <DatePicker allowClear={false} />
      </Form.Item>
      <Form.Item label="開始" name="start" rules={[{ required: true }]}>
        <TimePicker format="HH:mm" minuteStep={30} allowClear={false} />
      </Form.Item>
      <Form.Item label="結束" name="end" rules={[{ required: true }]}>
        <TimePicker format="HH:mm" minuteStep={30} allowClear={false} />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>+ 送出預約（鎖定）</Button>
      </Form.Item>
    </Form>
  );
}

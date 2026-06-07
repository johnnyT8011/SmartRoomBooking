// 本機開發未設 VITE_API_BASE -> 用 '/api'，走 vite proxy 到 localhost:8080
// 線上部署(Vercel)設 VITE_API_BASE=https://<後端>.onrender.com/api -> 直連後端
const BASE = import.meta.env.VITE_API_BASE || '/api';

/**
 * 檢查API的請求連線狀態
 */
async function handle(res) {
  if (!res.ok) {
    let message = `請求失敗 (${res.status})`;
    try {
      const body = await res.json();
      if (body && body.message) message = body.message;
    } catch {
      /* non-JSON error body */
    }
    throw new Error(message);
  }
  if (res.status === 204) return null;
  return res.json();
}

const jsonPost = (url, body) =>
  fetch(url, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  }).then(handle);

export const getRooms = () => fetch(`${BASE}/rooms`).then(handle);
export const getUsers = () => fetch(`${BASE}/users`).then(handle);

export const getSchedule = (fromIso, toIso) =>
  fetch(`${BASE}/rooms/schedule?from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`)
    .then(handle);

export const lockRoom = (payload) => jsonPost(`${BASE}/bookings/lock`, payload);
export const confirmBooking = (id) => jsonPost(`${BASE}/bookings/${id}/confirm`);
export const cancelBooking = (id) => jsonPost(`${BASE}/bookings/${id}/cancel`);
export const checkIn = (id) => jsonPost(`${BASE}/bookings/${id}/checkin`);

export const sseUrl = (userId) => `${BASE}/notifications/sse?userId=${userId}`;

// ---- 時空模擬控制台 (demo time-travel) ----
export const getSimTime = () => fetch(`${BASE}/sim/time`).then(handle);
export const simAdvance = (minutes) => jsonPost(`${BASE}/sim/advance?minutes=${minutes}`);
export const simJump = (toIso) => jsonPost(`${BASE}/sim/jump?to=${encodeURIComponent(toIso)}`);
export const simReset = () => jsonPost(`${BASE}/sim/reset`);

# 智慧會議室預約系統 (Smart Meeting Room Reservation System)

5 天敏捷開發專案。核心機制：**防重疊預約**、**5 分鐘預約鎖**、**15 分鐘無人報到自動釋放**、**SSE 即時通知**。

- **後端** `backend/`：Spring Boot 3.5 (Java 17)、Spring Data JPA、H2、JUnit 5 + Mockito、Server-Sent Events。**未使用 Redis 或 Message Queue。**
- **前端** `frontend/`：React (Vite) + Ant Design，外觀／互動參考原型 (`前端構思.html`)，含日 / 週(橫向) / 月三種視角。

---

## 執行方式

### 後端 (預設 http://localhost:8080)
```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
./mvnw test                   # 執行全部 48 個測試
```
H2 主控台：http://localhost:8080/h2-console （JDBC URL `jdbc:h2:mem:meetingroom`，使用者 `sa`，無密碼）。
啟動時會自動種子化會議室 A/B/C 與使用者 牛 / 豬 / 羊 / 鴕鳥。

> 也可用 Eclipse「Import → Existing Maven Project」匯入 `backend`，由內建 m2e 解析依賴。

### 前端 (http://localhost:5173)
```bash
cd frontend
npm install
npm run dev
```
`vite.config.js` 已將 `/api` 代理至 `http://localhost:8080`，因此前後端同源、SSE 連線正常。

---

## 架構分層 (對應規範)

| 層 | 類別 | 重點 |
|----|------|------|
| 表現層 | `BookingController` | `POST /api/bookings/lock`、`/{id}/confirm`、`/{id}/cancel`、`/{id}/checkin`、`GET /api/notifications/sse` |
| 表現層 | `MeetingRoomController` | `GET /api/rooms`、`GET /api/rooms/schedule`（預設本週；可帶 `from`/`to`） |
| 表現層 | `UserController` | `GET /api/users`（前端借用人選單；無認證，需取得 userId） |
| 業務層 | `BookingRuleValidator` | `validateTimeBlock` / `validateDuration` / `validateBookingWindow` / `checkUserConflict` |
| 業務層 | `BookingService` | `lockRoom` / `confirmBooking` / `cancelBooking` / `checkIn`，交易一致性 + 防重疊 |
| 業務層 | `BookingScheduler` | `@Scheduled releaseExpiredLocks()` / `releaseNoShows()`（每分鐘） |
| 業務層 | `NotificationService` | `sendSseEvent(userId, message)`，封裝 SSE 推播 |
| 資料層 | `BookingRepository` | 自訂 JPQL 時間交集查詢（LOCKING/BOOKED/CHECKED_IN） |
| 資料層 | `UserRepository` / `MeetingRoomRepository` | 基礎 CRUD；房間查詢含悲觀鎖 (`findByIdForUpdate`) |
| 實體層 | `User` / `MeetingRoom` / `Booking` / `BookingStatus` | `@ManyToOne` 單向關聯，無雙向迴圈；DTO 輸出避免 lazy 序列化 |

## 規範情境對應

| 情境 | 實作 / 驗證 |
|------|------|
| 1 成功預約 (Happy Path) | `lockRoom` → `confirmBooking`；`BookingServiceTest`、API 煙霧測試 |
| 2 時段衝突 | `findOverlapping` → `BookingConflictException`「時段重疊，預約失敗」(409) |
| 3 併發搶訂 | 房間列悲觀鎖序列化；`BookingServiceTest.lockRoom_concurrentRequests_onlyFirstSucceeds` |
| 4 超時未報到 | `BookingScheduler.releaseNoShows()`（start + 15 分）→ EXPIRED + 通知 |
| 5 寬限內報到 | `checkIn` → CHECKED_IN；排程不再視為未報到 |
| 6 非 30 分鐘 | `validateTimeBlock`「預約時間必須為 30 分鐘的倍數」(400) |
| 7 超過 4 小時 | `validateDuration`「單次預約不可超過 4 小時」(400) |
| 8 超出 7 天 | `validateBookingWindow`「僅開放預約未來 7 天內的會議室」(400) |
| 9 建立預約鎖 | `lockRoom` 建立 LOCKING，`lockExpiresAt = now + 5min` |
| 10 鎖逾時釋放 | `BookingScheduler.releaseExpiredLocks()` → EXPIRED |
| 11 主動取消 | `cancelBooking` → CANCELLED（保留紀錄供稽核，不刪除） |

## 時空模擬控制台 (Time-Travel Console)

為了不必等真實的 5 / 15 分鐘即可展示「預約鎖逾時釋放」與「無人報到自動釋放」，系統提供一組
demo 用的時間模擬 API，並由前端 header 的控制台操作。底層是一個可位移的 `MutableClock`
（offset 0 = 真實時間），驗證器、服務時間戳、排程器全部讀取它。

| 端點 | 說明 |
|------|------|
| `GET /api/sim/time` | 取得目前（可能已模擬的）系統時間與位移秒數 |
| `POST /api/sim/advance?minutes=15` | 快轉／倒退（負值）N 分鐘，並立即執行排程掃描 |
| `POST /api/sim/jump?to=2026-06-08T09:00:00` | 跳轉至絕對時間，並立即執行排程掃描 |
| `POST /api/sim/reset` | 還原為真實時間 |

**使用方式（前端）**：header 右側即「時空模擬控制台」。
1. 「⏩ 快轉15分 / ⏪ 倒退15分」：平移系統時鐘；紅色「現在」線與方塊狀態會即時跟著動。
2. 選日期時間 →「🚀 跳轉」：直接跳到指定時刻。
3. 出現「模擬中」標籤後，可按「↩ 還原真實時間」歸位。

每次時間變動後後端會立刻跑一次 `releaseExpiredLocks()` / `releaseNoShows()`，因此逾時的鎖與
未報到的 BOOKED 會「當下」被釋放並透過 SSE 推播，已 `CHECKED_IN` 的預約則不受影響。

> 此 API 純為 demo／驗收用途，非正式預約契約的一部分。前端時鐘以 `GET /api/sim/time` 的回應
> 與瀏覽器時間計算 offset，因此畫面上的「系統時間」即後端模擬時間。

## 設計註記
- 預約流程採規範的兩階段：**送出預約=建立鎖 (LOCKING)**，使用者再於時段方塊點「確認預約」轉 BOOKED。鎖僅在明確送出時建立，不會因進入頁面而占用資源。
- 排程器使用 `@Scheduled` + 可注入的 `Clock`（單元測試以 `Clock.fixed(...)` 固定時間驗證；執行期注入 `MutableClock` 供上述時空模擬）。
- `spring.jpa.open-in-view=false`：所有實體→DTO 轉換都在 `@Transactional` 服務內，或以 `JOIN FETCH` 初始化關聯後再映射，避免 `LazyInitializationException`。

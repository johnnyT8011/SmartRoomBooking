# 智慧會議室預約系統 (Smart Room Booking) — 可執行最小骨架

依「原始最小規格」實作：**防重疊預約 + 報到時間窗 + 15 分鐘未報到自動釋放 + 通知 + 加速測試端點**，
前後端分離，中間一條 HTTP API。

```
┌────────────────────┐      HTTP / JSON       ┌──────────────────────────┐
│  Streamlit 前端     │  ───────────────────►  │  Spring Boot 後端         │
│  (瘦前端，只畫畫面) │                        │  邏輯 + H2 記憶體資料庫   │
│  frontend/app.py    │  ◄───────────────────  │  防重疊 / @Scheduled 釋放 │
└────────────────────┘                        └──────────────────────────┘
```

後端是唯一的真相來源。前端不存資料、不算邏輯，每次都向後端拿最新狀態來畫。

---

## 環境需求
- 後端：JDK 17、Maven 3.6+
- 前端：Python 3.9+

## 執行（開兩個終端機）

後端：
```bash
cd backend
mvn spring-boot:run        # 監聽 http://localhost:8080
```

前端（另一個視窗）：
```bash
cd frontend
pip install -r requirements.txt
streamlit run app.py       # 開 http://localhost:8501
```

跑測試：
```bash
cd backend
mvn test
```

---

## 核心規則

| 規則 | 內容 | 實作位置 |
|------|------|----------|
| 防重疊 | 同房同時段不得有兩筆有效預約 | `BookingRepository.findOverlapping`（時間交集 SQL） |
| 報到時間窗 | 只能在 **開始前 5 分鐘 ~ 開始後 15 分鐘** 內報到，且須為 BOOKED | `BookingService.checkIn` |
| 自動釋放 | 開始後超過 15 分鐘仍未報到 → 釋放 | `BookingScheduler` 每 30 秒掃描 |

> 報到窗的「關門時間」= 自動釋放的「開鍘時間」= 同一個 15 分鐘（`NO_SHOW_GRACE_MINUTES`），
> 兩條規則的時間邊界刻意對齊，避免出現「已被釋放卻還能報到」的矛盾狀態。
> 前端按鈕灰化只是體驗提示；**真正的守門在後端 `checkIn`**，繞過前端直接打 API 一樣會被擋。

---

## Demo 腳本

啟動後預載示範資料：

| 會議室 | 借用人 | 狀態 | 看板 | 報到鈕 |
|--------|--------|------|------|--------|
| A | 示範-未報到 | BOOKED（現在開始） | 🟥 | 可報到（在窗口內） |
| B | 示範-已報到 | CHECKED_IN | 🟩 | — |
| C | 示範-稍後 | BOOKED（2 小時後） | 🟥 | 灰化「開始前 5 分鐘才開放報到」 |

1. **防重疊**：對 A 的同時段再訂一次 → 「時段重疊，預約失敗」。
2. **報到窗**：C 那筆因為還沒到開始前 5 分鐘，報到鈕是灰的；A 那筆在窗口內，可報到 → 轉 🟩。
3. **自動釋放（主秀）**：點 **⏱️ 觸發逾時掃描** → A 未報到那筆立刻釋放，紅色消失。
   真實情境不點也會發生（`@Scheduled` 每 30 秒掃，超過 15 分鐘未報到即釋放）。

---

## API 合約

| 方法 | 路徑 | 說明 |
|------|------|------|
| GET  | `/api/rooms` | 會議室清單 |
| GET  | `/api/bookings?date=YYYY-MM-DD` | 某日預約 |
| POST | `/api/bookings` | 建立預約；重疊回 409 |
| POST | `/api/bookings/{id}/checkin` | 報到；不在窗口/狀態不符回 409 |
| POST | `/api/test/trigger-timeout` | 加速測試：立即釋放 |
| GET  | `/api/notifications` | 近期通知 |

---

## 報到窗對應的行為規格（Gherkin）

```gherkin
Feature: 報到時間窗

  Scenario: 窗口內報到成功
    Given 員工甲有一筆 BOOKED 預約，開始時間在「現在」
    When  員工甲在開始前 5 分鐘到開始後 15 分鐘之間送出報到
    Then  系統將該預約狀態更新為 CHECKED_IN

  Scenario: 太早報到被拒
    Given 員工甲有一筆 BOOKED 預約，開始時間在 10 分鐘後
    When  員工甲送出報到
    Then  系統拒絕，並提示「尚未開放報到」

  Scenario: 逾時報到被拒
    Given 員工甲有一筆 BOOKED 預約，開始時間在 20 分鐘前
    When  員工甲送出報到
    Then  系統拒絕，並提示「報到時間已過」
```

這三條一對一對應 `BookingServiceTest` 的三個測試方法
（`checkIn_withinWindow_succeeds` / `checkIn_tooEarly_isRejected` / `checkIn_tooLate_isRejected`）。

---

## 測試現況

`backend/src/test/.../BookingServiceTest.java`（JUnit 5 + Mockito，純單元測試、不啟動 Spring）：

- 報到落在窗口內 → 成功
- 報到太早 → 拒絕
- 報到太晚 → 拒絕
- 報到時狀態非 BOOKED → 拒絕
- 預約撞期 → 丟 ConflictException（防重疊）
- 未報到超過寬限 → 轉 RELEASED（自動釋放）

時間情境用「把預約開始時間相對於現在挪動」來落在窗內/窗外，毫秒內跑完、不 flaky。

> 若要更嚴謹的時間控制，可把 `BookingService` 改成注入 `java.time.Clock`，
> 用固定 Clock 做完全決定性的測試——這是乾淨的升級方向，本版先用相對時間保持簡單。

---

## 還沒做（之後加分處）
- `@DataJpaTest`：用 H2 直接驗 `findOverlapping` 的時間交集 SQL。
- `@WebMvcTest`：驗 Controller 的狀態碼與 JSON。
- **ArchUnit**：CI 加「Controller 不可直接呼叫 Repository」等規則（分層已為此設計）。
- **CI/CD**：GitHub Actions 跑 `mvn test` + ArchUnit + Checkstyle。
- 通知升級為 SSE（目前是記憶體輪詢）。

## 已驗證 / 未驗證
- 前端 `app.py`、測試的時間情境邏輯：已驗證。
- 後端 Java：標準 Spring Boot 3.2.5 寫法，但本機未連 Maven Central，尚未實際編譯。
  請以 `mvn spring-boot:run` 與 `mvn test` 首次執行為準。

"""
智慧會議室預約看板 (Streamlit 前端)
瘦前端：自己不存資料、不算邏輯，所有狀態向 Java 後端 (localhost:8080) 拿。
報到按鈕的灰化只是「體驗」提示；真正的報到規則由後端守門。
"""
import streamlit as st
import requests
from datetime import datetime, date, time, timedelta

API = "http://localhost:8080/api"
HOURS = list(range(8, 18))            # 看板顯示 08:00 - 18:00
CHECKIN_OPEN_BEFORE = 5               # 與後端常數對齊
NO_SHOW_GRACE = 15                    # 與後端常數對齊

st.set_page_config(page_title="智慧會議室預約看板", layout="wide")
st.title("📅 智慧會議室預約看板")


def api_get(path, **params):
    r = requests.get(f"{API}{path}", params=params, timeout=5)
    r.raise_for_status()
    return r.json()


def api_post(path, json=None):
    return requests.post(f"{API}{path}", json=json, timeout=5)


# --- 連線檢查：後端沒開就停在這 ---
try:
    rooms = api_get("/rooms")
except Exception:
    st.error("無法連線到後端 (http://localhost:8080)。請先在 backend/ 執行：mvn spring-boot:run")
    st.stop()

day = st.date_input("檢視日期", value=date.today())
bookings = api_get("/bookings", date=day.isoformat())
now = datetime.now()


# --- 時間軸看板 ---
st.subheader("時間軸")
COLORS = {"BOOKED": "#ef4444", "CHECKED_IN": "#10b981", None: "#e5e7eb"}


def cell_status(room_id, hour):
    block_start = datetime.combine(day, time(hour, 0))
    block_end = block_start + timedelta(hours=1)
    for b in bookings:
        if b["roomId"] != room_id or b["status"] not in ("BOOKED", "CHECKED_IN"):
            continue
        s = datetime.fromisoformat(b["startTime"])
        e = datetime.fromisoformat(b["endTime"])
        if s < block_end and e > block_start:
            return b["status"], b["borrower"]
    return None, None


header = "<div style='display:flex'><div style='width:90px'></div>"
for h in HOURS:
    header += f"<div style='flex:1;text-align:center;font-size:11px;color:#888'>{h:02d}:00</div>"
header += "</div>"
st.markdown(header, unsafe_allow_html=True)

for room in rooms:
    row = "<div style='display:flex;align-items:center;margin-bottom:4px'>"
    row += f"<div style='width:90px;font-weight:bold'>{room['name']}</div>"
    for h in HOURS:
        status, who = cell_status(room["id"], h)
        color = COLORS[status]
        label = who or ""
        row += (f"<div title='{label}' style='flex:1;height:34px;margin:0 1px;border-radius:4px;"
                f"background:{color};display:flex;align-items:center;justify-content:center;"
                f"font-size:10px;color:white'>{label}</div>")
    row += "</div>"
    st.markdown(row, unsafe_allow_html=True)

st.caption("🟥 已預約　🟩 已報到　⬜ 空閒")


# --- 控制列 ---
c1, c2 = st.columns(2)
if c1.button("🔄 重新整理"):
    st.rerun()
if c2.button("⏱️ 觸發逾時掃描 (trigger-timeout)"):
    resp = api_post("/test/trigger-timeout")
    st.success(f"已釋放 {resp.json().get('released', 0)} 筆未報到預約")
    st.rerun()


# --- 新增預約 ---
st.subheader("新增預約")
with st.form("book"):
    f1, f2, f3, f4 = st.columns(4)
    room_map = {r["name"]: r["id"] for r in rooms}
    room_name = f1.selectbox("會議室", list(room_map.keys()))
    borrower = f2.text_input("借用人", value="員工甲")
    start_t = f3.time_input("開始", value=time(10, 0))
    end_t = f4.time_input("結束", value=time(11, 0))
    if st.form_submit_button("送出預約"):
        payload = {
            "roomId": room_map[room_name],
            "borrower": borrower,
            "start": datetime.combine(day, start_t).isoformat(timespec="seconds"),
            "end": datetime.combine(day, end_t).isoformat(timespec="seconds"),
        }
        resp = api_post("/bookings", json=payload)
        if resp.status_code == 201:
            st.success("預約成功")
            st.rerun()
        else:
            st.error(resp.json().get("message", "預約失敗"))


# --- 預約清單與報到（依報到時間窗灰化） ---
st.subheader("預約清單與報到")
st.caption(f"報到開放時間：預約開始前 {CHECKIN_OPEN_BEFORE} 分鐘 ~ 開始後 {NO_SHOW_GRACE} 分鐘")
active = [b for b in bookings if b["status"] in ("BOOKED", "CHECKED_IN")]
if not active:
    st.write("（本日尚無有效預約）")

for b in active:
    cols = st.columns([3, 2, 2, 2])
    cols[0].write(f"房間#{b['roomId']}　{b['borrower']}")
    s = datetime.fromisoformat(b["startTime"])
    e = datetime.fromisoformat(b["endTime"])
    cols[1].write(f"{s.strftime('%H:%M')}-{e.strftime('%H:%M')}")
    cols[2].write(b["status"])

    if b["status"] != "BOOKED":
        continue

    open_at = s - timedelta(minutes=CHECKIN_OPEN_BEFORE)
    close_at = s + timedelta(minutes=NO_SHOW_GRACE)
    key = f"ci{b['id']}"
    if open_at <= now <= close_at:
        if cols[3].button("報到", key=key):
            resp = api_post(f"/bookings/{b['id']}/checkin")
            if resp.status_code == 200:
                st.rerun()
            else:
                st.error(resp.json().get("message", "報到失敗"))
    elif now < open_at:
        cols[3].button("報到", key=key, disabled=True, help="開始前 5 分鐘才開放報到")
    else:
        cols[3].button("報到", key=key, disabled=True, help="報到時間已過")


# --- 通知（側欄輪詢） ---
st.sidebar.subheader("📢 即時通知")
try:
    for n in api_get("/notifications")[:15]:
        st.sidebar.write(f"・{n['message']}")
except Exception:
    st.sidebar.write("（無）")

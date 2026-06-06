import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App as AntdApp, ConfigProvider } from 'antd'
import zhTW from 'antd/locale/zh_TW'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-tw'
import './index.css'
import App from './App.jsx'

dayjs.locale('zh-tw')

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ConfigProvider
      locale={zhTW}
      theme={{ token: { colorPrimary: '#4f46e5', borderRadius: 8 } }}
    >
      <AntdApp>
        <App />
      </AntdApp>
    </ConfigProvider>
  </StrictMode>,
)

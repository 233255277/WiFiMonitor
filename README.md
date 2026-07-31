# WiFiMonitor

Android WiFi 黑名单监测工具 — 连接不安全 WiFi 时通过悬浮窗半透明遮罩警示用户。

## 功能

- 🔍 **实时监测**：基于 `ConnectivityManager.NetworkCallback` 事件驱动，零空闲功耗
- ⚫ **黑名单匹配**：支持关键词模糊匹配 SSID
- 🟥 **悬浮窗警示**：连接黑名单 WiFi 时显示半透明红色遮罩
- 🎨 **可自定义外观**：颜色、透明度、尺寸、位置均可调
- 🛡️ **防抖 & 缓存**：800ms 防抖 + SSID 缓存
- 📱 **前台服务**：持续后台运行

## 构建

```bash
./gradlew assembleDebug
```

- **JDK**: 17
- **AGP**: 8.2.0
- **compileSdk**: 34
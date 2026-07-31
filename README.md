# WiFiMonitor

Android WiFi 黑名单监测工具 — 连接不安全 WiFi 时通过悬浮窗半透明遮罩警示用户。

## 功能

- 🔍 **实时监测**：基于 `ConnectivityManager.NetworkCallback` 事件驱动，零空闲功耗
- ⚫ **黑名单匹配**：支持关键词模糊匹配 SSID（如"星巴克"可匹配"星巴克WiFi"）
- 🟥 **悬浮窗警示**：连接黑名单 WiFi 时，屏幕顶部显示半透明红色遮罩
- 🎨 **可自定义外观**：颜色、透明度、尺寸、位置均可调
- 🛡️ **防抖 & 缓存**：800ms 防抖避免闪烁，SSID 缓存防止误移除
- 📱 **前台服务**：持续运行，通知栏显示状态

## 使用场景

- 连接公共 WiFi 前自动提醒
- 防止手机自动连接到不安全的热点
- 公司/校园网络策略合规辅助

## 权限说明

| 权限 | 用途 |
|------|------|
| `ACCESS_WIFI_STATE` | 读取当前 WiFi SSID |
| `ACCESS_NETWORK_STATE` | 监听网络状态变化 |
| `ACCESS_FINE_LOCATION` | Android 8.0+ 获取 WiFi 名称必需 |
| `SYSTEM_ALERT_WINDOW` | 显示悬浮窗遮罩 |
| `FOREGROUND_SERVICE` | 后台持续监测 |
| `POST_NOTIFICATIONS` | Android 13+ 前台服务通知 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启动 |

## 构建

```bash
./gradlew assembleDebug
```

- **JDK**: 17
- **AGP**: 8.2.0
- **compileSdk**: 34
- **buildTools**: 34.0.0

## 架构

```
WifiMonitorService (前台服务)
  ├── ConnectivityManager.NetworkCallback  ← 系统推送 WiFi 事件
  ├── Handler + 防抖 (800ms)              ← 合并高频回调
  ├── SSID 缓存                           ← 防止 getCurrentSsid() 偶发 null
  ├── BroadcastReceiver                   ← 接收设置变更 (v1.2+)
  └── WindowManager 悬浮窗                ← 半透明遮罩
```

## 版本历史

- **v1.2** — 修复悬浮窗频繁消失（广播替代 stop/start 重启），加空指针保护
- **v1.1** — 修复悬浮窗闪烁（防抖 + SSID 缓存 + 延迟移除）
- **v1.0** — 初始版本，核心监测 + 黑名单 + 悬浮窗

## 许可证

MIT License
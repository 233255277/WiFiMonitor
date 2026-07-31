package com.example.wifimonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * WiFi 黑名单监测前台服务
 * - 使用 ConnectivityManager.NetworkCallback 实时监测网络变化
 * - 当 WiFi SSID 匹配黑名单关键词时，显示半透明悬浮窗警示
 * - 内置防抖 + SSID 缓存 + 延迟移除机制，避免悬浮窗频繁闪烁/消失
 */
public class WifiMonitorService extends Service {

    private static final String CHANNEL_ID = "wifi_monitor_channel";
    private static final int NOTIFICATION_ID = 1001;

    /** onCapabilitiesChanged 防抖延迟（毫秒） */
    private static final long DEBOUNCE_DELAY_MS = 800;

    /** onLost 延迟移除悬浮窗（毫秒），等待可能的快速重连 */
    private static final long LOST_REMOVE_DELAY_MS = 2500;

    /** 设置变更时 Service 内部更新的广播 action */
    public static final String ACTION_UPDATE_OVERLAY = "com.example.wifimonitor.UPDATE_OVERLAY";

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private WifiManager wifiManager;
    private WindowManager windowManager;
    private View overlayView;
    private PreferencesManager prefs;

    /** 主线程 Handler，用于防抖和延迟执行 */
    private Handler mainHandler;

    /** 防抖 Runnable（onCapabilitiesChanged 合并） */
    private Runnable debounceCheckRunnable;

    /** onLost 延迟移除 Runnable */
    private Runnable lostRemoveRunnable;

    /** 缓存上一次有效 SSID，避免 getCurrentSsid() 偶发 null 导致误判 */
    private String lastKnownSsid = null;

    /** 接收设置变更广播，触发悬浮窗更新 */
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_UPDATE_OVERLAY.equals(intent.getAction())) {
                // 取消防抖，立即重新检查当前 WiFi
                if (mainHandler != null && debounceCheckRunnable != null) {
                    mainHandler.removeCallbacks(debounceCheckRunnable);
                }
                cancelLostRemove();
                checkCurrentWifi();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PreferencesManager(this);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());

        // 注册设置变更广播接收器
        IntentFilter filter = new IntentFilter(ACTION_UPDATE_OVERLAY);
        registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);

        registerNetworkCallback();
        // 启动时立即检查一次当前 WiFi
        checkCurrentWifi();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 注销广播接收器
        try {
            unregisterReceiver(updateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        unregisterNetworkCallback();
        // 取消所有待执行的回调
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        removeOverlay();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ==================== 通知 ====================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_desc));
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    // ==================== 网络监测（含防抖） ====================

    private void registerNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                // WiFi 连接成功，取消任何待执行的 lost 移除，立即检查
                cancelLostRemove();
                debounceCheck();
            }

            @Override
            public void onLost(Network network) {
                // WiFi 断开：不立即移除悬浮窗，延迟等待可能的快速重连
                scheduleLostRemove();
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                // 能力变化极其频繁，使用防抖合并为一次检查
                debounceCheck();
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    // ---------- 防抖检查 ----------

    /**
     * 延迟执行 WiFi 检查，短时间内重复调用会不断重置计时器。
     * 保证 onCapabilitiesChanged 高频抖动时只在最后一次稳定后才真正执行。
     */
    private void debounceCheck() {
        if (mainHandler == null) return;
        if (debounceCheckRunnable != null) {
            mainHandler.removeCallbacks(debounceCheckRunnable);
        }
        debounceCheckRunnable = () -> {
            cancelLostRemove(); // 稳定后再取消可能残留的 lost 移除
            checkCurrentWifi();
        };
        mainHandler.postDelayed(debounceCheckRunnable, DEBOUNCE_DELAY_MS);
    }

    // ---------- onLost 延迟移除 ----------

    private void scheduleLostRemove() {
        if (mainHandler == null) return;
        // 先取消已有的
        cancelLostRemove();
        lostRemoveRunnable = () -> {
            removeOverlay();
            lastKnownSsid = null;
        };
        mainHandler.postDelayed(lostRemoveRunnable, LOST_REMOVE_DELAY_MS);
    }

    private void cancelLostRemove() {
        if (lostRemoveRunnable != null) {
            mainHandler.removeCallbacks(lostRemoveRunnable);
            lostRemoveRunnable = null;
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
            }
            networkCallback = null;
        }
    }

    /**
     * 获取当前连接的 WiFi SSID。
     * 如果当前获取失败，回退到上次缓存的有效 SSID。
     */
    private String getCurrentSsid() {
        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                String ssid = wifiInfo.getSSID();
                if (ssid != null && !ssid.equals("<unknown ssid>") && !ssid.isEmpty()) {
                    // 移除 SSID 两端的引号
                    if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                        ssid = ssid.substring(1, ssid.length() - 1);
                    }
                    lastKnownSsid = ssid; // 更新缓存
                    return ssid;
                }
            }
        } catch (SecurityException ignored) {
        }
        // 当前获取失败，使用缓存兜底（避免偶发 null 导致悬浮窗误移除）
        return lastKnownSsid;
    }

    private void checkCurrentWifi() {
        String ssid = getCurrentSsid();
        if (ssid != null && prefs.matchesBlacklist(ssid)) {
            showOverlay();
        } else if (ssid == null) {
            // 完全没有 SSID（WiFi 已断开且无缓存），移除悬浮窗
            removeOverlay();
        } else {
            // SSID 明确存在但不匹配黑名单
            removeOverlay();
        }
    }

    // ==================== 悬浮窗管理 ====================

    private void showOverlay() {
        if (overlayView != null) return; // 已经显示
        if (windowManager == null) return;

        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                return;
            }
        }

        overlayView = new View(this);
        int color = prefs.getOverlayColor();
        int alpha = prefs.getOverlayAlpha();
        // 提取 RGB 部分并重新附加 alpha
        int argb = (alpha << 24) | (color & 0x00FFFFFF);
        overlayView.setBackgroundColor(argb);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                prefs.getOverlayWidth(),
                prefs.getOverlayHeight(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getOverlayX();
        params.y = prefs.getOverlayY();

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            overlayView = null;
        }
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
        }
    }

    /**
     * 更新悬浮窗外观（当设置变更时调用）
     */
    public void updateOverlay() {
        if (overlayView == null || windowManager == null) return;

        int color = prefs.getOverlayColor();
        int alpha = prefs.getOverlayAlpha();
        int argb = (alpha << 24) | (color & 0x00FFFFFF);
        overlayView.setBackgroundColor(argb);

        WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
        if (params != null) {
            params.width = prefs.getOverlayWidth();
            params.height = prefs.getOverlayHeight();
            params.x = prefs.getOverlayX();
            params.y = prefs.getOverlayY();
            try {
                windowManager.updateViewLayout(overlayView, params);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 获取悬浮窗是否正在显示
     */
    public boolean isOverlayShowing() {
        return overlayView != null;
    }
}
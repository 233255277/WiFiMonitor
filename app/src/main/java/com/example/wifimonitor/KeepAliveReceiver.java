package com.example.wifimonitor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 保活广播接收器（合并原 BootReceiver 功能）
 * - BOOT_COMPLETED：开机自启动服务 + 注册看门狗
 * - ACTION_KEEPALIVE：定时看门狗巡检，服务挂了就拉起
 * - ACTION_RESTART_SERVICE：延迟重启（onTaskRemoved / onDestroy 触发）
 */
public class KeepAliveReceiver extends BroadcastReceiver {

    static final String ACTION_KEEPALIVE = "com.example.wifimonitor.KEEPALIVE";
    static final String ACTION_RESTART_SERVICE = "com.example.wifimonitor.RESTART_SERVICE";
    static final long WATCHDOG_INTERVAL_MS = 15 * 60 * 1000; // 15 分钟

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        PreferencesManager prefs = new PreferencesManager(context);

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                // 开机自启动
                if (prefs.isServiceEnabled()) {
                    startServiceSafe(context);
                }
                // 开机时也注册看门狗闹钟
                scheduleWatchdog(context);
                break;

            case ACTION_KEEPALIVE:
                // 看门狗巡检：服务挂了就拉起
                if (prefs.isServiceEnabled() && !isServiceRunning(context)) {
                    startServiceSafe(context);
                }
                // 无论是否拉起，继续下一次巡检
                scheduleWatchdog(context);
                break;

            case ACTION_RESTART_SERVICE:
                // 延迟重启（来自 onTaskRemoved / onDestroy）
                if (prefs.isServiceEnabled() && !isServiceRunning(context)) {
                    startServiceSafe(context);
                }
                break;
        }
    }

    /**
     * 安全启动前台服务（兼容 SDK 版本）
     */
    static void startServiceSafe(Context context) {
        Intent serviceIntent = new Intent(context, WifiMonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 启动看门狗定时闹钟（使用 setExactAndAllowWhileIdle 确保 Doze 模式下准时触发）
     */
    static void scheduleWatchdog(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, KeepAliveReceiver.class);
        intent.setAction(ACTION_KEEPALIVE);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        long triggerAt = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS;
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        } catch (Exception e) {
            // Android 12- 兼容降级
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
    }

    /**
     * 取消看门狗闹钟（用户主动停止服务时调用）
     */
    static void cancelWatchdog(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, KeepAliveReceiver.class);
        intent.setAction(ACTION_KEEPALIVE);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );
        am.cancel(pending);
    }

    /**
     * 延迟重启服务（用于 onTaskRemoved 1s / onDestroy 5s 后尝试恢复）
     */
    static void scheduleRestart(Context context, long delayMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, KeepAliveReceiver.class);
        intent.setAction(ACTION_RESTART_SERVICE);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        long triggerAt = System.currentTimeMillis() + delayMs;
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        } catch (Exception e) {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
    }

    private boolean isServiceRunning(Context context) {
        android.app.ActivityManager manager =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service :
                    manager.getRunningServices(Integer.MAX_VALUE)) {
                if (WifiMonitorService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
}

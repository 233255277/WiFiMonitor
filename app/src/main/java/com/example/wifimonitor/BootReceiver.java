package com.example.wifimonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机自启动广播接收器：设备启动完成后自动启动监测服务
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            PreferencesManager pm = new PreferencesManager(context);
            if (pm.isServiceEnabled()) {
                Intent serviceIntent = new Intent(context, WifiMonitorService.class);
                context.startForegroundService(serviceIntent);
            }
        }
    }
}
package com.example.wifimonitor;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY = 100;
    private static final int REQUEST_LOCATION = 101;
    private static final int REQUEST_NOTIFICATION = 102;

    private TextView tvWifiName;
    private TextView tvBlacklistStatus;
    private TextView tvServiceStatus;
    private Button btnToggleService;
    private Button btnOverlayPermission;
    private Button btnLocationPermission;
    private Button btnSettings;

    private PreferencesManager prefs;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PreferencesManager(this);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        initViews();
        initListeners();
        updateServiceStatus();
        updateWifiInfo();
        registerWifiCallback();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        updateWifiInfo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterWifiCallback();
    }

    private void initViews() {
        tvWifiName = findViewById(R.id.tv_wifi_name);
        tvBlacklistStatus = findViewById(R.id.tv_blacklist_status);
        tvServiceStatus = findViewById(R.id.tv_service_status);
        btnToggleService = findViewById(R.id.btn_toggle_service);
        btnOverlayPermission = findViewById(R.id.btn_overlay_permission);
        btnLocationPermission = findViewById(R.id.btn_location_permission);
        btnSettings = findViewById(R.id.btn_settings);
    }

    private void initListeners() {
        btnToggleService.setOnClickListener(v -> toggleService());
        btnOverlayPermission.setOnClickListener(v -> requestOverlayPermission());
        btnLocationPermission.setOnClickListener(v -> requestLocationPermission());
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    // ==================== 服务控制 ====================

    private void toggleService() {
        if (isServiceRunning()) {
            stopService(new Intent(this, WifiMonitorService.class));
            prefs.setServiceEnabled(false);
            Toast.makeText(this, "监测服务已停止", Toast.LENGTH_SHORT).show();
        } else {
            // 检查必要权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
                    requestOverlayPermission();
                    return;
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestNotificationPermission();
                    return;
                }
            }

            Intent intent = new Intent(this, WifiMonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            prefs.setServiceEnabled(true);
            Toast.makeText(this, "监测服务已启动", Toast.LENGTH_SHORT).show();
        }
        updateServiceStatus();
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (WifiMonitorService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateServiceStatus() {
        if (isServiceRunning()) {
            tvServiceStatus.setText(getString(R.string.service_running));
            tvServiceStatus.setTextColor(getColor(R.color.safe));
            btnToggleService.setText(getString(R.string.stop_service));
        } else {
            tvServiceStatus.setText(getString(R.string.service_stopped));
            tvServiceStatus.setTextColor(getColor(R.color.danger));
            btnToggleService.setText(getString(R.string.start_service));
        }
    }

    // ==================== WiFi 信息显示 ====================

    private void registerWifiCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> updateWifiInfo());
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> updateWifiInfo());
            }
        };

        android.net.NetworkRequest request = new android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private void unregisterWifiCallback() {
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
            }
            networkCallback = null;
        }
    }

    private void updateWifiInfo() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                String ssid = wifiInfo.getSSID();
                if (ssid != null && !ssid.equals("<unknown ssid>") && !ssid.isEmpty()) {
                    if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                        ssid = ssid.substring(1, ssid.length() - 1);
                    }
                    tvWifiName.setText(ssid);
                    // 检查黑名单
                    if (prefs.matchesBlacklist(ssid)) {
                        tvBlacklistStatus.setText(getString(R.string.in_blacklist));
                        tvBlacklistStatus.setTextColor(getColor(R.color.danger));
                    } else {
                        tvBlacklistStatus.setText(getString(R.string.not_in_blacklist));
                        tvBlacklistStatus.setTextColor(getColor(R.color.safe));
                    }
                    return;
                }
            }
        } catch (SecurityException ignored) {
        }
        tvWifiName.setText(getString(R.string.no_wifi));
        tvBlacklistStatus.setText("");
    }

    // ==================== 权限 ====================

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY);
            } else {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOCATION);
            } else {
                Toast.makeText(this, "位置权限已授予", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "悬浮窗权限未授予，悬浮窗无法显示", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "位置权限已授予，现在可以读取WiFi名称", Toast.LENGTH_SHORT).show();
                updateWifiInfo();
            } else {
                Toast.makeText(this, "位置权限是获取WiFi名称所必需的", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_NOTIFICATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
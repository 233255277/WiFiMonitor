package com.example.wifimonitor;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private EditText etBlacklist;
    private LinearLayout llBlacklistItems;
    private TextView tvEmptyBlacklist;
    private EditText etCustomColor;
    private View vColorPreview;
    private SeekBar sbOpacity;
    private TextView tvOpacityValue;
    private EditText etWidth;
    private EditText etHeight;
    private EditText etX;
    private EditText etY;
    private MaterialButton btnBatteryOpt;

    private PreferencesManager prefs;
    private int selectedColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new PreferencesManager(this);
        selectedColor = prefs.getOverlayColor();

        initViews();
        initListeners();
        loadSettings();
        updateBatteryOptStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBatteryOptStatus();
    }

    private void initViews() {
        etBlacklist = findViewById(R.id.et_blacklist);
        llBlacklistItems = findViewById(R.id.ll_blacklist_items);
        tvEmptyBlacklist = findViewById(R.id.tv_empty_blacklist);
        etCustomColor = findViewById(R.id.et_custom_color);
        vColorPreview = findViewById(R.id.v_color_preview);
        sbOpacity = findViewById(R.id.sb_opacity);
        tvOpacityValue = findViewById(R.id.tv_opacity_value);
        etWidth = findViewById(R.id.et_width);
        etHeight = findViewById(R.id.et_height);
        etX = findViewById(R.id.et_x);
        etY = findViewById(R.id.et_y);
        btnBatteryOpt = findViewById(R.id.btn_battery_opt);
    }

    private void initListeners() {
        findViewById(R.id.btn_add_blacklist).setOnClickListener(v -> addBlacklistItem());
        findViewById(R.id.btn_save).setOnClickListener(v -> saveAllSettings());
        findViewById(R.id.btn_preview).setOnClickListener(v -> togglePreview());
        if (btnBatteryOpt != null) {
            btnBatteryOpt.setOnClickListener(v -> requestBatteryOptimization());
        }

        // 颜色预设按钮
        int[] colorIds = {
                R.id.color_red, R.id.color_orange, R.id.color_yellow,
                R.id.color_blue, R.id.color_purple, R.id.color_black
        };
        int[] colorValues = {
                0xFFE53935, 0xFFFF9800, 0xFFFFEB3B,
                0xFF2196F3, 0xFF9C27B0, 0xFF000000
        };

        for (int i = 0; i < colorIds.length; i++) {
            final int color = colorValues[i];
            findViewById(colorIds[i]).setOnClickListener(v -> {
                selectedColor = color;
                vColorPreview.setBackgroundColor(color);
                etCustomColor.setText(String.format("#%08X", color));
            });
        }

        // 透明度滑块
        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvOpacityValue.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void addBlacklistItem() {
        String keyword = etBlacklist.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            Toast.makeText(this, "请输入WiFi名称关键词", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否已存在
        for (String existing : prefs.getBlacklistAsList()) {
            if (existing.equalsIgnoreCase(keyword)) {
                Toast.makeText(this, "该关键词已存在", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        prefs.addToBlacklist(keyword);
        etBlacklist.setText("");
        refreshBlacklistUI();

        // 如果服务正在运行，立即重新检查
        notifyServiceUpdate();
    }

    private void removeBlacklistItem(String keyword) {
        prefs.removeFromBlacklist(keyword);
        refreshBlacklistUI();
        notifyServiceUpdate();
    }

    private void refreshBlacklistUI() {
        llBlacklistItems.removeAllViews();
        List<String> items = prefs.getBlacklistAsList();

        if (items.isEmpty()) {
            tvEmptyBlacklist.setVisibility(View.VISIBLE);
        } else {
            tvEmptyBlacklist.setVisibility(View.GONE);
            for (String keyword : items) {
                Chip chip = new Chip(this);
                chip.setText(keyword);
                chip.setCloseIconVisible(true);
                chip.setCloseIconResource(android.R.drawable.ic_menu_close_clear_cancel);
                chip.setChipBackgroundColorResource(android.R.color.darker_gray);
                chip.setTextColor(Color.WHITE);
                chip.setOnCloseIconClickListener(v -> removeBlacklistItem(keyword));

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(0, 0, 8, 8);
                chip.setLayoutParams(params);

                llBlacklistItems.addView(chip);
            }
        }
    }

    private void loadSettings() {
        // 加载黑名单
        refreshBlacklistUI();

        // 加载颜色
        vColorPreview.setBackgroundColor(selectedColor);
        etCustomColor.setText(String.format("#%08X", selectedColor));

        // 加载透明度
        int opacity = prefs.getOverlayOpacityPercent();
        sbOpacity.setProgress(opacity);
        tvOpacityValue.setText(opacity + "%");

        // 加载尺寸
        etWidth.setText(String.valueOf(prefs.getOverlayWidth()));
        etHeight.setText(String.valueOf(prefs.getOverlayHeight()));

        // 加载位置
        etX.setText(String.valueOf(prefs.getOverlayX()));
        etY.setText(String.valueOf(prefs.getOverlayY()));
    }

    private void saveAllSettings() {
        // 保存自定义颜色
        String colorStr = etCustomColor.getText().toString().trim();
        if (!TextUtils.isEmpty(colorStr)) {
            try {
                selectedColor = (int) Long.parseLong(colorStr.replace("#", ""), 16);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "颜色格式不正确，使用之前选中的颜色", Toast.LENGTH_SHORT).show();
            }
        }
        prefs.setOverlayColor(selectedColor);

        // 保存透明度
        prefs.setOverlayOpacityPercent(sbOpacity.getProgress());

        // 保存尺寸
        try {
            int width = Integer.parseInt(etWidth.getText().toString().trim());
            prefs.setOverlayWidth(width > 0 ? width : 1080);
        } catch (NumberFormatException e) {
            prefs.setOverlayWidth(1080);
        }
        try {
            int height = Integer.parseInt(etHeight.getText().toString().trim());
            prefs.setOverlayHeight(height > 0 ? height : 50);
        } catch (NumberFormatException e) {
            prefs.setOverlayHeight(50);
        }

        // 保存位置
        try {
            prefs.setOverlayX(Integer.parseInt(etX.getText().toString().trim()));
        } catch (NumberFormatException e) {
            prefs.setOverlayX(0);
        }
        try {
            prefs.setOverlayY(Integer.parseInt(etY.getText().toString().trim()));
        } catch (NumberFormatException e) {
            prefs.setOverlayY(0);
        }

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        notifyServiceUpdate();
        finish();
    }

    /**
     * 通知正在运行的服务更新悬浮窗（不发广播，直接发本地广播）
     */
    private void notifyServiceUpdate() {
        if (isServiceRunning()) {
            // 发送本地广播通知 Service 重新检查 WiFi，而非暴力 stop/start
            Intent intent = new Intent(WifiMonitorService.ACTION_UPDATE_OVERLAY);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
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

    // ==================== 电池优化豁免（第4层保活） ====================

    private void updateBatteryOptStatus() {
        if (btnBatteryOpt == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                btnBatteryOpt.setText(R.string.battery_opt_granted);
                btnBatteryOpt.setEnabled(false);
            } else {
                btnBatteryOpt.setText(R.string.battery_opt_request);
                btnBatteryOpt.setEnabled(true);
            }
        } else {
            btnBatteryOpt.setVisibility(View.GONE);
        }
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, R.string.battery_opt_already, Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                // 部分 ROM 不支持，跳转手动设置页
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            }
        }
    }

    // 预览按钮状态
    private boolean previewShowing = false;

    private void togglePreview() {
        if (previewShowing) {
            // 隐藏预览：先标记禁用再停止服务
            if (isServiceRunning()) {
                prefs.setServiceEnabled(false);
                KeepAliveReceiver.cancelWatchdog(this);
                Intent intent = new Intent(this, WifiMonitorService.class);
                stopService(intent);
                Toast.makeText(this, "预览已隐藏，监测服务已停止", Toast.LENGTH_SHORT).show();
            }
            ((MaterialButton) findViewById(R.id.btn_preview)).setText(R.string.preview);
            previewShowing = false;
        } else {
            // 显示预览：临时启动服务
            if (!isServiceRunning()) {
                // 检查权限
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (!android.provider.Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "请先在主界面授予悬浮窗权限", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                Intent intent = new Intent(this, WifiMonitorService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                prefs.setServiceEnabled(true);
                KeepAliveReceiver.scheduleWatchdog(this);
                Toast.makeText(this, "预览模式：悬浮窗正在显示", Toast.LENGTH_SHORT).show();
            }
            ((MaterialButton) findViewById(R.id.btn_preview)).setText(R.string.hide_preview);
            previewShowing = true;
        }
    }
}

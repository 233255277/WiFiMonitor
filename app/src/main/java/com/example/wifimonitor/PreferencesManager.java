package com.example.wifimonitor;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 偏好设置管理器：统一管理所有自定义配置
 */
public class PreferencesManager {

    private static final String PREFS_NAME = "wifi_monitor_prefs";
    private static final String KEY_BLACKLIST = "blacklist";
    private static final String KEY_OVERLAY_COLOR = "overlay_color";
    private static final String KEY_OVERLAY_OPACITY = "overlay_opacity";
    private static final String KEY_OVERLAY_WIDTH = "overlay_width";
    private static final String KEY_OVERLAY_HEIGHT = "overlay_height";
    private static final String KEY_OVERLAY_X = "overlay_x";
    private static final String KEY_OVERLAY_Y = "overlay_y";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";

    private final SharedPreferences prefs;

    public PreferencesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ==================== 黑名单 ====================

    public Set<String> getBlacklist() {
        return prefs.getStringSet(KEY_BLACKLIST, new HashSet<>());
    }

    public void setBlacklist(Set<String> blacklist) {
        prefs.edit().putStringSet(KEY_BLACKLIST, blacklist).apply();
    }

    public void addToBlacklist(String keyword) {
        Set<String> set = new HashSet<>(getBlacklist());
        set.add(keyword.trim());
        setBlacklist(set);
    }

    public void removeFromBlacklist(String keyword) {
        Set<String> set = new HashSet<>(getBlacklist());
        set.remove(keyword);
        setBlacklist(set);
    }

    public List<String> getBlacklistAsList() {
        return new ArrayList<>(getBlacklist());
    }

    /**
     * 检查给定 SSID 是否匹配黑名单中任何关键词
     */
    public boolean matchesBlacklist(String ssid) {
        if (ssid == null) return false;
        String lowerSsid = ssid.toLowerCase().trim();
        for (String keyword : getBlacklist()) {
            if (!keyword.isEmpty() && lowerSsid.contains(keyword.toLowerCase().trim())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 悬浮窗颜色 ====================

    public int getOverlayColor() {
        return prefs.getInt(KEY_OVERLAY_COLOR, 0xFFE53935); // 默认红色
    }

    public void setOverlayColor(int color) {
        prefs.edit().putInt(KEY_OVERLAY_COLOR, color).apply();
    }

    // ==================== 悬浮窗透明度 ====================

    /**
     * @return 透明度值 (0-255)，0=完全透明，255=完全不透明
     */
    public int getOverlayAlpha() {
        // 存储为百分比 0-100，转换为 alpha 0-255
        int percent = prefs.getInt(KEY_OVERLAY_OPACITY, 50);
        return (int) (percent / 100.0f * 255);
    }

    public int getOverlayOpacityPercent() {
        return prefs.getInt(KEY_OVERLAY_OPACITY, 50);
    }

    public void setOverlayOpacityPercent(int percent) {
        prefs.edit().putInt(KEY_OVERLAY_OPACITY, Math.max(10, Math.min(90, percent))).apply();
    }

    // ==================== 悬浮窗宽高 ====================

    public int getOverlayWidth() {
        return prefs.getInt(KEY_OVERLAY_WIDTH, 1080);
    }

    public void setOverlayWidth(int width) {
        prefs.edit().putInt(KEY_OVERLAY_WIDTH, Math.max(1, width)).apply();
    }

    public int getOverlayHeight() {
        return prefs.getInt(KEY_OVERLAY_HEIGHT, 50);
    }

    public void setOverlayHeight(int height) {
        prefs.edit().putInt(KEY_OVERLAY_HEIGHT, Math.max(1, height)).apply();
    }

    // ==================== 悬浮窗位置 ====================

    public int getOverlayX() {
        return prefs.getInt(KEY_OVERLAY_X, 0);
    }

    public void setOverlayX(int x) {
        prefs.edit().putInt(KEY_OVERLAY_X, x).apply();
    }

    public int getOverlayY() {
        return prefs.getInt(KEY_OVERLAY_Y, 0);
    }

    public void setOverlayY(int y) {
        prefs.edit().putInt(KEY_OVERLAY_Y, y).apply();
    }

    // ==================== 服务状态 ====================

    public boolean isServiceEnabled() {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false);
    }

    public void setServiceEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply();
    }
}
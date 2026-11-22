package com.h.browser.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static SharedPreferences sp;

    private Prefs() {
    }

    public static void init(Context context) {
        if (sp == null) {
            sp = context.getApplicationContext().getSharedPreferences("h_prefs", Context.MODE_PRIVATE);
        }
    }

    public static SharedPreferences get() {
        return sp;
    }

    public static String searchEngine() {
        return sp.getString("search_engine", "https://www.baidu.com/s?wd=%s");
    }

    public static void setSearchEngine(String value) {
        sp.edit().putString("search_engine", value).apply();
    }

    public static String homepage() {
        return sp.getString("homepage", "v://home");
    }

    public static void setHomepage(String value) {
        sp.edit().putString("homepage", value).apply();
    }

    public static boolean adblockEnabled() {
        return sp.getBoolean("adblock", true);
    }

    public static void setAdblockEnabled(boolean value) {
        sp.edit().putBoolean("adblock", value).apply();
    }

    public static String adblockSubscription() {
        return sp.getString("adblock_sub", "https://easylist-downloads.adblockplus.org/easylistchina.txt");
    }

    public static void setAdblockSubscription(String value) {
        sp.edit().putString("adblock_sub", value).apply();
    }

    public static long adblockUpdated() {
        return sp.getLong("adblock_time", 0L);
    }

    public static void setAdblockUpdated(long value) {
        sp.edit().putLong("adblock_time", value).apply();
    }

    public static int uaMode() {
        return sp.getInt("ua_mode", 0);
    }

    public static void setUaMode(int value) {
        sp.edit().putInt("ua_mode", value).apply();
    }

    public static String customUa() {
        return sp.getString("ua_custom", "");
    }

    public static void setCustomUa(String value) {
        sp.edit().putString("ua_custom", value).apply();
    }

    public static int fontZoom() {
        return sp.getInt("font_zoom", 100);
    }

    public static void setFontZoom(int value) {
        sp.edit().putInt("font_zoom", value).apply();
    }

    public static boolean pullRefresh() {
        return sp.getBoolean("pull_refresh", true);
    }

    public static void setPullRefresh(boolean value) {
        sp.edit().putBoolean("pull_refresh", value).apply();
    }

    public static boolean edgeGesture() {
        return sp.getBoolean("edge_gesture", true);
    }

    public static void setEdgeGesture(boolean value) {
        sp.edit().putBoolean("edge_gesture", value).apply();
    }

    public static boolean webDark() {
        return sp.getBoolean("web_dark", false);
    }

    public static void setWebDark(boolean value) {
        sp.edit().putBoolean("web_dark", value).apply();
    }

    public static String toolbarItems() {
        return sp.getString("toolbar_items", "back,forward,home,tabs,menu");
    }

    public static void setToolbarItems(String value) {
        sp.edit().putString("toolbar_items", value).apply();
    }

    public static boolean incognitoDefault() {
        return sp.getBoolean("incognito", false);
    }

    public static void setIncognitoDefault(boolean value) {
        sp.edit().putBoolean("incognito", value).apply();
    }

    public static String aiBase() {
        return sp.getString("ai_base", "");
    }

    public static String aiKey() {
        return sp.getString("ai_key", "");
    }

    public static String aiModel() {
        return sp.getString("ai_model", "gpt-4o-mini");
    }

    public static void setAi(String base, String key, String model) {
        sp.edit().putString("ai_base", base).putString("ai_key", key).putString("ai_model", model).apply();
    }

    public static String transUrl() {
        return sp.getString("trans_url", "");
    }

    public static String transKey() {
        return sp.getString("trans_key", "");
    }

    public static String transTarget() {
        return sp.getString("trans_target", "zh");
    }

    public static void setTrans(String url, String key, String target) {
        sp.edit().putString("trans_url", url).putString("trans_key", key).putString("trans_target", target).apply();
    }

    public static float ttsSpeed() {
        return sp.getFloat("tts_speed", 1.0f);
    }

    public static void setTtsSpeed(float value) {
        sp.edit().putFloat("tts_speed", value).apply();
    }

    public static String davUrl() {
        return sp.getString("dav_url", "");
    }

    public static String davUser() {
        return sp.getString("dav_user", "");
    }

    public static String davPass() {
        return sp.getString("dav_pass", "");
    }

    public static void setDav(String url, String user, String pass) {
        sp.edit().putString("dav_url", url).putString("dav_user", user).putString("dav_pass", pass).apply();
    }
}

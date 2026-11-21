package com.h.browser.ui;

import android.webkit.JavascriptInterface;

import androidx.appcompat.app.AppCompatDelegate;

import com.h.browser.adblock.AdBlock;
import com.h.browser.core.BrowserActivity;
import com.h.browser.data.Db;
import com.h.browser.data.Prefs;
import com.h.browser.theme.SkinStore;
import com.h.browser.widget.BaseSearchWidget;

public class NativeBridge {

    private final BrowserActivity activity;

    public NativeBridge(BrowserActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void search(final String query) {
        activity.runOnUiThread(() -> activity.search(query));
    }

    @JavascriptInterface
    public void openUrl(final String url) {
        activity.runOnUiThread(() -> activity.openUrl(url, false));
    }

    @JavascriptInterface
    public void newTab(final String url) {
        activity.runOnUiThread(() -> activity.openUrl(url, true));
    }

    @JavascriptInterface
    public void deleteBookmark(final long id) {
        new Thread(() -> Db.get(activity).removeBookmark(id)).start();
    }

    @JavascriptInterface
    public void deleteHistory(final long id) {
        new Thread(() -> Db.get(activity).removeHistory(id)).start();
    }

    @JavascriptInterface
    public void clearHistory() {
        new Thread(() -> Db.get(activity).clearHistory()).start();
    }

    @JavascriptInterface
    public void clearLog() {
        AdBlock.clearLog();
    }

    @JavascriptInterface
    public void updateAdblock() {
        final String url = Prefs.adblockSubscription();
        AdBlock.updateSubscription(activity, url, (ok, message) -> activity.runOnUiThread(() -> {
            activity.toast(message);
            activity.reloadInternal();
        }));
    }

    @JavascriptInterface
    public void setThemeMode(final int mode) {
        Prefs.get().edit().putInt("theme_mode", mode).apply();
        BaseSearchWidget.refresh(activity);
        activity.runOnUiThread(() -> {
            AppCompatDelegate.setDefaultNightMode(mode == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                    : mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            activity.recreate();
        });
    }

    @JavascriptInterface
    public void setSearchEngine(final String template) {
        if (template != null && template.contains("%s")) {
            Prefs.setSearchEngine(template);
        }
    }

    @JavascriptInterface
    public String homeData() {
        return SkinStore.statsJson(activity);
    }

    @JavascriptInterface
    public void showLinkMenu(final String link, final String image) {
        activity.runOnUiThread(() -> activity.showLinkSheet(link, image));
    }

    @JavascriptInterface
    public void applySkin(final String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        Prefs.setSkinId(id);
        BaseSearchWidget.refresh(activity);
        activity.runOnUiThread(() -> {
            activity.toast("已应用主题");
            activity.refreshCurrentIfInternal();
        });
    }

    @JavascriptInterface
    public void deleteSkin(final String id) {
        final boolean deleted = SkinStore.delete(activity, id);
        activity.runOnUiThread(() -> {
            activity.toast(deleted ? "已删除主题" : "内置主题不可删除");
            BaseSearchWidget.refresh(activity);
            activity.refreshCurrentIfInternal();
        });
    }

    @JavascriptInterface
    public void installSkinUrl(final String url) {
        if (url == null || url.trim().isEmpty()) {
            activity.runOnUiThread(() -> activity.toast("请输入主题 JSON 地址"));
            return;
        }
        SkinStore.installFromUrl(activity, url.trim(), (ok, message) ->
                activity.runOnUiThread(() -> {
                    activity.toast(message);
                    if (ok) {
                        activity.refreshCurrentIfInternal();
                    }
                }));
    }

    @JavascriptInterface
    public void pickSkinFile() {
        activity.runOnUiThread(activity::pickSkinFile);
    }

    @JavascriptInterface
    public void fetchSkinIndex(final String url) {
        String target = url == null || url.trim().isEmpty() ? Prefs.skinIndex() : url.trim();
        SkinStore.fetchIndex(activity, target, (ok, message) ->
                activity.runOnUiThread(() -> {
                    activity.toast(message);
                    if (ok) {
                        activity.refreshCurrentIfInternal();
                    }
                }));
    }

    @JavascriptInterface
    public void installMarketSkin(final int index) {
        SkinStore.installMarket(activity, index, (ok, message) ->
                activity.runOnUiThread(() -> {
                    activity.toast(message);
                    if (ok) {
                        activity.refreshCurrentIfInternal();
                    }
                }));
    }

    @JavascriptInterface
    public void pickBookmarkFile() {
        activity.runOnUiThread(activity::importBookmarksFile);
    }

    @JavascriptInterface
    public void exportBookmarks() {
        activity.runOnUiThread(activity::exportBookmarksFile);
    }

    @JavascriptInterface
    public void exportHistory() {
        activity.runOnUiThread(activity::exportHistoryFile);
    }

    @JavascriptInterface
    public void setMonetSource(final int source) {
        Prefs.setMonetSource(source);
        activity.runOnUiThread(() -> {
            BaseSearchWidget.refresh(activity);
            activity.recreate();
        });
    }

    @JavascriptInterface
    public void setMonetSeed(final String hex) {
        try {
            int color = android.graphics.Color.parseColor(hex.startsWith("#") ? hex : "#" + hex);
            Prefs.setMonetSeed(color | 0xFF000000);
            Prefs.setMonetSource(2);
            activity.runOnUiThread(activity::recreate);
        } catch (Throwable t) {
            activity.runOnUiThread(() -> activity.toast("颜色格式应为 #RRGGBB"));
        }
    }

    @JavascriptInterface
    public void openSettings() {
        activity.runOnUiThread(activity::openSettings);
    }

    @JavascriptInterface
    public void openDownloads() {
        activity.runOnUiThread(activity::openDownloads);
    }

    @JavascriptInterface
    public void openVault() {
        activity.runOnUiThread(activity::openVault);
    }

    @JavascriptInterface
    public void openSync() {
        activity.runOnUiThread(activity::openSync);
    }

    @JavascriptInterface
    public void startScan() {
        activity.runOnUiThread(activity::startScan);
    }

    @JavascriptInterface
    public void readAloud() {
        activity.runOnUiThread(activity::readAloud);
    }

    @JavascriptInterface
    public void reloadPage() {
        activity.runOnUiThread(activity::reloadInternal);
    }
}

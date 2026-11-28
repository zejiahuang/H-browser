package com.h.browser.ui;

import android.webkit.JavascriptInterface;

import androidx.appcompat.app.AppCompatDelegate;

import com.h.browser.adblock.AdBlock;
import com.h.browser.core.BrowserActivity;
import com.h.browser.data.Db;
import com.h.browser.data.Prefs;
import com.h.browser.widget.BaseSearchWidget;

public class NativeBridge {

    private final BrowserActivity activity;

    public NativeBridge(BrowserActivity activity) {
        this.activity = activity;
    }

    private boolean trusted() {
        return activity.currentPageInternal();
    }

    @JavascriptInterface
    public void search(final String query) {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(() -> activity.search(query));
    }

    @JavascriptInterface
    public void openUrl(final String url) {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(() -> activity.openUrl(url, false));
    }

    @JavascriptInterface
    public void newTab(final String url) {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(() -> activity.openUrl(url, true));
    }

    @JavascriptInterface
    public void deleteBookmark(final long id) {
        if (!trusted()) {
            return;
        }
        new Thread(() -> Db.get(activity).removeBookmark(id)).start();
    }

    @JavascriptInterface
    public void deleteHistory(final long id) {
        if (!trusted()) {
            return;
        }
        new Thread(() -> Db.get(activity).removeHistory(id)).start();
    }

    @JavascriptInterface
    public void clearHistory() {
        if (!trusted()) {
            return;
        }
        new Thread(() -> Db.get(activity).clearHistory()).start();
    }

    @JavascriptInterface
    public void clearLog() {
        if (!trusted()) {
            return;
        }
        AdBlock.clearLog();
    }

    @JavascriptInterface
    public void updateAdblock() {
        if (!trusted()) {
            return;
        }
        AdBlock.updateAll(activity, (ok, message) -> activity.runOnUiThread(() -> {
            activity.toast(message);
            activity.reloadInternal();
        }));
    }

    @JavascriptInterface
    public void saveAdRule(final String host, final String selector) {
        activity.runOnUiThread(() -> activity.saveAdRule(host, selector));
    }

    @JavascriptInterface
    public void showLinkMenu(final String link, final String image, final String selector) {
        activity.runOnUiThread(() -> activity.showLinkSheetFromPage(link, image, selector));
    }

    @JavascriptInterface
    public void setThemeMode(final int mode) {
        if (!trusted()) {
            return;
        }
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
        if (!trusted()) {
            return;
        }
        if (template != null && template.contains("%s")) {
            Prefs.setSearchEngine(template);
        }
    }

    @JavascriptInterface
    public void pickBookmarkFile() {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(activity::importBookmarksFile);
    }

    @JavascriptInterface
    public void exportBookmarks() {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(activity::exportBookmarksFile);
    }

    @JavascriptInterface
    public void exportHistory() {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(activity::exportHistoryFile);
    }

    @JavascriptInterface
    public void openSettings() {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(activity::openSettings);
    }

    @JavascriptInterface
    public void openDownloads() {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(activity::openDownloads);
    }

    @JavascriptInterface
    public void openVault() {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(activity::openVault);
    }

    @JavascriptInterface
    public void openSync() {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(activity::openSync);
    }

    @JavascriptInterface
    public void startScan() {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(activity::startScan);
    }

    @JavascriptInterface
    public void readAloud() {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(activity::readAloud);
    }

    @JavascriptInterface
    public void reloadPage() {
        if (!trusted()) {
            return;
        }
        activity.runOnUiThread(activity::reloadInternal);
    }
}

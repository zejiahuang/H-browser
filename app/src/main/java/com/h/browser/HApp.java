package com.h.browser;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.h.browser.data.Prefs;

public class HApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        Prefs.init(this);
        com.h.browser.privacy.PrivacyStats.init(this);
        com.h.browser.net.WebViewProxy.apply();
        com.h.browser.ui.Glass.migrate();
        com.h.browser.ui.MenuDefs.migrateLegacy();
        com.h.browser.userscript.CustomTheme.init(this);
        int mode = Prefs.get().getInt("theme_mode", 0);
        AppCompatDelegate.setDefaultNightMode(mode == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}

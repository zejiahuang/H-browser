package com.h.browser;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.h.browser.data.Prefs;

public class HApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        Prefs.init(this);
        int mode = Prefs.get().getInt("theme_mode", 0);
        AppCompatDelegate.setDefaultNightMode(mode == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        try {
            DynamicColors.applyToActivitiesIfAvailable(this);
        } catch (Throwable ignored) {
        }
    }
}

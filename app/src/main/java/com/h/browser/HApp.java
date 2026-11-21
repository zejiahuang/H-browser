package com.h.browser;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.h.browser.data.Prefs;
import com.h.browser.theme.Monet;

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
        if (Build.VERSION.SDK_INT >= 29) {
            registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
                    Monet.apply(activity);
                }

                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                }
            });
        }
    }
}

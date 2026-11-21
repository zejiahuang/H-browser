package com.h.browser.theme;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Build;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.h.browser.data.Prefs;

/** 莫奈取色：Android 12+ 跟随壁纸，或从皮肤/自定义颜色生成整套配色。 */
public final class Monet {

    public static final int SOURCE_WALLPAPER = 0;
    public static final int SOURCE_SKIN = 1;
    public static final int SOURCE_CUSTOM = 2;
    public static final int SOURCE_OFF = 3;

    private Monet() {
    }

    public static boolean supported() {
        return Build.VERSION.SDK_INT >= 31;
    }

    public static void apply(Activity activity) {
        if (!supported()) {
            return;
        }
        int source = Prefs.monetSource();
        try {
            if (source == SOURCE_OFF) {
                return;
            }
            if (source == SOURCE_WALLPAPER) {
                DynamicColors.applyToActivityIfAvailable(activity);
                return;
            }
            int seed = source == SOURCE_SKIN ? SkinStore.seedColor(activity) : Prefs.monetSeed();
            Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            bitmap.setPixel(0, 0, seed | 0xFF000000);
            DynamicColors.applyToActivityIfAvailable(activity,
                    new DynamicColorsOptions.Builder().setContentBasedSource(bitmap).build());
            bitmap.recycle();
        } catch (Throwable ignored) {
        }
    }

    public static String summary() {
        if (!supported()) {
            return "需 Android 12+";
        }
        switch (Prefs.monetSource()) {
            case SOURCE_SKIN:
                return "从当前皮肤取色";
            case SOURCE_CUSTOM:
                return String.format("#%06X", Prefs.monetSeed() & 0xFFFFFF);
            case SOURCE_OFF:
                return "关闭（经典氢青）";
            default:
                return "跟随壁纸";
        }
    }
}

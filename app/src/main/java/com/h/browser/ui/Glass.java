package com.h.browser.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

import com.h.browser.data.Prefs;

/**
 * 玻璃材质的统一入口：偏好读取、面板背景、窗口级背景模糊。
 */
public final class Glass {

    private Glass() {
    }

    public static boolean enabled() {
        return Prefs.get().getBoolean("glass", true);
    }

    public static int material() {
        return Prefs.get().getInt("glass_material", 0);
    }

    /** 高级材质：柔光 / 磨砂 / 液态 / 纯色。 */
    public static int style() {
        return Prefs.get().getInt("glass_style", GlassPreset.SOFT);
    }

    public static GlassPreset preset() {
        return GlassPreset.of(style());
    }

    /** 是否走 RuntimeShader 折射（柔光 / 液态）。 */
    public static boolean vitro() {
        return preset().shader;
    }

    public static boolean frosted() {
        return style() == GlassPreset.FROST;
    }

    public static boolean solid() {
        return style() == GlassPreset.SOLID;
    }

    public static String materialLabel() {
        return preset().label;
    }

    public static int radius() {
        return Prefs.get().getInt("glass_radius", 24);
    }

    /** 底栏卡外层圆角（R93 默认 40dp）。 */
    public static int outerRadius() {
        return Prefs.get().getInt("glass_outer_radius", 40);
    }

    /** 底栏卡 G2 曲线 0–100。 */
    public static int curve() {
        return Prefs.get().getInt("glass_curve", 0);
    }

    /** 地址行与底栏卡顶部的间距（R93 默认 10dp）。 */
    public static int addressGap() {
        return Prefs.get().getInt("glass_gap", 10);
    }

    /** 首次运行补全新键：旧「材质」值映射到四种高级材质。 */
    public static void migrate() {
        if (!Prefs.get().contains("glass_outer_radius")) {
            // R93 的悬浮卡固定 40dp 外圆角，旧工具栏圆角不再沿用，避免默认值偏离
            Prefs.get().edit().putInt("glass_outer_radius", 40).apply();
        }
        if (!Prefs.get().contains("glass_style")) {
            int old = Prefs.get().getInt("glass_material", -1);
            int style = old == 1 ? GlassPreset.LIQUID
                    : old == 0 ? GlassPreset.FROST : GlassPreset.SOFT;
            Prefs.get().edit().putInt("glass_style", style).apply();
        }
    }

    public static int progressiveRadius() {
        return Prefs.get().getInt("progressive_radius", 18);
    }

    public static boolean progressive() {
        return enabled() && Prefs.get().getBoolean("progressive_blur", true);
    }

    public static boolean floatingToolbar() {
        return Prefs.get().getBoolean("floating_toolbar", true);
    }

    public static int toolbarRadius() {
        return Prefs.get().getInt("toolbar_radius", 28);
    }

    public static int toolbarMargin() {
        return Prefs.get().getInt("toolbar_margin", 15);
    }

    public static boolean menuSimple() {
        return Prefs.get().getInt("menu_style", 0) == 1;
    }

    public static boolean night(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static GradientDrawable panel(Context context, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        boolean night = night(context);
        GlassPreset p = preset();
        int fill;
        if (solid()) {
            fill = night ? p.fillNight : p.fillDay;
        } else if (night) {
            fill = vitro() ? 0xB81B2223 : 0xFF181F20;
        } else {
            fill = vitro() ? 0xB8F2F7F7 : 0xFFFCFDFD;
        }
        drawable.setColor(fill);
        drawable.setCornerRadius(Ui.dp(context, radiusDp));
        drawable.setStroke(Math.max(1, Ui.dp(context, 1)),
                solid() ? (night ? 0x1AFFFFFF : 0x22000000)
                        : vitro() ? (night ? 0x4DFFFFFF : 0x6670C8CE)
                        : (night ? 0x1AFFFFFF : 0x22000000));
        return drawable;
    }

    public static GradientDrawable panel(Context context, float[] radii) {
        GradientDrawable drawable = panel(context, 0);
        drawable.setCornerRadii(radii);
        return drawable;
    }

    /**
     * API 31+ 使用系统背景模糊；更低版本保留半透明玻璃，不抛异常。
     * 需要在 window 已可见（show 之后）调用。
     */
    public static void applyWindowBlur(Window window) {
        if (window == null || !enabled()) {
            return;
        }
        int px = Ui.dp(window.getContext(), Math.max(1, radius()));
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        window.setAttributes(attrs);
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.setBackgroundBlurRadius(px);
            } catch (Throwable ignored) {
            }
        }
    }

    public static int scrim(Context context) {
        return night(context) ? 0x66000000 : 0x33000000;
    }
}

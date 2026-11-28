package com.h.browser.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.core.view.ViewCompat;

import com.h.browser.R;

/**
 * 浏览器内统一底部浮层：透明全屏窗口 + 底部锚定玻璃卡，与菜单同款进出场、下滑关闭。
 * 快照玻璃不可用（低版本或调试回退）时换成近不透明同色面板，纯色材质直接用纯色卡。
 * 窗口参数必须在 show() 之前设置，否则首帧会按默认居中小窗绘制并跳变。
 */
public final class GlassSheet {

    private final Activity activity;
    private final Dialog dialog;
    private final SheetGesture floating;
    private final LinearLayout sheet;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final GlassDrawable glass;
    private final GlassShadowView shadow;
    private final int sidePadding;
    private final FrameLayout.LayoutParams sheetParams;
    private final Runnable closeAnim;
    private Runnable onDismiss;
    private android.content.DialogInterface.OnDismissListener dismissListener;
    private boolean closing;

    public GlassSheet(Activity activity) {
        this.activity = activity;
        final int radiusDp = Glass.outerRadius();
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        glass = Glass.snapshotSupported()
                ? new GlassDrawable(activity.getWindow(), true, radiusDp, Glass.curve()) : null;
        sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        if (glass != null) {
            glass.setTintAlpha(Glass.preset().menuDay, Glass.preset().menuNight);
            glass.setDrawShadow(false);
            sheet.setBackground(glass);
        } else if (!Glass.enabled() || Glass.solid()) {
            GradientDrawable bg = Glass.enabled() ? Glass.panel(activity, radiusDp) : new GradientDrawable();
            if (!Glass.enabled()) {
                bg.setColor(Ui.color(activity, R.color.surface_container));
                bg.setCornerRadius(Ui.dp(activity, radiusDp));
            }
            sheet.setBackground(bg);
        } else {
            sheet.setBackground(Glass.panelFallback(activity, radiusDp));
        }
        if (Build.VERSION.SDK_INT >= 21) {
            sheet.setClipToOutline(false);
            sheet.setElevation(glass != null ? 0 : dp(24));
        }

        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(20));
        scroll = new ScrollView(activity);
        scroll.setClipToOutline(false);
        scroll.addView(list);
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        floating = new SheetGesture(activity);
        floating.setClipChildren(false);
        floating.setClipToPadding(false);
        // 背景层只负责点按关闭，不进入 TalkBack 焦点链
        floating.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        sidePadding = dp(14);
        floating.setPadding(sidePadding, 0, sidePadding, 0);

        if (glass != null) {
            GlassPreset p = Glass.preset();
            shadow = new GlassShadowView(activity);
            shadow.setShape(radiusDp, Glass.curve());
            shadow.setShadow(p.shadowAlpha, p.shadowSpreadDp + 8f, p.shadowSigmaDp);
            floating.addView(shadow, new FrameLayout.LayoutParams(0, 0));
        } else {
            shadow = null;
        }

        sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        sheetParams.bottomMargin = dp(15);
        floating.addView(sheet, sheetParams);

        final Runnable placeShadow = () -> {
            if (shadow == null || sheet.getWidth() <= 0 || sheet.getHeight() <= 0) {
                return;
            }
            float spread = shadow.spreadPx();
            int sw = sheet.getWidth() + Math.round(spread * 2);
            int sh = sheet.getHeight() + Math.round(spread * 2 + dp(6));
            int left = Math.round(sheet.getLeft() - sidePadding - spread);
            int top = Math.round(sheet.getTop() - spread);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) shadow.getLayoutParams();
            if (lp.width != sw || lp.height != sh || lp.leftMargin != left
                    || lp.topMargin != top) {
                lp.width = sw;
                lp.height = sh;
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                lp.leftMargin = left;
                lp.topMargin = top;
                shadow.setLayoutParams(lp);
            }
            if (glass != null && glass.backdrop() != null) {
                shadow.setBackdrop(glass.backdrop(),
                        spread - glass.offsetX(),
                        spread - glass.offsetY(),
                        Glass.night(activity) ? 0.34f : 0.18f);
            }
        };
        sheet.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (v.getWidth() > 0 && v.getHeight() > 0) {
                G2Path.applyOutline(v, dp(radiusDp), Glass.curve() / 100f);
            }
            placeShadow.run();
        });
        if (glass != null) {
            glass.setOnBuilt(() -> sheet.post(placeShadow));
        }

        closeAnim = () -> {
            if (closing || !dialog.isShowing()) {
                return;
            }
            closing = true;
            float distance = Math.max(0f, sheet.getTranslationY()) + dp(70);
            sheet.animate().translationY(distance).alpha(0f).setDuration(150)
                    .withEndAction(dialog::dismiss).start();
            if (shadow != null) {
                shadow.animate().translationY(distance).alpha(0f).setDuration(150).start();
            }
        };
        floating.setOnClickListener(v -> closeAnim.run());
        floating.setCallback(new SheetGesture.Callback() {
            @Override
            public void onHorizontal(float dx) {
            }

            @Override
            public void onPageSwipe(float dx) {
            }

            @Override
            public void onDrag(float dy) {
                sheet.setTranslationY(dy);
                sheet.setAlpha(1f - Math.min(0.45f,
                        dy / Math.max(1f, sheet.getHeight()) * 0.8f));
                if (shadow != null) {
                    shadow.setTranslationY(dy);
                }
            }

            @Override
            public void onDragRelease(float dy, float velocityY) {
                if (dy > dp(88) || velocityY > 1100f) {
                    closeAnim.run();
                    return;
                }
                sheet.animate().translationY(0f).alpha(1f).setDuration(160).start();
                if (shadow != null) {
                    shadow.animate().translationY(0f).alpha(1f).setDuration(160).start();
                }
            }
        });
        dialog.setOnCancelListener(d -> closeAnim.run());
        dialog.setOnDismissListener(d -> {
            closing = false;
            if (glass != null) {
                glass.release();
            }
            if (dismissListener != null) {
                dismissListener.onDismiss(d);
            }
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        dialog.setContentView(floating);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            w.setDimAmount(0f);
            w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(0);
            // 宿主窗口若带状态栏内缩（如设置页），弹层内容会被整体上移而触摸
            // 仍按屏幕坐标派发；强制装饰层铺满全屏，保证绘制与派发同一坐标系
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            if (Build.VERSION.SDK_INT >= 30) {
                w.setDecorFitsSystemWindows(false);
            } else {
                w.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
            if (Glass.frosted()) {
                Glass.applyWindowBlur(w);
            }
        }
        floating.setAlpha(0f);
        sheet.setTranslationY(dp(26));
        if (shadow != null) {
            shadow.setTranslationY(dp(26));
        }
        dialog.setOnShowListener(d -> {
            floating.animate().alpha(1f).setDuration(120).start();
            sheet.animate().translationY(0f).setDuration(200).start();
            if (shadow != null) {
                shadow.animate().translationY(0f).setDuration(200).start();
            }
            sheet.post(() -> {
                int maxH = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.58f);
                if (scroll.getHeight() > maxH) {
                    ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                    lp.height = maxH;
                    scroll.setLayoutParams(lp);
                }
            });
        });
    }

    public Activity activity() {
        return activity;
    }

    /** 内容容器（已滚动），行内边距由调用方按内容类型设置。 */
    public LinearLayout content() {
        return list;
    }

    public View sheetView() {
        return sheet;
    }

    public Dialog dialog() {
        return dialog;
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    /** 底部让位：传入锚点（底栏）本身占用的高度，浮层自动再留 15dp。 */
    public GlassSheet setBottomInset(int px) {
        sheetParams.bottomMargin = Math.max(0, px) + dp(15);
        return this;
    }

    public GlassSheet setPaneTitle(String title) {
        ViewCompat.setAccessibilityPaneTitle(sheet, title);
        return this;
    }

    public GlassSheet setCancelable(boolean cancelable) {
        dialog.setCancelable(cancelable);
        return this;
    }

    public GlassSheet setOnDismiss(Runnable task) {
        this.onDismiss = task;
        return this;
    }

    public GlassSheet setOnDismissListener(
            android.content.DialogInterface.OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    public void show() {
        closing = false;
        if (!dialog.isShowing()) {
            dialog.show();
        }
    }

    /** 立即关闭（行内操作沿用旧 sheet 的即时收起语义）。 */
    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    /** 下滑/回退等用户手势触发的动画收起。 */
    public void closeAnimated() {
        closeAnim.run();
    }

    private int dp(int value) {
        return Ui.dp(activity, value);
    }
}

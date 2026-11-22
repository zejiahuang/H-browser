package com.h.browser.ui;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

/**
 * 玻璃背景 Drawable：抓取窗口内的模糊快照，交给 GlassRenderer 做折射着色。
 * 快照捕获前把宿主隐藏一帧，避免把自身画进背景；失败时回退为半透明填充。
 * 首次 bounds 早于 attach 时捕获会落空，draw() 会节流重试。
 */
public final class GlassDrawable extends Drawable {

    private static final int MARGIN_DP = 54;

    private final Window window;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable captureTask = this::capture;
    private final Runnable restoreTask = this::restoreAlpha;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlassRenderer renderer = new GlassRenderer();
    private final GlassPreset preset;
    private final float density;
    private final boolean useShader;
    private final boolean snapshot;

    private float radiusDp;
    private float curve;
    private Path path;
    private Bitmap blurred;
    private final java.util.ArrayList<View> captureHides = new java.util.ArrayList<>();
    private boolean capturing;
    private boolean released;
    private int capturedOffsetX;
    private int capturedOffsetY;
    private int alpha = 255;
    private int dayAlpha;
    private int nightAlpha;
    private boolean tintOverride;
    private boolean drawShadow = true;
    private Bitmap shadowBitmap;
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Runnable onBuilt;
    private long lastAttempt;
    private int attempts;

    public GlassDrawable(Window window, boolean shaderMode, float radiusDp, float curvePct) {
        this.window = window;
        this.preset = Glass.preset();
        this.useShader = shaderMode && preset.shader;
        this.snapshot = shaderMode && preset.capture;
        this.radiusDp = radiusDp;
        this.curve = Math.max(0f, Math.min(1f, curvePct / 100f));
        this.density = window != null && window.getContext() != null
                ? window.getContext().getResources().getDisplayMetrics().density : 2f;
        this.dayAlpha = preset.tintDay;
        this.nightAlpha = preset.tintNight;
        paint.setFilterBitmap(true);
    }

    public GlassPreset preset() {
        return preset;
    }

    public void setRadius(float radiusDp, float curvePct) {
        this.radiusDp = radiusDp;
        this.curve = Math.max(0f, Math.min(1f, curvePct / 100f));
        path = null;
        shadowBitmap = null;
        invalidateSelf();
    }

    /** 覆盖玻璃奶白浓度（0-255），菜单等需要更实的表面可单独设置。 */
    public void setTintAlpha(int day, int night) {
        this.dayAlpha = Math.max(0, Math.min(255, day));
        this.nightAlpha = Math.max(0, Math.min(255, night));
        this.tintOverride = true;
        invalidateSelf();
    }

    /** 快照时一并隐藏的兄弟视图（如外阴影），避免阴影被拍进玻璃底色。 */
    public void addCaptureHide(View view) {
        if (view != null && !captureHides.contains(view)) {
            captureHides.add(view);
        }
    }

    /** 快照重建完成回调（主线程），外部阴影视图据此换成模糊背景填充。 */
    public void setOnBuilt(Runnable task) {
        this.onBuilt = task;
    }

    public Bitmap backdrop() {
        return blurred;
    }

    public int offsetX() {
        return capturedOffsetX;
    }

    public int offsetY() {
        return capturedOffsetY;
    }

    /** 外部已有独立阴影视图时关掉内建阴影，避免双层。 */
    public void setDrawShadow(boolean enabled) {
        this.drawShadow = enabled;
        if (!enabled && shadowBitmap != null) {
            shadowBitmap.recycle();
            shadowBitmap = null;
        }
        invalidateSelf();
    }

    public void refresh() {
        if (!snapshot || released) {
            return;
        }
        handler.removeCallbacks(captureTask);
        handler.postDelayed(captureTask, 80);
    }

    public void refreshAfterIdle() {
        if (!snapshot || released) {
            return;
        }
        handler.removeCallbacks(captureTask);
        handler.postDelayed(captureTask, 480);
    }

    public void release() {
        released = true;
        handler.removeCallbacks(captureTask);
        handler.removeCallbacks(restoreTask);
        restoreAlpha();
        renderer.release();
        if (blurred != null && !blurred.isRecycled()) {
            blurred.recycle();
        }
        blurred = null;
        if (shadowBitmap != null && !shadowBitmap.isRecycled()) {
            shadowBitmap.recycle();
        }
        shadowBitmap = null;
    }

    private View host() {
        Drawable.Callback cb = getCallback();
        return cb instanceof View ? (View) cb : null;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        path = null;
        if (!snapshot) {
            if (drawShadow) {
                rebuildShadow(bounds.width(), bounds.height());
            }
            return;
        }
        renderer.setShape(bounds.width(), bounds.height(), dp(radiusDp), curve);
        renderer.setOptics(dp(preset.edgeDp), dp(preset.shiftDp), preset.saturation, preset.veil);
        renderer.setFinish(preset.chromatic, preset.gloss);
        renderer.setTint(night() ? 27 : 255, night() ? 34 : 255, night() ? 35 : 255,
                night() ? nightAlpha : dayAlpha);
        renderer.setVeil(0f, 0f);
        if (drawShadow) {
            rebuildShadow(bounds.width(), bounds.height());
        }
        refresh();
    }

    /**
     * 预渲染一圈柔和外阴影：R93 的悬浮卡来自独立窗口，系统 elevation 阴影
     * 达不到那种扩散范围，这里用 MaskFilter 离屏生成，随尺寸缓存。
     */
    private void rebuildShadow(int w, int h) {
        if (shadowBitmap != null && !shadowBitmap.isRecycled()) {
            shadowBitmap.recycle();
        }
        shadowBitmap = null;
        if (w < 2 || h < 2) {
            return;
        }
        float spread = dp(preset.shadowSpreadDp);
        int bw = (int) (w + spread * 2);
        int bh = (int) (h + spread * 2 + dp(6));
        try {
            Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.BLACK);
            p.setAlpha(preset.shadowAlpha);
            p.setMaskFilter(new BlurMaskFilter(dp(preset.shadowSigmaDp), BlurMaskFilter.Blur.OUTER));
            c.translate(spread, spread + dp(6));
            c.drawPath(G2Path.of(w, h, dp(radiusDp), curve), p);
            shadowBitmap = bmp;
        } catch (Throwable ignored) {
            shadowBitmap = null;
        }
    }

    private float dp(float value) {
        return value * density;
    }

    private boolean night() {
        View host = host();
        if (host == null) {
            return false;
        }
        return Glass.night(host.getContext());
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        if (path == null) {
            path = G2Path.of(bounds.width(), bounds.height(), dp(radiusDp), curve);
            if (snapshot) {
                renderer.setShape(bounds.width(), bounds.height(), dp(radiusDp), curve);
            }
            if (drawShadow && shadowBitmap == null) {
                rebuildShadow(bounds.width(), bounds.height());
            }
        }
        if (drawShadow && shadowBitmap != null && !shadowBitmap.isRecycled()) {
            float spread = dp(preset.shadowSpreadDp);
            canvas.drawBitmap(shadowBitmap, -spread, -spread, shadowPaint);
        }
        if (preset.style == GlassPreset.SOLID) {
            paint.setShader(null);
            paint.setColor(night() ? preset.fillNight : preset.fillDay);
            canvas.drawPath(path, paint);
            drawRim(canvas, path);
            return;
        }
        if (snapshot) {
            if (useShader && renderer.draw(canvas, path)) {
                drawRim(canvas, path);
                return;
            }
            if (blurred != null && !blurred.isRecycled()) {
                int save = canvas.save();
                canvas.clipPath(path);
                canvas.drawBitmap(blurred, -capturedOffsetX, -capturedOffsetY, paint);
                tintPaint.setColor(night() ? (nightAlpha << 24) | 0x101718
                        : (dayAlpha << 24) | 0xFFFFFF);
                canvas.drawPaint(tintPaint);
                canvas.restoreToCount(save);
                drawRim(canvas, path);
                return;
            }
            paint.setShader(null);
            paint.setColor(night() ? 0xD91B2223 : 0xD9F2F7F7);
            canvas.drawPath(path, paint);
            drawRim(canvas, path);
            ensureCapture();
            return;
        }
        paint.setShader(null);
        paint.setColor(night() ? 0xF21B2223 : 0xF2F2F7F7);
        canvas.drawPath(path, paint);
    }

    private void drawRim(Canvas canvas, Path path) {
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(dp(1.2f));
        if (night()) {
            rimPaint.setColor(preset.style == GlassPreset.SOLID ? 0x1AFFFFFF : 0x30FFFFFF);
        } else {
            int alpha = preset.style == GlassPreset.LIQUID ? 0xB0
                    : preset.style == GlassPreset.SOFT ? 0xA0 : 0x80;
            rimPaint.setColor((alpha << 24) | 0xFFFFFF);
        }
        canvas.drawPath(path, rimPaint);
    }

    /** 首次捕获常发生在 attach 之前；之后由 draw() 节流补捕。 */
    private void ensureCapture() {
        if (!snapshot || capturing || released || blurred != null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastAttempt < 700) {
            return;
        }
        lastAttempt = now;
        handler.removeCallbacks(captureTask);
        handler.postDelayed(captureTask, 150);
    }

    private void capture() {
        if (released || !snapshot || capturing || Build.VERSION.SDK_INT < 26
                || window == null) {
            return;
        }
        attempts++;
        lastAttempt = SystemClock.uptimeMillis();
        View host = host();
        if (host == null || !host.isShown() || host.getAlpha() < 1f
                || host.getWidth() < 2 || host.getHeight() < 2) {
            retryLater();
            return;
        }
        int[] loc = new int[2];
        if (host.getRootView() == window.getDecorView()) {
            host.getLocationInWindow(loc);
        } else {
            // 宿主在对话框等另一个窗口里，换算到被捕获窗口的坐标系
            int[] hs = new int[2];
            int[] ds = new int[2];
            host.getLocationOnScreen(hs);
            window.getDecorView().getLocationOnScreen(ds);
            loc[0] = hs[0] - ds[0];
            loc[1] = hs[1] - ds[1];
        }
        int margin = Math.round(dp(MARGIN_DP));
        Rect src = new Rect(loc[0] - margin, loc[1] - margin,
                loc[0] + host.getWidth() + margin, loc[1] + host.getHeight() + margin);
        View root = host.getRootView();
        int rw = root.getWidth();
        int rh = root.getHeight();
        if (rw < 2 || rh < 2) {
            retryLater();
            return;
        }
        if (!src.intersect(0, 0, rw, rh)) {
            retryLater();
            return;
        }
        if (src.width() < 2 || src.height() < 2) {
            retryLater();
            return;
        }
        final int offX = loc[0] - src.left;
        final int offY = loc[1] - src.top;
        final Bitmap raw;
        try {
            raw = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            retryLater();
            return;
        }
        capturing = true;
        // 仅当宿主与被捕获窗口是同一个窗口时才需要处理自身像素；
        // 对话框里的玻璃抓取的是 Activity 窗口，没有自采样问题。
        boolean sameWindow = host.getRootView() == window.getDecorView();
        if (sameWindow) {
            // 不再把宿主透明化取样（那会让底栏每取一次样闪一下）；
            // 改成把宿主占据的区域用上方页面像素抹平（见 build），
            // 视觉上近似“透过玻璃看到页面”，且全程无隐藏帧。
            for (View hide : captureHides) {
                hide.setAlpha(0f);
            }
            handler.postDelayed(restoreTask, 500);
        }
        final int smearW = sameWindow ? host.getWidth() : 0;
        final int smearH = sameWindow ? host.getHeight() : 0;
        try {
            host.postDelayed(() -> {
                try {
                    PixelCopy.request(window, src, raw, result -> {
                        capturing = false;
                        restoreAlpha();
                        if (result == PixelCopy.SUCCESS && !released) {
                            build(raw, offX, offY, smearW, smearH);
                        } else {
                            raw.recycle();
                            retryLater();
                        }
                    }, handler);
                } catch (Throwable t) {
                    capturing = false;
                    restoreAlpha();
                    raw.recycle();
                    retryLater();
                }
            }, 32);
        } catch (Throwable t) {
            capturing = false;
            restoreAlpha();
            raw.recycle();
            retryLater();
        }
    }

    /**
     * 用宿主上方一行页面像素铺满宿主区域：宿主仍在画面上（不隐藏），
     * 直接取样会把玻璃自身拍进背景，这里把它抹成“页面延续”的样子。
     */
    private void smearHostArea(Bitmap raw, int offX, int offY, int w, int h) {
        if (w < 2 || h < 2 || raw.isRecycled()) {
            return;
        }
        int left = Math.max(0, offX);
        int top = Math.max(0, offY);
        int right = Math.min(raw.getWidth(), offX + w);
        int bottom = Math.min(raw.getHeight(), offY + h);
        if (right - left < 2 || bottom - top < 2) {
            return;
        }
        int srcY = top - 1;
        if (srcY < 0) {
            srcY = bottom;
        }
        if (srcY < 0 || srcY >= raw.getHeight()) {
            return;
        }
        try {
            int[] row = new int[right - left];
            raw.getPixels(row, 0, right - left, left, srcY, right - left, 1);
            for (int y = top; y < bottom; y++) {
                raw.setPixels(row, 0, right - left, left, y, right - left, 1);
            }
        } catch (Throwable ignored) {
        }
    }

    private void retryLater() {
        if (released || attempts >= 6 || blurred != null) {
            return;
        }
        handler.removeCallbacks(captureTask);
        handler.postDelayed(captureTask, 400);
    }

    private void restoreAlpha() {
        handler.removeCallbacks(restoreTask);
        View host = host();
        if (host != null && host.getAlpha() < 1f) {
            host.setAlpha(1f);
        }
        for (View hide : captureHides) {
            if (hide.getAlpha() < 1f) {
                hide.setAlpha(1f);
            }
        }
    }

    private void build(Bitmap raw, int offX, int offY, int smearW, int smearH) {
        smearHostArea(raw, offX, offY, smearW, smearH);
        int radius = Math.max(12, Glass.radius());
        int factor = preset.blurDivisor <= 0 ? 3 : Math.max(2, radius / preset.blurDivisor);
        Bitmap out = GlassRenderer.blur(raw, factor);
        raw.recycle();
        if (out == null || released) {
            if (out != null) {
                out.recycle();
            }
            return;
        }
        capturedOffsetX = offX;
        capturedOffsetY = offY;
        if (blurred != null && !blurred.isRecycled()) {
            blurred.recycle();
        }
        blurred = out;
        if (useShader) {
            renderer.setBackdrop(out, offX, offY);
        }
        if (onBuilt != null) {
            onBuilt.run();
        }
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

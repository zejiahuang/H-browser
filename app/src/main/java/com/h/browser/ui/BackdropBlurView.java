package com.h.browser.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.webkit.WebView;

/**
 * 悬浮工具栏背后的实时模糊：抓取窗口底部像素，降采样上采样模糊后绘制，
 * 顶部做短渐变过渡，避免出现硬边。API 26 以下退化为纯色薄纱。
 */
public final class BackdropBlurView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable captureTask = this::capture;
    private final int fallbackColor;

    private WebView target;
    private Bitmap snapshot;
    private boolean capturing;

    public BackdropBlurView(Context context) {
        super(context);
        setWillNotDraw(false);
        boolean night = (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        fallbackColor = night ? 0xB30E1415 : 0xB3F5FAFA;
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    public void attach(WebView web) {
        if (target == web) {
            if (snapshot == null) {
                schedule(80);
            }
            return;
        }
        target = web;
        clear();
        schedule(80);
    }

    public void detach() {
        target = null;
        clear();
    }

    public void onPageScroll() {
        schedule(180);
    }

    public void onPageFinished() {
        schedule(60);
    }

    public void onPageStarted() {
        clear();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != VISIBLE) {
            clear();
        }
    }

    public void clear() {
        handler.removeCallbacks(captureTask);
        if (snapshot != null && !snapshot.isRecycled()) {
            snapshot.recycle();
        }
        snapshot = null;
        invalidate();
    }

    private void schedule(long delay) {
        handler.removeCallbacks(captureTask);
        handler.postDelayed(captureTask, delay);
    }

    private void capture() {
        if (target == null || capturing || Build.VERSION.SDK_INT < 26
                || getWidth() <= 0 || getHeight() <= 0 || !isShown()
                || getVisibility() != VISIBLE || !(getContext() instanceof Activity)) {
            return;
        }
        int[] location = new int[2];
        getLocationInWindow(location);
        int w = getWidth();
        int h = getHeight();
        Rect src = new Rect(location[0], location[1], location[0] + w, location[1] + h);
        if (src.left < 0 || src.top < 0 || src.width() < 2 || src.height() < 2) {
            return;
        }
        final Bitmap raw;
        try {
            raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            return;
        }
        capturing = true;
        try {
            PixelCopy.request(((Activity) getContext()).getWindow(), src, raw, result -> {
                capturing = false;
                if (result == PixelCopy.SUCCESS && isAttachedToWindow()) {
                    build(raw);
                }
                raw.recycle();
            }, handler);
        } catch (Throwable t) {
            capturing = false;
            raw.recycle();
        }
    }

    private void build(Bitmap raw) {
        int w = getWidth();
        int h = getHeight();
        int factor = Math.max(4, Glass.radius() / 3);
        int sw = Math.max(2, w / factor);
        int sh = Math.max(2, h / factor);
        Bitmap small = null;
        Bitmap output = null;
        try {
            small = Bitmap.createScaledBitmap(raw, sw, sh, true);
            output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            canvas.drawBitmap(small, null, new Rect(0, 0, w, h), paint);
            maskPaint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0x00000000, 0xD9000000, 0xFF000000},
                    new float[]{0f, 0.3f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, maskPaint);
        } catch (Throwable t) {
            if (output != null) {
                output.recycle();
            }
            output = null;
        } finally {
            if (small != null && !small.isRecycled()) {
                small.recycle();
            }
        }
        if (snapshot != null && !snapshot.isRecycled()) {
            snapshot.recycle();
        }
        snapshot = output;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (snapshot != null && !snapshot.isRecycled()) {
            canvas.drawBitmap(snapshot, 0, 0, paint);
            return;
        }
        Paint fallback = new Paint();
        fallback.setShader(new LinearGradient(0, 0, 0, getHeight(),
                0x00000000, fallbackColor, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), fallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(captureTask);
        super.onDetachedFromWindow();
    }
}

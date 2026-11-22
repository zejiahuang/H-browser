package com.h.browser.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.view.View;

/**
 * 悬浮卡的外阴影：普通 View 的背景会被 RenderNode 裁到自身 bounds，
 * 达不到 R93 那种宽扩散光晕，因此用独立兄弟视图承载完整阴影位图。
 * 有玻璃快照时用「压暗的模糊背景」填充羽化遮罩——R93 的阴影在照片上是
 * 奶白漫射而不是纯黑，纯黑阴影会在彩色页上糊成一团。
 */
public final class GlassShadowView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint backdropPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final PorterDuffXfermode maskXfer =
            new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private final float density;
    private float radiusDp;
    private float curve;
    private int shadowAlpha = 96;
    private float spreadDp = 44f;
    private float sigmaDp = 14f;
    private Bitmap bitmap;
    private Bitmap backdrop;
    private float backdropDx;
    private float backdropDy;
    private float backdropDarken;
    private boolean useBackdrop;

    public GlassShadowView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
    }

    public void setShape(float radiusDp, float curvePct) {
        this.radiusDp = radiusDp;
        this.curve = Math.max(0f, Math.min(1f, curvePct / 100f));
        rebuild();
        invalidate();
    }

    public void setShadow(int alpha, float spreadDp, float sigmaDp) {
        this.shadowAlpha = Math.max(0, Math.min(255, alpha));
        this.spreadDp = Math.max(1f, spreadDp);
        this.sigmaDp = Math.max(1f, sigmaDp);
        rebuild();
        invalidate();
    }

    /** 用模糊背景（窗口快照）做阴影内容，darken 为压暗比例（0–1）。 */
    public void setBackdrop(Bitmap blurred, float dx, float dy, float darken) {
        this.backdrop = blurred;
        this.backdropDx = dx;
        this.backdropDy = dy;
        this.backdropDarken = Math.max(0f, Math.min(1f, darken));
        this.useBackdrop = blurred != null && !blurred.isRecycled();
        updateBackdropFilter();
        invalidate();
    }

    public void clearBackdrop() {
        this.backdrop = null;
        this.useBackdrop = false;
        invalidate();
    }

    private void updateBackdropFilter() {
        float s = 1f - backdropDarken;
        ColorMatrix cm = new ColorMatrix(new float[]{
                s, 0, 0, 0, 0,
                0, s, 0, 0, 0,
                0, 0, s, 0, 0,
                0, 0, 0, 1, 0});
        backdropPaint.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    public float spreadPx() {
        return spreadDp * density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    private void rebuild() {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        bitmap = null;
        int w = getWidth();
        int h = getHeight();
        if (w < 4 || h < 4) {
            return;
        }
        float spread = spreadDp * density;
        int cw = Math.max(2, w - (int) (spread * 2));
        int ch = Math.max(2, h - (int) (spread * 2 + 6 * density));
        try {
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.BLACK);
            p.setAlpha(255);
            p.setMaskFilter(new BlurMaskFilter(sigmaDp * density, BlurMaskFilter.Blur.OUTER));
            canvas.translate(spread, spread + 6 * density);
            canvas.drawPath(G2Path.of(cw, ch, radiusDp * density, curve), p);
            bitmap = bmp;
        } catch (Throwable ignored) {
            bitmap = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        if (useBackdrop && backdrop != null && !backdrop.isRecycled()) {
            int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            backdropPaint.setAlpha(255);
            canvas.drawBitmap(backdrop, backdropDx, backdropDy, backdropPaint);
            maskPaint.setXfermode(maskXfer);
            canvas.drawBitmap(bitmap, 0, 0, maskPaint);
            maskPaint.setXfermode(null);
            canvas.restoreToCount(save);
            return;
        }
        paint.setAlpha(shadowAlpha);
        canvas.drawBitmap(bitmap, 0, 0, paint);
    }
}

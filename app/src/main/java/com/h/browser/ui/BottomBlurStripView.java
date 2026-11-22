package com.h.browser.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.View;

/**
 * 底栏下方全宽的渐进模糊垫层：R93 在屏幕底部叠一条约 56dp 的
 * 模糊快照 + 由下而上渐强的雾面（日间白雾、夜间深雾），
 * 悬浮玻璃卡压在其上。模糊层由上向下淡入，避免生硬的边。
 */
public final class BottomBlurStripView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint veilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffXfermode dstIn = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private Bitmap backdrop;
    private float dx;
    private float dy;
    private boolean night;

    public BottomBlurStripView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        maskPaint.setXfermode(dstIn);
    }

    public void setBackdrop(Bitmap bmp, float dx, float dy, boolean night) {
        this.backdrop = bmp;
        this.dx = dx;
        this.dy = dy;
        this.night = night;
        invalidate();
    }

    public void clear() {
        backdrop = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (backdrop == null || backdrop.isRecycled() || getWidth() < 2 || getHeight() < 2) {
            return;
        }
        float h = getHeight();
        int save = canvas.saveLayer(0, 0, getWidth(), h, null);
        canvas.drawBitmap(backdrop, dx, dy, paint);
        maskPaint.setShader(new LinearGradient(0, 0, 0, h * 0.55f,
                0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), h, maskPaint);
        maskPaint.setShader(null);
        canvas.restoreToCount(save);
        int veil = night ? 0x3C3C3C : 0xFFFFFF;
        veilPaint.setShader(new LinearGradient(0, 0, 0, h,
                veil & 0x00FFFFFF, (night ? 0x4D : 0x9E) << 24 | (veil & 0x00FFFFFF),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), h, veilPaint);
        shadePaint.setShader(new LinearGradient(0, 0, 0, h,
                0x00000000, (night ? 0x0A : 0x14) << 24, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), h, shadePaint);
    }
}

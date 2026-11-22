package com.h.browser.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * 品牌字标：几何化的 H，氢青渐变。绘制区域 72x60dp，缩放由 setScale 控制。
 */
public final class HMarkView extends View {

    private static final int W = 72;
    private static final int H = 60;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float scale = 1f;

    public HMarkView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setContentDescription("H");
    }

    public void setScale(float value) {
        scale = value;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Math.round(W * scale * density), Math.round(H * scale * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.scale(scale, scale);
        float s = density;
        paint.setShader(new LinearGradient(10 * s, 6 * s, 62 * s, 54 * s,
                0xFF5BE7F0, 0xFF00A7B5, Shader.TileMode.CLAMP));
        float r = 3.2f * s;
        rect.set(10 * s, 6 * s, 28 * s, 54 * s);
        canvas.drawRoundRect(rect, r, r, paint);
        rect.set(44 * s, 6 * s, 62 * s, 54 * s);
        canvas.drawRoundRect(rect, r, r, paint);
        rect.set(27 * s, 26 * s, 45 * s, 38 * s);
        canvas.drawRoundRect(rect, r, r, paint);
        canvas.restore();
    }
}

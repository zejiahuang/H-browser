package com.h.browser.ui;

import android.graphics.Outline;
import android.graphics.Path;
import android.view.View;

/**
 * G2（超椭圆）圆角矩形路径：curve 0 为普通圆角，越大越接近方圆形。
 * 作为裁剪、阴影 outline 与玻璃边缘折射的共同形状来源。
 */
public final class G2Path {

    private static Path cached;
    private static float cachedW, cachedH, cachedR, cachedC;

    private G2Path() {
    }

    public static Path of(float w, float h, float radius, float curve) {
        if (cached != null && cachedW == w && cachedH == h
                && cachedR == radius && cachedC == curve) {
            return cached;
        }
        Path path = build(w, h, radius, curve);
        cached = path;
        cachedW = w;
        cachedH = h;
        cachedR = radius;
        cachedC = curve;
        return path;
    }

    public static void applyOutline(View view, float radius, float curve) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }
        final Path path = new Path(of(view.getWidth(), view.getHeight(), radius, curve));
        view.setClipToOutline(false);
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                try {
                    o.setConvexPath(path);
                } catch (Throwable t) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            }
        });
        view.invalidateOutline();
    }

    private static Path build(float w, float h, float radius, float curve) {
        Path path = new Path();
        if (w <= 0 || h <= 0) {
            return path;
        }
        float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2f));
        if (r < 1f) {
            path.addRect(0, 0, w, h, Path.Direction.CW);
            return path;
        }
        // 超椭圆指数：2 为标准圆弧，越大越方
        float p = 2f + 6f * Math.max(0f, Math.min(1f, curve));
        float exp = 2f / p;
        path.moveTo(r, 0);
        path.lineTo(w - r, 0);
        appendArc(path, w - r, r, r, exp, -90, 0);
        path.lineTo(w, h - r);
        appendArc(path, w - r, h - r, r, exp, 0, 90);
        path.lineTo(r, h);
        appendArc(path, r, h - r, r, exp, 90, 180);
        path.lineTo(0, r);
        appendArc(path, r, r, r, exp, 180, 270);
        path.close();
        return path;
    }

    private static void appendArc(Path path, float cx, float cy, float r, float exp,
                                  float fromDeg, float toDeg) {
        final int steps = 16;
        for (int i = 1; i <= steps; i++) {
            double angle = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / (double) steps);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            float x = cx + (float) (r * Math.signum(cos) * Math.pow(Math.abs(cos), exp));
            float y = cy + (float) (r * Math.signum(sin) * Math.pow(Math.abs(sin), exp));
            path.lineTo(x, y);
        }
    }
}

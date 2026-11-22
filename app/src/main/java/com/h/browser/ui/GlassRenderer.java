package com.h.browser.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

/**
 * 液态玻璃绘制核心。API 33+ 用 RuntimeShader 做边缘折射与着色，
 * 背景是模糊后的窗口快照；不可用时静态回退，不抛异常。
 */
public final class GlassRenderer {

    private static final String SHADER =
            "uniform float2 size;\n"
            + "uniform float radius;\n"
            + "uniform float4 optics;\n"
            + "uniform float4 tint;\n"
            + "uniform float4 gray;\n"
            + "uniform float2 extra;\n"
            + "uniform shader backdrop;\n"
            + "\n"
            + "const float3 W = float3(0.2126, 0.7152, 0.0722);\n"
            + "\n"
            + "half4 main(float2 coord) {\n"
            + "    float2 halfSize = size * 0.5;\n"
            + "    float2 p = coord - halfSize;\n"
            + "    float2 q = abs(p) - halfSize + radius;\n"
            + "    float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;\n"
            + "    float2 n;\n"
            + "    if (q.x > 0.0 && q.y > 0.0) {\n"
            + "        n = normalize(sign(p) * q);\n"
            + "    } else if (q.x > q.y) {\n"
            + "        n = float2(sign(p.x), 0.0);\n"
            + "    } else {\n"
            + "        n = float2(0.0, sign(p.y));\n"
            + "    }\n"
            + "    float inset = max(-sd, 0.0);\n"
            + "    float band = max(optics.x, 0.0001);\n"
            + "    float slope = 1.0 - clamp(inset / band, 0.0, 1.0);\n"
            + "    float2 shift = -n * optics.y * slope * slope * slope;\n"
            + "    float3 color = float3(0.0);\n"
            + "    if (extra.x > 0.001) {\n"
            + "        float ca = extra.x * 0.02;\n"
            + "        color.r = backdrop.eval(coord + shift * (1.0 + ca)).r;\n"
            + "        color.g = backdrop.eval(coord + shift).g;\n"
            + "        color.b = backdrop.eval(coord + shift * (1.0 - ca)).b;\n"
            + "    } else {\n"
            + "        color = backdrop.eval(coord + shift).rgb;\n"
            + "    }\n"
            + "    float luma = clamp(dot(color, W), 0.0, 1.0);\n"
            + "    color = max(mix(float3(luma), color, optics.z), 0.0);\n"
            + "    float veil = clamp(optics.w, 0.0, 1.0);\n"
            + "    float3 shaded = mix(color, float3(luma), 0.58 * veil) + veil * (1.32 - 1.34 * luma);\n"
            + "    float glow = pow(slope, 4.0) * max(extra.y, 0.0) * 0.20;\n"
            + "    shaded += glow;\n"
            + "    shaded = mix(shaded, float3(gray.x), clamp(gray.y, 0.0, 1.0));\n"
            + "    shaded = mix(shaded, tint.rgb, clamp(tint.a, 0.0, 1.0));\n"
            + "    return half4(clamp(shaded, 0.0, 1.0), 1.0);\n"
            + "}\n";

    private static Boolean supported;

    private RuntimeShader shader;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private boolean failed;
    private float width;
    private float height;
    private float radiusPx;
    private float curve;
    private float edgePx;
    private float shiftPx;
    private float saturation = 1.1f;
    private float veil = 0.06f;
    private float chromatic;
    private float gloss;
    private float grayLevel;
    private float grayAmount;
    private float[] tint = {1f, 1f, 1f, 0.10f};
    private Bitmap backdrop;

    public static boolean supported() {
        if (supported == null) {
            boolean ok = false;
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    new RuntimeShader(SHADER);
                    ok = true;
                } catch (Throwable ignored) {
                }
            }
            supported = ok;
        }
        return supported;
    }

    public void setShape(float width, float height, float radiusPx, float curve) {
        boolean changed = this.width != width || this.height != height
                || this.radiusPx != radiusPx || this.curve != curve;
        this.width = width;
        this.height = height;
        this.radiusPx = Math.max(0f, Math.min(radiusPx, Math.min(width, height) / 2f));
        this.curve = Math.max(0f, Math.min(1f, curve));
        if (changed) {
            applyUniforms();
        }
    }

    public void setOptics(float edgePx, float shiftPx, float saturation, float veil) {
        this.edgePx = edgePx;
        this.shiftPx = shiftPx;
        this.saturation = saturation;
        this.veil = veil;
        applyUniforms();
    }

    /** chromatic 为色散强度，gloss 为边缘高光强度（0 关闭）。 */
    public void setFinish(float chromatic, float gloss) {
        this.chromatic = chromatic;
        this.gloss = gloss;
        applyUniforms();
    }

    public void setVeil(float grayLevel, float grayAmount) {
        this.grayLevel = grayLevel;
        this.grayAmount = grayAmount;
        applyUniforms();
    }

    public void setTint(int r, int g, int b, int a) {
        tint = new float[]{r / 255f, g / 255f, b / 255f, a / 255f};
        applyUniforms();
    }

    /** 绑定模糊背景；offsetPx 为背景位图相对 host 左上角的偏移（通常等于外扩边距）。 */
    public boolean setBackdrop(Bitmap bitmap, float offsetX, float offsetY) {
        backdrop = bitmap;
        if (!supported() || failed || bitmap == null || bitmap.isRecycled()
                || width <= 0 || height <= 0) {
            return false;
        }
        try {
            if (shader == null) {
                shader = new RuntimeShader(SHADER);
            }
            BitmapShader bs = new BitmapShader(bitmap, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            matrix.setTranslate(offsetX, offsetY);
            bs.setLocalMatrix(matrix);
            shader.setInputShader("backdrop", bs);
            applyUniforms();
            return true;
        } catch (Throwable t) {
            failed = true;
            android.util.Log.w("GlassRenderer", "setBackdrop", t);
            return false;
        }
    }

    private void applyUniforms() {
        if (shader == null || failed) {
            return;
        }
        try {
            shader.setFloatUniform("size", width, height);
            shader.setFloatUniform("radius", radiusPx);
            shader.setFloatUniform("optics", Math.max(edgePx, 0.001f), shiftPx,
                    saturation, veil);
            shader.setFloatUniform("tint", tint[0], tint[1], tint[2], tint[3]);
            shader.setFloatUniform("gray", grayLevel, grayAmount);
            shader.setFloatUniform("extra", chromatic, gloss);
        } catch (Throwable t) {
            failed = true;
        }
    }

    public boolean canDraw() {
        return shader != null && !failed && backdrop != null && !backdrop.isRecycled()
                && width > 0 && height > 0;
    }

    public boolean draw(Canvas canvas, Path path) {
        if (!canDraw()) {
            return false;
        }
        try {
            paint.setShader(shader);
            canvas.drawPath(path, paint);
            paint.setShader(null);
            return true;
        } catch (Throwable t) {
            failed = true;
            android.util.Log.w("GlassRenderer", "shader draw", t);
            paint.setShader(null);
            return false;
        }
    }

    public void release() {
        paint.setShader(null);
        shader = null;
        backdrop = null;
    }

    /** 降采样再放大，得到低成本的高斯近似模糊。 */
    public static Bitmap blur(Bitmap src, int factor) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w < 2 || h < 2) {
            return null;
        }
        int f = Math.max(2, factor);
        int sw = Math.max(2, w / f);
        int sh = Math.max(2, h / f);
        Bitmap small = null;
        Bitmap out = null;
        try {
            small = Bitmap.createScaledBitmap(src, sw, sh, true);
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(small, null, new Rect(0, 0, w, h), p);
        } catch (Throwable t) {
            if (out != null) {
                out.recycle();
            }
            out = null;
        } finally {
            if (small != null && !small.isRecycled()) {
                small.recycle();
            }
        }
        return out;
    }
}

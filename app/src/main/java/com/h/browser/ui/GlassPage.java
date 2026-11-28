package com.h.browser.ui;

import android.app.Activity;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/**
 * 设置页/定制页的静态模糊背景：壁纸降采样到约 180px 后一次模糊，进程内缓存复用。
 * 页面内容不做 PixelCopy，避免每帧采样开销。
 */
public final class GlassPage {

    private static Bitmap blurred;
    private static int cachedWidth;
    private static int cachedHeight;

    private GlassPage() {
    }

    /** 返回进程级缓存的模糊壁纸；不可用时返回 null，调用方回退纯色。 */
    public static synchronized Bitmap blurred(Activity activity) {
        Drawable wallpaper = null;
        try {
            wallpaper = WallpaperManager.getInstance(activity).getDrawable();
        } catch (Throwable ignored) {
        }
        if (wallpaper == null) {
            return null;
        }
        int width = wallpaper.getIntrinsicWidth() > 0 ? wallpaper.getIntrinsicWidth() : 1080;
        int height = wallpaper.getIntrinsicHeight() > 0 ? wallpaper.getIntrinsicHeight() : 1920;
        if (blurred != null && !blurred.isRecycled()
                && cachedWidth == width && cachedHeight == height) {
            return blurred;
        }
        int scale = Math.max(1, width / 180);
        int sw = Math.max(2, width / scale);
        int sh = Math.max(2, height / scale);
        Bitmap small = null;
        try {
            small = Bitmap.createBitmap(sw, sh, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(small);
            wallpaper.setBounds(0, 0, sw, sh);
            wallpaper.draw(canvas);
        } catch (Throwable t) {
            if (small != null) {
                small.recycle();
            }
            return null;
        }
        Bitmap out = GlassRenderer.blur(small, 6);
        small.recycle();
        if (out == null) {
            return null;
        }
        if (blurred != null && !blurred.isRecycled()) {
            blurred.recycle();
        }
        blurred = out;
        cachedWidth = width;
        cachedHeight = height;
        return blurred;
    }
}

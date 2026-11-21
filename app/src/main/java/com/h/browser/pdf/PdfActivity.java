package com.h.browser.pdf;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.h.browser.R;
import com.h.browser.ui.Ui;

import java.io.File;

public class PdfActivity extends AppCompatActivity {

    private LinearLayout pages;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.topBar(this, "PDF 预览"));
        ScrollView scroll = new ScrollView(this);
        pages = Ui.column(this);
        pages.setPadding(0, 0, 0, Ui.dp(this, 16));
        scroll.addView(pages);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = Ui.text(this, "正在渲染…", 14, R.color.on_surface_variant);
        status.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        pages.addView(status);

        Uri uri;
        if (getIntent().getData() != null) {
            uri = getIntent().getData();
        } else if (getIntent().getStringExtra("url") != null) {
            uri = Uri.parse(getIntent().getStringExtra("url"));
        } else {
            toast("没有 PDF 地址");
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT < 21) {
            openExternally(uri);
            return;
        }
        new Thread(() -> Renderer21.render(this, uri), "pdf-render").start();
    }

    private void openExternally(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } catch (Throwable t) {
            toast("没有可打开 PDF 的应用");
            finish();
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static final class Renderer21 {

        static void render(final PdfActivity activity, final Uri uri) {
            ParcelFileDescriptor descriptor = null;
            PdfRenderer renderer = null;
            try {
                if ("file".equals(uri.getScheme())) {
                    descriptor = ParcelFileDescriptor.open(new File(uri.getPath()),
                            ParcelFileDescriptor.MODE_READ_ONLY);
                } else {
                    descriptor = activity.getContentResolver().openFileDescriptor(uri, "r");
                }
                if (descriptor == null) {
                    throw new IllegalStateException("无法打开文件");
                }
                renderer = new PdfRenderer(descriptor);
                final int count = Math.min(renderer.getPageCount(), 60);
                activity.runOnUiThread(() -> activity.status.setText("共 " + count + " 页"));
                int targetWidth = Math.min(activity.getResources().getDisplayMetrics().widthPixels,
                        Ui.dp(activity, 900));
                for (int i = 0; i < count; i++) {
                    PdfRenderer.Page page = renderer.openPage(i);
                    int width = targetWidth;
                    int height = Math.max(1, width * page.getHeight() / page.getWidth());
                    final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                    final int index = i;
                    activity.runOnUiThread(() -> {
                        ImageView image = new ImageView(activity);
                        image.setImageBitmap(bitmap);
                        image.setAdjustViewBounds(true);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        params.setMargins(Ui.dp(activity, 8), Ui.dp(activity, 8),
                                Ui.dp(activity, 8), 0);
                        activity.pages.addView(image, params);
                        activity.status.setText("第 " + (index + 1) + " / " + count + " 页");
                    });
                }
                activity.runOnUiThread(() -> activity.status.setVisibility(android.view.View.GONE));
            } catch (Throwable t) {
                final String message = t.getMessage();
                activity.runOnUiThread(() -> {
                    activity.toast("渲染失败：" + message);
                    activity.status.setText("无法渲染，尝试用系统应用打开");
                    activity.openExternally(uri);
                });
            } finally {
                if (renderer != null) {
                    renderer.close();
                }
                if (descriptor != null) {
                    try {
                        descriptor.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }
}

package com.h.browser.download;

import android.app.DownloadManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.h.browser.R;
import com.h.browser.pdf.PdfActivity;
import com.h.browser.ui.Ui;

public class DownloadActivity extends AppCompatActivity {

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.topBar(this, "下载管理"));
        ScrollView scroll = new ScrollView(this);
        content = Ui.column(this);
        content.setPadding(0, 0, 0, Ui.dp(this, 24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill();
    }

    private void fill() {
        content.removeAllViews();
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        long[] ids = Downloader.ids(this);
        if (manager == null || ids.length == 0) {
            empty();
            return;
        }
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(ids);
        Cursor cursor = null;
        try {
            cursor = manager.query(query);
            if (cursor == null || !cursor.moveToFirst()) {
                empty();
                return;
            }
            do {
                final long id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                long done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                String localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                String mime = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE));
                String summary = statusText(status);
                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED) {
                    if (total > 0) {
                        summary += " · " + (done * 100 / total) + "%";
                    } else {
                        summary += " · " + (done / 1024) + " KB";
                    }
                } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    summary += " · 点击打开";
                }
                LinearLayout item = Ui.item(this, title, summary, () -> {
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        open(localUri, mime);
                    } else {
                        Toast.makeText(this, "文件尚未下载完成", Toast.LENGTH_SHORT).show();
                    }
                });
                item.setOnLongClickListener(v -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("删除任务")
                            .setMessage(title)
                            .setPositiveButton("删除", (d, w) -> {
                                try {
                                    manager.remove(id);
                                } catch (Throwable ignored) {
                                }
                                Downloader.forget(this, id);
                                fill();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                });
                content.addView(item);
            } while (cursor.moveToNext());
        } catch (Throwable t) {
            empty();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void empty() {
        content.removeAllViews();
        TextView view = Ui.text(this, "还没有下载任务。长按网页中的链接或媒体即可下载。",
                14, R.color.on_surface_variant);
        view.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), 0);
        content.addView(view);
    }

    private void open(String localUri, String mime) {
        if (localUri == null) {
            return;
        }
        try {
            Uri uri = Uri.parse(localUri);
            if ("application/pdf".equals(mime)) {
                Intent pdf = new Intent(this, PdfActivity.class);
                pdf.setData(uri);
                startActivity(pdf);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime == null ? "*/*" : mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private String statusText(int status) {
        switch (status) {
            case DownloadManager.STATUS_PENDING:
                return "等待中";
            case DownloadManager.STATUS_RUNNING:
                return "下载中";
            case DownloadManager.STATUS_PAUSED:
                return "已暂停";
            case DownloadManager.STATUS_SUCCESSFUL:
                return "已完成";
            case DownloadManager.STATUS_FAILED:
                return "失败";
            default:
                return "未知";
        }
    }
}

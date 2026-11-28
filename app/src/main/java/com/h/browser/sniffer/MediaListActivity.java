package com.h.browser.sniffer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.h.browser.R;
import com.h.browser.core.BrowserActivity;
import com.h.browser.download.Downloader;
import com.h.browser.ui.Ui;

import java.util.List;

public class MediaListActivity extends AppCompatActivity {

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.topBar(this, "媒体嗅探"));
        ScrollView scroll = new ScrollView(this);
        content = Ui.column(this);
        content.setPadding(0, 0, 0, Ui.dp(this, 24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        fill();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill();
    }

    private void fill() {
        content.removeAllViews();
        List<String> urls = MediaSniffer.list();
        content.addView(Ui.item(this, "清空列表", urls.size() + " 个媒体地址", () -> {
            MediaSniffer.clear();
            fill();
        }));
        if (urls.isEmpty()) {
            TextView empty = Ui.text(this, "当前页面没有发现音视频。播放网页中的媒体后，地址会出现在这里。",
                    14, R.color.on_surface_variant);
            empty.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), 0);
            content.addView(empty);
            return;
        }
        for (int i = 0; i < urls.size(); i++) {
            final String url = urls.get(i);
            String name = url;
            int slash = url.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < url.length()) {
                name = url.substring(slash + 1);
                int question = name.indexOf('?');
                if (question > 0) {
                    name = name.substring(0, question);
                }
            }
            LinearLayout item = Ui.item(this, name, url, () -> play(url));
            final String label = name;
            item.setOnLongClickListener(v -> {
                String[] actions = {"下载", "在浏览器播放", "播放（系统）", "复制链接"};
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(label)
                        .setItems(actions, (dialog, which) -> {
                            if (which == 0) {
                                Downloader.enqueue(this, url, null, null, null);
                            } else if (which == 1) {
                                play(url);
                            } else if (which == 2) {
                                playExternal(url);
                            } else {
                                ClipboardManager manager = (ClipboardManager)
                                        getSystemService(Context.CLIPBOARD_SERVICE);
                                if (manager != null) {
                                    manager.setPrimaryClip(ClipData.newPlainText("media", url));
                                    Toast.makeText(this, "已复制 " + label,
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .show();
                return true;
            });
            content.addView(item);
        }
    }

    private void play(String url) {
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("url", url);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void playExternal(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(android.net.Uri.parse(url), guessMime(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "没有可播放此格式的应用", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "无法交给系统播放", Toast.LENGTH_SHORT).show();
        }
    }

    private static String guessMime(String url) {
        String path = url == null ? "" : url.toLowerCase(java.util.Locale.US);
        int question = path.indexOf('?');
        if (question >= 0) {
            path = path.substring(0, question);
        }
        if (path.endsWith(".m3u8")) return "application/x-mpegURL";
        if (path.endsWith(".mpd")) return "application/dash+xml";
        if (path.endsWith(".mp4") || path.endsWith(".m4v")) return "video/mp4";
        if (path.endsWith(".webm")) return "video/webm";
        if (path.endsWith(".mkv")) return "video/x-matroska";
        if (path.endsWith(".ts")) return "video/mp2t";
        if (path.endsWith(".mov")) return "video/quicktime";
        if (path.endsWith(".avi")) return "video/x-msvideo";
        if (path.endsWith(".flv")) return "video/x-flv";
        if (path.endsWith(".mp3")) return "audio/mpeg";
        if (path.endsWith(".m4a")) return "audio/mp4";
        if (path.endsWith(".aac")) return "audio/aac";
        if (path.endsWith(".ogg") || path.endsWith(".oga")) return "audio/ogg";
        if (path.endsWith(".opus")) return "audio/opus";
        if (path.endsWith(".flac")) return "audio/flac";
        if (path.endsWith(".wav")) return "audio/wav";
        return "video/*";
    }
}

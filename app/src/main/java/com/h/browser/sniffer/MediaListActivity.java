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
                String[] actions = {"下载", "在浏览器播放", "复制链接"};
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(label)
                        .setItems(actions, (dialog, which) -> {
                            if (which == 0) {
                                Downloader.enqueue(this, url, null, null, null);
                            } else if (which == 1) {
                                play(url);
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
}

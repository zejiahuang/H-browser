package com.h.browser.reader;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.h.browser.R;
import com.h.browser.core.BrowserActivity;
import com.h.browser.ui.Ui;

public class ReaderActivity extends AppCompatActivity {

    private WebView web;
    private String html;
    private String title;
    private int fontSize = 18;
    private boolean dark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        title = getIntent().getStringExtra("title");
        html = getIntent().getStringExtra("html");
        if (html == null) {
            html = "";
        }
        if (title == null || title.trim().isEmpty()) {
            title = "阅读模式";
        }

        LinearLayout root = Ui.screen(this);
        root.addView(Ui.topBar(this, title));

        LinearLayout controls = Ui.row(this);
        controls.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 6));
        controls.addView(control("A-", () -> {
            fontSize = Math.max(12, fontSize - 2);
            render();
        }));
        controls.addView(control("A+", () -> {
            fontSize = Math.min(32, fontSize + 2);
            render();
        }));
        controls.addView(control("切换背景", () -> {
            dark = !dark;
            render();
        }));
        root.addView(controls);

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        web.setBackgroundColor(Ui.color(this, R.color.surface));
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                openExternal(url);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                openExternal(request.getUrl().toString());
                return true;
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        render();
    }

    private void openExternal(String url) {
        try {
            Intent intent = new Intent(this, BrowserActivity.class);
            intent.putExtra("url", url);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private TextView control(String label, Runnable action) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        view.setBackground(Ui.ripple(this));
        view.setTextColor(Ui.color(this, R.color.primary));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void render() {
        String bg = dark ? "#0E1415" : "#F6FAFA";
        String fg = dark ? "#DEE4E4" : "#171D1E";
        String muted = dark ? "#BEC8C9" : "#3F4849";
        String link = dark ? "#4FD8E4" : "#006A71";
        String page = "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "body{margin:0;padding:22px 18px 64px;background:" + bg + ";color:" + fg + ";"
                + "font-family:system-ui,-apple-system,'Noto Sans SC',sans-serif;"
                + "font-size:" + fontSize + "px;line-height:1.75;max-width:720px;margin:0 auto;"
                + "word-wrap:break-word;overflow-wrap:anywhere}"
                + "h1{font-size:1.7em;line-height:1.3;margin:.2em 0 .8em}"
                + "h2,h3{line-height:1.35;margin:1.4em 0 .5em}"
                + "p{margin:0 0 1em}"
                + "img,video,figure{max-width:100%;height:auto}"
                + "a{color:" + link + "}"
                + "blockquote{border-left:3px solid " + link + ";margin:1em 0;padding:.2em 0 .2em 1em;color:" + muted + "}"
                + "pre,code{background:" + (dark ? "#1A2122" : "#EAF0F1") + ";border-radius:8px;"
                + "padding:2px 6px;font-size:.9em;overflow-x:auto}"
                + "table{width:100%;border-collapse:collapse}td,th{border:1px solid " + muted + ";padding:6px}"
                + "</style></head><body>" + html + "</body></html>";
        web.loadDataWithBaseURL(getIntent().getStringExtra("url"), page, "text/html", "utf-8", null);
    }
}

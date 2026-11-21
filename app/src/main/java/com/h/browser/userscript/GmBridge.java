package com.h.browser.userscript;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GmBridge {

    private final Context context;
    private final WebView webView;

    public GmBridge(Context context, WebView webView) {
        this.context = context.getApplicationContext();
        this.webView = webView;
    }

    @JavascriptInterface
    public void setValue(String id, String key, String value) {
        UserScripts.storage(context, id).edit().putString(key, value).apply();
    }

    @JavascriptInterface
    public String getValue(String id, String key) {
        return UserScripts.storage(context, id).getString(key, null);
    }

    @JavascriptInterface
    public void deleteValue(String id, String key) {
        UserScripts.storage(context, id).edit().remove(key).apply();
    }

    @JavascriptInterface
    public void addStyle(String id, final String css) {
        final String js = "(function(){var s=document.createElement('style');s.textContent="
                + JSONObject.quote(css) + ";(document.head||document.documentElement).appendChild(s);})();";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @JavascriptInterface
    public void xhr(final String rid, final String method, final String url, final String data) {
        new Thread(() -> {
            int status = 0;
            String text = "";
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setRequestMethod(method);
                if (data != null && !data.isEmpty()) {
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(data.getBytes(StandardCharsets.UTF_8));
                }
                status = conn.getResponseCode();
                InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (in != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                    in.close();
                    text = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            } catch (Throwable t) {
                text = "error: " + t.getMessage();
            }
            final int finalStatus = status;
            final String finalText = text;
            final String js = "(function(){var f=(window.__HGM_CB||{})[" + JSONObject.quote(rid) + "];if(f){f("
                    + finalStatus + "," + JSONObject.quote(finalText) + ");delete window.__HGM_CB["
                    + JSONObject.quote(rid) + "];}})();";
            webView.post(() -> webView.evaluateJavascript(js, null));
        }, "gm-xhr").start();
    }
}

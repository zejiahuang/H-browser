package com.h.browser.privacy;

import android.net.Uri;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.util.Collections;

public final class TrustedChannel {

    private static final String NAME = "HTrust";

    private TrustedChannel() {
    }

    public static void install(WebView web, final boolean incognito) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return;
        }
        try {
            WebViewCompat.addWebMessageListener(web, NAME, Collections.singleton("*"),
                    (view, message, sourceOrigin, isMainFrame, replyProxy) ->
                            handle(message.getData(), sourceOrigin, isMainFrame, incognito));
        } catch (Throwable ignored) {
        }
    }

    private static void handle(String data, Uri origin, boolean mainFrame, boolean incognito) {
        if (!mainFrame || data == null || data.length() > 512 || origin == null) {
            return;
        }
        String scheme = origin.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return;
        }
        try {
            JSONObject object = new JSONObject(data);
            String op = object.optString("op", "");
            if ("fp".equals(op) && !incognito) {
                String kind = object.optString("kind", "");
                if (kind.length() <= 16) {
                    PrivacyStats.recordFingerprint(kind);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}

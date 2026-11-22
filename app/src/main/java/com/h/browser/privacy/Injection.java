package com.h.browser.privacy;

import android.content.Context;
import android.webkit.WebView;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class Injection {

    private static final Map<WebView, ScriptHandler> HANDLERS = new WeakHashMap<>();

    private Injection() {
    }

    public static void install(WebView web, Context context) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return;
        }
        String script = script(context);
        try {
            synchronized (HANDLERS) {
                ScriptHandler old = HANDLERS.remove(web);
                if (old != null) {
                    old.remove();
                }
                if (script.isEmpty()) {
                    return;
                }
                HANDLERS.put(web, WebViewCompat.addDocumentStartJavaScript(
                        web, script, Collections.singleton("*")));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String script(Context context) {
        StringBuilder sb = new StringBuilder("(function(){");
        boolean any = false;
        if (PrivacyPrefs.dnt()) {
            String dnt = read(context, "privacy/dnt.js");
            if (!dnt.isEmpty()) {
                sb.append(dnt);
                any = true;
            }
        }
        if (PrivacyPrefs.blockWebRtc()) {
            String webrtc = read(context, "privacy/webrtc.js");
            if (!webrtc.isEmpty()) {
                sb.append(webrtc);
                any = true;
            }
        }
        if (PrivacyPrefs.fingerprintMaster()) {
            String fp = read(context, "privacy/fingerprint.js");
            if (!fp.isEmpty()) {
                sb.append("var __hp=").append(fingerprintConfig()).append(';');
                sb.append(fp);
                any = true;
            }
        }
        sb.append("})();");
        return any ? sb.toString() : "";
    }

    private static String fingerprintConfig() {
        org.json.JSONObject config = new org.json.JSONObject();
        try {
            org.json.JSONObject kinds = new org.json.JSONObject();
            kinds.put("canvas", PrivacyPrefs.fingerprint("canvas"));
            kinds.put("webgl", PrivacyPrefs.fingerprint("webgl"));
            kinds.put("audio", PrivacyPrefs.fingerprint("audio"));
            kinds.put("fonts", PrivacyPrefs.fingerprint("fonts"));
            config.put("kinds", kinds);
            config.put("exempt", new org.json.JSONArray(PrivacyPrefs.fingerprintExemptions()));
        } catch (Throwable ignored) {
        }
        return config.toString();
    }

    private static String read(Context context, String path) {
        try (InputStream in = context.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }
}

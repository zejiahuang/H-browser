package com.h.browser.net;

import android.os.Handler;
import android.os.Looper;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import com.h.browser.privacy.PrivacyPrefs;

import java.util.Locale;
import java.util.concurrent.Executor;

public final class WebViewProxy {

    private static final Executor DIRECT = Runnable::run;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile String applied;
    private static volatile String lastError;
    private static volatile String authHost = "";
    private static volatile boolean authEnabled;
    private static volatile String authUser = "";
    private static volatile String authPass = "";

    private WebViewProxy() {
    }

    public static String lastError() {
        return lastError;
    }

    public static String authUser() {
        return authUser;
    }

    public static String authPass() {
        return authPass;
    }

    public static boolean shouldAutoAuth(String challengeHost) {
        return authEnabled && !authHost.isEmpty() && challengeHost != null
                && challengeHost.equalsIgnoreCase(authHost);
    }

    public static void apply() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(WebViewProxy::apply);
            return;
        }
        boolean enabled = PrivacyPrefs.proxyEnabled();
        String host = normalize(PrivacyPrefs.proxyHost());
        int port = PrivacyPrefs.proxyPort();
        authHost = enabled ? host : "";
        authUser = PrivacyPrefs.proxyUser();
        authPass = PrivacyPrefs.proxyPass();
        authEnabled = enabled && !authUser.isEmpty();
        lastError = null;

        if (!enabled) {
            clear();
            return;
        }
        if (host.isEmpty() || port <= 0 || port > 65535) {
            lastError = "代理主机或端口无效，已回到直连";
            clear();
            return;
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            lastError = "当前 WebView 不支持代理，请更新 Android System WebView";
            return;
        }
        ProxyController controller;
        try {
            controller = ProxyController.getInstance();
        } catch (Throwable t) {
            lastError = "无法获取 WebView 代理控制器";
            return;
        }
        String hostPart = host.contains(":") ? "[" + host + "]" : host;
        String rule = "http://" + hostPart + ":" + port;
        if (rule.equals(applied)) {
            return;
        }
        try {
            ProxyConfig config = new ProxyConfig.Builder().addProxyRule(rule).build();
            controller.setProxyOverride(config, DIRECT, () -> {
            });
            applied = rule;
        } catch (Throwable t) {
            lastError = "代理规则无效：" + t.getMessage();
        }
    }

    private static void clear() {
        if (applied == null) {
            return;
        }
        try {
            ProxyController controller = ProxyController.getInstance();
            controller.clearProxyOverride(DIRECT, () -> {
            });
        } catch (Throwable ignored) {
        }
        applied = null;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.US);
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int at = value.lastIndexOf('@');
        if (at >= 0) {
            value = value.substring(at + 1);
        }
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end > 0 ? value.substring(1, end) : value;
        }
        if (value.indexOf(':') > 0 && value.indexOf(':') == value.lastIndexOf(':')) {
            value = value.substring(0, value.indexOf(':'));
        }
        return value.trim();
    }
}

package com.h.browser.privacy;

import android.net.Uri;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 导航期的隐私处理：清跟踪参数、HTTPS 升级、DNT 请求头。 */
public final class PrivacyNav {

    private static final Set<String> TRACKING = new HashSet<>(Arrays.asList(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "utm_id", "utm_name", "utm_reader", "utm_referrer", "utm_social",
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid",
            "mc_cid", "mc_eid", "igshid", "ttclid", "twclid", "spm", "scm",
            "vero_id", "oly_enc_id", "oly_anon_id", "rb_clickid", "s_cid"));

    private static final Set<String> SIGNED = new HashSet<>(Arrays.asList(
            "code", "state", "token", "access_token", "id_token", "ticket",
            "samlresponse", "signature", "sig", "expires", "st", "se", "sp"));

    private PrivacyNav() {
    }

    public static boolean stripAllowed(String host) {
        return host == null || !"false".equals(readSiteFlag("site_strip_", host));
    }

    public static String strip(String url) {
        if (url == null || !PrivacyPrefs.stripTracking()) {
            return url;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return url;
        }
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (!stripAllowed(host)) {
                return url;
            }
            String query = uri.getQuery();
            if (query == null || query.isEmpty()) {
                return url;
            }
            Set<String> names = new LinkedHashSet<>();
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                names.add(eq < 0 ? pair : pair.substring(0, eq));
            }
            boolean signed = false;
            for (String name : names) {
                if (SIGNED.contains(name.toLowerCase(java.util.Locale.US))) {
                    signed = true;
                    break;
                }
            }
            if (signed) {
                return url;
            }
            StringBuilder kept = new StringBuilder();
            boolean removed = false;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                String name = eq < 0 ? pair : pair.substring(0, eq);
                String lower = name.toLowerCase(java.util.Locale.US);
                if (TRACKING.contains(lower) || lower.startsWith("utm_")) {
                    removed = true;
                    continue;
                }
                if (kept.length() > 0) {
                    kept.append('&');
                }
                kept.append(pair);
            }
            if (!removed) {
                return url;
            }
            PrivacyStats.recordStripped();
            Uri.Builder builder = uri.buildUpon().clearQuery();
            if (kept.length() > 0) {
                builder.encodedQuery(kept.toString());
            }
            return builder.build().toString();
        } catch (Throwable t) {
            return url;
        }
    }

    public static boolean upgradeAllowed(String host) {
        return host != null && !"true".equals(readSiteFlag("site_http_", host));
    }

    public static String upgrade(String url) {
        if (url == null || !PrivacyPrefs.httpsOnly() || !url.startsWith("http://")) {
            return url;
        }
        try {
            String host = Uri.parse(url).getHost();
            if (!upgradeAllowed(host)) {
                return url;
            }
            return "https://" + url.substring("http://".length());
        } catch (Throwable t) {
            return url;
        }
    }

    public static Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        if (PrivacyPrefs.dnt()) {
            headers.put("DNT", "1");
            headers.put("Sec-GPC", "1");
        }
        return headers;
    }

    private static String readSiteFlag(String prefix, String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        try {
            return com.h.browser.data.Prefs.get().getString(prefix + host, null);
        } catch (Throwable t) {
            return null;
        }
    }
}

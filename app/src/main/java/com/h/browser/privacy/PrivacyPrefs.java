package com.h.browser.privacy;

import com.h.browser.data.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PrivacyPrefs {

    private PrivacyPrefs() {
    }

    public static boolean dnt() {
        return Prefs.get().getBoolean("dnt", true);
    }

    public static void setDnt(boolean value) {
        Prefs.get().edit().putBoolean("dnt", value).apply();
    }

    public static boolean blockWebRtc() {
        if (Prefs.get().contains("no_webrtc")) {
            return Prefs.get().getBoolean("no_webrtc", false);
        }
        return Prefs.get().getBoolean("privacy_webrtc_block", true);
    }

    public static void setBlockWebRtc(boolean value) {
        Prefs.get().edit()
                .putBoolean("privacy_webrtc_block", value)
                .putBoolean("no_webrtc", value)
                .apply();
    }

    public static boolean httpsOnly() {
        return Prefs.get().getBoolean("privacy_https_only", false);
    }

    public static void setHttpsOnly(boolean value) {
        Prefs.get().edit().putBoolean("privacy_https_only", value).apply();
    }

    public static boolean blockThirdPartyCookies() {
        return Prefs.get().getBoolean("privacy_third_party_cookies", false);
    }

    public static void setBlockThirdPartyCookies(boolean value) {
        Prefs.get().edit().putBoolean("privacy_third_party_cookies", value).apply();
    }

    public static boolean stripTracking() {
        return Prefs.get().getBoolean("privacy_strip_params", true);
    }

    public static void setStripTracking(boolean value) {
        Prefs.get().edit().putBoolean("privacy_strip_params", value).apply();
    }

    public static boolean fingerprintMaster() {
        return Prefs.get().getBoolean("privacy_fp", false);
    }

    public static boolean fingerprint(String kind) {
        if (!fingerprintMaster()) {
            return false;
        }
        return Prefs.get().getBoolean("privacy_fp_" + kind, true);
    }

    public static Set<String> fingerprintExemptions() {
        String raw = Prefs.get().getString("privacy_fp_exempt", "");
        Set<String> hosts = new HashSet<>();
        if (raw == null || raw.isEmpty()) {
            return hosts;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String host = array.optString(i, "").trim().toLowerCase(java.util.Locale.US);
                if (!host.isEmpty()) {
                    hosts.add(host);
                }
            }
        } catch (Throwable ignored) {
        }
        return hosts;
    }

    public static boolean exempt(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        Set<String> hosts = fingerprintExemptions();
        return hosts.contains(host.toLowerCase(java.util.Locale.US));
    }

    public static void setFingerprintExemptions(Set<String> hosts) {
        JSONArray array = new JSONArray();
        if (hosts != null) {
            for (String host : hosts) {
                array.put(host);
            }
        }
        Prefs.get().edit().putString("privacy_fp_exempt", array.toString()).apply();
    }

    public static String fingerprintFallback() {
        return Prefs.get().getString("privacy_fp_fallback", "allow");
    }

    public static void setFingerprintFallback(String mode) {
        String value = "block".equals(mode) ? "block" : "allow";
        Prefs.get().edit().putString("privacy_fp_fallback", value).apply();
    }

    public static String proxyHost() {
        return Prefs.get().getString("proxy_host", "");
    }

    public static int proxyPort() {
        return Prefs.get().getInt("proxy_port", 0);
    }

    public static String proxyUser() {
        return Prefs.get().getString("proxy_user", "");
    }

    public static String proxyPass() {
        return Prefs.get().getString("proxy_pass", "");
    }

    public static boolean proxyEnabled() {
        return Prefs.get().getBoolean("proxy_enabled", false) && !proxyHost().isEmpty()
                && proxyPort() > 0;
    }

    public static void setProxy(boolean enabled, String host, int port, String user, String pass) {
        Prefs.get().edit()
                .putBoolean("proxy_enabled", enabled)
                .putString("proxy_host", host == null ? "" : host)
                .putInt("proxy_port", Math.max(0, port))
                .putString("proxy_user", user == null ? "" : user)
                .putString("proxy_pass", pass == null ? "" : pass)
                .apply();
    }

    public static JSONObject snapshot() {
        JSONObject object = new JSONObject();
        try {
            object.put("dnt", dnt());
            object.put("webrtc", blockWebRtc());
            object.put("httpsOnly", httpsOnly());
            object.put("thirdPartyCookies", blockThirdPartyCookies());
            object.put("stripParams", stripTracking());
            object.put("fp", fingerprintMaster());
            object.put("fpCanvas", fingerprint("canvas"));
            object.put("fpWebgl", fingerprint("webgl"));
            object.put("fpAudio", fingerprint("audio"));
            object.put("fpFonts", fingerprint("fonts"));
            object.put("fpFallback", fingerprintFallback());
            object.put("fpExempt", new JSONArray(fingerprintExemptions()));
        } catch (Throwable ignored) {
        }
        return object;
    }

    public static Set<String> emptySet() {
        return Collections.emptySet();
    }
}

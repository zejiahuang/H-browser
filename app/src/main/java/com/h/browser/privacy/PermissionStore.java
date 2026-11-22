package com.h.browser.privacy;

import android.content.Context;

import com.h.browser.data.Prefs;

public final class PermissionStore {

    public static final int ASK = 0;
    public static final int ALLOW = 1;
    public static final int DENY = 2;

    private PermissionStore() {
    }

    private static String key(String type, String host) {
        return "site_perm_" + type + "_" + host;
    }

    public static int mode(Context context, String type, String host) {
        if (host == null || host.isEmpty()) {
            return ASK;
        }
        String value = Prefs.get().getString(key(type, host), "");
        if ("allow".equals(value)) {
            return ALLOW;
        }
        if ("deny".equals(value)) {
            return DENY;
        }
        return ASK;
    }

    public static void set(Context context, String type, String host, int mode) {
        if (host == null || host.isEmpty()) {
            return;
        }
        if (mode == ALLOW) {
            Prefs.get().edit().putString(key(type, host), "allow").apply();
        } else if (mode == DENY) {
            Prefs.get().edit().putString(key(type, host), "deny").apply();
        } else {
            Prefs.get().edit().remove(key(type, host)).apply();
        }
    }

    public static void clearHost(Context context, String host) {
        if (host == null || host.isEmpty()) {
            return;
        }
        android.content.SharedPreferences.Editor editor = Prefs.get().edit();
        for (String type : new String[]{"camera", "microphone", "geolocation"}) {
            editor.remove(key(type, host));
        }
        editor.apply();
    }
}

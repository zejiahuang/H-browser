package com.h.browser.privacy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class PrivacyStats {

    private static final String PREFS = "h_privacy_stats";
    private static final int MAX_EVENTS = 200;
    private static final long EVENT_TTL = 7L * 24 * 60 * 60 * 1000;

    private static SharedPreferences sp;

    private PrivacyStats() {
    }

    public static void init(Context context) {
        if (sp == null) {
            sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
        rollDay();
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private static synchronized void rollDay() {
        if (sp == null) {
            return;
        }
        String day = today();
        if (!day.equals(sp.getString("day", ""))) {
            sp.edit()
                    .putString("day", day)
                    .putInt("blocked_day", 0)
                    .putInt("fp_day", 0)
                    .putInt("stripped_day", 0)
                    .putInt("permissions_day", 0)
                    .apply();
        }
    }

    public static void recordBlocked() {
        synchronized (PrivacyStats.class) {
            rollDay();
            if (sp == null) {
                return;
            }
            sp.edit()
                    .putInt("blocked_day", sp.getInt("blocked_day", 0) + 1)
                    .putInt("blocked_total", sp.getInt("blocked_total", 0) + 1)
                    .apply();
        }
    }

    public static void recordStripped() {
        synchronized (PrivacyStats.class) {
            rollDay();
            if (sp == null) {
                return;
            }
            sp.edit()
                    .putInt("stripped_day", sp.getInt("stripped_day", 0) + 1)
                    .putInt("stripped_total", sp.getInt("stripped_total", 0) + 1)
                    .apply();
        }
    }

    public static void recordFingerprint(String kind) {
        synchronized (PrivacyStats.class) {
            rollDay();
            if (sp == null) {
                return;
            }
            sp.edit()
                    .putInt("fp_day", sp.getInt("fp_day", 0) + 1)
                    .putInt("fp_total", sp.getInt("fp_total", 0) + 1)
                    .apply();
        }
    }

    public static void recordPermission(String host, String type, String decision) {
        synchronized (PrivacyStats.class) {
            rollDay();
            if (sp == null || host == null || host.isEmpty()) {
                return;
            }
            sp.edit().putInt("permissions_day", sp.getInt("permissions_day", 0) + 1).apply();
            long now = System.currentTimeMillis();
            JSONArray events = events();
            try {
                events.put(new JSONObject()
                        .put("t", now)
                        .put("h", host)
                        .put("k", type == null ? "" : type)
                        .put("d", decision == null ? "" : decision));
            } catch (Throwable ignored) {
            }
            JSONArray kept = new JSONArray();
            int start = Math.max(0, events.length() - MAX_EVENTS);
            for (int i = start; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (event != null && event.optLong("t", 0) >= now - EVENT_TTL) {
                    kept.put(event);
                }
            }
            sp.edit().putString("perm_events", kept.toString()).apply();
        }
    }

    private static JSONArray events() {
        String raw = sp.getString("perm_events", "");
        if (raw == null || raw.isEmpty()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(raw);
        } catch (Throwable ignored) {
            return new JSONArray();
        }
    }

    public static int blockedToday() {
        rollDay();
        return sp == null ? 0 : sp.getInt("blocked_day", 0);
    }

    public static int blockedTotal() {
        return sp == null ? 0 : sp.getInt("blocked_total", 0);
    }

    public static int fingerprintsToday() {
        rollDay();
        return sp == null ? 0 : sp.getInt("fp_day", 0);
    }

    public static int fingerprintsTotal() {
        return sp == null ? 0 : sp.getInt("fp_total", 0);
    }

    public static int strippedToday() {
        rollDay();
        return sp == null ? 0 : sp.getInt("stripped_day", 0);
    }

    public static int strippedTotal() {
        return sp == null ? 0 : sp.getInt("stripped_total", 0);
    }

    public static int permissionsToday() {
        rollDay();
        return sp == null ? 0 : sp.getInt("permissions_day", 0);
    }

    public static JSONArray frequentPermissions(int threshold) {
        long since = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        JSONArray events = events();
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String[]> labels = new LinkedHashMap<>();
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null || event.optLong("t", 0) < since) {
                continue;
            }
            String host = event.optString("h", "");
            String kind = event.optString("k", "");
            if (host.isEmpty()) {
                continue;
            }
            String key = host + "|" + kind;
            int[] count = counts.get(key);
            if (count == null) {
                count = new int[1];
                counts.put(key, count);
                labels.put(key, new String[]{host, kind});
            }
            count[0]++;
        }
        JSONArray result = new JSONArray();
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            if (entry.getValue()[0] < threshold) {
                continue;
            }
            String[] label = labels.get(entry.getKey());
            try {
                result.put(new JSONObject()
                        .put("host", label[0])
                        .put("kind", label[1])
                        .put("count", entry.getValue()[0]));
            } catch (Throwable ignored) {
            }
            if (result.length() >= 20) {
                break;
            }
        }
        return result;
    }

    public static void reset() {
        if (sp == null) {
            return;
        }
        sp.edit().clear().apply();
        rollDay();
    }
}

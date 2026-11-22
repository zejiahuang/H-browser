package com.h.browser.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.webkit.CookieManager;

import com.h.browser.adblock.Subscriptions;
import com.h.browser.data.BackupCrypto;
import com.h.browser.data.Db;
import com.h.browser.data.Prefs;
import com.h.browser.userscript.UserScripts;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

public final class WebDav {

    public interface Callback {
        void done(boolean ok, String message);
    }

    private static final String[] SETTINGS = {
            "theme_mode", "web_dark", "address_style", "bar_autohide", "pull_refresh",
            "auto_sniff", "toolbar_items", "tts_speed",
            "home_logo", "home_logo_scale", "home_search", "home_search_hint", "home_bg_dim",
            "menu_order", "menu_hidden_ids",
            "adblock", "adblock_update_days",
            "dnt", "no_webrtc", "privacy_webrtc_block", "privacy_https_only",
            "privacy_third_party_cookies", "privacy_strip_params",
            "privacy_fp", "privacy_fp_canvas", "privacy_fp_webgl", "privacy_fp_audio",
            "privacy_fp_fonts", "privacy_fp_fallback", "privacy_fp_exempt"
    };

    private WebDav() {
    }

    private static String endpoint() {
        String url = Prefs.davUrl().trim();
        if (url.isEmpty()) {
            return null;
        }
        if (url.endsWith("/")) {
            url = url + "h-browser-backup.json";
        }
        return url;
    }

    private static void auth(HttpURLConnection connection) {
        String user = Prefs.davUser();
        String pass = Prefs.davPass();
        if (user == null || user.isEmpty()) {
            return;
        }
        String token = Base64.encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP);
        connection.setRequestProperty("Authorization", "Basic " + token);
    }

    public static void backup(final Context context, final Callback callback) {
        final String url = endpoint();
        if (url == null) {
            callback.done(false, "请先填写 WebDAV 地址");
            return;
        }
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject root = new JSONObject();
                root.put("version", 2);
                root.put("time", System.currentTimeMillis());
                root.put("homepage", Prefs.homepage());
                root.put("searchEngine", Prefs.searchEngine());
                Db db = Db.get(app);
                JSONArray bookmarks = new JSONArray();
                for (Db.Item item : db.bookmarks(10000)) {
                    bookmarks.put(new JSONObject().put("title", item.title).put("url", item.url)
                            .put("time", item.time));
                }
                root.put("bookmarks", bookmarks);
                JSONArray history = new JSONArray();
                for (Db.Item item : db.history(500)) {
                    history.put(new JSONObject().put("title", item.title).put("url", item.url)
                            .put("time", item.time));
                }
                root.put("history", history);

                root.put("settings", encodeSettings());
                root.put("scripts", encodeScripts(app));
                root.put("adblock", encodeAdblock(app));
                root.put("cookies", captureCookies(bookmarks, history));

                String json = root.toString();
                String pass = Prefs.davBackupPass();
                boolean encrypted = pass != null && !pass.isEmpty();
                byte[] body = (encrypted ? BackupCrypto.encrypt(json, pass) : json)
                        .getBytes(StandardCharsets.UTF_8);

                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(30000);
                connection.setRequestMethod("PUT");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        encrypted ? "text/plain; charset=utf-8" : "application/json; charset=utf-8");
                auth(connection);
                OutputStream out = connection.getOutputStream();
                out.write(body);
                out.close();
                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    callback.done(true, "备份完成（" + bookmarks.length() + " 个书签"
                            + (encrypted ? "，已加密" : "") + "）");
                } else {
                    callback.done(false, "备份失败：HTTP " + code);
                }
            } catch (Throwable t) {
                callback.done(false, "备份失败：" + describe(t));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "dav-backup").start();
    }

    public static void restore(final Context context, final Callback callback) {
        final String url = endpoint();
        if (url == null) {
            callback.done(false, "请先填写 WebDAV 地址");
            return;
        }
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(30000);
                connection.setRequestMethod("GET");
                auth(connection);
                int code = connection.getResponseCode();
                if (code >= 400) {
                    callback.done(false, "恢复失败：HTTP " + code);
                    return;
                }
                InputStream in = connection.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
                in.close();
                String text = new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
                if (BackupCrypto.isEncrypted(text)) {
                    String pass = Prefs.davBackupPass();
                    if (pass == null || pass.isEmpty()) {
                        callback.done(false, "该备份已加密，请填写备份口令后重试");
                        return;
                    }
                    try {
                        text = BackupCrypto.decrypt(text, pass);
                    } catch (Throwable t) {
                        callback.done(false, "解密失败：口令错误或文件已损坏");
                        return;
                    }
                }
                JSONObject root = new JSONObject(text);

                int bookmarks = restoreBookmarks(app, root.optJSONArray("bookmarks"));
                applySettings(root.optJSONObject("settings"));
                int scripts = restoreScripts(app, root.optJSONArray("scripts"));
                int subs = restoreAdblock(app, root.optJSONObject("adblock"));
                int cookies = restoreCookies(root.optJSONObject("cookies"));

                String homepage = root.optString("homepage", "");
                if (!homepage.isEmpty()) {
                    Prefs.setHomepage(homepage);
                }
                String engine = root.optString("searchEngine", "");
                if (!engine.isEmpty()) {
                    Prefs.setSearchEngine(engine);
                }
                Subscriptions.refresh(app);

                StringBuilder message = new StringBuilder("恢复完成（书签 ").append(bookmarks)
                        .append(" · 脚本 ").append(scripts)
                        .append(" · 订阅 ").append(subs)
                        .append(" · Cookie ").append(cookies).append("）");
                callback.done(true, message.toString());
            } catch (Throwable t) {
                callback.done(false, "恢复失败：" + describe(t));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "dav-restore").start();
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isEmpty() ? t.toString() : message;
    }

    private static JSONObject encodeSettings() {
        JSONObject out = new JSONObject();
        SharedPreferences sp = Prefs.get();
        for (String key : SETTINGS) {
            try {
                if (!sp.contains(key)) {
                    continue;
                }
                Object value = sp.getAll().get(key);
                JSONObject typed = new JSONObject();
                if (value instanceof Boolean) {
                    typed.put("t", "b").put("v", value);
                } else if (value instanceof Integer) {
                    typed.put("t", "i").put("v", value);
                } else if (value instanceof Long) {
                    typed.put("t", "l").put("v", value);
                } else if (value instanceof Float) {
                    typed.put("t", "f").put("v", ((Float) value).doubleValue());
                } else if (value instanceof java.util.Set) {
                    JSONArray array = new JSONArray();
                    for (Object item : (java.util.Set<?>) value) {
                        array.put(String.valueOf(item));
                    }
                    typed.put("t", "s").put("v", array);
                } else {
                    typed.put("t", "s").put("v", String.valueOf(value));
                }
                out.put(key, typed);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static void applySettings(JSONObject settings) {
        if (settings == null) {
            return;
        }
        SharedPreferences.Editor editor = Prefs.get().edit();
        Iterator<String> keys = settings.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!isWhitelisted(key)) {
                continue;
            }
            JSONObject typed = settings.optJSONObject(key);
            if (typed == null) {
                continue;
            }
            try {
                switch (typed.optString("t", "s")) {
                    case "b":
                        editor.putBoolean(key, typed.optBoolean("v"));
                        break;
                    case "i":
                        editor.putInt(key, typed.optInt("v"));
                        break;
                    case "l":
                        editor.putLong(key, typed.optLong("v"));
                        break;
                    case "f":
                        editor.putFloat(key, (float) typed.optDouble("v"));
                        break;
                    case "s":
                        if (typed.opt("v") instanceof JSONArray) {
                            JSONArray array = typed.optJSONArray("v");
                            java.util.Set<String> set = new java.util.HashSet<>();
                            for (int i = 0; i < array.length(); i++) {
                                set.add(array.optString(i));
                            }
                            editor.putStringSet(key, set);
                        } else {
                            editor.putString(key, typed.optString("v"));
                        }
                        break;
                }
            } catch (Throwable ignored) {
            }
        }
        editor.apply();
    }

    private static boolean isWhitelisted(String key) {
        for (String allowed : SETTINGS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static JSONArray encodeScripts(Context context) {
        JSONArray out = new JSONArray();
        try {
            for (UserScripts.Script script : UserScripts.list()) {
                if (script.source == null || script.source.length() > 512 * 1024) {
                    continue;
                }
                out.put(new JSONObject()
                        .put("name", script.name)
                        .put("source", script.source)
                        .put("enabled", UserScripts.isEnabled(script.id)));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static int restoreScripts(Context context, JSONArray scripts) {
        if (scripts == null) {
            return 0;
        }
        int added = 0;
        try {
            List<UserScripts.Script> existing = UserScripts.list();
            for (int i = 0; i < scripts.length(); i++) {
                JSONObject object = scripts.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String name = object.optString("name", "");
                String source = object.optString("source", "");
                if (source.isEmpty()) {
                    continue;
                }
                boolean present = false;
                for (UserScripts.Script script : existing) {
                    if (script.name.equals(name) && source.equals(script.source)) {
                        present = true;
                        break;
                    }
                }
                if (present) {
                    continue;
                }
                UserScripts.Script installed = UserScripts.install(context, source);
                if (installed != null) {
                    UserScripts.setEnabled(installed.id, object.optBoolean("enabled", true));
                    added++;
                }
            }
        } catch (Throwable ignored) {
        }
        return added;
    }

    private static JSONObject encodeAdblock(Context context) {
        JSONObject out = new JSONObject();
        try {
            JSONArray subs = new JSONArray();
            for (Subscriptions.Sub sub : Subscriptions.list(context)) {
                if (sub.builtin || sub.url == null || !sub.url.startsWith("http")) {
                    continue;
                }
                subs.put(new JSONObject().put("name", sub.name).put("url", sub.url)
                        .put("enabled", sub.enabled));
            }
            out.put("subs", subs);
            JSONArray rules = new JSONArray();
            for (String rule : Subscriptions.userRules(context)) {
                rules.put(rule);
            }
            out.put("userRules", rules);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static int restoreAdblock(Context context, JSONObject adblock) {
        if (adblock == null) {
            return 0;
        }
        int added = 0;
        try {
            JSONArray subs = adblock.optJSONArray("subs");
            if (subs != null) {
                List<Subscriptions.Sub> existing = Subscriptions.list(context);
                for (int i = 0; i < subs.length(); i++) {
                    JSONObject object = subs.optJSONObject(i);
                    if (object == null) {
                        continue;
                    }
                    String subUrl = object.optString("url", "");
                    boolean known = false;
                    for (Subscriptions.Sub item : existing) {
                        if (item.url != null && item.url.equals(subUrl)) {
                            known = true;
                            break;
                        }
                    }
                    Subscriptions.Sub sub = Subscriptions.addCustom(context,
                            object.optString("name"), subUrl);
                    if (sub != null) {
                        Subscriptions.setEnabled(context, sub.id, object.optBoolean("enabled", true));
                        if (!known) {
                            added++;
                        }
                    }
                }
            }
            JSONArray rules = adblock.optJSONArray("userRules");
            if (rules != null) {
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < rules.length(); i++) {
                    lines.add(rules.optString(i));
                }
                Subscriptions.mergeUserRules(context, lines);
            }
        } catch (Throwable ignored) {
        }
        return added;
    }

    private static JSONObject captureCookies(JSONArray bookmarks, JSONArray history) {
        JSONObject out = new JSONObject();
        try {
            LinkedHashSet<String> hosts = new LinkedHashSet<>();
            collectHosts(hosts, bookmarks);
            collectHosts(hosts, history);
            CookieManager manager = CookieManager.getInstance();
            int stored = 0;
            for (String host : hosts) {
                if (stored >= 200) {
                    break;
                }
                String value = manager.getCookie("https://" + host);
                if (value != null && !value.isEmpty() && value.length() <= 8192) {
                    out.put(host, value);
                    stored++;
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void collectHosts(LinkedHashSet<String> hosts, JSONArray items) {
        if (items == null) {
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            String url = items.optJSONObject(i) == null ? "" : items.optJSONObject(i).optString("url");
            try {
                String host = new URL(url).getHost();
                if (host != null && !host.isEmpty()) {
                    hosts.add(host);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static int restoreCookies(JSONObject cookies) {
        if (cookies == null) {
            return 0;
        }
        int count = 0;
        try {
            CookieManager manager = CookieManager.getInstance();
            Iterator<String> hosts = cookies.keys();
            while (hosts.hasNext()) {
                String host = hosts.next();
                String value = cookies.optString(host, "");
                if (value.isEmpty()) {
                    continue;
                }
                manager.setCookie("https://" + host, value);
                count++;
            }
            manager.flush();
        } catch (Throwable ignored) {
        }
        return count;
    }

    private static int restoreBookmarks(Context context, JSONArray bookmarks) {
        int count = 0;
        if (bookmarks == null) {
            return 0;
        }
        Db db = Db.get(context);
        for (int i = 0; i < bookmarks.length(); i++) {
            JSONObject object = bookmarks.optJSONObject(i);
            if (object == null) {
                continue;
            }
            String bookmarkUrl = object.optString("url", "");
            if (!bookmarkUrl.isEmpty()) {
                db.addBookmark(object.optString("title", bookmarkUrl), bookmarkUrl);
                count++;
            }
        }
        return count;
    }
}

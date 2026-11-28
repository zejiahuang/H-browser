package com.h.browser.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;

import com.h.browser.adblock.Subscriptions;
import com.h.browser.userscript.UserScripts;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 本地备份归档：与 WebDAV 云端共用的 v2 结构（书签/历史/设置/脚本/订阅/Cookie），
 * 可选 BackupCrypto 口令加密（HBK1: 前缀）。
 */
public final class BackupArchive {

    public static final int BOOKMARKS = 1;
    public static final int HISTORY = 1 << 1;
    public static final int SETTINGS = 1 << 2;
    public static final int SCRIPTS = 1 << 3;
    public static final int ADBLOCK = 1 << 4;
    public static final int COOKIES = 1 << 5;
    public static final int ALL = BOOKMARKS | HISTORY | SETTINGS | SCRIPTS | ADBLOCK | COOKIES;

    public static final String[] CATEGORY_KEYS = {
            "bookmarks", "history", "settings", "scripts", "adblock", "cookies"};
    public static final String[] CATEGORY_NAMES = {
            "书签", "历史记录", "设置", "用户脚本", "订阅与规则", "Cookie"};

    private static final String[] SETTINGS_KEYS = {
            "theme_mode", "web_dark", "address_style", "bar_autohide", "pull_refresh",
            "auto_sniff", "toolbar_items", "tts_speed",
            "home_logo", "home_logo_scale", "home_search", "home_search_hint", "home_bg_dim",
            "menu_order", "menu_hidden_ids",
            "adblock", "adblock_update_days",
            "dnt", "no_webrtc", "privacy_webrtc_block", "privacy_https_only",
            "privacy_third_party_cookies", "privacy_strip_params",
            "privacy_fp", "privacy_fp_canvas", "privacy_fp_webgl", "privacy_fp_audio",
            "privacy_fp_fonts", "privacy_fp_fallback", "privacy_fp_exempt",
            "screen_orientation", "glass_style", "glass_material", "progressive_blur",
            "settings_wallpaper", "clear_on_exit", "state_restore"
    };

    public static final class Summary {
        public int bookmarks;
        public int scripts;
        public int subs;
        public int cookies;

        public String message() {
            return "书签 " + bookmarks + " · 脚本 " + scripts
                    + " · 订阅 " + subs + " · Cookie " + cookies;
        }
    }

    private BackupArchive() {
    }

    public static boolean isEncrypted(String text) {
        return BackupCrypto.isEncrypted(text);
    }

    public static JSONObject encode(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 2);
        root.put("time", System.currentTimeMillis());
        root.put("homepage", Prefs.homepage());
        root.put("searchEngine", Prefs.searchEngine());
        Db db = Db.get(context);
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
        root.put("scripts", encodeScripts());
        root.put("adblock", encodeAdblock(context));
        root.put("cookies", captureCookies(bookmarks, history));
        return root;
    }

    /** 只保留勾选分类，再序列化。 */
    public static String encodeString(Context context, int categories) throws Exception {
        JSONObject root = encode(context);
        for (int i = 0; i < CATEGORY_KEYS.length; i++) {
            if ((categories & (1 << i)) == 0) {
                root.remove(CATEGORY_KEYS[i]);
            }
        }
        return root.toString();
    }

    /** 写入到输出流；password 非空时整体加密。 */
    public static void exportTo(Context context, OutputStream out, int categories,
                                String password) throws Exception {
        String json = encodeString(context, categories);
        String text = password == null || password.isEmpty()
                ? json : BackupCrypto.encrypt(json, password);
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** 从流恢复；口令错误抛 IllegalArgumentException，且不写入任何数据。 */
    public static Summary importFrom(Context context, InputStream in, String password)
            throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = in.read(data)) > 0) {
            buffer.write(data, 0, n);
            if (buffer.size() > 16 * 1024 * 1024) {
                throw new java.io.IOException("文件过大");
            }
        }
        String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8).trim();
        if (BackupCrypto.isEncrypted(text)) {
            if (password == null || password.isEmpty()) {
                throw new IllegalArgumentException("该备份已加密，请填写备份口令");
            }
            try {
                text = BackupCrypto.decrypt(text, password);
            } catch (Throwable t) {
                throw new IllegalArgumentException("口令错误或备份文件已损坏");
            }
        }
        return restore(context, new JSONObject(text));
    }

    public static Summary restore(Context context, JSONObject root) throws Exception {
        Summary summary = new Summary();
        summary.bookmarks = restoreBookmarks(context, root.optJSONArray("bookmarks"));
        applySettings(root.optJSONObject("settings"));
        summary.scripts = restoreScripts(context, root.optJSONArray("scripts"));
        summary.subs = restoreAdblock(context, root.optJSONObject("adblock"));
        summary.cookies = restoreCookies(root.optJSONObject("cookies"));
        String homepage = root.optString("homepage", "");
        if (!homepage.isEmpty()) {
            Prefs.setHomepage(homepage);
        }
        String engine = root.optString("searchEngine", "");
        if (!engine.isEmpty()) {
            Prefs.setSearchEngine(engine);
        }
        Subscriptions.refresh(context);
        return summary;
    }

    static JSONObject encodeSettings() {
        JSONObject out = new JSONObject();
        SharedPreferences sp = Prefs.get();
        for (String key : SETTINGS_KEYS) {
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

    static void applySettings(JSONObject settings) {
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

    static boolean isWhitelisted(String key) {
        for (String allowed : SETTINGS_KEYS) {
            if (allowed.equals(key)) {
                return true;
            }
        }
        return false;
    }

    static JSONArray encodeScripts() {
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

    static int restoreScripts(Context context, JSONArray scripts) {
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

    static JSONObject encodeAdblock(Context context) {
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

    static int restoreAdblock(Context context, JSONObject adblock) {
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

    static JSONObject captureCookies(JSONArray bookmarks, JSONArray history) {
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

    static int restoreCookies(JSONObject cookies) {
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

    static int restoreBookmarks(Context context, JSONArray bookmarks) {
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

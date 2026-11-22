package com.h.browser.adblock;

import android.content.Context;

import com.h.browser.data.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public final class Subscriptions {

    public static final String URL_CHINA =
            "https://easylist-downloads.adblockplus.org/easylistchina.txt";
    public static final String URL_PRIVACY =
            "https://easylist-downloads.adblockplus.org/easyprivacy.txt";
    public static final String URL_EASYLIST =
            "https://easylist-downloads.adblockplus.org/easylist.txt";
    public static final String URL_CHENGFENG =
            "https://raw.githubusercontent.com/xinggsf/Adblock-Plus-Rule/master/rule.txt";

    private static final String KEY = "adblock_subs";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_LINES = 500000;
    private static final int MAX_USER_RULES = 500;

    public static final String BUILTIN =
            "||doubleclick.net^\n||googlesyndication.com^\n||googleadservices.com^\n"
            + "||adservice.google.com^\n||ads.yahoo.com^\n||adnxs.com^\n||criteo.com^\n"
            + "||taboola.com^\n||outbrain.com^\n||scorecardresearch.com^\n||quantserve.com^\n"
            + "||adcolony.com^\n||applovin.com^\n||unityads.unity3d.com^\n||inmobi.com^\n"
            + "||moatads.com^\n||advertising.com^\n||adform.net^\n||rubiconproject.com^\n"
            + "||pubmatic.com^\n||openx.net^\n||casalemedia.com^\n||smartadserver.com^\n"
            + "||zedo.com^\n||media.net^\n||propellerads.com^\n||popads.net^\n"
            + "||umeng.com^\n||cnzz.com^\n||hm.baidu.com^\n||pos.baidu.com^\n"
            + "##.adsbygoogle\n##.ad-banner\n##.advertisement\n##.ad-container\n"
            + "##[id^=\"ad-\"]\n##[class^=\"ad-\"]\n";

    private static boolean updating;

    public static final class Sub {
        public String id;
        public String name;
        public String url;
        public boolean enabled;
        public boolean builtin;
        public String etag;
        public String lastModified;
        public long updated;
        public int rules;
        public String error;

        Sub(String id, String name, String url, boolean enabled, boolean builtin) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.enabled = enabled;
            this.builtin = builtin;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("url", url)
                    .put("enabled", enabled)
                    .put("builtin", builtin)
                    .put("etag", etag == null ? "" : etag)
                    .put("lastModified", lastModified == null ? "" : lastModified)
                    .put("updated", updated)
                    .put("rules", rules)
                    .put("error", error == null ? "" : error);
        }

        static Sub fromJson(JSONObject o) {
            Sub sub = new Sub(o.optString("id"), o.optString("name"), o.optString("url"),
                    o.optBoolean("enabled", true), o.optBoolean("builtin", false));
            sub.etag = o.optString("etag", "");
            sub.lastModified = o.optString("lastModified", "");
            sub.updated = o.optLong("updated", 0L);
            sub.rules = o.optInt("rules", 0);
            sub.error = o.optString("error", "");
            return sub;
        }
    }

    private Subscriptions() {
    }

    private static List<Sub> defaults() {
        List<Sub> list = new ArrayList<>();
        list.add(new Sub("easylist-china", "EasyList China", URL_CHINA, true, false));
        list.add(new Sub("easylist-privacy", "EasyPrivacy 反跟踪", URL_PRIVACY, true, false));
        list.add(new Sub("easylist", "EasyList", URL_EASYLIST, false, false));
        list.add(new Sub("chengfeng", "乘风规则", URL_CHENGFENG, false, false));
        return list;
    }

    public static synchronized List<Sub> list(Context context) {
        String raw = Prefs.get().getString(KEY, "");
        List<Sub> out = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.optJSONObject(i);
                    if (o != null) {
                        Sub sub = Sub.fromJson(o);
                        if (sub.id != null && !sub.id.isEmpty() && sub.url != null && !sub.url.isEmpty()) {
                            out.add(sub);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (out.isEmpty()) {
            out = defaults();
            String legacy = Prefs.adblockSubscription();
            if (legacy != null && !legacy.isEmpty() && !legacy.equals(URL_CHINA)) {
                out.get(0).url = legacy;
            }
            save(context, out);
        } else {
            boolean changed = false;
            for (Sub sub : out) {
                if ("easylist-privacy".equals(sub.id) && sub.url != null
                        && sub.url.contains("easylistprivacy.txt")) {
                    sub.url = URL_PRIVACY;
                    sub.error = "";
                    sub.etag = "";
                    sub.lastModified = "";
                    changed = true;
                }
            }
            if (changed) {
                save(context, out);
            }
        }
        return out;
    }

    public static synchronized void save(Context context, List<Sub> subs) {
        JSONArray array = new JSONArray();
        for (Sub sub : subs) {
            try {
                array.put(sub.toJson());
            } catch (Throwable ignored) {
            }
        }
        Prefs.get().edit().putString(KEY, array.toString()).apply();
    }

    public static synchronized Sub find(Context context, String id) {
        for (Sub sub : list(context)) {
            if (sub.id.equals(id)) {
                return sub;
            }
        }
        return null;
    }

    public static synchronized void setEnabled(Context context, String id, boolean enabled) {
        List<Sub> subs = list(context);
        for (Sub sub : subs) {
            if (sub.id.equals(id)) {
                sub.enabled = enabled;
            }
        }
        save(context, subs);
    }

    public static synchronized void remove(Context context, String id) {
        List<Sub> subs = list(context);
        for (int i = 0; i < subs.size(); i++) {
            if (subs.get(i).id.equals(id) && !subs.get(i).builtin) {
                subs.remove(i);
                break;
            }
        }
        save(context, subs);
        delete(subFile(context, id));
        refresh(context);
    }

    public static synchronized Sub addCustom(Context context, String name, String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            return null;
        }
        List<Sub> subs = list(context);
        for (Sub sub : subs) {
            if (sub.url.equals(trimmed)) {
                return sub;
            }
        }
        String id = "custom-" + Integer.toHexString(trimmed.hashCode());
        Sub sub = new Sub(id, name == null || name.trim().isEmpty() ? "自定义订阅" : name.trim(),
                trimmed, true, false);
        subs.add(sub);
        save(context, subs);
        return sub;
    }

    public static synchronized Sub importRules(Context context, String name, String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        String id = "import-" + Integer.toHexString(content.hashCode());
        writeAtomic(subFile(context, id), content);
        List<Sub> subs = list(context);
        Sub sub = null;
        for (Sub existing : subs) {
            if (existing.id.equals(id)) {
                sub = existing;
                break;
            }
        }
        if (sub == null) {
            sub = new Sub(id, name == null || name.trim().isEmpty() ? "导入规则" : name.trim(),
                    "file:" + id, true, false);
            subs.add(sub);
        }
        sub.enabled = true;
        sub.updated = System.currentTimeMillis();
        sub.rules = countRules(content);
        sub.error = "";
        save(context, subs);
        refresh(context);
        return sub;
    }

    public static File dir(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "adblock");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File subFile(Context context, String id) {
        return new File(dir(context), "sub_" + safeId(id) + ".txt");
    }

    public static File userFile(Context context) {
        return new File(dir(context), "user.txt");
    }

    private static String safeId(String id) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; id != null && i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? "custom" : sb.toString();
    }

    public static synchronized void refresh(Context context) {
        if (context == null) {
            return;
        }
        migrateLegacy(context);
        StringBuilder sb = new StringBuilder(BUILTIN);
        sb.append('\n');
        int enabledSubs = 0;
        for (Sub sub : list(context)) {
            if (!sub.enabled) {
                continue;
            }
            File file = subFile(context, sub.id);
            if (file.exists() && file.length() > 0) {
                String content = readText(file);
                if (!content.isEmpty()) {
                    sb.append(content);
                    if (!content.endsWith("\n")) sb.append('\n');
                    enabledSubs++;
                }
            }
        }
        String user = readUser(context);
        int userCount = 0;
        if (!user.isEmpty()) {
            sb.append(user);
            for (String line : user.split("\n")) {
                if (!line.trim().isEmpty()) userCount++;
            }
        }
        Rules rules = Rules.parse(sb.toString());
        writeAtomic(new File(dir(context), "merged.txt"), sb.toString());
        AdBlock.applySnapshot(rules, userCount, enabledSubs);
    }

    private static void migrateLegacy(Context context) {
        File legacy = new File(context.getApplicationContext().getFilesDir(), "adblock_rules.txt");
        if (!legacy.exists() || legacy.length() == 0) {
            return;
        }
        File target = subFile(context, "easylist-china");
        if (!target.exists() || target.length() == 0) {
            try {
                writeAtomic(target, readText(legacy));
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized String readUser(Context context) {
        File file = userFile(context);
        if (!file.exists()) {
            return "";
        }
        return readText(file);
    }

    public static synchronized List<String> userRules(Context context) {
        List<String> out = new ArrayList<>();
        String content = readUser(context);
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    public static synchronized boolean addUserRule(Context context, String host, String selector) {
        String domain = cleanHost(host);
        if (domain.isEmpty() || !Rules.safeSelector(selector)) {
            return false;
        }
        List<String> rules = userRules(context);
        String line = domain + "##" + selector.trim();
        if (rules.contains(line) || rules.size() >= MAX_USER_RULES) {
            return false;
        }
        rules.add(line);
        StringBuilder sb = new StringBuilder();
        for (String rule : rules) {
            sb.append(rule).append('\n');
        }
        writeAtomic(userFile(context), sb.toString());
        return true;
    }

    public static synchronized int mergeUserRules(Context context, List<String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return 0;
        }
        List<String> rules = userRules(context);
        int added = 0;
        for (String raw : incoming) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            int idx = line.indexOf("##");
            if (idx <= 0 || line.length() > 420 || line.contains("\r") || line.contains("\n")) {
                continue;
            }
            String domain = cleanHost(line.substring(0, idx));
            String selector = line.substring(idx + 2);
            if (domain.isEmpty() || !Rules.safeSelector(selector)) {
                continue;
            }
            String rule = domain + "##" + selector.trim();
            if (rules.contains(rule) || rules.size() >= MAX_USER_RULES) {
                continue;
            }
            rules.add(rule);
            added++;
        }
        if (added > 0) {
            StringBuilder sb = new StringBuilder();
            for (String rule : rules) {
                sb.append(rule).append('\n');
            }
            writeAtomic(userFile(context), sb.toString());
            refresh(context);
        }
        return added;
    }

    public static synchronized boolean removeUserRule(Context context, String line) {
        List<String> rules = userRules(context);
        if (!rules.remove(line)) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (String rule : rules) {
            sb.append(rule).append('\n');
        }
        writeAtomic(userFile(context), sb.toString());
        return true;
    }

    public static int userRuleCount(Context context) {
        return userRules(context).size();
    }

    private static String cleanHost(String host) {
        if (host == null) {
            return "";
        }
        String value = host.trim().toLowerCase(Locale.US);
        if (value.startsWith("www.")) {
            value = value.substring(4);
        }
        if (value.isEmpty() || value.length() > 253 || value.contains(":") || value.contains("/")
                || value.contains(" ") || !value.contains(".")) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-')) {
                return "";
            }
        }
        return value;
    }

    public static void updateAll(Context context, AdBlock.Callback callback) {
        final Context app = context.getApplicationContext();
        synchronized (Subscriptions.class) {
            if (updating) {
                if (callback != null) {
                    callback.onResult(false, "正在更新，请稍候");
                }
                return;
            }
            updating = true;
        }
        Prefs.get().edit().putLong("adblock_check", System.currentTimeMillis()).apply();
        new Thread(() -> {
            List<Sub> subs = list(app);
            int ok = 0;
            int total = 0;
            StringBuilder errors = new StringBuilder();
            try {
                for (Sub sub : subs) {
                    if (!sub.enabled) {
                        continue;
                    }
                    total++;
                    String error = download(app, sub);
                    if (error == null) {
                        ok++;
                    } else if (errors.length() == 0) {
                        errors.append(sub.name).append("：").append(error);
                    }
                }
                refresh(app);
            } finally {
                synchronized (Subscriptions.class) {
                    updating = false;
                }
            }
            if (callback != null) {
                if (total == 0) {
                    callback.onResult(false, "没有启用的订阅规则");
                } else if (ok == total) {
                    callback.onResult(true, "已更新 " + ok + " 个订阅，规则 "
                            + AdBlock.ruleCount() + " 条");
                } else {
                    callback.onResult(false, "更新 " + ok + "/" + total + " 个订阅，"
                            + (errors.length() == 0 ? "部分失败" : errors.toString()));
                }
            }
        }, "adblock-update").start();
    }

    public static void updateOne(Context context, String id, AdBlock.Callback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            Sub sub = find(app, id);
            if (sub == null) {
                if (callback != null) callback.onResult(false, "订阅不存在");
                return;
            }
            String error = download(app, sub);
            refresh(app);
            if (callback != null) {
                callback.onResult(error == null,
                        error == null ? "已更新 " + sub.name + "，规则 " + sub.rules + " 条"
                                : "更新失败：" + error);
            }
        }, "adblock-update-one").start();
    }

    private static String download(Context context, Sub sub) {
        if (sub.url.startsWith("file:")) {
            sub.updated = System.currentTimeMillis();
            sub.error = "";
            persist(context, sub);
            return null;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(sub.url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) H-browser");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            if (sub.etag != null && !sub.etag.isEmpty()) {
                conn.setRequestProperty("If-None-Match", sub.etag);
            }
            if (sub.lastModified != null && !sub.lastModified.isEmpty()) {
                conn.setRequestProperty("If-Modified-Since", sub.lastModified);
            }
            int code = conn.getResponseCode();
            if (code == 304) {
                sub.updated = System.currentTimeMillis();
                sub.error = "";
                persist(context, sub);
                Prefs.setAdblockUpdated(sub.updated);
                return null;
            }
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                in = new GZIPInputStream(in);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            int lines = 0;
            boolean first = true;
            while ((read = in.read(buffer)) > 0) {
                for (int i = 0; i < read; i++) {
                    if (buffer[i] == '\n') {
                        lines++;
                    }
                }
                if (out.size() + read > MAX_BYTES) {
                    in.close();
                    throw new IOException("规则文件超过 8MB 上限");
                }
                out.write(buffer, 0, read);
                if (lines > MAX_LINES) {
                    in.close();
                    throw new IOException("规则超过 50 万行上限");
                }
            }
            in.close();
            String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (first && content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
            if (content.trim().isEmpty()) {
                throw new IOException("规则内容为空");
            }
            File file = subFile(context, sub.id);
            writeAtomic(file, content);
            sub.etag = conn.getHeaderField("ETag");
            sub.lastModified = conn.getHeaderField("Last-Modified");
            sub.updated = System.currentTimeMillis();
            sub.rules = countRules(content);
            sub.error = "";
            persist(context, sub);
            Prefs.setAdblockUpdated(sub.updated);
            return null;
        } catch (Throwable t) {
            sub.error = t.getMessage() == null ? t.toString() : t.getMessage();
            persist(context, sub);
            return sub.error;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void persist(Context context, Sub updated) {
        List<Sub> subs = list(context);
        for (int i = 0; i < subs.size(); i++) {
            if (subs.get(i).id.equals(updated.id)) {
                subs.set(i, updated);
                break;
            }
        }
        save(context, subs);
    }

    private static int countRules(String content) {
        int count = 0;
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("[")) {
                count++;
            }
        }
        return count;
    }

    public static void maybeAutoUpdate(Context context) {
        final Context app = context.getApplicationContext();
        int days = Prefs.get().getInt("adblock_update_days", 3);
        if (days <= 0) {
            return;
        }
        long last = Prefs.get().getLong("adblock_check", 0L);
        if (last > 0 && System.currentTimeMillis() - last < days * 24L * 60 * 60 * 1000) {
            return;
        }
        synchronized (Subscriptions.class) {
            if (updating) {
                return;
            }
        }
        boolean any = false;
        for (Sub sub : list(app)) {
            if (sub.enabled) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        updateAll(app, null);
    }

    public static String summary(Context context) {
        List<Sub> subs = list(context);
        StringBuilder sb = new StringBuilder();
        sb.append("订阅规则\n");
        int enabled = 0;
        for (Sub sub : subs) {
            if (sub.enabled) enabled++;
            sb.append(sub.enabled ? "✓ " : "· ").append(sub.name);
            if (sub.updated > 0) {
                sb.append(" · ").append(sub.rules).append(" 条");
            }
            if (sub.error != null && !sub.error.isEmpty()) {
                sb.append(" · ").append(sub.error);
            }
            sb.append('\n');
        }
        sb.append("已启用 ").append(enabled).append(" 个订阅 · 我的规则 ")
                .append(userRuleCount(context)).append(" 条");
        return sb.toString();
    }

    static String readText(File file) {
        try {
            FileInputStream in = new FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            in.close();
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            return text;
        } catch (Throwable t) {
            return "";
        }
    }

    static void writeAtomic(File file, String content) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tmp = new File(file.getPath() + ".tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            out = null;
            if (!tmp.renameTo(file)) {
                delete(file);
                if (!tmp.renameTo(file)) {
                    throw new IOException("rename failed");
                }
            }
        } catch (Throwable t) {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
            delete(tmp);
        }
    }

    private static void delete(File file) {
        try {
            if (file != null && file.exists()) {
                file.delete();
            }
        } catch (Throwable ignored) {
        }
    }
}

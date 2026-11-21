package com.h.browser.theme;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.h.browser.adblock.AdBlock;
import com.h.browser.data.Prefs;
import com.h.browser.core.UrlUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Via 兼容的主页皮肤：一个 HTML + 一个 CSS，存放在 files/skins/<id>/。 */
public final class SkinStore {

    public static final String CLASSIC = "classic";
    private static final String[] BUILTINS = {"hydrogen", "ink", "paper"};
    private static final String ORIGIN = "https://appassets.androidplatform.net/skin/";
    private static final Pattern CSS_URL = Pattern.compile(
            "url\\(\\s*(['\"]?)([^)'\"]+)\\1\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTR = Pattern.compile(
            "(src|href|poster)\\s*=\\s*(['\"])([^'\"]+)\\2", Pattern.CASE_INSENSITIVE);

    public static final class Skin {
        public String id;
        public String name;
        public String author;
        public String version;
        public int accent;
        public boolean builtin;
        public File dir;
    }

    public static final class MarketEntry {
        public String id;
        public String name;
        public String author;
        public String preview;
        public JSONObject raw;
    }

    public interface Callback {
        void done(boolean ok, String message);
    }

    private static final List<MarketEntry> MARKET = new ArrayList<>();

    private SkinStore() {
    }

    public static synchronized void ensure(Context context) {
        File root = root(context);
        if (!root.exists() && !root.mkdirs()) {
            return;
        }
        for (String id : BUILTINS) {
            File dir = new File(root, id);
            if (new File(dir, "index.html").exists()) {
                continue;
            }
            dir.mkdirs();
            try {
                String[] files = context.getAssets().list("skins/" + id);
                if (files == null) {
                    continue;
                }
                for (String name : files) {
                    copyAsset(context, "skins/" + id + "/" + name, new File(dir, name));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static File root(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "skins");
    }

    public static synchronized List<Skin> list(Context context) {
        ensure(context);
        List<Skin> result = new ArrayList<>();
        Skin classic = new Skin();
        classic.id = CLASSIC;
        classic.name = "经典";
        classic.author = "氢";
        classic.version = "";
        classic.accent = 0xFF00B8C4;
        classic.builtin = true;
        result.add(classic);
        File[] dirs = root(context).listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                Skin skin = read(dir);
                if (skin != null) {
                    result.add(skin);
                }
            }
        }
        Collections.sort(result, new Comparator<Skin>() {
            @Override
            public int compare(Skin a, Skin b) {
                if (a.builtin != b.builtin) {
                    return a.builtin ? -1 : 1;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return result;
    }

    public static Skin read(File dir) {
        File manifest = new File(dir, "skin.json");
        if (!new File(dir, "index.html").exists()) {
            return null;
        }
        Skin skin = new Skin();
        skin.id = dir.getName();
        skin.name = skin.id;
        skin.author = "";
        skin.version = "";
        skin.accent = 0xFF00B8C4;
        skin.dir = dir;
        skin.builtin = Arrays.asList(BUILTINS).contains(skin.id);
        try {
            String text = readFile(manifest);
            if (!text.isEmpty()) {
                JSONObject json = new JSONObject(text);
                skin.name = json.optString("name", skin.name);
                skin.author = json.optString("author", "");
                skin.version = json.optString("version", "");
                String accent = json.optString("accent", "");
                if (accent.startsWith("#")) {
                    try {
                        skin.accent = Color.parseColor(accent);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return skin;
    }

    public static Skin active(Context context) {
        String id = Prefs.skinId();
        if (CLASSIC.equals(id)) {
            return null;
        }
        ensure(context);
        File dir = new File(root(context), id);
        if (!new File(dir, "index.html").exists()) {
            return null;
        }
        return read(dir);
    }

    public static String pageUrl(String id) {
        return ORIGIN + id + "/index.html";
    }

    public static String fileUrl(String id, String name) {
        return ORIGIN + id + "/" + name;
    }

    public static boolean isSkinUrl(String url) {
        return url != null && url.startsWith(ORIGIN);
    }

    /** 把 HTML 与 CSS 合成单页，并注入 window.open 路由，保持 Via 主题可直接运行。 */
    public static String inlineHtml(Context context) {
        Skin skin = active(context);
        if (skin == null) {
            return null;
        }
        String html = readFile(new File(skin.dir, "index.html"));
        String css = readFile(new File(skin.dir, "style.css"));
        if (html.isEmpty()) {
            return null;
        }
        String shim = "<meta name=viewport content='width=device-width,initial-scale=1,viewport-fit=cover'>"
                + "<script>window.open=function(u){try{HNative.newTab(u)}catch(e){};return null};"
                + "window.close=function(){};</script>";
        if (html.toLowerCase(Locale.US).indexOf("<title") < 0) {
            shim += "<title>" + UrlUtils.escapeHtml(skin.name) + "</title>";
        }
        StringBuilder head = new StringBuilder(shim);
        if (!css.isEmpty()) {
            head.append("<style>").append(css).append("</style>");
        }
        int index = html.toLowerCase(Locale.US).indexOf("</head>");
        if (index >= 0) {
            return html.substring(0, index) + head + html.substring(index);
        }
        return "<!doctype html><html><head>" + head + "</head><body>" + html + "</body></html>";
    }

    public static synchronized boolean delete(Context context, String id) {
        if (Arrays.asList(BUILTINS).contains(id) || CLASSIC.equals(id)) {
            return false;
        }
        File dir = new File(root(context), id);
        deleteTree(dir);
        if (Prefs.skinId().equals(id)) {
            Prefs.setSkinId("hydrogen");
        }
        return true;
    }

    // ---------- 安装 ----------

    public static void installFromUrl(final Context context, final String jsonUrl, final Callback cb) {
        new Thread(() -> {
            try {
                JSONObject entry = new JSONObject(fetchText(jsonUrl));
                installEntry(context, entry, jsonUrl, cb);
            } catch (Throwable t) {
                post(cb, false, "安装失败：" + shortError(t));
            }
        }, "skin-install").start();
    }

    public static void installEntry(final Context context, final JSONObject entry,
                                    final String base, final Callback cb) {
        new Thread(() -> {
            try {
                ensure(context);
                String name = localized(entry, "names");
                if (name.isEmpty()) {
                    name = entry.optString("name", "未命名主题");
                }
                String author = localized(entry, "authors");
                if (author.isEmpty()) {
                    author = entry.optString("author", "未知作者");
                }
                String id = slug(entry.optString("id", name));
                File dir = new File(root(context), id);
                int suffix = 2;
                while (dir.exists() && !Arrays.asList(BUILTINS).contains(id)) {
                    id = slug(entry.optString("id", name)) + "-" + suffix++;
                    dir = new File(root(context), id);
                }
                if (!dir.mkdirs() && !dir.exists()) {
                    throw new java.io.IOException("无法创建皮肤目录");
                }
                String htmlUrl = pick(entry.optJSONObject("htmlUrls"), entry.optString("html"));
                String cssUrl = pick(entry.optJSONObject("cssUrls"), entry.optString("css"));
                if (htmlUrl.isEmpty()) {
                    throw new java.io.IOException("缺少 HTML 地址");
                }
                htmlUrl = absolute(base, htmlUrl);
                cssUrl = absolute(base, cssUrl);
                String html = fetchText(htmlUrl);
                String css = cssUrl.isEmpty() ? "" : fetchText(cssUrl);
                html = rewriteHtml(html, htmlUrl);
                css = rewriteCss(css, cssUrl);
                writeFile(new File(dir, "index.html"), html);
                writeFile(new File(dir, "style.css"), css);
                String previewUrl = entry.optString("previewUrl", entry.optString("preview", ""));
                if (!previewUrl.isEmpty()) {
                    try {
                        byte[] bytes = fetchBytes(absolute(base, previewUrl));
                        writeFile(new File(dir, "preview.img"), bytes);
                    } catch (Throwable ignored) {
                    }
                }
                JSONObject manifest = new JSONObject();
                manifest.put("id", id);
                manifest.put("name", name);
                manifest.put("author", author);
                manifest.put("version", entry.optString("version", "1.0"));
                manifest.put("accent", entry.optString("accent", "#00B8C4"));
                writeFile(new File(dir, "skin.json"), manifest.toString(2));
                Prefs.setSkinId(id);
                post(cb, true, "已安装并应用：" + name);
            } catch (Throwable t) {
                post(cb, false, "安装失败：" + shortError(t));
            }
        }, "skin-install").start();
    }

    public static void installZip(final Context context, final Uri uri, final Callback cb) {
        new Thread(() -> {
            File dir = null;
            try {
                ensure(context);
                String label = displayName(context, uri).replaceAll("(?i)\\.zip$", "").trim();
                if (label.isEmpty()) {
                    label = "skin";
                }
                String id = slug(label);
                dir = new File(root(context), id);
                int suffix = 2;
                while (dir.exists()) {
                    id = slug(label) + "-" + suffix++;
                    dir = new File(root(context), id);
                }
                dir.mkdirs();
                InputStream raw = context.getContentResolver().openInputStream(uri);
                if (raw == null) {
                    throw new java.io.IOException("无法读取文件");
                }
                ZipInputStream zip = new ZipInputStream(raw);
                ZipEntry item;
                long total = 0;
                while ((item = zip.getNextEntry()) != null) {
                    String entryName = item.getName().replace('\\', '/');
                    if (item.isDirectory() || entryName.startsWith("/") || entryName.contains("..")
                            || entryName.startsWith("__MACOSX/")) {
                        continue;
                    }
                    byte[] data = readAll(zip, 6 * 1024 * 1024);
                    total += data.length;
                    if (total > 16 * 1024 * 1024) {
                        throw new java.io.IOException("主题包过大");
                    }
                    File out = new File(dir, entryName);
                    if (!out.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
                        continue;
                    }
                    File parent = out.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    writeFile(out, data);
                }
                zip.close();

                File html = findSkinFile(dir, ".html", 4);
                if (html == null) {
                    throw new java.io.IOException("压缩包内没有 HTML");
                }
                hoist(html.getParentFile(), dir);
                html = findSkinFile(dir, ".html", 1);
                File css = referencedCss(html, dir);
                if (css == null) {
                    css = findSkinFile(dir, ".css", 2);
                }
                File targetHtml = new File(dir, "index.html");
                if (!html.equals(targetHtml)) {
                    if (targetHtml.exists()) {
                        targetHtml.delete();
                    }
                    if (!html.renameTo(targetHtml)) {
                        writeFile(targetHtml, readFile(html));
                        html.delete();
                    }
                    html = targetHtml;
                }
                File targetCss = new File(dir, "style.css");
                if (css != null && !css.equals(targetCss)) {
                    if (targetCss.exists()) {
                        targetCss.delete();
                    }
                    if (!css.renameTo(targetCss)) {
                        writeFile(targetCss, readFile(css));
                        css.delete();
                    }
                }

                File manifestFile = new File(dir, "skin.json");
                String name = label;
                String author = "本地导入";
                String version = "1.0";
                String accent = "#00B8C4";
                if (manifestFile.exists()) {
                    JSONObject existing = new JSONObject(readFile(manifestFile));
                    name = existing.optString("name", name);
                    author = existing.optString("author", author);
                    version = existing.optString("version", version);
                    accent = existing.optString("accent", accent);
                }
                JSONObject manifest = new JSONObject();
                manifest.put("id", id);
                manifest.put("name", name);
                manifest.put("author", author);
                manifest.put("version", version);
                manifest.put("accent", accent);
                writeFile(manifestFile, manifest.toString(2));
                Prefs.setSkinId(id);
                post(cb, true, "已导入并应用：" + name);
            } catch (Throwable t) {
                if (dir != null) {
                    deleteTree(dir);
                }
                post(cb, false, "导入失败：" + shortError(t));
            }
        }, "skin-zip").start();
    }

    /** 压缩包常见的「一级子目录 + index.html」结构：把 HTML 所在目录的内容提到根。 */
    private static void hoist(File src, File dst) {
        if (src == null || !src.isDirectory() || src.equals(dst)) {
            return;
        }
        File[] files = src.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            File target = new File(dst, file.getName());
            if (file.isDirectory()) {
                if (target.isDirectory()) {
                    hoist(file, target);
                    deleteTree(file);
                } else {
                    if (target.exists()) {
                        target.delete();
                    }
                    if (!file.renameTo(target)) {
                        deleteTree(file);
                    }
                }
            } else if (!target.exists()) {
                file.renameTo(target);
            }
        }
        String[] rest = src.list();
        if (rest != null && rest.length == 0) {
            src.delete();
        }
    }

    private static File findSkinFile(File dir, String suffix, int depth) {
        if (dir == null || depth < 0) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        File fallback = null;
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(suffix)) {
                if (file.getName().equalsIgnoreCase("index.html")) {
                    return file;
                }
                if (fallback == null) {
                    fallback = file;
                }
            }
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File hit = findSkinFile(file, suffix, depth - 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return fallback;
    }

    private static File referencedCss(File html, File root) {
        try {
            Matcher matcher = HTML_ATTR.matcher(readFile(html));
            while (matcher.find()) {
                String value = matcher.group(3);
                if (!value.toLowerCase(Locale.US).endsWith(".css")) {
                    continue;
                }
                if (value.startsWith("http") || value.startsWith("//") || value.startsWith("/")) {
                    continue;
                }
                File resolved = new File(html.getParentFile(), value);
                if (resolved.isFile()) {
                    return resolved;
                }
                File rootLevel = new File(root, value);
                if (rootLevel.isFile()) {
                    return rootLevel;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String displayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? "skin" : last;
    }

    // ---------- 在线索引 ----------

    public static synchronized void fetchIndex(final Context context, final String url,
                                               final Callback cb) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(fetchText(url));
                JSONArray themes = json.optJSONArray("themes");
                List<MarketEntry> entries = new ArrayList<>();
                if (themes != null) {
                    for (int i = 0; i < themes.length() && i < 60; i++) {
                        JSONObject item = themes.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }
                        MarketEntry entry = new MarketEntry();
                        entry.raw = item;
                        entry.id = item.optString("id", String.valueOf(i));
                        entry.name = localized(item, "names");
                        if (entry.name.isEmpty()) {
                            entry.name = entry.id;
                        }
                        entry.author = localized(item, "authors");
                        entry.preview = absolute(url, item.optString("previewUrl",
                                item.optString("preview", "")));
                        String html = pick(item.optJSONObject("htmlUrls"), item.optString("html"));
                        if (html.isEmpty()) {
                            continue;
                        }
                        entry.preview = absolute(url, entry.preview);
                        entries.add(entry);
                    }
                }
                synchronized (SkinStore.class) {
                    MARKET.clear();
                    MARKET.addAll(entries);
                }
                Prefs.setSkinIndex(url);
                post(cb, true, "发现 " + entries.size() + " 个主题");
            } catch (Throwable t) {
                post(cb, false, "获取主题索引失败：" + shortError(t));
            }
        }, "skin-index").start();
    }

    public static synchronized List<MarketEntry> market() {
        return new ArrayList<>(MARKET);
    }

    public static void installMarket(final Context context, final int index, final Callback cb) {
        MarketEntry entry;
        synchronized (SkinStore.class) {
            if (index < 0 || index >= MARKET.size()) {
                post(cb, false, "主题不存在，请刷新列表");
                return;
            }
            entry = MARKET.get(index);
        }
        installEntry(context, entry.raw, Prefs.skinIndex(), cb);
    }

    // ---------- 取色 ----------

    public static int seedColor(Context context) {
        Skin skin = active(context);
        if (skin == null) {
            return Prefs.monetSeed();
        }
        File preview = new File(skin.dir, "preview.img");
        if (preview.exists()) {
            int color = dominant(preview);
            if (color != 0) {
                return color;
            }
        }
        return skin.accent;
    }

    private static int dominant(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 64 || bounds.outHeight / sample > 64) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) {
                return 0;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true);
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            float[] hsv = new float[3];
            int best = 0;
            double bestScore = 0;
            for (int x = 0; x < scaled.getWidth(); x += 2) {
                for (int y = 0; y < scaled.getHeight(); y += 2) {
                    int pixel = scaled.getPixel(x, y);
                    Color.colorToHSV(pixel, hsv);
                    if (hsv[2] < 0.15f || hsv[2] > 0.97f || hsv[1] < 0.12f) {
                        continue;
                    }
                    double score = hsv[1] * (1 - Math.abs(hsv[2] - 0.55));
                    if (score > bestScore) {
                        bestScore = score;
                        best = pixel;
                    }
                }
            }
            scaled.recycle();
            return best;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String statsJson(Context context) {
        try {
            JSONObject json = new JSONObject();
            JSONArray marks = new JSONArray();
            for (com.h.browser.data.Db.Item item : com.h.browser.data.Db.get(context).bookmarks(8)) {
                JSONObject o = new JSONObject();
                o.put("title", shortTitle(item.title, item.url));
                o.put("url", item.url);
                marks.put(o);
            }
            JSONArray recent = new JSONArray();
            for (com.h.browser.data.Db.Item item : com.h.browser.data.Db.get(context).history(6)) {
                JSONObject o = new JSONObject();
                o.put("title", shortTitle(item.title, item.url));
                o.put("url", item.url);
                recent.put(o);
            }
            json.put("bookmarks", marks);
            json.put("recent", recent);
            json.put("blocked", AdBlock.blockedCount());
            json.put("rules", AdBlock.ruleCount());
            return json.toString();
        } catch (Throwable t) {
            return "{}";
        }
    }

    // ---------- 工具 ----------

    private static String shortTitle(String title, String url) {
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        String host = UrlUtils.host(url);
        return host.isEmpty() ? url : host;
    }

    private static String localized(JSONObject parent, String key) {
        JSONObject map = parent.optJSONObject(key);
        if (map == null) {
            return "";
        }
        String[] order = {"zh-CN", "zh-cn", "zh-TW", "zh", "en"};
        for (String lang : order) {
            String value = map.optString(lang, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        JSONArray names = map.names();
        if (names != null && names.length() > 0) {
            return map.optString(names.optString(0), "");
        }
        return "";
    }

    private static String pick(JSONObject map, String fallback) {
        if (map != null) {
            String[] keys = {"sourse", "source", "mark.via", "mark.via.gp", "default"};
            for (String key : keys) {
                String value = map.optString(key, "");
                if (!value.isEmpty()) {
                    return value;
                }
            }
            JSONArray names = map.names();
            if (names != null && names.length() > 0) {
                return map.optString(names.optString(0), "");
            }
        }
        return fallback == null ? "" : fallback;
    }

    private static String slug(String value) {
        String slug = value == null ? "" : value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "skin-" + Long.toHexString(System.currentTimeMillis());
        }
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    private static String absolute(String base, String value) {
        if (value == null || value.isEmpty() || value.startsWith("http://")
                || value.startsWith("https://") || value.startsWith("data:")
                || value.startsWith("//")) {
            return value == null ? "" : value;
        }
        try {
            URL url = new URL(base);
            return new URL(url, value).toString();
        } catch (Throwable t) {
            return value;
        }
    }

    private static String rewriteCss(String css, String base) {
        Matcher matcher = CSS_URL.matcher(css);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(2);
            String replacement = matcher.group(0);
            if (!value.startsWith("data:") && !value.startsWith("#")) {
                replacement = "url(" + matcher.group(1) + absolute(base, value) + matcher.group(1) + ")";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String rewriteHtml(String html, String base) {
        Matcher matcher = HTML_ATTR.matcher(html);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(3);
            if (value.startsWith("http") || value.startsWith("data:") || value.startsWith("#")
                    || value.startsWith("javascript:") || value.startsWith("//")) {
                continue;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    matcher.group(1) + "=" + matcher.group(2) + absolute(base, value) + matcher.group(2)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String fetchText(String url) throws Exception {
        return new String(fetchBytes(url), StandardCharsets.UTF_8);
    }

    private static byte[] fetchBytes(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 H-browser");
        try {
            int code = connection.getResponseCode();
            if (code / 100 != 2) {
                throw new java.io.IOException("HTTP " + code);
            }
            return readAll(connection.getInputStream(), 12 * 1024 * 1024);
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
            if (out.size() > limit) {
                throw new java.io.IOException("内容过大");
            }
        }
        return out.toByteArray();
    }

    private static String readFile(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        try (InputStream in = new java.io.FileInputStream(file)) {
            return new String(readAll(in, 8 * 1024 * 1024), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private static void writeFile(File file, String text) throws Exception {
        writeFile(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFile(File file, byte[] data) throws Exception {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
    }

    private static void copyAsset(Context context, String name, File out) throws Exception {
        try (InputStream in = context.getAssets().open(name)) {
            writeFile(out, readAll(in, 2 * 1024 * 1024));
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
    }

    private static void post(Callback cb, boolean ok, String message) {
        if (cb != null) {
            cb.done(ok, message);
        }
    }

    private static String shortError(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isEmpty() ? t.getClass().getSimpleName() : message;
    }
}

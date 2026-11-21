package com.h.browser.sync;

import android.content.Context;
import android.util.Base64;

import com.h.browser.data.Db;
import com.h.browser.data.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class WebDav {

    public interface Callback {
        void done(boolean ok, String message);
    }

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
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject root = new JSONObject();
                root.put("version", 1);
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
                byte[] body = root.toString().getBytes(StandardCharsets.UTF_8);

                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(30000);
                connection.setRequestMethod("PUT");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                auth(connection);
                OutputStream out = connection.getOutputStream();
                out.write(body);
                out.close();
                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    callback.done(true, "备份完成（" + bookmarks.length() + " 个书签）");
                } else {
                    callback.done(false, "备份失败：HTTP " + code);
                }
            } catch (Throwable t) {
                callback.done(false, "备份失败：" + t.getMessage());
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
                JSONObject root = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
                JSONArray bookmarks = root.optJSONArray("bookmarks");
                int count = 0;
                if (bookmarks != null) {
                    Db db = Db.get(context);
                    for (int i = 0; i < bookmarks.length(); i++) {
                        JSONObject object = bookmarks.getJSONObject(i);
                        String bookmarkUrl = object.optString("url", "");
                        if (!bookmarkUrl.isEmpty()) {
                            db.addBookmark(object.optString("title", bookmarkUrl), bookmarkUrl);
                            count++;
                        }
                    }
                }
                String homepage = root.optString("homepage", "");
                if (!homepage.isEmpty()) {
                    Prefs.setHomepage(homepage);
                }
                String engine = root.optString("searchEngine", "");
                if (!engine.isEmpty()) {
                    Prefs.setSearchEngine(engine);
                }
                callback.done(true, "恢复完成（合并 " + count + " 个书签）");
            } catch (Throwable t) {
                callback.done(false, "恢复失败：" + t.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "dav-restore").start();
    }
}

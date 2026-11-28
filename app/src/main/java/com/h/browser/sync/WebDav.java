package com.h.browser.sync;

import android.content.Context;
import android.util.Base64;

import com.h.browser.data.BackupArchive;
import com.h.browser.data.BackupCrypto;
import com.h.browser.data.Prefs;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject root = BackupArchive.encode(app);
                int bookmarkCount = root.optJSONArray("bookmarks") == null
                        ? 0 : root.optJSONArray("bookmarks").length();

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
                    callback.done(true, "备份完成（" + bookmarkCount + " 个书签"
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
                BackupArchive.Summary summary = BackupArchive.restore(app, new JSONObject(text));
                callback.done(true, "恢复完成（" + summary.message() + "）");
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
}

package com.h.browser.download;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.h.browser.core.UrlUtils;

public final class Downloader {

    private static final String PREFS = "h_downloads";

    private Downloader() {
    }

    public static long enqueue(Context context, String url, String userAgent,
                               String contentDisposition, String mimeType) {
        try {
            String name = UrlUtils.guessFileName(url, contentDisposition);
            String mime = UrlUtils.mimeFor(name, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(name);
            request.setMimeType(mime);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (userAgent != null && !userAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                Toast.makeText(context, "下载服务不可用", Toast.LENGTH_SHORT).show();
                return -1;
            }
            long id = manager.enqueue(request);
            remember(context, id);
            Toast.makeText(context, "已加入下载：" + name, Toast.LENGTH_SHORT).show();
            return id;
        } catch (Throwable t) {
            Toast.makeText(context, "无法下载：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            return -1;
        }
    }

    static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void remember(Context context, long id) {
        SharedPreferences preferences = sp(context);
        java.util.Set<String> ids = new java.util.HashSet<>(
                preferences.getStringSet("ids", java.util.Collections.emptySet()));
        ids.add(String.valueOf(id));
        preferences.edit().putStringSet("ids", ids).apply();
    }

    static void forget(Context context, long id) {
        SharedPreferences preferences = sp(context);
        java.util.Set<String> ids = new java.util.HashSet<>(
                preferences.getStringSet("ids", java.util.Collections.emptySet()));
        ids.remove(String.valueOf(id));
        preferences.edit().putStringSet("ids", ids).apply();
    }

    static long[] ids(Context context) {
        java.util.Set<String> ids = sp(context).getStringSet("ids",
                java.util.Collections.emptySet());
        long[] result = new long[ids.size()];
        int i = 0;
        for (String value : ids) {
            try {
                result[i++] = Long.parseLong(value);
            } catch (Throwable ignored) {
                result[i++] = 0;
            }
        }
        return result;
    }
}

package com.h.browser.data;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Backup {

    private static final Pattern ANCHOR = Pattern.compile(
            "<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private Backup() {
    }

    public static void exportBookmarks(Context context, OutputStream out) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n");
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
        sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n");
        for (Db.Item item : Db.get(context).bookmarks(10000)) {
            sb.append("    <DT><A HREF=\"").append(escape(item.url)).append("\" ADD_DATE=\"")
                    .append(item.time / 1000).append("\">")
                    .append(escape(item.title)).append("</A>\n");
        }
        sb.append("</DL><p>\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public static int importBookmarks(Context context, InputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = in.read(data)) > 0) {
            buffer.write(data, 0, n);
            if (buffer.size() > 8 * 1024 * 1024) {
                throw new java.io.IOException("文件过大");
            }
        }
        String html = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        Matcher matcher = ANCHOR.matcher(html);
        int count = 0;
        while (matcher.find()) {
            String url = decode(matcher.group(1)).trim();
            if (url.isEmpty() || url.startsWith("javascript:") || url.startsWith("v://")) {
                continue;
            }
            String title = decode(matcher.group(2).replaceAll("<[^>]+>", "")).trim();
            Db.get(context).addBookmark(title.isEmpty() ? url : title, url);
            count++;
        }
        return count;
    }

    public static void exportHistory(Context context, OutputStream out) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=utf-8><title>氢 · 历史记录</title>")
                .append("<style>body{font-family:system-ui;margin:24px;color:#171D1E}")
                .append("a{color:#006A71;text-decoration:none}li{margin:10px 0}")
                .append("small{color:#6F797A}</style></head><body><h1>历史记录</h1><ul>");
        List<Db.Item> items = Db.get(context).history(3000);
        for (Db.Item item : items) {
            sb.append("<li><a href=\"").append(escape(item.url)).append("\">")
                    .append(escape(item.title)).append("</a><br><small>")
                    .append(escape(item.url)).append("</small></li>");
        }
        sb.append("</ul></body></html>");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String decode(String value) {
        return value.replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&#x27;", "'").replace("&nbsp;", " ");
    }
}

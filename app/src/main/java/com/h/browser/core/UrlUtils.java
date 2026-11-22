package com.h.browser.core;

import android.net.Uri;
import android.webkit.MimeTypeMap;

import com.h.browser.data.Prefs;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UrlUtils {

    private UrlUtils() {
    }

    public static boolean isInternal(String url) {
        return url != null && url.startsWith("v://");
    }

    public static boolean isHome(String url) {
        if (url == null) {
            return false;
        }
        return "v://home".equals(url) || url.startsWith("v://home/")
                || url.startsWith("v://home?") || url.startsWith("v://home#");
    }

    public static boolean looksLikeUrl(String input) {
        if (input == null) {
            return false;
        }
        String s = input.trim();
        if (s.isEmpty() || s.contains(" ")) {
            return false;
        }
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("v://")) {
            return true;
        }
        return s.contains(".") && !s.endsWith(".");
    }

    public static String toUrl(String input) {
        if (input == null) {
            return "v://home";
        }
        String s = input.trim();
        if (s.isEmpty()) {
            return Prefs.homepage();
        }
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("v://")) {
            return s;
        }
        if (looksLikeUrl(s)) {
            return "https://" + s;
        }
        try {
            return String.format(Locale.US, Prefs.searchEngine(), URLEncoder.encode(s, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return "https://www.baidu.com/s?wd=" + s;
        }
    }

    public static String host(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Throwable t) {
            return "";
        }
    }

    public static String origin(String url) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return url;
            }
            return scheme + "://" + host;
        } catch (Throwable t) {
            return url;
        }
    }

    public static String guessFileName(String url, String disposition) {
        String name = null;
        if (disposition != null) {
            int i = disposition.toLowerCase(Locale.US).indexOf("filename=");
            if (i >= 0) {
                name = disposition.substring(i + 9).trim();
                if (name.startsWith("\"") && name.endsWith("\"") && name.length() > 1) {
                    name = name.substring(1, name.length() - 1);
                }
                int semi = name.indexOf(';');
                if (semi > 0) {
                    name = name.substring(0, semi);
                }
            }
        }
        if (name == null || name.isEmpty()) {
            try {
                String path = Uri.parse(url).getLastPathSegment();
                if (path != null) {
                    name = path;
                }
            } catch (Throwable ignored) {
            }
        }
        if (name == null || name.isEmpty()) {
            name = "download_" + System.currentTimeMillis();
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!name.contains(".")) {
            name = name + ".bin";
        }
        return name;
    }

    public static String mimeFor(String fileName, String fallback) {
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            ext = fileName.substring(dot + 1).toLowerCase(Locale.US);
        }
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? fallback : mime;
    }

    public static boolean isMedia(String url) {
        String lower = url.toLowerCase(Locale.US);
        int q = lower.indexOf('?');
        if (q >= 0) {
            lower = lower.substring(0, q);
        }
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8")
                || lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".flv")
                || lower.endsWith(".mov") || lower.endsWith(".ogg");
    }

    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static Pattern wildcardToPattern(String wildcard) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else if (c == '?') {
                sb.append('.');
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }

    public static boolean matches(String pattern, String url) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        try {
            return wildcardToPattern(pattern).matcher(url).matches();
        } catch (Throwable t) {
            return false;
        }
    }
}

package com.h.browser.userscript;

import android.content.Context;
import android.webkit.WebView;

import com.h.browser.data.Prefs;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class CustomTheme {

    public static final int MAX_CHARS = 200000;

    private static File dir;
    private static String cssCache;
    private static String jsCache;
    private static long cssStamp;
    private static long jsStamp;

    private CustomTheme() {
    }

    public static void init(Context context) {
        dir = new File(context.getApplicationContext().getFilesDir(), "theme");
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public static boolean cssEnabled() {
        return Prefs.get().getBoolean("theme_css_enabled", true);
    }

    public static void setCssEnabled(boolean enabled) {
        Prefs.get().edit().putBoolean("theme_css_enabled", enabled).apply();
    }

    public static boolean jsEnabled() {
        return Prefs.get().getBoolean("theme_js_enabled", true);
    }

    public static void setJsEnabled(boolean enabled) {
        Prefs.get().edit().putBoolean("theme_js_enabled", enabled).apply();
    }

    public static String css() {
        synchronized (CustomTheme.class) {
            File file = cssFile();
            if (file == null) {
                return "";
            }
            long stamp = file.exists() ? file.lastModified() : 0;
            if (cssCache == null || stamp != cssStamp) {
                cssCache = read(file);
                cssStamp = stamp;
            }
            return cssCache;
        }
    }

    public static String js() {
        synchronized (CustomTheme.class) {
            File file = jsFile();
            if (file == null) {
                return "";
            }
            long stamp = file.exists() ? file.lastModified() : 0;
            if (jsCache == null || stamp != jsStamp) {
                jsCache = read(file);
                jsStamp = stamp;
            }
            return jsCache;
        }
    }

    public static boolean hasContent() {
        return !css().trim().isEmpty() || !js().trim().isEmpty();
    }

    public static void setCss(String source) {
        write(cssFile(), source);
        synchronized (CustomTheme.class) {
            cssCache = source;
            cssStamp = cssFile() == null ? 0 : cssFile().lastModified();
        }
    }

    public static void setJs(String source) {
        write(jsFile(), source);
        synchronized (CustomTheme.class) {
            jsCache = source;
            jsStamp = jsFile() == null ? 0 : jsFile().lastModified();
        }
    }

    public static void inject(WebView web, String url) {
        if (web == null || url == null) {
            return;
        }
        if (url.startsWith("v://") || url.startsWith("data:") || url.startsWith("about:")) {
            return;
        }
        String css = cssEnabled() ? css() : "";
        String js = jsEnabled() ? js() : "";
        boolean cssEmpty = css.trim().isEmpty();
        boolean jsEmpty = js.trim().isEmpty();
        if (cssEmpty && jsEmpty) {
            return;
        }
        StringBuilder script = new StringBuilder("(function(){");
        if (!cssEmpty) {
            script.append("try{var d=document,s=d.getElementById('h_theme_css');")
                    .append("if(!s){s=d.createElement('style');s.id='h_theme_css';")
                    .append("(d.head||d.documentElement).appendChild(s);}")
                    .append("s.textContent=").append(JSONObject.quote(css))
                    .append(";}catch(e){}");
        }
        if (!jsEmpty) {
            script.append("try{if(window.__h_theme_js_done)return;")
                    .append("var run=function(){window.__h_theme_js_done=1;try{\n")
                    .append(js)
                    .append("\n}catch(e){try{console.error('H theme JS',e);}catch(_){}}};")
                    .append("if(document.readyState==='loading'){")
                    .append("document.addEventListener('DOMContentLoaded',run,{once:true});}")
                    .append("else{run();}}catch(e){}");
        }
        script.append("})();");
        web.evaluateJavascript(script.toString(), null);
    }

    private static File cssFile() {
        return dir == null ? null : new File(dir, "custom.css");
    }

    private static File jsFile() {
        return dir == null ? null : new File(dir, "custom.js");
    }

    private static String read(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            FileInputStream in = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            in.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private static void write(File file, String source) {
        if (file == null) {
            return;
        }
        try {
            FileOutputStream out = new FileOutputStream(file);
            out.write(source.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Throwable ignored) {
        }
    }
}

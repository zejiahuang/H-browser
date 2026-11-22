package com.h.browser.userscript;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebView;

import com.h.browser.data.Prefs;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class UserScripts {

    public static final class Script {
        public String id;
        public String name;
        public String source;
        public List<String> matches = new ArrayList<>();
        public List<String> includes = new ArrayList<>();
    }

    private static File dir;

    private UserScripts() {
    }

    public static void init(Context context) {
        dir = new File(context.getApplicationContext().getFilesDir(), "userscripts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public static synchronized List<Script> list() {
        List<Script> scripts = new ArrayList<>();
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return scripts;
        }
        for (File file : files) {
            if (!file.getName().endsWith(".js")) {
                continue;
            }
            try {
                String source = read(file);
                Script script = parse(file.getName(), source);
                scripts.add(script);
            } catch (Throwable ignored) {
            }
        }
        Collections.sort(scripts, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return scripts;
    }

    public static synchronized Script install(Context context, String source) throws Exception {
        init(context);
        Script script = parse("pending.js", source);
        String id = "u" + System.currentTimeMillis();
        script.id = id;
        File file = new File(dir, id + ".js");
        FileOutputStream out = new FileOutputStream(file);
        out.write(source.getBytes(StandardCharsets.UTF_8));
        out.close();
        script.source = source;
        return script;
    }

    public static synchronized void delete(String id) {
        File file = new File(dir, id + ".js");
        if (file.exists()) {
            file.delete();
        }
    }

    private static Script parse(String fileName, String source) {
        Script script = new Script();
        script.id = fileName.substring(0, fileName.length() - 3);
        script.source = source;
        String name = script.id;
        boolean meta = false;
        String[] lines = source.split("\n", 120);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.contains("==UserScript==")) {
                meta = true;
                continue;
            }
            if (line.contains("==/UserScript==")) {
                meta = false;
                continue;
            }
            if (!meta) {
                continue;
            }
            if (line.startsWith("@name")) {
                name = valueOf(line);
            } else if (line.startsWith("@match")) {
                script.matches.add(valueOf(line));
            } else if (line.startsWith("@include")) {
                script.includes.add(valueOf(line));
            }
        }
        script.name = name;
        return script;
    }

    private static String valueOf(String line) {
        int space = line.indexOf(' ');
        return space > 0 ? line.substring(space + 1).trim() : line.trim();
    }

    public static boolean matches(Script script, String url) {
        if (script.matches.isEmpty() && script.includes.isEmpty()) {
            return true;
        }
        for (String pattern : script.matches) {
            if (pattern.startsWith("/") && pattern.endsWith("/") && pattern.length() > 2) {
                try {
                    if (url.matches(pattern.substring(1, pattern.length() - 1))) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            } else if (matchPattern(pattern, url)) {
                return true;
            }
        }
        for (String pattern : script.includes) {
            if (matchPattern(pattern, url)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchPattern(String pattern, String url) {
        if (pattern == null || pattern.isEmpty() || url == null) {
            return false;
        }
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append('$');
        try {
            return url.matches(regex.toString());
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isEnabled(String id) {
        return !Prefs.get().getStringSet("userscript_disabled",
                Collections.emptySet()).contains(id);
    }

    public static void setEnabled(String id, boolean enabled) {
        java.util.Set<String> set = new java.util.HashSet<>(
                Prefs.get().getStringSet("userscript_disabled", Collections.emptySet()));
        if (enabled) {
            set.remove(id);
        } else {
            set.add(id);
        }
        Prefs.get().edit().putStringSet("userscript_disabled", set).apply();
    }

    public static void inject(WebView webView, String url) {
        for (Script script : list()) {
            if (!matches(script, url) || !isEnabled(script.id)) {
                continue;
            }
            String bootstrap = "(function(){var sid=" + JSONObject.quote(script.id) + ";"
                    + "window.unsafeWindow=window;"
                    + "window.GM_setValue=function(k,v){HGM.setValue(sid,k,String(v));};"
                    + "window.GM_getValue=function(k,d){var v=HGM.getValue(sid,k);return v==null?d:v;};"
                    + "window.GM_deleteValue=function(k){HGM.deleteValue(sid,k);};"
                    + "window.GM_addStyle=function(css){HGM.addStyle(sid,css);};"
                    + "window.GM_xmlhttpRequest=function(o){var rid='r'+Date.now()+Math.random();"
                    + "window.__HGM_CB=window.__HGM_CB||{};"
                    + "window.__HGM_CB[rid]=function(status,text){try{o.onload&&o.onload({status:status,responseText:text});}catch(e){}};"
                    + "HGM.xhr(rid,o.method||'GET',o.url,o.data||'');};"
                    + "})();\n";
            String wrapped = "(function(){\n" + bootstrap + script.source + "\n})();";
            webView.evaluateJavascript(wrapped, null);
        }
    }

    private static String read(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileInputStream in = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static SharedPreferences storage(Context context, String id) {
        return context.getApplicationContext().getSharedPreferences("gm_" + id, Context.MODE_PRIVATE);
    }
}

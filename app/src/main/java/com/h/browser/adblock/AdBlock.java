package com.h.browser.adblock;

import android.content.Context;

import com.h.browser.core.UrlUtils;
import com.h.browser.data.Prefs;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

public final class AdBlock {

    private static final Set<String> HOSTS = new HashSet<>();
    private static final Set<String> EXCEPTIONS = new HashSet<>();
    private static final Map<String, List<String>> COSMETIC = new LinkedHashMap<>();
    private static final List<String> GENERIC = new ArrayList<>();
    private static final AtomicInteger BLOCKED = new AtomicInteger();
    private static final ConcurrentLinkedDeque<String> LOG = new ConcurrentLinkedDeque<>();
    private static volatile boolean loaded;
    private static File ruleFile;

    private static final String SEED =
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

    private AdBlock() {
    }

    public static void init(Context context) {
        if (ruleFile == null) {
            ruleFile = new File(context.getApplicationContext().getFilesDir(), "adblock_rules.txt");
        }
        if (!loaded) {
            loaded = true;
            new Thread(() -> {
                try {
                    String content;
                    if (ruleFile.exists() && ruleFile.length() > 0) {
                        content = readFile(ruleFile);
                    } else {
                        content = SEED;
                    }
                    parse(content);
                } catch (Throwable t) {
                    parse(SEED);
                }
            }, "adblock-load").start();
        }
    }

    private static String readFile(File file) throws Exception {
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

    public static synchronized void parse(String content) {
        Set<String> hosts = new HashSet<>();
        Set<String> exceptions = new HashSet<>();
        Map<String, List<String>> cosmetic = new LinkedHashMap<>();
        List<String> generic = new ArrayList<>();
        String[] lines = content.split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("#")) {
                continue;
            }
            try {
                parseLine(line, hosts, exceptions, cosmetic, generic);
            } catch (Throwable ignored) {
            }
        }
        synchronized (AdBlock.class) {
            HOSTS.clear();
            HOSTS.addAll(hosts);
            EXCEPTIONS.clear();
            EXCEPTIONS.addAll(exceptions);
            COSMETIC.clear();
            COSMETIC.putAll(cosmetic);
            GENERIC.clear();
            GENERIC.addAll(generic);
        }
    }

    private static void parseLine(String line, Set<String> hosts, Set<String> exceptions,
                                  Map<String, List<String>> cosmetic, List<String> generic) {
        if (line.startsWith("@@")) {
            String host = extractHost(line.substring(2));
            if (!host.isEmpty()) {
                exceptions.add(host);
            }
            return;
        }
        int cosmeticIndex = line.indexOf("##");
        if (cosmeticIndex > 0 || line.startsWith("##")) {
            String domains = cosmeticIndex > 0 ? line.substring(0, cosmeticIndex) : "";
            String selector = line.substring(cosmeticIndex + 2).trim();
            if (selector.isEmpty()) {
                return;
            }
            if (domains.isEmpty()) {
                generic.add(selector);
            } else {
                for (String domain : domains.split(",")) {
                    String d = domain.trim().toLowerCase(Locale.US);
                    if (d.startsWith("~") || d.isEmpty()) {
                        continue;
                    }
                    List<String> list = cosmetic.get(d);
                    if (list == null) {
                        list = new ArrayList<>();
                        cosmetic.put(d, list);
                    }
                    list.add(selector);
                }
            }
            return;
        }
        String host = extractHost(line);
        if (!host.isEmpty()) {
            hosts.add(host);
        }
    }

    private static String extractHost(String raw) {
        String s = raw.trim();
        if (s.startsWith("||")) {
            s = s.substring(2);
        }
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        int end = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '^' || c == '/' || c == '|' || c == '$' || c == '*') {
                end = i;
                break;
            }
        }
        s = s.substring(0, end).toLowerCase(Locale.US);
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        if (s.contains(":") || s.contains(" ") || !s.contains(".")) {
            return "";
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_')) {
                return "";
            }
        }
        return s;
    }

    public static boolean isEnabled() {
        return Prefs.adblockEnabled();
    }

    public static boolean shouldBlock(String url) {
        if (!isEnabled() || url == null) {
            return false;
        }
        String host = UrlUtils.host(url);
        if (host.isEmpty()) {
            return false;
        }
        synchronized (AdBlock.class) {
            for (String exception : EXCEPTIONS) {
                if (host.equals(exception) || host.endsWith("." + exception)) {
                    return false;
                }
            }
            for (String rule : HOSTS) {
                if (host.equals(rule) || host.endsWith("." + rule)) {
                    BLOCKED.incrementAndGet();
                    LOG.addFirst(host + "  " + url);
                    while (LOG.size() > 200) {
                        LOG.removeLast();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public static String cosmeticCss(String url) {
        if (!isEnabled()) {
            return "";
        }
        String host = UrlUtils.host(url);
        List<String> selectors = new ArrayList<>();
        synchronized (AdBlock.class) {
            selectors.addAll(GENERIC);
            for (Map.Entry<String, List<String>> entry : COSMETIC.entrySet()) {
                String domain = entry.getKey();
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    selectors.addAll(entry.getValue());
                }
            }
        }
        if (selectors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String selector : selectors) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(selector);
        }
        sb.append("{display:none!important}");
        return sb.toString();
    }

    public static String injectCssScript(String css) {
        if (css.isEmpty()) {
            return "";
        }
        return "(function(){var id='h-adblock-style';var s=document.getElementById(id);"
                + "if(!s){s=document.createElement('style');s.id=id;(document.head||document.documentElement).appendChild(s);}"
                + "s.textContent=" + JSONObject.quote(css) + ";})();";
    }

    public static int blockedCount() {
        return BLOCKED.get();
    }

    public static List<String> recentBlocks() {
        return new ArrayList<>(LOG);
    }

    public static void clearLog() {
        LOG.clear();
        BLOCKED.set(0);
    }

    public static int ruleCount() {
        synchronized (AdBlock.class) {
            return HOSTS.size();
        }
    }

    public interface Callback {
        void onResult(boolean ok, String message);
    }

    public static void updateSubscription(Context context, String url, Callback callback) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(40000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) H-browser");
                conn.setRequestProperty("Accept-Encoding", "gzip");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    callback.onResult(false, "HTTP " + code);
                    return;
                }
                InputStream in = conn.getInputStream();
                if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                    in = new GZIPInputStream(in);
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 400000) {
                    sb.append(line).append('\n');
                    count++;
                }
                reader.close();
                String content = sb.toString();
                parse(content);
                FileOutputStream out = new FileOutputStream(ruleFile);
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.close();
                Prefs.setAdblockUpdated(System.currentTimeMillis());
                callback.onResult(true, "已更新，规则 " + ruleCount() + " 条");
            } catch (Throwable t) {
                callback.onResult(false, "更新失败：" + t.getMessage());
            }
        }, "adblock-update").start();
    }

    public static List<String> hostRulesSnapshot() {
        synchronized (AdBlock.class) {
            return new ArrayList<>(HOSTS);
        }
    }

    public static void setHostRules(List<String> rules) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(rules.get(i));
        }
        parse(sb.toString());
    }
}

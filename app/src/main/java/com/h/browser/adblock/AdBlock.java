package com.h.browser.adblock;

import android.content.Context;

import com.h.browser.core.UrlUtils;
import com.h.browser.data.Prefs;
import com.h.browser.privacy.PrivacyStats;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdBlock {

    private static final AtomicInteger BLOCKED = new AtomicInteger();
    private static final ConcurrentLinkedDeque<String> LOG = new ConcurrentLinkedDeque<>();
    private static final Map<String, Boolean> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 4096;
                }
            });

    private static volatile Rules rules = Rules.EMPTY;
    private static volatile boolean loaded;
    private static volatile int userCount;
    private static volatile int subscriptionCount;

    private AdBlock() {
    }

    public static void init(Context context) {
        if (loaded) {
            return;
        }
        loaded = true;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                Subscriptions.refresh(app);
                Subscriptions.maybeAutoUpdate(app);
            } catch (Throwable t) {
                applySnapshot(Rules.parse(Subscriptions.BUILTIN), 0, 0);
            }
        }, "adblock-load").start();
    }

    static void applySnapshot(Rules snapshot, int users, int subs) {
        if (snapshot == null) {
            return;
        }
        rules = snapshot;
        userCount = users;
        subscriptionCount = subs;
        CACHE.clear();
    }

    public static void parse(String content) {
        applySnapshot(Rules.parse(content), 0, 0);
    }

    public static boolean isEnabled() {
        return Prefs.adblockEnabled();
    }

    public static boolean shouldBlock(String url) {
        return shouldBlock(url, url, false, false);
    }

    public static boolean shouldBlock(String url, String pageUrl, boolean mainFrame,
                                      boolean incognito) {
        if (mainFrame || url == null || !isEnabled()) {
            return false;
        }
        if (UrlUtils.isInternal(url)) {
            return false;
        }
        String docHost = UrlUtils.host(pageUrl);
        String type = Rules.requestType(url);
        String key = url + '\n' + docHost + '\n' + type;
        Boolean cached = CACHE.get(key);
        if (cached != null) {
            if (cached) {
                BLOCKED.incrementAndGet();
                if (!incognito) {
                    PrivacyStats.recordBlocked();
                }
            }
            return cached;
        }
        boolean blocked = rules.blocks(url, docHost, type);
        CACHE.put(key, blocked);
        if (blocked) {
            BLOCKED.incrementAndGet();
            if (!incognito) {
                PrivacyStats.recordBlocked();
                LOG.addFirst(UrlUtils.host(url) + "  " + url);
                while (LOG.size() > 200) {
                    LOG.removeLast();
                }
            }
        }
        return blocked;
    }

    public static List<String> selectorsFor(String url) {
        if (!isEnabled() || url == null || url.isEmpty()) {
            return Collections.emptyList();
        }
        return rules.cosmeticFor(UrlUtils.host(url));
    }

    public static String cosmeticCss(String url) {
        List<String> selectors = selectorsFor(url);
        if (selectors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        int bytes = 0;
        for (String selector : selectors) {
            int extra = selector.length() + 1;
            if (count >= 4000 || bytes + extra > 512 * 1024) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(selector);
            count++;
            bytes += extra;
        }
        if (sb.length() == 0) {
            return "";
        }
        sb.append("{display:none!important}");
        return sb.toString();
    }

    public static String injectScript(String url) {
        List<String> selectors = selectorsFor(url);
        if (selectors.isEmpty()) {
            return legacyScript();
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;
        int bytes = 0;
        for (String selector : selectors) {
            if (count >= 4000 || bytes > 512 * 1024) {
                break;
            }
            if (current.length() > 0) {
                current.append(',');
            }
            current.append(selector);
            count++;
            bytes += selector.length() + 1;
            if (count % 200 == 0) {
                current.append("{display:none!important}");
                chunks.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            current.append("{display:none!important}");
            chunks.add(current.toString());
        }
        StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                array.append(',');
            }
            array.append(JSONObject.quote(chunks.get(i)));
        }
        array.append(']');
        return "(function(){for(var i=0;i<16;i++){var e=document.getElementById('h-adblock-'+i);"
                + "if(e&&e.parentNode)e.parentNode.removeChild(e);}"
                + "var c=" + array + ";for(var i=0;i<c.length;i++){"
                + "try{var s=document.createElement('style');s.id='h-adblock-'+i;s.textContent=c[i];"
                + "(document.head||document.documentElement).appendChild(s);}catch(e){}}"
                + legacyPart() + "})();";
    }

    private static String legacyScript() {
        String part = legacyPart();
        return part.isEmpty() ? "" : "(function(){" + part + "})();";
    }

    private static String legacyPart() {
        return "try{var a=JSON.parse(localStorage.getItem('h_ad_rules')||'[]');"
                + "var q=[];for(var i=0;i<a.length;i++){var v=String(a[i]);"
                + "if(v&&v.length<300&&!/[{};\\n\\r]/.test(v))q.push(v);}"
                + "if(q.length){var s=document.getElementById('h-ad-rules');"
                + "if(!s){s=document.createElement('style');s.id='h-ad-rules';"
                + "(document.head||document.documentElement).appendChild(s);}"
                + "s.textContent=q.join(',')+'{display:none!important}';}}catch(e){}";
    }

    public static String injectCssScript(String css) {
        if (css == null || css.isEmpty()) {
            return "";
        }
        return "(function(){var id='h-adblock-0';var s=document.getElementById(id);"
                + "if(!s){s=document.createElement('style');s.id=id;"
                + "(document.head||document.documentElement).appendChild(s);}"
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
        return rules.counts.total();
    }

    public static int hostRuleCount() {
        return rules.counts.hostRules;
    }

    public static Rules.Counts counts() {
        return rules.counts;
    }

    public static int userRuleCount() {
        return userCount;
    }

    public static int subscriptionCount() {
        return subscriptionCount;
    }

    public static String statsSummary() {
        Rules.Counts c = rules.counts;
        return "主机 " + c.hostRules + " · 路径/选项 " + c.netRules
                + " · 例外 " + c.exceptions + " · 元素 " + c.cosmeticRules
                + "\n我的规则 " + userCount + " 条 · 启用订阅 " + subscriptionCount
                + " · 未支持 " + c.unsupported;
    }

    public static String subscriptionSummary(Context context) {
        return Subscriptions.summary(context);
    }

    public static List<String> userRules(Context context) {
        return Subscriptions.userRules(context);
    }

    public static boolean saveUserRule(Context context, String host, String selector) {
        boolean ok = Subscriptions.addUserRule(context, host, selector);
        if (ok) {
            Subscriptions.refresh(context);
        }
        return ok;
    }

    public static boolean removeUserRule(Context context, String line) {
        boolean ok = Subscriptions.removeUserRule(context, line);
        if (ok) {
            Subscriptions.refresh(context);
        }
        return ok;
    }

    public interface Callback {
        void onResult(boolean ok, String message);
    }

    public static void updateSubscription(Context context, String url, Callback callback) {
        if (url == null || url.trim().isEmpty()) {
            updateAll(context, callback);
            return;
        }
        Subscriptions.Sub match = null;
        for (Subscriptions.Sub sub : Subscriptions.list(context)) {
            if (sub.url.equals(url.trim())) {
                match = sub;
                break;
            }
        }
        if (match != null) {
            Subscriptions.updateOne(context, match.id, callback);
            return;
        }
        Subscriptions.Sub added = Subscriptions.addCustom(context, null, url);
        if (added == null) {
            if (callback != null) {
                callback.onResult(false, "订阅地址无效");
            }
            return;
        }
        Subscriptions.updateOne(context, added.id, callback);
    }

    public static void updateAll(Context context, Callback callback) {
        Subscriptions.updateAll(context, callback);
    }
}

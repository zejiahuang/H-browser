package com.h.browser.ui;

import android.content.Context;

import com.h.browser.adblock.AdBlock;
import com.h.browser.core.UrlUtils;
import com.h.browser.data.Db;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class InternalPages {

    private static final String CSS = "<style>"
            + ":root{--bg:#F5FAFA;--fg:#171D1E;--muted:#3F4849;--card:#EAF0F1;--primary:#006A71;--outline:#BEC8C9}"
            + "@media(prefers-color-scheme:dark){:root{--bg:#0E1415;--fg:#DEE4E4;--muted:#BEC8C9;--card:#1A2122;--primary:#4FD8E4;--outline:#3F4849}}"
            + "*{box-sizing:border-box}html,body{margin:0;background:var(--bg);color:var(--fg)}"
            + "body{font-family:system-ui,-apple-system,'Noto Sans SC',sans-serif;line-height:1.5}"
            + ".wrap{padding:20px 16px 48px;max-width:760px;margin:0 auto}"
            + "h1{font-size:34px;font-weight:800;letter-spacing:-.6px;margin:8px 0 4px}"
            + "h2{font-size:18px;margin:0 0 8px}.sub{color:var(--muted);font-size:13px;margin-bottom:16px}"
            + ".card{background:var(--card);border-radius:24px;padding:16px 18px;margin:12px 0}"
            + "a{color:var(--primary);text-decoration:none}"
            + "input{width:100%;border:0;border-radius:22px;padding:15px 18px;font-size:16px;background:var(--bg);color:var(--fg);outline:none}"
            + "button{border:0;border-radius:20px;padding:12px 18px;background:var(--primary);color:#fff;font-size:15px;font-weight:600}"
            + "button.ghost{background:transparent;color:var(--muted);border:1px solid var(--outline)}"
            + ".row{display:flex;align-items:center;gap:10px;padding:12px 2px;border-bottom:1px solid var(--outline)}"
            + ".row:last-child{border-bottom:0}.t{flex:1;min-width:0}"
            + ".t b{display:block;font-size:15px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
            + ".t span{display:block;font-size:12px;color:var(--muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
            + ".small{background:transparent;color:var(--muted);font-size:13px;padding:8px}"
            + ".chip{display:inline-block;background:var(--bg);border-radius:18px;padding:9px 15px;margin:4px 6px 4px 0;font-size:14px}"
            + ".mono{font-family:ui-monospace,monospace;font-size:12px;word-break:break-all}"
            + "</style>";

    private InternalPages() {
    }

    public static String render(Context context, String uri) {
        String path = uri.startsWith("v://") ? uri.substring(4) : uri;
        int slash = path.indexOf('/');
        if (slash >= 0) {
            path = path.substring(0, slash);
        }
        switch (path) {
            case "bookmarks":
                return wrap("书签", bookmarks(context));
            case "history":
                return wrap("历史", history(context));
            case "log":
                return wrap("拦截日志", log());
            case "skins":
                return wrap("外观", skins());
            case "about":
                return wrap("关于", about());
            case "home":
            default:
                return home(context);
        }
    }

    private static String wrap(String title, String body) {
        return "<!doctype html><html><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'>"
                + CSS + "</head><body><div class=wrap><h1>" + title + "</h1>" + body + "</div></body></html>";
    }

    private static String home(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=wrap><h1>氢</h1><div class=sub>轻量 · 干净 · 属于你</div>");
        sb.append("<form onsubmit='event.preventDefault();HNative.search(this.q.value);return false'>");
        sb.append("<input name=q placeholder='搜索或输入网址' autocomplete=off autocapitalize=off spellcheck=false></form>");
        List<Db.Item> marks = Db.get(context).bookmarks(8);
        if (!marks.isEmpty()) {
            sb.append("<div class=card><h2>快速访问</h2><div>");
            for (Db.Item item : marks) {
                sb.append("<a class=chip href='javascript:void(0)' onclick=\"HNative.openUrl('")
                        .append(js(UrlUtils.escapeHtml(item.url))).append("')\">")
                        .append(UrlUtils.escapeHtml(shortTitle(item.title, item.url))).append("</a>");
            }
            sb.append("</div></div>");
        }
        List<Db.Item> history = Db.get(context).history(6);
        if (!history.isEmpty()) {
            sb.append("<div class=card><h2>最近</h2>");
            for (Db.Item item : history) {
                sb.append("<div class=row><div class=t><b>")
                        .append(UrlUtils.escapeHtml(shortTitle(item.title, item.url)))
                        .append("</b><span>").append(UrlUtils.escapeHtml(item.url)).append("</span></div>");
                sb.append("<button class=small onclick=\"HNative.openUrl('")
                        .append(js(UrlUtils.escapeHtml(item.url))).append("')\">打开</button></div>");
            }
            sb.append("</div>");
        }
        sb.append("<div class=card><div class=row><div class=t><b>广告拦截</b><span>已拦截 ")
                .append(AdBlock.blockedCount()).append(" 次 · 规则 ").append(AdBlock.ruleCount())
                .append(" 条</span></div><button class=small onclick=\"HNative.openUrl('v://log')\">查看</button></div>");
        sb.append("<div class=row><div class=t><b>外部工具</b><span>书签、历史、脚本、同步</span></div>"
                + "<button class=small onclick='HNative.openSettings()'>设置</button></div></div>");
        sb.append("<div class=sub style='margin-top:18px'>H browser 0.1 · 系统 WebView 内核</div></div>");
        return "<!doctype html><html><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'>"
                + CSS + "</head><body>" + sb + "</body></html>";
    }

    private static String bookmarks(Context context) {
        StringBuilder sb = new StringBuilder();
        List<Db.Item> items = Db.get(context).bookmarks(500);
        if (items.isEmpty()) {
            return "<div class=sub>还没有书签。在网页上通过菜单收藏。</div>";
        }
        for (Db.Item item : items) {
            sb.append("<div class=row><div class=t><b>").append(UrlUtils.escapeHtml(shortTitle(item.title, item.url)))
                    .append("</b><span>").append(UrlUtils.escapeHtml(item.url)).append("</span></div>")
                    .append("<button class=small onclick=\"HNative.openUrl('").append(js(UrlUtils.escapeHtml(item.url)))
                    .append("')\">打开</button>")
                    .append("<button class=small onclick=\"HNative.deleteBookmark(").append(item.id)
                    .append(");this.closest('.row').remove()\">删除</button></div>");
        }
        return sb.toString();
    }

    private static String history(Context context) {
        StringBuilder sb = new StringBuilder();
        List<Db.Item> items = Db.get(context).history(300);
        sb.append("<div style='margin-bottom:12px'><button class=ghost onclick='HNative.clearHistory();location.reload()'>清空历史</button></div>");
        if (items.isEmpty()) {
            return sb + "<div class=sub>暂无记录。</div>";
        }
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        for (Db.Item item : items) {
            sb.append("<div class=row><div class=t><b>").append(UrlUtils.escapeHtml(shortTitle(item.title, item.url)))
                    .append("</b><span>").append(UrlUtils.escapeHtml(item.url)).append(" · ")
                    .append(format.format(new Date(item.time))).append("</span></div>")
                    .append("<button class=small onclick=\"HNative.openUrl('").append(js(UrlUtils.escapeHtml(item.url)))
                    .append("')\">打开</button>")
                    .append("<button class=small onclick=\"HNative.deleteHistory(").append(item.id)
                    .append(");this.closest('.row').remove()\">删除</button></div>");
        }
        return sb.toString();
    }

    private static String log() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=card><h2>统计</h2><div class=sub>已拦截 ").append(AdBlock.blockedCount())
                .append(" 次 · 主机规则 ").append(AdBlock.ruleCount()).append(" 条</div>");
        sb.append("<button onclick='HNative.updateAdblock()'>更新订阅</button> ")
                .append("<button class=ghost onclick='HNative.clearLog();location.reload()'>清空</button></div>");
        List<String> logs = AdBlock.recentBlocks();
        if (logs.isEmpty()) {
            sb.append("<div class=sub>本次没有拦截记录。</div>");
        } else {
            sb.append("<div class=card><h2>最近拦截</h2>");
            int limit = Math.min(logs.size(), 100);
            for (int i = 0; i < limit; i++) {
                sb.append("<div class=mono style='padding:8px 0;border-bottom:1px solid var(--outline)'>")
                        .append(UrlUtils.escapeHtml(logs.get(i))).append("</div>");
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    private static String skins() {
        return "<div class=card><h2>主题</h2><div class=sub>跟随系统或固定明暗；Android 12+ 自动取色。</div>"
                + "<button onclick='HNative.setThemeMode(0)'>跟随系统</button> "
                + "<button onclick='HNative.setThemeMode(1)'>浅色</button> "
                + "<button onclick='HNative.setThemeMode(2)'>深色</button></div>"
                + "<div class=card><h2>设计</h2><div class=sub>Material 3 Expressive · 氢青配色 · 24dp 圆角 · 无内置字体和图片，保持安装包轻量。</div></div>";
    }

    private static String about() {
        return "<div class=card><h2>氢 (H browser) 0.1.0</h2>"
                + "<div class=sub>一个基于系统 WebView 的轻量浏览器。无遥测，无账号，无私有云依赖。</div></div>"
                + "<div class=card><h2>已实现</h2><div class=sub>多标签 · 广告拦截 · 用户脚本 · 阅读模式 · AI 助手 · 翻译 · 朗读 · "
                + "媒体嗅探 · 下载 · PDF · 扫码 · 密码库 · WebDAV 同步 · 无痕浏览</div></div>"
                + "<div class=card><h2>许可与致谢</h2><div class=sub>Apache-2.0。图标基于 Material Icons。"
                + "广告规则来自用户订阅（默认 EasyList China，CC BY-SA），本应用不内置第三方规则文件。</div></div>";
    }

    private static String shortTitle(String title, String url) {
        if (title != null && !title.trim().isEmpty() && !title.startsWith("v://")) {
            return title;
        }
        String host = UrlUtils.host(url);
        return host.isEmpty() ? url : host;
    }

    private static String js(String escaped) {
        return escaped.replace("\\", "\\\\").replace("'", "\\'");
    }
}

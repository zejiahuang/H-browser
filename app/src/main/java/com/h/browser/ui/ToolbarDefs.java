package com.h.browser.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 底栏工具按钮的 id/名称总表，读写时统一截断到最多 8 个并保证含菜单。 */
public final class ToolbarDefs {

    public static final int MAX_VISIBLE = 8;

    public static final String[] IDS = {
            "back", "forward", "home", "refresh", "tabs", "newtab", "bookmarks",
            "downloads", "share", "search", "find", "settings", "ai", "menu"};

    public static final String[] NAMES = {
            "后退", "前进", "主页", "刷新", "标签", "新标签", "书签",
            "下载", "分享", "搜索", "页内查找", "设置", "AI", "菜单"};

    private ToolbarDefs() {
    }

    public static Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < IDS.length; i++) {
            map.put(IDS[i], NAMES[i]);
        }
        return map;
    }

    public static boolean valid(String id) {
        for (String candidate : IDS) {
            if (candidate.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static String name(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) {
                return NAMES[i];
            }
        }
        return "";
    }

    /** 去重、剔除非白名单、截断到 8 个，且必须包含菜单。 */
    public static String sanitize(String csv) {
        List<String> out = new ArrayList<>();
        if (csv != null) {
            for (String raw : csv.split(",")) {
                String id = raw.trim();
                if (valid(id) && !out.contains(id)) {
                    out.add(id);
                }
            }
        }
        if (!out.contains("menu")) {
            if (out.size() >= MAX_VISIBLE) {
                out.remove(out.size() - 1);
            }
            out.add("menu");
        }
        while (out.size() > MAX_VISIBLE) {
            out.remove(out.size() - 1);
        }
        return String.join(",", out);
    }
}

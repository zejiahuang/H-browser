package com.h.browser.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.h.browser.R;
import com.h.browser.data.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 浏览器菜单的动作清单与排序/隐藏偏好。
 * id 是稳定键，文案只用于显示，避免升级后旧 menu_hidden 失效。
 */
public final class MenuDefs {

    public static final String CUSTOMIZE_ID = "menu_customize";
    public static final int COLUMNS = 4;
    public static final int PAGE_SIZE = 12;
    public static final int SIMPLE_COUNT = 8;

    public static final class Item {
        public final String id;
        public final String label;
        public final int icon;
        public final boolean needsContent;

        Item(String id, String label, int icon, boolean needsContent) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.needsContent = needsContent;
        }
    }

    public static final Item[] ITEMS = {
            new Item("night", "夜间模式", R.drawable.ic_moon, false),
            new Item("bookmarks", "书签", R.drawable.ic_star, false),
            new Item("history", "历史", R.drawable.ic_history, false),
            new Item("downloads", "下载", R.drawable.ic_download, false),
            new Item("incognito", "隐身", R.drawable.ic_visibility_off, false),
            new Item("share", "分享", R.drawable.ic_share, false),
            new Item("add_bookmark", "添加书签", R.drawable.ic_star_filled, false),
            new Item("desktop", "电脑模式", R.drawable.ic_desktop, false),
            new Item("toolbox", "工具箱", R.drawable.ic_toolbox, false),
            new Item("settings", "设置", R.drawable.ic_settings, false),
            new Item("find", "页内查找", R.drawable.ic_search, false),
            new Item("save", "保存", R.drawable.ic_save, true),
            new Item("offline", "离线页面", R.drawable.ic_offline, false),
            new Item("translate", "翻译", R.drawable.ic_translate, true),
            new Item("source", "源码", R.drawable.ic_source, true),
            new Item("fullscreen", "全屏", R.drawable.ic_fullscreen, false),
            new Item("images", "有图模式", R.drawable.ic_image, false),
            new Item("sniffer", "资源嗅探", R.drawable.ic_wave_square, false),
            new Item("site_settings", "网站设定", R.drawable.ic_shield_check, false),
            new Item("netlog", "网络日志", R.drawable.ic_log, false),
            new Item("scan", "扫描二维码", R.drawable.ic_scan, false),
            new Item("shortcut", "添加到桌面", R.drawable.ic_shortcut, false),
            new Item("tts", "朗读网页", R.drawable.ic_tts, true),
            new Item("ai", "AI", R.drawable.ic_ai, false),
            new Item("rotate", "屏幕方向", R.drawable.ic_rotate, false),
            new Item("adblock", "广告拦截", R.drawable.ic_block, false),
            new Item("mark_ad", "标记广告", R.drawable.ic_edit, true),
            new Item("font", "字体大小", R.drawable.ic_font, true),
            new Item("report", "举报网站", R.drawable.ic_report, true),
            new Item(CUSTOMIZE_ID, "定制菜单", R.drawable.ic_tune, false)
    };

    private static final Map<String, Item> BY_ID = new LinkedHashMap<>();
    private static final Map<String, String> LABEL_TO_ID = new LinkedHashMap<>();

    static {
        for (Item item : ITEMS) {
            BY_ID.put(item.id, item);
            LABEL_TO_ID.put(item.label, item.id);
        }
    }

    private MenuDefs() {
    }

    public static Item find(String id) {
        return BY_ID.get(id);
    }

    public static String labelOf(String id) {
        Item item = BY_ID.get(id);
        return item == null ? id : item.label;
    }

    public static int iconOf(String id) {
        Item item = BY_ID.get(id);
        return item == null ? R.drawable.ic_tune : item.icon;
    }

    public static List<String> defaultOrder() {
        List<String> ids = new ArrayList<>();
        for (Item item : ITEMS) {
            ids.add(item.id);
        }
        return ids;
    }

    public static List<String> order() {
        List<String> result = new ArrayList<>();
        String raw = Prefs.get().getString("menu_order", "");
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split(",")) {
                String id = part.trim();
                if (BY_ID.containsKey(id) && !result.contains(id)) {
                    result.add(id);
                }
            }
        }
        for (String id : defaultOrder()) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    public static Set<String> hiddenIds() {
        return new HashSet<>(Prefs.get().getStringSet("menu_hidden_ids",
                Collections.<String>emptySet()));
    }

    /** 旧版按文案保存 menu_hidden，升级时换算成 id，旧键保留。 */
    public static void migrateLegacy() {
        if (Prefs.get().contains("menu_hidden_ids")) {
            return;
        }
        Set<String> legacy = Prefs.get().getStringSet("menu_hidden",
                Collections.<String>emptySet());
        Set<String> ids = new HashSet<>();
        for (String label : legacy) {
            String id = LABEL_TO_ID.get(label);
            if (id != null) {
                ids.add(id);
            }
        }
        Prefs.get().edit().putStringSet("menu_hidden_ids", ids).apply();
    }

    public static void showEditor(final Context context, final Runnable onSaved) {
        final List<String> order = order();
        final Set<String> hidden = hiddenIds();

        final LinearLayout list = Ui.column(context);
        list.setPadding(Ui.dp(context, 8), Ui.dp(context, 4), Ui.dp(context, 8), 0);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            for (int i = 0; i < order.size(); i++) {
                final int index = i;
                final String id = order.get(i);
                final boolean fixed = CUSTOMIZE_ID.equals(id);
                LinearLayout row = Ui.row(context);
                row.setMinimumHeight(Ui.dp(context, 48));
                row.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
                row.addView(Ui.text(context, labelOf(id), 16, R.color.on_surface),
                        new LinearLayout.LayoutParams(0,
                                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(actionText(context,
                        fixed ? "固定" : (hidden.contains(id) ? "隐藏" : "显示"), () -> {
                            if (fixed) {
                                return;
                            }
                            if (!hidden.remove(id)) {
                                hidden.add(id);
                            }
                            render[0].run();
                        }));
                row.addView(actionText(context, "↑", () -> {
                    if (index == 0) {
                        return;
                    }
                    order.add(index - 1, order.remove(index));
                    render[0].run();
                }));
                row.addView(actionText(context, "↓", () -> {
                    if (index == order.size() - 1) {
                        return;
                    }
                    order.add(index + 1, order.remove(index));
                    render[0].run();
                }));
                list.addView(row);
            }
        };
        render[0].run();

        new MaterialAlertDialogBuilder(context)
                .setTitle("定制菜单")
                .setMessage("上移/下移调整顺序，或隐藏不常用的项目；定制菜单始终显示。")
                .setView(scroll)
                .setPositiveButton("保存", (d, w) -> {
                    Prefs.get().edit()
                            .putString("menu_order", join(order))
                            .putStringSet("menu_hidden_ids", hidden)
                            .apply();
                    if (onSaved != null) {
                        onSaved.run();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String join(List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (String id : order) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private static TextView actionText(Context context, String label, final Runnable action) {
        TextView view = Ui.text(context, label, 14, R.color.primary);
        int h = Ui.dp(context, 8);
        int w = Ui.dp(context, 12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(w, h, w, h);
        view.setBackground(Ui.ripple(context));
        view.setOnClickListener(v -> action.run());
        return view;
    }
}

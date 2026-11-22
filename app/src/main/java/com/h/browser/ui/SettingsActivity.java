package com.h.browser.ui;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.app.WallpaperManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.h.browser.R;
import com.h.browser.adblock.AdBlock;
import com.h.browser.ai.AiChatActivity;
import com.h.browser.data.Backup;
import com.h.browser.data.Prefs;
import com.h.browser.download.DownloadActivity;
import com.h.browser.sniffer.MediaListActivity;
import com.h.browser.sync.SyncActivity;
import com.h.browser.userscript.UserScripts;
import com.h.browser.vault.VaultActivity;
import com.h.browser.widget.BaseSearchWidget;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_IMPORT_BM = 3001;
    private static final int REQ_EXPORT_BM = 3002;
    private static final int REQ_EXPORT_HISTORY = 3003;

    private static final String[][] CATEGORIES = {
            {"general", "通用", "搜索引擎 主页 浏览器标识 字体 下载 嗅探 书签 历史 小组件 无痕 语言 同步 AI 屏幕方向 操作设定"},
            {"personal", "个性化", "菜单样式 材质样式 工具栏设置 模糊 玻璃 圆角 布局 背景 外观"},
            {"custom", "定制", "主题 模糊 玻璃 工具栏 深色 背景 Logo 搜索框 外观"},
            {"privacy", "隐私", "无痕 密码 清除 跟踪 WebRTC 同步 WebDAV"},
            {"advanced", "高级", "广告 拦截 订阅 AI 翻译 朗读 接口 规则 安全浏览 SSL 调试"},
            {"scripts", "脚本", "脚本 用户脚本 导入"},
            {"experimental", "实验", "模糊 玻璃 背景"},
            {"about", "关于", "版本 许可 反馈 协议 隐私政策"}
    };

    private interface Saver {
        void save(String value);
    }

    private interface BooleanSaver {
        void save(boolean value);
    }

    private String cat;
    private LinearLayout root;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cat = getIntent().getStringExtra("cat");
        root = Ui.screen(this);
        if (pref("settings_wallpaper")) {
            android.graphics.drawable.Drawable wallpaper = null;
            try {
                wallpaper = WallpaperManager.getInstance(this).getDrawable();
            } catch (Throwable ignored) {
            }
            if (wallpaper != null) {
                getWindow().setBackgroundDrawable(wallpaper);
            } else {
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
                getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(0x00000000));
            }
            root.setBackgroundColor(Glass.night(this) ? 0xA6000000 : 0xAAF7F7F7);
        }
        LinearLayout top;
        if ("scripts".equals(cat)) {
            android.widget.ImageButton add = new android.widget.ImageButton(this);
            add.setImageResource(R.drawable.ic_add);
            add.setBackground(Ui.ripple(this));
            add.setContentDescription("添加脚本");
            int pad = Ui.dp(this, 10);
            add.setPadding(pad, pad, pad, pad);
            add.setOnClickListener(v -> importFromClipboard());
            add.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
            TextView update = Ui.text(this, "更新", 15, R.color.on_surface);
            update.setGravity(Gravity.CENTER);
            update.setBackground(Ui.ripple(this));
            update.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 4), 0);
            update.setOnClickListener(v -> toast("已安装脚本均为最新解析版本"));
            update.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44)));
            top = Ui.topBar(this, labelOf(cat), add, update);
        } else {
            top = Ui.topBar(this, cat == null ? "设置" : labelOf(cat));
        }
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        content = Ui.column(this);
        content.setPadding(0, 0, 0, Ui.dp(this, 32));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (cat == null) {
            buildMain();
        } else {
            buildCategory(cat);
        }
        if ("custom".equals(cat)) {
            tintDesignerChrome(top);
            root.addView(buildDesignerTabs(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
        }
    }

    private void tintDesignerChrome(View topBar) {
        if (!(topBar instanceof ViewGroup)) {
            return;
        }
        ViewGroup outer = (ViewGroup) topBar;
        for (int i = 0; i < outer.getChildCount(); i++) {
            View child = outer.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup bar = (ViewGroup) child;
                for (int j = 0; j < bar.getChildCount(); j++) {
                    View item = bar.getChildAt(j);
                    if (item instanceof TextView) {
                        ((TextView) item).setTextColor(0xFFFFFFFF);
                    } else if (item instanceof android.widget.ImageButton) {
                        ((android.widget.ImageButton) item).setColorFilter(0xFFFFFFFF);
                    }
                }
            } else if (child.getBackground() != null) {
                child.setBackgroundColor(0xFF3A3A3A);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        recreate();
    }

    private static String labelOf(String key) {
        if ("toolbar".equals(key)) {
            return "工具栏设置";
        }
        for (String[] item : CATEGORIES) {
            if (item[0].equals(key)) {
                return item[1];
            }
        }
        return "设置";
    }

    private void buildMain() {
        content.removeAllViews();

        EditText search = new EditText(this);
        search.setHint("搜索");
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setTextColor(Ui.color(this, R.color.on_surface));
        search.setHintTextColor(Ui.color(this, R.color.on_surface_variant));
        search.setBackground(pill());
        int hpad = Ui.dp(this, 16);
        search.setPadding(hpad, 0, hpad, 0);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 40));
        searchParams.setMargins(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        content.addView(search, searchParams);

        final List<View> rows = new ArrayList<>();
        for (String[] item : CATEGORIES) {
            final String key = item[0];
            LinearLayout row = Ui.row(this);
            row.setBackground(Ui.ripple(this));
            row.setMinimumHeight(Ui.dp(this, 61));
            row.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
            TextView label = Ui.text(this, item[1], 14, R.color.on_surface_variant);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)
                    .putExtra("cat", key)));
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            rows.add(row);
        }

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim().toLowerCase();
                for (int i = 0; i < rows.size(); i++) {
                    String[] item = CATEGORIES[i];
                    boolean match = query.isEmpty()
                            || item[1].toLowerCase().contains(query)
                            || item[2].toLowerCase().contains(query);
                    rows.get(i).setVisibility(match ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    private GradientDrawable pill() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Glass.night(this) ? 0xFF202020 : 0xFFE9E9E9);
        bg.setCornerRadius(Ui.dp(this, 20));
        return bg;
    }

    private void buildCategory(String key) {
        content.removeAllViews();
        switch (key) {
            case "general":
                buildGeneral();
                break;
            case "personal":
                buildPersonal();
                break;
            case "toolbar":
                buildToolbarSettings();
                break;
            case "custom":
                buildDesigner();
                break;
            case "privacy":
                buildPrivacy();
                break;
            case "advanced":
                buildAdvanced();
                break;
            case "scripts":
                buildScripts();
                break;
            case "experimental":
                buildExperimental();
                break;
            case "about":
                buildAbout();
                break;
            default:
                buildMain();
                break;
        }
    }

    private void buildGeneral() {
        flat("同步", null, null,
                () -> startActivity(new Intent(this, SyncActivity.class)));
        flat("浏览器标识", uaName(), null, this::uaDialog);
        flat("清除数据", null, null, this::clearData);
        flat("广告拦截", null, null, this::adblockDialog);
        flat("网站设定", null, null, this::siteSettingsOverview);
        flat("密码管理器", null, null,
                () -> startActivity(new Intent(this, VaultActivity.class)));
        flat("夜间模式", null, null, this::themeDialog);
        flat("阅读模式", null, null,
                () -> info("阅读模式", "在网页菜单中点「阅读模式」即可提取正文。"));
        flat("工具栏设置", null, null, this::toolbarDialog);
        flat("定制菜单", null, null,
                () -> info("定制菜单", "请在浏览器菜单中点「定制菜单」勾选要显示的项目。"));
        flat("定制长按菜单", null, null,
                () -> info("定制长按菜单", "长按网页中的链接或图片即可打开操作菜单。"));
        flat("语言", null, null,
                () -> info("语言", "当前版本仅提供简体中文。"));
        flat("搜索设置", Prefs.searchEngine(), null,
                () -> inputDialog("搜索引擎（用 %s 代表关键词）",
                        Prefs.searchEngine(), Prefs::setSearchEngine));
        flat("AI 设置", aiSummary(), null, this::aiDialog);
        flat("屏幕方向", orientationName(), null, this::orientationDialog);
        flat("下载目录", "/sdcard/Download", null,
                () -> info("下载目录", "下载文件保存在系统下载目录 /sdcard/Download。"));
        flat("下载器", "系统下载器", null,
                () -> info("下载器", "使用系统 DownloadManager 下载，支持通知栏进度。"));
        flat("外置视频播放器", "系统分享", null,
                () -> info("外置视频播放器", "点击视频时使用系统分享面板选择播放器。"));
        switchFlat("退出时清除数据", "退出浏览器时清理缓存与 Cookie",
                pref("clear_on_exit"), value -> setPref("clear_on_exit", value));
        flat("字体", Prefs.fontZoom() + "%", null, this::zoomDialog);
        flat("操作设定", "手势、快捷方式", null,
                () -> info("操作设定", "下拉刷新：" + (Prefs.pullRefresh() ? "开" : "关")
                        + "；边缘滑动：" + (Prefs.edgeGesture() ? "开" : "关")
                        + "。可在「定制」中调整。"));
        flat("导入/备份书签", "HTML 书签文件", null, this::backupDialog);
    }

    private void buildPersonal() {
        FrameLayout preview = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFBFE3E3, 0xFFE6F1F0, 0xFFF5EEE5});
        bg.setCornerRadius(Ui.dp(this, 24));
        bg.setStroke(Math.max(1, Ui.dp(this, 1)),
                Glass.night(this) ? 0x22FFFFFF : 0x33000000);
        preview.setBackground(bg);
        if (pref("settings_wallpaper")) {
            try {
                android.graphics.drawable.Drawable wp =
                        android.app.WallpaperManager.getInstance(this).getDrawable();
                if (wp != null) {
                    android.widget.ImageView wallpaper = new android.widget.ImageView(this);
                    wallpaper.setImageDrawable(wp);
                    wallpaper.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    wallpaper.setClipToOutline(true);
                    G2Path.applyOutline(wallpaper, Ui.dp(this, 24), 0f);
                    preview.addView(wallpaper, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                }
            } catch (Throwable ignored) {
            }
        }

        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.VERTICAL);
        mini.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        if (Glass.enabled() && android.os.Build.VERSION.SDK_INT >= 26) {
            GlassDrawable miniGlass = new GlassDrawable(getWindow(), true, 20, Glass.curve());
            miniGlass.setDrawShadow(false);
            mini.setBackground(miniGlass);
        } else {
            mini.setBackground(Glass.panel(this, 20));
        }
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Glass.night(this) ? 0xCC2A3031 : 0xCCEFEFEF);
        pillBg.setCornerRadius(Ui.dp(this, 13));
        pill.setBackground(pillBg);
        pill.addView(Ui.text(this, "主页", 12, R.color.on_surface));
        mini.addView(pill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));
        LinearLayout nav = Ui.row(this);
        nav.setGravity(Gravity.CENTER);
        int[] icons = {R.drawable.ic_back, R.drawable.ic_forward, R.drawable.ic_home,
                R.drawable.ic_tabs_count, R.drawable.ic_menu};
        for (int i = 0; i < icons.length; i++) {
            android.widget.ImageView icon = new android.widget.ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(androidx.core.content.ContextCompat.getColor(
                    this, R.color.on_surface));
            LinearLayout holder = new LinearLayout(this);
            holder.setGravity(Gravity.CENTER);
            holder.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
            nav.addView(holder, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 32));
        navParams.topMargin = Ui.dp(this, 6);
        mini.addView(nav, navParams);

        FrameLayout.LayoutParams miniParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        miniParams.setMargins(Ui.dp(this, 18), 0, Ui.dp(this, 18), Ui.dp(this, 18));
        preview.addView(mini, miniParams);

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 190));
        previewParams.setMargins(Ui.dp(this, 14), Ui.dp(this, 12),
                Ui.dp(this, 14), Ui.dp(this, 10));
        content.addView(preview, previewParams);

        flat("菜单样式", Glass.menuSimple() ? "简约" : "完整", null,
                () -> choiceDialog("菜单样式", new String[]{"完整", "简约"},
                        new int[]{0, 1}, "menu_style", 0));
        flat("材质样式", Glass.materialLabel(), null,
                () -> choiceDialog("材质样式",
                        new String[]{"柔光玻璃", "磨砂", "液态玻璃", "纯色"},
                        new int[]{0, 1, 2, 3}, "glass_style", 0));
        flat("工具栏设置", toolbarSummary(), null,
                () -> startActivity(new Intent(this, SettingsActivity.class)
                        .putExtra("cat", "toolbar")));
        slider("外层圆角", 24, 48, Glass.outerRadius(), "dp", "glass_outer_radius");
        slider("G2 曲线", 0, 100, Glass.curve(), "%", "glass_curve");
        slider("地址框上方间距", 0, 20, Glass.addressGap(), "dp", "glass_gap");
    }

    private void buildToolbarSettings() {
        switchFlat("模糊效果", "为菜单、悬浮工具栏与页面顶部启用玻璃模糊",
                Glass.enabled(), value -> {
                    setPref("glass", value);
                    recreate();
                });
        flat("模糊半径", Glass.radius() + "dp", null, () -> choiceDialog("模糊半径",
                new String[]{"12dp", "18dp", "24dp", "32dp", "40dp"},
                new int[]{12, 18, 24, 32, 40}, "glass_radius", 24));
        switchFlat("渐进式模糊", "页面顶部随滚动渐隐",
                pref("progressive_blur", true),
                value -> setPref("progressive_blur", value));
        switchFlat("沉浸悬浮工具栏", "底栏以玻璃面板悬浮在页面之上",
                pref("floating_toolbar", true),
                value -> {
                    setPref("floating_toolbar", value);
                    recreate();
                });
        flat("工具栏边距", Glass.toolbarMargin() + "dp", null,
                () -> choiceDialog("工具栏边距",
                        new String[]{"0dp", "8dp", "12dp", "16dp", "20dp"},
                        new int[]{0, 8, 12, 16, 20}, "toolbar_margin", 16));
    }

    private void slider(String title, final int min, final int max, int value,
                        final String suffix, final String key) {
        LinearLayout block = Ui.column(this);
        block.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 4));
        LinearLayout head = Ui.row(this);
        head.addView(Ui.text(this, title, 14, R.color.on_surface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView current = Ui.text(this, value + suffix, 14, R.color.primary);
        head.addView(current);
        block.addView(head);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                current.setText((min + progress) + suffix);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Prefs.get().edit().putInt(key, min + seekBar.getProgress()).apply();
            }
        });
        block.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void siteSettingsOverview() {
        java.util.TreeSet<String> hosts = new java.util.TreeSet<>();
        for (String key : Prefs.get().getAll().keySet()) {
            if (!key.startsWith("site_") || key.length() <= 5) {
                continue;
            }
            String rest = key.substring(5);
            int underscore = rest.indexOf('_');
            if (underscore > 0) {
                hosts.add(rest.substring(underscore + 1));
            }
        }
        if (hosts.isEmpty()) {
            info("网站设定", "还没有站点自定义。\n\n浏览网页时打开菜单 →「网站设定」，"
                    + "可对当前站点单独设置 JavaScript、加载图片、桌面版网页与广告拦截。");
            return;
        }
        final String[] items = hosts.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("网站设定")
                .setItems(items, (d, which) -> new MaterialAlertDialogBuilder(this)
                        .setTitle(items[which])
                        .setMessage("清除该站点的全部自定义设定？")
                        .setPositiveButton("清除", (d2, w2) -> {
                            String host = items[which];
                            Prefs.get().edit()
                                    .remove("site_js_" + host)
                                    .remove("site_img_" + host)
                                    .remove("site_ua_" + host)
                                    .remove("site_ad_" + host).apply();
                            toast("已清除");
                            buildCategory("general");
                        })
                        .setNegativeButton("取消", null)
                        .show())
                .setNegativeButton("关闭", null)
                .show();
    }

    private void buildPrivacy() {
        switchFlat("不跟踪", null,
                pref("dnt", true), value -> setPref("dnt", value));
        switchFlat("禁用 WebRTC", null,
                pref("no_webrtc"), value -> setPref("no_webrtc", value));
        switchFlat("不出售或分享数据",
                "请求网站不出售或分享我的数据以保护隐私（实验性）",
                pref("no_sell"), value -> setPref("no_sell", value));
    }

    private void buildAdvanced() {
        String trans = Prefs.transUrl().isEmpty()
                ? "未设置（LibreTranslate / OpenAI 兼容）" : Prefs.transUrl();
        flat("翻译接口", trans, null, this::transDialog);
        switchFlat("流量节省", "请求网站节省流量",
                pref("data_saver"), value -> setPref("data_saver", value));
        switchFlat("自动展示嗅探按钮", null,
                pref("auto_sniff"), value -> setPref("auto_sniff", value));
        switchFlat("允许调试网页", null,
                pref("debug_web"), value -> {
                    setPref("debug_web", value);
                    if (android.os.Build.VERSION.SDK_INT >= 19) {
                        WebView.setWebContentsDebuggingEnabled(value);
                    }
                });
        switchFlat("禁用预测返回手势", null,
                pref("no_predictive"), value -> setPref("no_predictive", value));
        switchFlat("禁用 Custom Tabs", "跳过第三方应用通过 Custom Tabs 打开链接时的中转页",
                pref("no_custom_tabs"), value -> setPref("no_custom_tabs", value));
        switchFlat("禁用安全浏览",
                "安全浏览允许 WebView 通过验证链接来防止恶意软件和网络钓鱼攻击",
                pref("no_safe_browsing"), value -> setPref("no_safe_browsing", value));
        switchFlat("忽略 SSL 证书警告",
                "请小心，忽略 SSL 证书警告可能会使网站的连接不安全",
                pref("ignore_ssl"), value -> setPref("ignore_ssl", value));
    }

    private void buildScripts() {
        List<UserScripts.Script> scripts = UserScripts.list();
        if (scripts.isEmpty()) {
            TextView empty = Ui.text(this, "\\_(ツ)_/¯", 24, R.color.on_surface_variant);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 340), 0, 0);
            content.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (UserScripts.Script script : scripts) {
            flat(script.name, "用户脚本", null, () ->
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(script.name)
                            .setMessage("要删除这个脚本吗？")
                            .setPositiveButton("删除", (d, w) -> {
                                UserScripts.delete(script.id);
                                buildCategory("scripts");
                            })
                            .setNegativeButton("取消", null)
                            .show());
        }
    }

    private void buildAbout() {
        LinearLayout head = Ui.column(this);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setPadding(0, Ui.dp(this, 36), 0, Ui.dp(this, 28));
        HMarkView logo = new HMarkView(this);
        logo.setScale(0.67f);
        head.addView(logo);
        TextView name = Ui.text(this, "氢", 17, R.color.on_surface);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = Ui.dp(this, 18);
        head.addView(name, nameParams);
        head.addView(Ui.text(this, version(), 13, R.color.on_surface_variant),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        flat("检查更新", null, null,
                () -> toast("已是最新版本"));
        flat("意见反馈", null, null, this::feedback);
        flat("通过邮件联系我", null, null, () -> {
            try {
                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")));
            } catch (Throwable t) {
                toast("没有可用的邮件应用");
            }
        });
        flat("协助翻译", null, null, () ->
                info("协助翻译", "欢迎通过 GitHub 提交界面与文档翻译。"));
        flat("使用协议", null, null, () ->
                info("使用协议", "本应用按现状提供，不附带任何明示或暗示担保。"
                        + "你需自行承担使用风险并遵守当地法律法规。"));
        flat("隐私政策", null, null, () ->
                info("隐私政策", "本应用没有遥测、统计或账号系统，"
                        + "书签、历史、密码与设置均保存在本机。\n\n"
                        + "仅当你主动使用 AI、翻译或 WebDAV 时，"
                        + "相关数据才会发送到你自行配置的服务器。"));
        flat("开源许可协议", null, null, () ->
                info("开源许可协议", "本应用源代码采用 Apache License 2.0。\n\n"
                        + "广告拦截种子规则参考 EasyList 格式，"
                        + "EasyList 由社区维护，采用 CC BY-SA 3.0 许可。"));
    }

    private void buildDesigner() {
        root.setBackgroundColor(0xFF222222);
        content.setPadding(0, 0, 0, 0);
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setGravity(Gravity.CENTER_HORIZONTAL);
        preview.setPadding(0, Ui.dp(this, 190), 0, 0);
        GradientDrawable bg = new GradientDrawable();
        int homeBg = Prefs.get().getInt("home_bg", 0);
        bg.setColor(homeBg != 0 ? homeBg : 0xFF000000);
        bg.setCornerRadius(Ui.dp(this, 24));
        bg.setStroke(Math.max(1, Ui.dp(this, 1)), 0x33FFFFFF);
        preview.setBackground(bg);
        HMarkView logo = new HMarkView(this);
        logo.setScale(0.94f);
        preview.addView(logo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout searchPill = new LinearLayout(this);
        searchPill.setOrientation(LinearLayout.HORIZONTAL);
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setStroke(Math.max(1, Ui.dp(this, 1)), 0xFF343434);
        outline.setCornerRadius(Ui.dp(this, 23));
        searchPill.setBackground(outline);
        TextView pillHint = Ui.text(this, "搜索或输入网址", 13, R.color.on_surface_variant);
        pillHint.setTextColor(0xFFDDDDDD);
        searchPill.addView(pillHint);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        pillParams.setMargins(Ui.dp(this, 16), Ui.dp(this, 27), Ui.dp(this, 16), 0);
        preview.addView(searchPill, pillParams);

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 600));
        previewParams.setMargins(Ui.dp(this, 11), Ui.dp(this, 15),
                Ui.dp(this, 11), Ui.dp(this, 17));
        content.addView(preview, previewParams);
    }

    private LinearLayout buildDesignerTabs() {
        LinearLayout tabs = Ui.row(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF070707);
        bg.setCornerRadii(new float[]{Ui.dp(this, 14), Ui.dp(this, 14),
                Ui.dp(this, 14), Ui.dp(this, 14), 0, 0, 0, 0});
        tabs.setBackground(bg);
        String[] labels = {"Logo", "搜索框", "背景", "收藏", "高级"};
        Runnable[] actions = {
                this::designerLogo,
                () -> inputDialog("搜索引擎（用 %s 代表关键词）",
                        Prefs.searchEngine(), Prefs::setSearchEngine),
                this::backgroundDialog,
                () -> info("主页收藏", "主页收藏以快捷方式显示在搜索框下方，"
                        + "可在书签页长按设置。"),
                () -> startActivity(new Intent(this, SettingsActivity.class)
                        .putExtra("cat", "experimental"))
        };
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tab = Ui.text(this, labels[i], 14, R.color.on_surface_variant);
            tab.setTextColor(0xFFDDDDDD);
            tab.setGravity(Gravity.CENTER);
            tab.setBackground(Ui.ripple(this));
            tab.setOnClickListener(v -> actions[index].run());
            tabs.addView(tab, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return tabs;
    }

    private void designerLogo() {
        info("Logo", "主页搜索框上方固定显示“H”标识。");
    }

    private void backgroundDialog() {
        final String[] labels = {"跟随主题", "深色", "纯黑", "氢青", "暖白"};
        final int[] colors = {0, 0xFF101415, 0xFF000000, 0xFF00343A, 0xFFF2F7F7};
        int current = Prefs.get().getInt("home_bg", 0);
        int checked = 0;
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == current) checked = i;
        }
        new MaterialAlertDialogBuilder(this).setTitle("主页背景")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    Prefs.get().edit().putInt("home_bg", colors[which]).apply();
                    buildCategory("custom");
                    toast("主页背景已更新");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void flat(String title, String summary, View right, Runnable action) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        row.setMinimumHeight(Ui.dp(this, 62));
        if (action != null) {
            row.setBackground(Ui.ripple(this));
            row.setOnClickListener(v -> action.run());
        }
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 14, R.color.on_surface));
        if (summary != null && !summary.isEmpty()) {
            texts.addView(Ui.text(this, summary, 12, R.color.on_surface_variant));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (right != null) {
            row.addView(right);
        }
        content.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void switchFlat(String title, String summary, boolean checked,
                            final BooleanSaver saver) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        row.setMinimumHeight(Ui.dp(this, 62));
        row.setBackground(Ui.ripple(this));
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 14, R.color.on_surface));
        if (summary != null && !summary.isEmpty()) {
            texts.addView(Ui.text(this, summary, 12, R.color.on_surface_variant));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final DotToggle toggle = new DotToggle(this, checked);
        row.addView(toggle, new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
        row.setOnClickListener(v -> {
            toggle.setChecked(!toggle.isChecked());
            saver.save(toggle.isChecked());
        });
        content.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private boolean pref(String key) {
        return pref(key, false);
    }

    private boolean pref(String key, boolean fallback) {
        return Prefs.get().getBoolean(key, fallback);
    }

    private void setPref(String key, boolean value) {
        Prefs.get().edit().putBoolean(key, value).apply();
    }

    private void info(String title, String message) {
        new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message)
                .setPositiveButton("好", null).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void themeDialog() {
        int mode = Prefs.get().getInt("theme_mode", 0);
        new MaterialAlertDialogBuilder(this).setTitle("夜间模式")
                .setSingleChoiceItems(new String[]{"跟随系统", "浅色", "深色"}, mode,
                        (dialog, which) -> {
                            dialog.dismiss();
                            Prefs.get().edit().putInt("theme_mode", which).apply();
                            AppCompatDelegate.setDefaultNightMode(which == 1
                                    ? AppCompatDelegate.MODE_NIGHT_NO
                                    : which == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                            recreate();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void adblockDialog() {
        boolean enabled = Prefs.adblockEnabled();
        new MaterialAlertDialogBuilder(this).setTitle("广告拦截")
                .setMessage((enabled ? "已启用" : "已关闭") + " · 主机规则 "
                        + AdBlock.ruleCount() + " 条 · 已拦截 "
                        + AdBlock.blockedCount() + " 次")
                .setPositiveButton("更新规则", (d, w) -> {
                    toast("开始更新…");
                    AdBlock.updateSubscription(this, Prefs.adblockSubscription(), (ok, message) ->
                            runOnUiThread(() -> {
                                toast(message);
                                buildCategory("general");
                            }));
                })
                .setNeutralButton(enabled ? "关闭" : "开启", (d, w) -> {
                    Prefs.setAdblockEnabled(!enabled);
                    AdBlock.init(this);
                    buildCategory("general");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String orientationName() {
        return Prefs.get().getString("screen_orientation", "跟随系统");
    }

    private void orientationDialog() {
        String[] labels = {"跟随系统", "自动", "竖屏", "横屏"};
        int checked = Arrays.asList(labels).indexOf(orientationName());
        new MaterialAlertDialogBuilder(this).setTitle("屏幕方向")
                .setSingleChoiceItems(labels, Math.max(checked, 0), (dialog, which) -> {
                    dialog.dismiss();
                    Prefs.get().edit().putString("screen_orientation", labels[which]).apply();
                    buildCategory("general");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void backupDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("导入/备份书签")
                .setItems(new String[]{"导入书签", "导出书签", "导出历史"}, (d, which) -> {
                    if (which == 0) {
                        importBookmarks();
                    } else if (which == 1) {
                        exportBookmarks();
                    } else {
                        exportHistory();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearData() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("清除数据")
                .setMessage("清除缓存、Cookie 与表单数据？书签、历史和密码不受影响。")
                .setPositiveButton("清除", (d, w) -> {
                    try {
                        CookieManager.getInstance().removeAllCookies(null);
                        CookieManager.getInstance().flush();
                        WebView probe = new WebView(this);
                        probe.clearCache(true);
                        probe.clearFormData();
                        probe.clearHistory();
                        probe.destroy();
                        toast("已清除");
                    } catch (Throwable t) {
                        toast("清除失败");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void feedback() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/zejiahuang/H-browser/issues/new")));
        } catch (Throwable t) {
            toast("没有可用的浏览器");
        }
    }

    private void uaDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("浏览器标识")
                .setSingleChoiceItems(new String[]{"Android（默认）", "桌面版", "自定义"},
                        Prefs.uaMode(), (dialog, which) -> {
                            dialog.dismiss();
                            Prefs.setUaMode(which);
                            if (which == 2) {
                                inputDialog("自定义 UA", Prefs.customUa(), value -> {
                                    Prefs.setCustomUa(value);
                                    toastRefresh();
                                });
                            } else {
                                toastRefresh();
                            }
                            buildCategory("general");
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toastRefresh() {
        Toast.makeText(this, "已保存，重新加载页面后生效", Toast.LENGTH_SHORT).show();
    }

    private String themeName() {
        int mode = Prefs.get().getInt("theme_mode", 0);
        return mode == 1 ? "浅色" : mode == 2 ? "深色" : "跟随系统";
    }

    private String uaName() {
        int mode = Prefs.uaMode();
        return mode == 1 ? "桌面版" : mode == 2 ? "自定义" : "默认";
    }

    private String aiSummary() {
        String base = Prefs.aiBase();
        return base.isEmpty() ? "未配置（OpenAI 兼容）" : base + " · " + Prefs.aiModel();
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "0.2.0";
        }
    }

    private void inputDialog(String title, String value, final Saver saver) {
        final EditText input = Ui.input(this, title);
        input.setText(value);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        int pad = Ui.dp(this, 20);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(pad, Ui.dp(this, 8), pad, 0);
        wrapper.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(wrapper)
                .setPositiveButton("保存", (d, w) -> {
                    saver.save(input.getText().toString().trim());
                    if (cat == null) {
                        buildMain();
                    } else {
                        buildCategory(cat);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void aiDialog() {
        LinearLayout wrapper = Ui.column(this);
        int pad = Ui.dp(this, 20);
        wrapper.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final EditText base = Ui.input(this, "接口地址，如 https://api.openai.com/v1");
        base.setText(Prefs.aiBase());
        base.setSingleLine(false);
        final EditText key = Ui.input(this, "API Key");
        key.setText(Prefs.aiKey());
        final EditText model = Ui.input(this, "模型，如 gpt-4o-mini");
        model.setText(Prefs.aiModel());
        wrapper.addView(base);
        wrapper.addView(key);
        wrapper.addView(model);
        new MaterialAlertDialogBuilder(this)
                .setTitle("AI 设置（OpenAI 兼容）")
                .setView(wrapper)
                .setPositiveButton("保存", (d, w) -> {
                    Prefs.setAi(base.getText().toString().trim(), key.getText().toString().trim(),
                            model.getText().toString().trim());
                    buildCategory("general");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void transDialog() {
        LinearLayout wrapper = Ui.column(this);
        int pad = Ui.dp(this, 20);
        wrapper.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final EditText url = Ui.input(this, "接口地址（可含 %s）");
        url.setText(Prefs.transUrl());
        url.setSingleLine(false);
        final EditText key = Ui.input(this, "API Key（可选）");
        key.setText(Prefs.transKey());
        final EditText target = Ui.input(this, "目标语言，如 zh / en / ja");
        target.setText(Prefs.transTarget());
        wrapper.addView(url);
        wrapper.addView(key);
        wrapper.addView(target);
        new MaterialAlertDialogBuilder(this)
                .setTitle("翻译接口（LibreTranslate / OpenAI 兼容）")
                .setView(wrapper)
                .setPositiveButton("保存", (d, w) -> {
                    Prefs.setTrans(url.getText().toString().trim(), key.getText().toString().trim(),
                            target.getText().toString().trim());
                    buildCategory("advanced");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void manageScripts() {
        final List<UserScripts.Script> scripts = UserScripts.list();
        final String[] labels = new String[scripts.size() + 1];
        labels[0] = "从剪贴板导入脚本";
        for (int i = 0; i < scripts.size(); i++) {
            labels[i + 1] = "删除 · " + scripts.get(i).name;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("用户脚本")
                .setItems(labels, (d, which) -> {
                    if (which == 0) {
                        importFromClipboard();
                    } else {
                        UserScripts.delete(scripts.get(which - 1).id);
                        buildCategory("scripts");
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void importFromClipboard() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String source = "";
        if (manager != null && manager.getPrimaryClip() != null
                && manager.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = manager.getPrimaryClip().getItemAt(0).coerceToText(this);
            source = text == null ? "" : text.toString();
        }
        source = source.trim();
        if (source.contains("==UserScript==") && installScript(source)) {
            return;
        }
        final EditText input = Ui.input(this, "用户脚本源码，需包含 ==UserScript== 元数据");
        input.setText(source);
        input.setSingleLine(false);
        input.setMinLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        LinearLayout wrapper = Ui.column(this);
        int pad = Ui.dp(this, 20);
        wrapper.setPadding(pad, Ui.dp(this, 8), pad, 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 260)));
        new MaterialAlertDialogBuilder(this)
                .setTitle("导入用户脚本")
                .setView(wrapper)
                .setPositiveButton("导入", (d, w) -> {
                    if (!installScript(input.getText().toString().trim())) {
                        toast("不是有效的用户脚本");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean installScript(String source) {
        if (source == null || !source.contains("==UserScript==")) {
            return false;
        }
        try {
            UserScripts.Script script = UserScripts.install(this, source);
            toast("已安装：" + script.name);
            buildCategory("scripts");
            return true;
        } catch (Throwable t) {
            toast("安装失败");
            return false;
        }
    }

    private void buildExperimental() {
        switchFlat("模糊效果", "为菜单、悬浮工具栏与页面顶部启用玻璃模糊",
                Glass.enabled(), value -> {
                    setPref("glass", value);
                    recreate();
                });
        switchFlat("在设置中显示背景", "设置页以半透明玻璃浮在壁纸之上",
                pref("settings_wallpaper"), value -> {
                    setPref("settings_wallpaper", value);
                    recreate();
                });
    }

    private void choiceDialog(String title, final String[] labels, final int[] values,
                              final String key, final int fallback) {
        int current = Prefs.get().getInt(key, fallback);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) checked = i;
        }
        new MaterialAlertDialogBuilder(this).setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    Prefs.get().edit().putInt(key, values[which]).apply();
                    if ("glass_material".equals(key) || "glass_style".equals(key)) {
                        recreate();
                    } else {
                        buildCategory(cat == null ? "experimental" : cat);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String toolbarSummary() {
        List<String> names = new ArrayList<>();
        for (String id : Prefs.toolbarItems().split(",")) {
            String name = toolbarName(id);
            if (!name.isEmpty()) names.add(name);
        }
        return names.isEmpty() ? "仅菜单" : String.join(" · ", names);
    }

    private static String toolbarName(String id) {
        switch (id) {
            case "back": return "后退";
            case "forward": return "前进";
            case "home": return "主页";
            case "refresh": return "刷新";
            case "tabs": return "标签";
            case "newtab": return "新标签";
            case "menu": return "菜单";
            default: return "";
        }
    }

    private TextView actionText(String label, final Runnable action) {
        TextView view = Ui.text(this, label, 14, R.color.primary);
        int h = Ui.dp(this, 8);
        int w = Ui.dp(this, 12);
        view.setPadding(w, h, w, h);
        view.setBackground(Ui.ripple(this));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void toolbarDialog() {
        final Map<String, String> names = new LinkedHashMap<>();
        names.put("back", "后退");
        names.put("forward", "前进");
        names.put("home", "主页");
        names.put("refresh", "刷新");
        names.put("tabs", "标签");
        names.put("newtab", "新标签");
        names.put("menu", "菜单");
        final List<String> order = new ArrayList<>();
        final List<String> saved = Arrays.asList(Prefs.toolbarItems().split(","));
        for (String id : saved) {
            if (names.containsKey(id) && !order.contains(id)) order.add(id);
        }
        for (String id : names.keySet()) {
            if (!order.contains(id)) order.add(id);
        }
        final Set<String> hidden = new HashSet<>();
        for (String id : names.keySet()) {
            if (!saved.contains(id)) hidden.add(id);
        }
        hidden.remove("menu");

        final LinearLayout list = Ui.column(this);
        list.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), 0);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            for (int i = 0; i < order.size(); i++) {
                final int index = i;
                final String id = order.get(i);
                final boolean fixed = id.equals("menu");
                LinearLayout row = Ui.row(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(this, 48));
                row.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
                row.addView(Ui.text(this, names.get(id), 16, R.color.on_surface),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(actionText(fixed ? "固定" : (hidden.contains(id) ? "隐藏" : "显示"), () -> {
                    if (fixed) return;
                    if (!hidden.remove(id)) hidden.add(id);
                    render[0].run();
                }));
                row.addView(actionText("↑", () -> {
                    if (index == 0) return;
                    order.add(index - 1, order.remove(index));
                    render[0].run();
                }));
                row.addView(actionText("↓", () -> {
                    if (index == order.size() - 1) return;
                    order.add(index + 1, order.remove(index));
                    render[0].run();
                }));
                list.addView(row);
            }
        };
        render[0].run();

        new MaterialAlertDialogBuilder(this)
                .setTitle("工具栏设置")
                .setMessage("调整顺序，或隐藏不常用的按钮。")
                .setView(scroll)
                .setPositiveButton("保存", (d, w) -> {
                    List<String> visible = new ArrayList<>();
                    for (String id : order) {
                        if (!hidden.contains(id)) visible.add(id);
                    }
                    if (!visible.contains("menu")) visible.add("menu");
                    Prefs.setToolbarItems(String.join(",", visible));
                    buildCategory(cat == null ? "general" : cat);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void zoomDialog() {
        final String[] labels = {"80%", "90%", "100%", "110%", "125%", "150%", "175%", "200%"};
        final int[] values = {80, 90, 100, 110, 125, 150, 175, 200};
        int checked = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == Prefs.fontZoom()) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("网页字体大小")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    Prefs.setFontZoom(values[which]);
                    dialog.dismiss();
                    if (cat == null) {
                        buildMain();
                    } else {
                        buildCategory(cat);
                    }
                    toast("重新加载页面后生效");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void importBookmarks() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"text/html", "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQ_IMPORT_BM);
        } catch (Throwable t) {
            toast("无法打开文件选择器");
        }
    }

    private void exportBookmarks() {
        createDocument("h-bookmarks.html", REQ_EXPORT_BM);
    }

    private void exportHistory() {
        createDocument("h-history.html", REQ_EXPORT_HISTORY);
    }

    private void createDocument(String name, int request) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(intent, request);
        } catch (Throwable t) {
            toast("无法打开保存位置选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_IMPORT_BM) {
            new Thread(() -> {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    int count = Backup.importBookmarks(this, in);
                    runOnUiThread(() -> toast("已导入 " + count + " 条书签"));
                } catch (Throwable t) {
                    runOnUiThread(() -> toast("导入失败"));
                }
            }).start();
        } else if (requestCode == REQ_EXPORT_BM || requestCode == REQ_EXPORT_HISTORY) {
            final boolean isHistory = requestCode == REQ_EXPORT_HISTORY;
            new Thread(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (isHistory) {
                        Backup.exportHistory(this, out);
                    } else {
                        Backup.exportBookmarks(this, out);
                    }
                    runOnUiThread(() -> toast("已导出到所选位置"));
                } catch (Throwable t) {
                    runOnUiThread(() -> toast("导出失败"));
                }
            }).start();
        }
    }

    private static final class DotToggle extends View {
        private boolean checked;

        DotToggle(Context context, boolean checked) {
            super(context);
            this.checked = checked;
            setContentDescription(checked ? "已开启" : "已关闭");
            apply();
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean value) {
            checked = value;
            setContentDescription(value ? "已开启" : "已关闭");
            apply();
        }

        private void apply() {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            if (checked) {
                bg.setColor(Ui.color(getContext(), R.color.primary));
            } else {
                bg.setColor(Color.TRANSPARENT);
                bg.setStroke(Math.max(2, (int) (1.5f * getResources()
                        .getDisplayMetrics().density)), 0xFF3C3C3C);
            }
            setBackground(bg);
        }
    }
}

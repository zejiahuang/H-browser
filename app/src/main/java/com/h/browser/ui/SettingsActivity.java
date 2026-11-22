package com.h.browser.ui;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.h.browser.R;
import com.h.browser.adblock.AdBlock;
import com.h.browser.adblock.Subscriptions;
import com.h.browser.ai.AiChatActivity;
import com.h.browser.core.BrowserActivity;
import com.h.browser.data.Backup;
import com.h.browser.data.Prefs;
import com.h.browser.download.DownloadActivity;
import com.h.browser.sniffer.MediaListActivity;
import com.h.browser.sync.SyncActivity;
import com.h.browser.userscript.UserScripts;
import com.h.browser.vault.VaultActivity;
import com.h.browser.widget.BaseSearchWidget;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
    private static final int REQ_IMPORT_RULES = 3004;
    private static final int REQ_HOME_BG = 3005;

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
        flat("隐私报告", "拦截、指纹读取、权限请求与保护状态", null, () ->
                startActivity(new Intent(this, BrowserActivity.class)
                        .setAction(Intent.ACTION_VIEW).setData(Uri.parse("v://privacy"))));
        switchFlat("不跟踪", "发送 DNT / GPC 隐私信号",
                com.h.browser.privacy.PrivacyPrefs.dnt(),
                value -> com.h.browser.privacy.PrivacyPrefs.setDnt(value));
        switchFlat("禁用 WebRTC", "阻止网页通过 WebRTC 获取真实 IP",
                com.h.browser.privacy.PrivacyPrefs.blockWebRtc(),
                value -> com.h.browser.privacy.PrivacyPrefs.setBlockWebRtc(value));
        switchFlat("清理跟踪参数", "剥离 utm、fbclid 等参数，保留登录回调",
                com.h.browser.privacy.PrivacyPrefs.stripTracking(),
                value -> com.h.browser.privacy.PrivacyPrefs.setStripTracking(value));
        switchFlat("仅 HTTPS", "HTTP 页面自动升级，失败可手动继续",
                com.h.browser.privacy.PrivacyPrefs.httpsOnly(),
                value -> com.h.browser.privacy.PrivacyPrefs.setHttpsOnly(value));
        switchFlat("阻止第三方 Cookie", "可能影响部分站点的登录与嵌入内容",
                com.h.browser.privacy.PrivacyPrefs.blockThirdPartyCookies(),
                value -> com.h.browser.privacy.PrivacyPrefs.setBlockThirdPartyCookies(value));
        switchFlat("指纹保护", "Canvas/WebGL/Audio/字体加入噪声（实验）",
                com.h.browser.privacy.PrivacyPrefs.fingerprintMaster(),
                value -> setPref("privacy_fp", value));
        switchFlat("Canvas 指纹噪声", "对绘图读取加入每站点微噪声",
                pref("privacy_fp_canvas", true),
                value -> setPref("privacy_fp_canvas", value));
        switchFlat("WebGL 指纹噪声", "泛化显卡厂商与渲染器信息",
                pref("privacy_fp_webgl", true),
                value -> setPref("privacy_fp_webgl", value));
        switchFlat("Audio 指纹噪声", "对音频处理输出加入听不见的噪声",
                pref("privacy_fp_audio", true),
                value -> setPref("privacy_fp_audio", value));
        switchFlat("字体指纹干扰", "降低通过文字测量枚举本机字体的准确性",
                pref("privacy_fp_fonts", true),
                value -> setPref("privacy_fp_fonts", value));
        flat("指纹豁免站点", fingerprintExemptSummary(), null, this::fingerprintExemptionsDialog);
        flat("代理服务器", proxySummary(), null, this::proxyDialog);
    }

    private String fingerprintExemptSummary() {
        java.util.Set<String> hosts = com.h.browser.privacy.PrivacyPrefs.fingerprintExemptions();
        if (hosts.isEmpty()) {
            return "未豁免任何站点";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String host : hosts) {
            if (i++ > 0) {
                sb.append("、");
            }
            sb.append(host);
            if (i >= 3) {
                sb.append(" 等 ").append(hosts.size()).append(" 个");
                break;
            }
        }
        return sb.toString();
    }

    private String proxySummary() {
        if (!com.h.browser.privacy.PrivacyPrefs.proxyEnabled()) {
            String err = com.h.browser.net.WebViewProxy.lastError();
            return err == null ? "未启用（HTTP/HTTPS）" : "未启用 · " + err;
        }
        String summary = "已启用 · " + com.h.browser.privacy.PrivacyPrefs.proxyHost()
                + ":" + com.h.browser.privacy.PrivacyPrefs.proxyPort();
        String err = com.h.browser.net.WebViewProxy.lastError();
        return err == null ? summary : summary + " · " + err;
    }

    private void fingerprintExemptionsDialog() {
        final java.util.List<String> items = new ArrayList<>(
                com.h.browser.privacy.PrivacyPrefs.fingerprintExemptions());
        items.add("＋ 添加豁免站点");
        new MaterialAlertDialogBuilder(this)
                .setTitle("指纹豁免站点")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == items.size() - 1) {
                        addFingerprintExemption();
                        return;
                    }
                    String host = items.get(which);
                    new MaterialAlertDialogBuilder(this).setTitle(host)
                            .setMessage("移除该站点的指纹豁免？")
                            .setPositiveButton("移除", (d2, w2) -> {
                                java.util.Set<String> hosts =
                                        com.h.browser.privacy.PrivacyPrefs.fingerprintExemptions();
                                hosts.remove(host);
                                com.h.browser.privacy.PrivacyPrefs.setFingerprintExemptions(hosts);
                                buildCategory("privacy");
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void addFingerprintExemption() {
        EditText input = new EditText(this);
        input.setHint("example.com");
        input.setSingleLine(true);
        LinearLayout wrapper = Ui.column(this);
        int pad = Ui.dp(this, 20);
        wrapper.setPadding(pad, Ui.dp(this, 8), pad, 0);
        wrapper.addView(input);
        new MaterialAlertDialogBuilder(this).setTitle("添加豁免站点")
                .setView(wrapper)
                .setPositiveButton("添加", (d, w) -> {
                    String host = input.getText().toString().trim()
                            .toLowerCase(java.util.Locale.US);
                    if (host.isEmpty()) {
                        return;
                    }
                    java.util.Set<String> hosts =
                            com.h.browser.privacy.PrivacyPrefs.fingerprintExemptions();
                    hosts.add(host);
                    com.h.browser.privacy.PrivacyPrefs.setFingerprintExemptions(hosts);
                    buildCategory("privacy");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void proxyDialog() {
        LinearLayout form = Ui.column(this);
        int pad = Ui.dp(this, 20);
        form.setPadding(pad, Ui.dp(this, 8), pad, 0);
        EditText host = new EditText(this);
        host.setHint("代理主机，如 127.0.0.1");
        host.setSingleLine(true);
        host.setText(com.h.browser.privacy.PrivacyPrefs.proxyHost());
        EditText port = new EditText(this);
        port.setHint("端口，如 7890");
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setSingleLine(true);
        int currentPort = com.h.browser.privacy.PrivacyPrefs.proxyPort();
        if (currentPort > 0) {
            port.setText(String.valueOf(currentPort));
        }
        EditText user = new EditText(this);
        user.setHint("用户名（可选）");
        user.setSingleLine(true);
        user.setText(com.h.browser.privacy.PrivacyPrefs.proxyUser());
        EditText pass = new EditText(this);
        pass.setHint("密码（可选，加密保存）");
        pass.setSingleLine(true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setText(com.h.browser.privacy.PrivacyPrefs.proxyPass());
        form.addView(host);
        form.addView(port);
        form.addView(user);
        form.addView(pass);
        form.addView(Ui.text(this, "仅支持 HTTP/HTTPS 代理。认证只在挑战主机等于代理主机时提交；"
                + "SOCKS5 认证中转将在后续版本提供。", 12, R.color.on_surface_variant));
        new MaterialAlertDialogBuilder(this).setTitle("代理服务器")
                .setView(form)
                .setPositiveButton("保存并应用", (d, w) -> {
                    String h = host.getText().toString().trim();
                    int portValue = 0;
                    try {
                        portValue = Integer.parseInt(port.getText().toString().trim());
                    } catch (Throwable ignored) {
                    }
                    boolean enabled = !h.isEmpty() && portValue > 0 && portValue <= 65535;
                    com.h.browser.privacy.PrivacyPrefs.setProxy(enabled, h, portValue,
                            user.getText().toString().trim(), pass.getText().toString());
                    com.h.browser.net.WebViewProxy.apply();
                    String err = com.h.browser.net.WebViewProxy.lastError();
                    toast(err != null ? err : enabled ? "代理已启用" : "代理未启用：主机或端口无效");
                    buildCategory("privacy");
                })
                .setNeutralButton("停用", (d, w) -> {
                    com.h.browser.privacy.PrivacyPrefs.setProxy(false,
                            com.h.browser.privacy.PrivacyPrefs.proxyHost(),
                            com.h.browser.privacy.PrivacyPrefs.proxyPort(),
                            com.h.browser.privacy.PrivacyPrefs.proxyUser(),
                            com.h.browser.privacy.PrivacyPrefs.proxyPass());
                    com.h.browser.net.WebViewProxy.apply();
                    toast("代理已停用");
                    buildCategory("privacy");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void buildAdvanced() {
        String trans = Prefs.transUrl().isEmpty()
                ? "未设置（LibreTranslate / OpenAI 兼容）" : Prefs.transUrl();
        flat("翻译接口", trans, null, this::transDialog);
        switchFlat("自动展示嗅探按钮", "页面出现可播放媒体时提示",
                pref("auto_sniff", true), value -> setPref("auto_sniff", value));
        flat("朗读语速", Prefs.ttsSpeed() + "×", null, () -> {
            final String[] labels = {"0.75×", "1×", "1.25×", "1.5×", "2×"};
            final float[] values = {0.75f, 1f, 1.25f, 1.5f, 2f};
            int checked = 1;
            for (int i = 0; i < values.length; i++) {
                if (Math.abs(values[i] - Prefs.ttsSpeed()) < 0.01f) {
                    checked = i;
                }
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("朗读语速")
                    .setSingleChoiceItems(labels, checked, (d, which) -> {
                        Prefs.setTtsSpeed(values[which]);
                        d.dismiss();
                        buildCategory("advanced");
                    })
                    .show();
        });
        if (com.h.browser.BuildConfig.DEBUG) {
            switchFlat("允许调试网页", null,
                    pref("debug_web"), value -> {
                        setPref("debug_web", value);
                        WebView.setWebContentsDebuggingEnabled(value);
                    });
        }
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
            switchFlat(script.name, "用户脚本 · " + (UserScripts.isEnabled(script.id) ? "已启用" : "已停用"),
                    UserScripts.isEnabled(script.id), value -> {
                        UserScripts.setEnabled(script.id, value);
                        buildCategory("scripts");
                    });
            flat("删除「" + script.name + "」", null, null, () ->
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
        FrameLayout preview = new FrameLayout(this);
        int homeBg = Prefs.get().getInt("home_bg", 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(homeBg != 0 ? homeBg : 0xFF000000);
        bg.setCornerRadius(Ui.dp(this, 24));
        bg.setStroke(Math.max(1, Ui.dp(this, 1)), 0x33FFFFFF);
        preview.setBackground(bg);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            preview.setClipToOutline(true);
        }
        File bgFile = new File(getFilesDir(), "home_bg.jpg");
        if (bgFile.exists()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bitmap = decodeBounded(bgFile, 900);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
            }
            preview.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View scrim = new View(this);
            scrim.setBackgroundColor(0xFF000000);
            scrim.setAlpha(Prefs.homeBgDim() / 100f);
            preview.addView(scrim, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        LinearLayout group = Ui.column(this);
        group.setGravity(Gravity.CENTER_HORIZONTAL);
        group.setPadding(0, Ui.dp(this, 190), 0, 0);
        HMarkView logo = new HMarkView(this);
        logo.setScale(0.94f * Prefs.homeLogoScale() / 100f);
        logo.setVisibility(Prefs.homeLogo() ? View.VISIBLE : View.GONE);
        group.addView(logo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout searchPill = new LinearLayout(this);
        searchPill.setOrientation(LinearLayout.HORIZONTAL);
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        searchPill.setVisibility(Prefs.homeSearch() ? View.VISIBLE : View.GONE);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setStroke(Math.max(1, Ui.dp(this, 1)), 0xFF343434);
        outline.setCornerRadius(Ui.dp(this, 23));
        searchPill.setBackground(outline);
        TextView pillHint = Ui.text(this, Prefs.homeSearchHint(), 13, R.color.on_surface_variant);
        pillHint.setTextColor(0xFFDDDDDD);
        searchPill.addView(pillHint);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        pillParams.setMargins(Ui.dp(this, 16), Ui.dp(this, 27), Ui.dp(this, 16), 0);
        group.addView(searchPill, pillParams);
        preview.addView(group, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 600));
        previewParams.setMargins(Ui.dp(this, 11), Ui.dp(this, 15),
                Ui.dp(this, 11), Ui.dp(this, 17));
        content.addView(preview, previewParams);
    }

    private Bitmap decodeBounded(File file, int max) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > max) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            return null;
        }
    }

    private LinearLayout buildDesignerTabs() {
        LinearLayout tabs = Ui.row(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF070707);
        bg.setCornerRadii(new float[]{Ui.dp(this, 14), Ui.dp(this, 14),
                Ui.dp(this, 14), Ui.dp(this, 14), 0, 0, 0, 0});
        tabs.setBackground(bg);
        String[] labels = {"Logo", "搜索", "背景", "更多"};
        Runnable[] actions = {
                this::designerLogo,
                this::designerSearch,
                this::backgroundDialog,
                this::designerMore
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
        LinearLayout wrapper = Ui.column(this);
        int pad = Ui.dp(this, 20);
        wrapper.setPadding(pad, Ui.dp(this, 8), pad, 0);
        LinearLayout switchRow = Ui.row(this);
        switchRow.addView(Ui.text(this, "显示 H 标识", 16, R.color.on_surface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final SwitchCompat show = new SwitchCompat(this);
        show.setChecked(Prefs.homeLogo());
        switchRow.addView(show);
        wrapper.addView(switchRow);
        final TextView value = Ui.text(this, Prefs.homeLogoScale() + "%", 14,
                R.color.on_surface_variant);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = Ui.dp(this, 14);
        wrapper.addView(value, valueParams);
        final SeekBar seek = new SeekBar(this);
        seek.setMax(80);
        seek.setProgress(Math.max(0, Math.min(80, Prefs.homeLogoScale() - 60)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText((progress + 60) + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        wrapper.addView(seek);
        new MaterialAlertDialogBuilder(this)
                .setTitle("主页 Logo")
                .setView(wrapper)
                .setPositiveButton("保存", (d, w) -> {
                    Prefs.setHomeLogo(show.isChecked());
                    Prefs.setHomeLogoScale(seek.getProgress() + 60);
                    buildDesignerRefresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void designerSearch() {
        final String[] items = {
                "搜索框提示语",
                Prefs.homeSearch() ? "隐藏搜索框" : "显示搜索框",
                "搜索引擎"
        };
        new MaterialAlertDialogBuilder(this).setTitle("搜索框")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            homeHintDialog();
                            break;
                        case 1:
                            Prefs.setHomeSearch(!Prefs.homeSearch());
                            buildDesignerRefresh();
                            break;
                        default:
                            searchEngineDialog();
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void homeHintDialog() {
        final EditText input = Ui.input(this, "搜索框提示语");
        input.setText(Prefs.homeSearchHint());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int pad = Ui.dp(this, 20);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(pad, Ui.dp(this, 8), pad, 0);
        wrapper.addView(input);
        new MaterialAlertDialogBuilder(this).setTitle("搜索框提示语")
                .setView(wrapper)
                .setPositiveButton("保存", (d, w) -> {
                    Prefs.setHomeSearchHint(input.getText().toString());
                    buildDesignerRefresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void searchEngineDialog() {
        final String[][] engines = {
                {"百度", "https://www.baidu.com/s?wd=%s"},
                {"Bing", "https://www.bing.com/search?q=%s"},
                {"Google", "https://www.google.com/search?q=%s"},
                {"搜狗", "https://www.sogou.com/web?query=%s"},
                {"360", "https://www.so.com/s?q=%s"},
                {"DuckDuckGo", "https://duckduckgo.com/?q=%s"}
        };
        String[] labels = new String[engines.length + 1];
        int checked = -1;
        for (int i = 0; i < engines.length; i++) {
            labels[i] = engines[i][0];
            if (engines[i][1].equals(Prefs.searchEngine())) {
                checked = i;
            }
        }
        labels[engines.length] = "自定义模板…";
        new MaterialAlertDialogBuilder(this).setTitle("默认搜索引擎")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == engines.length) {
                        inputDialog("搜索引擎（用 %s 代表关键词）",
                                Prefs.searchEngine(), Prefs::setSearchEngine);
                    } else {
                        Prefs.setSearchEngine(engines[which][1]);
                        toast("已切换为" + engines[which][0]);
                        buildDesignerRefresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void buildDesignerRefresh() {
        buildCategory("custom");
    }

    private void designerMore() {
        final String[] items = {
                "主题模式",
                "网页深色：" + (Prefs.webDark() ? "开" : "关"),
                "地址栏显示：" + addressStyleName(Prefs.addressStyle()),
                "底栏自动隐藏：" + (Prefs.barAutoHide() ? "开" : "关"),
                "工具栏设置",
                "定制菜单",
                "材质与玻璃"
        };
        new MaterialAlertDialogBuilder(this).setTitle("更多外观")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            themeDialog();
                            break;
                        case 1:
                            boolean dark = !Prefs.webDark();
                            Prefs.get().edit().putBoolean("web_dark", dark).apply();
                            toast(dark ? "网页深色已开启" : "网页深色已关闭");
                            buildDesignerRefresh();
                            break;
                        case 2:
                            addressStyleDialog();
                            break;
                        case 3:
                            boolean auto = !Prefs.barAutoHide();
                            Prefs.get().edit().putBoolean("bar_autohide", auto).apply();
                            toast(auto ? "底栏将随滚动隐藏" : "底栏始终显示");
                            buildDesignerRefresh();
                            break;
                        case 4:
                            toolbarDialog();
                            break;
                        case 5:
                            MenuDefs.showEditor(this, null);
                            break;
                        default:
                            startActivity(new Intent(this, SettingsActivity.class)
                                    .putExtra("cat", "experimental"));
                            break;
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private String addressStyleName(int style) {
        if (style == 0) {
            return "网页标题";
        }
        return style == 2 ? "完整地址" : "主域名";
    }

    private void addressStyleDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("地址栏显示")
                .setSingleChoiceItems(new String[]{"网页标题", "主域名", "完整地址"},
                        Prefs.addressStyle(), (dialog, which) -> {
                            dialog.dismiss();
                            Prefs.get().edit().putInt("address_style", which).apply();
                            toast("地址栏已更新");
                            buildDesignerRefresh();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void backgroundDialog() {
        final String[] labels = {"跟随主题", "深色", "纯黑", "氢青", "暖白"};
        final int[] colors = {0, 0xFF101415, 0xFF000000, 0xFF00343A, 0xFFF2F7F7};
        int current = Prefs.get().getInt("home_bg", 0);
        int checked = 0;
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == current) {
                checked = i;
            }
        }
        File bgFile = new File(getFilesDir(), "home_bg.jpg");
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(bgFile.exists() ? "主页背景 · 图片使用中" : "主页背景")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    Prefs.get().edit().putInt("home_bg", colors[which]).apply();
                    buildDesignerRefresh();
                    toast("主页背景已更新");
                })
                .setPositiveButton("选择图片", (d, w) -> pickHomeBackground())
                .setNegativeButton("取消", null);
        if (bgFile.exists()) {
            builder.setNeutralButton("清除图片", (d, w) -> {
                if (bgFile.delete()) {
                    bumpHomeBgVersion();
                    buildDesignerRefresh();
                    toast("已清除图片背景");
                } else {
                    toast("清除失败");
                }
            });
        }
        builder.show();
    }

    private void pickHomeBackground() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_HOME_BG);
        } catch (Throwable t) {
            toast("无法打开图片选择器");
        }
    }

    private void bumpHomeBgVersion() {
        int version = Prefs.get().getInt("home_bg_version", 0) + 1;
        Prefs.get().edit().putInt("home_bg_version", version).apply();
    }

    private void saveHomeBackground(final Uri uri) {
        new Thread(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1440) {
                    sample *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                Bitmap bitmap;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(in, null, options);
                }
                if (bitmap == null) {
                    throw new IllegalStateException("无法读取图片");
                }
                int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
                if (max > 1440) {
                    float scale = 1440f / max;
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                            Math.max(1, Math.round(bitmap.getWidth() * scale)),
                            Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
                    if (scaled != bitmap) {
                        bitmap.recycle();
                        bitmap = scaled;
                    }
                }
                File target = new File(getFilesDir(), "home_bg.jpg");
                File tmp = new File(getFilesDir(), "home_bg.tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
                }
                bitmap.recycle();
                if (!tmp.renameTo(target)) {
                    if (target.exists() && !target.delete()) {
                        throw new IllegalStateException("无法覆盖背景");
                    }
                    if (!tmp.renameTo(target)) {
                        throw new IllegalStateException("无法保存背景");
                    }
                }
                bumpHomeBgVersion();
                runOnUiThread(() -> {
                    buildDesignerRefresh();
                    toast("主页背景已更新");
                });
            } catch (Throwable t) {
                runOnUiThread(() -> toast("设置背景失败"));
            }
        }, "home-bg-save").start();
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

    private void listSheet(String title, String summary, String[] items,
            android.content.DialogInterface.OnClickListener pick,
            String positive, Runnable onPositive,
            String neutral, Runnable onNeutral) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        if (summary != null && !summary.isEmpty()) {
            TextView head = Ui.text(this, summary, 13, R.color.on_surface_variant);
            head.setPadding(pad, 0, pad, Ui.dp(this, 6));
            box.addView(head);
        }
        final AlertDialog[] ref = new AlertDialog[1];
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(Ui.dp(this, 380), Ui.dp(this, 58 * items.length + 12))));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            list.addView(Ui.item(this, items[i], null, () -> {
                if (ref[0] != null) ref[0].dismiss();
                pick.onClick(ref[0], idx);
            }));
        }
        scroll.addView(list);
        box.addView(scroll);
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                .setTitle(title).setView(box).setNegativeButton("关闭", null);
        if (positive != null) {
            b.setPositiveButton(positive, (d, w) -> onPositive.run());
        }
        if (neutral != null) {
            b.setNeutralButton(neutral, (d, w) -> onNeutral.run());
        }
        ref[0] = b.show();
    }

    private void adblockDialog() {
        boolean enabled = Prefs.adblockEnabled();
        String[] items = {
                enabled ? "关闭广告拦截" : "开启广告拦截",
                "更新全部订阅",
                "订阅规则",
                "我的规则",
                "站点例外",
                "查看拦截日志"
        };
        String summary = (enabled ? "已启用" : "已关闭") + " · 规则 "
                + AdBlock.ruleCount() + " 条 · 已拦截 "
                + AdBlock.blockedCount() + " 次\n"
                + AdBlock.statsSummary();
        listSheet("广告拦截", summary, items, (d, which) -> {
            switch (which) {
                case 0:
                    Prefs.setAdblockEnabled(!enabled);
                    AdBlock.init(this);
                    buildCategory("general");
                    break;
                case 1:
                    toast("开始更新全部订阅…");
                    AdBlock.updateAll(this, (ok, message) -> runOnUiThread(() -> {
                        toast(message);
                        buildCategory("general");
                    }));
                    break;
                case 2:
                    subscriptionsDialog();
                    break;
                case 3:
                    userRulesDialog();
                    break;
                case 4:
                    siteExceptionsDialog();
                    break;
                default:
                    startActivity(new Intent(this, BrowserActivity.class)
                            .setAction(Intent.ACTION_VIEW)
                            .setData(Uri.parse("v://log")));
                    break;
            }
        }, null, null, null, null);
    }

    private void subscriptionsDialog() {
        List<Subscriptions.Sub> subs = Subscriptions.list(this);
        String[] labels = new String[subs.size()];
        for (int i = 0; i < subs.size(); i++) {
            Subscriptions.Sub sub = subs.get(i);
            labels[i] = (sub.enabled ? "✓ " : "· ") + sub.name
                    + (sub.rules > 0 ? "  " + sub.rules + " 条" : "");
        }
        listSheet("订阅规则", "内置基础规则始终启用。点击订阅可启停、更新或删除。",
                labels, (d, which) -> subscriptionOptions(subs.get(which)),
                "添加订阅", this::addSubscriptionDialog,
                "全部更新", () -> {
                    toast("开始更新全部订阅…");
                    AdBlock.updateAll(this, (ok, message) ->
                            runOnUiThread(() -> toast(message)));
                });
    }

    private void subscriptionOptions(Subscriptions.Sub sub) {
        List<String> actions = new ArrayList<>();
        actions.add(sub.enabled ? "停用" : "启用");
        actions.add("立即更新");
        if (!sub.builtin) {
            actions.add("删除");
        }
        String summary = sub.url + (sub.error == null || sub.error.isEmpty()
                ? "" : "\n错误：" + sub.error);
        listSheet(sub.name, summary, actions.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        final boolean enable = !sub.enabled;
                        new Thread(() -> {
                            Subscriptions.setEnabled(SettingsActivity.this, sub.id, enable);
                            Subscriptions.refresh(SettingsActivity.this);
                            runOnUiThread(() -> {
                                toast(enable ? "已启用" : "已停用");
                                subscriptionsDialog();
                            });
                        }).start();
                    } else if (which == 1) {
                        toast("开始更新…");
                        Subscriptions.updateOne(this, sub.id, (ok, message) ->
                                runOnUiThread(() -> {
                                    toast(message);
                                    subscriptionsDialog();
                                }));
                    } else {
                        new MaterialAlertDialogBuilder(this).setTitle("删除订阅")
                                .setMessage("删除 " + sub.name + " 的已下载规则？")
                                .setPositiveButton("删除", (d2, w2) -> {
                                    new Thread(() -> {
                                        Subscriptions.remove(SettingsActivity.this, sub.id);
                                        runOnUiThread(() -> {
                                            toast("已删除");
                                            subscriptionsDialog();
                                        });
                                    }).start();
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                }, null, null, null, null);
    }

    private void addSubscriptionDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        EditText name = new EditText(this);
        name.setHint("名称（可留空）");
        name.setSingleLine(true);
        EditText url = new EditText(this);
        url.setHint("规则地址（http/https）");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(name);
        box.addView(url);
        new MaterialAlertDialogBuilder(this).setTitle("添加订阅")
                .setView(box)
                .setPositiveButton("添加", (d, w) -> {
                    Subscriptions.Sub sub = Subscriptions.addCustom(this,
                            name.getText().toString(), url.getText().toString());
                    if (sub == null) {
                        toast("地址无效");
                        return;
                    }
                    toast("已添加，开始下载…");
                    Subscriptions.updateOne(this, sub.id, (ok, message) ->
                            runOnUiThread(() -> toast(message)));
                })
                .setNeutralButton("从文件导入", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/*");
                    try {
                        startActivityForResult(intent, REQ_IMPORT_RULES);
                    } catch (Throwable t) {
                        toast("没有可用的文件选择器");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void userRulesDialog() {
        List<String> rules = AdBlock.userRules(this);
        if (rules.isEmpty()) {
            toast("还没有自定义规则，可在网页菜单中“标记广告”");
            return;
        }
        listSheet("我的规则", "点击规则可删除。共 " + rules.size() + " 条。",
                rules.toArray(new String[0]), (d, which) -> {
                    String line = rules.get(which);
                    new MaterialAlertDialogBuilder(this).setTitle("删除规则")
                            .setMessage(line)
                            .setPositiveButton("删除", (d2, w2) -> {
                                AdBlock.removeUserRule(this, line);
                                toast("已删除");
                                userRulesDialog();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }, null, null, null, null);
    }

    private void siteExceptionsDialog() {
        List<String> hosts = new ArrayList<>();
        Map<String, ?> all = getSharedPreferences("h_prefs", MODE_PRIVATE).getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith("site_ad_")
                    && Boolean.FALSE.equals(entry.getValue())) {
                hosts.add(entry.getKey().substring("site_ad_".length()));
            }
        }
        if (hosts.isEmpty()) {
            toast("没有站点例外");
            return;
        }
        listSheet("站点例外", "这些站点已关闭广告拦截，点击可恢复。",
                hosts.toArray(new String[0]), (d, which) -> {
                    Prefs.get().edit().remove("site_ad_" + hosts.get(which)).apply();
                    toast("已恢复 " + hosts.get(which));
                    siteExceptionsDialog();
                }, null, null, null, null);
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
        names.put("bookmarks", "书签");
        names.put("downloads", "下载");
        names.put("share", "分享");
        names.put("search", "搜索");
        names.put("find", "页内查找");
        names.put("settings", "设置");
        names.put("ai", "AI");
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
                    if (fixed) {
                        return;
                    }
                    if (hidden.contains(id)) {
                        int visible = 0;
                        for (String name : names.keySet()) {
                            if (!hidden.contains(name)) {
                                visible++;
                            }
                        }
                        if (visible >= 8) {
                            toast("最多显示 8 个按钮");
                            return;
                        }
                        hidden.remove(id);
                    } else {
                        hidden.add(id);
                    }
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
        } else if (requestCode == REQ_IMPORT_RULES) {
            final String name = uri.getLastPathSegment() == null ? "导入规则"
                    : uri.getLastPathSegment();
            new Thread(() -> {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16384];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        if (out.size() + n > 8 * 1024 * 1024) {
                            throw new IllegalStateException("文件超过 8MB");
                        }
                        out.write(buffer, 0, n);
                    }
                    String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
                    Subscriptions.Sub sub = Subscriptions.importRules(this, name, content);
                    runOnUiThread(() -> {
                        if (sub == null) {
                            toast("规则文件为空");
                        } else {
                            toast("已导入 " + sub.rules + " 条规则");
                            subscriptionsDialog();
                        }
                    });
                } catch (Throwable t) {
                    runOnUiThread(() -> toast("导入失败"));
                }
            }).start();
        } else if (requestCode == REQ_HOME_BG) {
            saveHomeBackground(uri);
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

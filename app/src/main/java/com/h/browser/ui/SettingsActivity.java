package com.h.browser.ui;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.google.android.material.materialswitch.MaterialSwitch;
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
import com.h.browser.theme.Monet;
import com.h.browser.theme.SkinStore;
import com.h.browser.widget.BaseSearchWidget;
import com.h.browser.download.DownloadActivity;
import com.h.browser.sniffer.MediaListActivity;
import com.h.browser.sync.SyncActivity;
import com.h.browser.userscript.UserScripts;
import com.h.browser.vault.VaultActivity;

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

    private interface Saver {
        void save(String value);
    }

    private LinearLayout content;
    private LinearLayout card;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.topBar(this, "设置"));

        ScrollView scroll = new ScrollView(this);
        content = Ui.column(this);
        content.setPadding(0, 0, 0, Ui.dp(this, 32));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        build();
    }

    private void build() {
        content.removeAllViews();
        card = null;

        section("搜索与主页");
        add(Ui.item(this, "搜索引擎",
                Prefs.searchEngine(), () -> inputDialog("搜索引擎（用 %s 代表关键词）",
                Prefs.searchEngine(), Prefs::setSearchEngine)));
        add(Ui.item(this, "主页", Prefs.homepage(),
                () -> inputDialog("主页地址", Prefs.homepage(), Prefs::setHomepage)));

        section("外观");
        add(Ui.item(this, "主题模式", themeName(),
                () -> new MaterialAlertDialogBuilder(this).setTitle("主题模式")
                        .setItems(new String[]{"跟随系统", "浅色", "深色"}, (d, which) -> {
                            Prefs.get().edit().putInt("theme_mode", which).apply();
                            AppCompatDelegate.setDefaultNightMode(which == 1
                                    ? AppCompatDelegate.MODE_NIGHT_NO
                                    : which == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                            recreate();
                        }).show()));
        add(Ui.item(this, "主题色 · 莫奈", Monet.summary(), this::monetDialog));
        add(Ui.item(this, "主页皮肤", skinSummary(), this::openSkins));
        add(Ui.item(this, "工具栏布局", toolbarSummary(), this::toolbarDialog));
        add(switchRow("网页深色", "对浅色网页自动压暗", Prefs.webDark(), value -> {
            Prefs.setWebDark(value);
            Toast.makeText(this, "重新加载页面后生效", Toast.LENGTH_SHORT).show();
        }));
        add(Ui.item(this, "网页字体大小", Prefs.fontZoom() + "%", this::zoomDialog));
        add(switchRow("下拉刷新", "在页面顶部下拉重新加载",
                Prefs.pullRefresh(), Prefs::setPullRefresh));
        add(switchRow("边缘滑动", "从屏幕左右边缘滑动前进/后退",
                Prefs.edgeGesture(), Prefs::setEdgeGesture));

        section("浏览");
        add(Ui.item(this, "浏览器标识", uaName(),
                () -> new MaterialAlertDialogBuilder(this).setTitle("浏览器标识")
                        .setItems(new String[]{"Android（默认）", "桌面版", "自定义"}, (d, which) -> {
                            Prefs.setUaMode(which);
                            if (which == 2) {
                                inputDialog("自定义 UA", Prefs.customUa(), value -> {
                                    Prefs.setCustomUa(value);
                                    toastRefresh();
                                });
                            }
                        }).show()));
        add(switchRow("默认无痕", "新标签不记录历史",
                Prefs.incognitoDefault(), Prefs::setIncognitoDefault));

        section("广告拦截");
        add(switchRow("启用拦截", AdBlock.ruleCount() + " 条规则",
                Prefs.adblockEnabled(), value -> {
                    Prefs.setAdblockEnabled(value);
                    AdBlock.init(this);
                }));
        add(Ui.item(this, "订阅地址", Prefs.adblockSubscription(),
                () -> inputDialog("订阅地址", Prefs.adblockSubscription(), Prefs::setAdblockSubscription)));
        add(Ui.item(this, "更新规则", "下载并替换本地规则", () -> {
            Toast.makeText(this, "开始更新…", Toast.LENGTH_SHORT).show();
            AdBlock.updateSubscription(this, Prefs.adblockSubscription(), (ok, message) ->
                    runOnUiThread(() -> {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        build();
                    }));
        }));

        section("用户脚本");
        add(Ui.item(this, "管理脚本", UserScripts.list().size() + " 个已安装", this::manageScripts));

        section("增强能力");
        add(Ui.item(this, "AI 接口", aiSummary(), this::aiDialog));
        add(Ui.item(this, "AI 对话", "打开助手", () ->
                startActivity(new Intent(this, AiChatActivity.class))));
        add(Ui.item(this, "翻译接口", Prefs.transUrl().isEmpty() ? "未配置" : Prefs.transUrl(),
                this::transDialog));
        add(Ui.item(this, "朗读语速", Prefs.ttsSpeed() + "x",
                () -> new MaterialAlertDialogBuilder(this).setTitle("朗读语速")
                        .setItems(new String[]{"0.8x", "1.0x", "1.3x", "1.6x"}, (d, which) -> {
                            Prefs.setTtsSpeed(new float[]{0.8f, 1.0f, 1.3f, 1.6f}[which]);
                            build();
                        }).show()));
        add(Ui.item(this, "媒体嗅探", "查看拦截到的音视频", () ->
                startActivity(new Intent(this, MediaListActivity.class))));
        add(Ui.item(this, "下载管理", "系统下载任务", () ->
                startActivity(new Intent(this, DownloadActivity.class))));

        section("隐私与同步");
        add(Ui.item(this, "密码库", "本地加密保存账号", () ->
                startActivity(new Intent(this, VaultActivity.class))));
        add(Ui.item(this, "WebDAV 同步", Prefs.davUrl().isEmpty() ? "未配置" : Prefs.davUrl(),
                () -> startActivity(new Intent(this, SyncActivity.class))));

        section("数据与小组件");
        add(Ui.item(this, "导出书签", "生成 Netscape HTML 书签文件", this::exportBookmarks));
        add(Ui.item(this, "导入书签", "从 HTML 书签文件合并", this::importBookmarks));
        add(Ui.item(this, "导出历史", "生成 HTML 历史记录", this::exportHistory));
        add(Ui.item(this, "桌面小组件", "搜索 / 透明搜索 · 长按桌面添加", () ->
                new MaterialAlertDialogBuilder(this).setTitle("桌面小组件")
                        .setMessage("长按桌面空白处 → 小组件 → 氢。\n\n"
                                + "提供「搜索」与「搜索（透明）」两种，点击直接开始输入。")
                        .setPositiveButton("知道了", null).show()));

        section("关于");
        add(Ui.item(this, "氢 (H browser)", "版本 " + version(), () ->
                new MaterialAlertDialogBuilder(this).setTitle("氢 (H browser)")
                        .setMessage("轻量 Material 3 Expressive 浏览器\n"
                                + "基于系统 WebView，无遥测、无内置账号服务。\n"
                                + "广告规则来自 EasyList（CC BY-SA 3.0）。\n"
                                + "许可证：Apache-2.0")
                        .setPositiveButton("好", null)
                        .show()));
        add(Ui.item(this, "开源许可", "Apache-2.0 / EasyList CC BY-SA", () ->
                new MaterialAlertDialogBuilder(this).setTitle("开源许可")
                        .setMessage("本应用源代码采用 Apache License 2.0。\n\n"
                                + "广告拦截种子规则参考 EasyList 格式，"
                                + "EasyList 由社区维护，采用 CC BY-SA 3.0 许可。")
                        .setPositiveButton("好", null)
                        .show()));
    }

    private void section(String label) {
        content.addView(Ui.section(this, label));
        card = Ui.card(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        content.addView(card, params);
    }

    private void add(android.view.View row) {
        if (card == null) {
            section("通用");
        }
        if (card.getChildCount() > 0) {
            android.view.View divider = new android.view.View(this);
            divider.setBackgroundColor((Ui.color(this, R.color.outline_variant) & 0x00FFFFFF)
                    | 0x33000000);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            params.setMargins(Ui.dp(this, 18), 0, 0, 0);
            card.addView(divider, params);
        }
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        return mode == 1 ? "桌面版" : mode == 2 ? "自定义" : "Android（默认）";
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

    private LinearLayout switchRow(String title, String summary, boolean checked,
                                   final BooleanSaver saver) {
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.ripple(this));
        row.setPadding(Ui.dp(this, 18), Ui.dp(this, 13), Ui.dp(this, 16), Ui.dp(this, 13));
        row.setMinimumHeight(Ui.dp(this, 58));
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 15, R.color.on_surface));
        texts.addView(Ui.text(this, summary, 13, R.color.on_surface_variant));
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((buttonView, value) -> saver.save(value));
        row.addView(toggle);
        return row;
    }

    private interface BooleanSaver {
        void save(boolean value);
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
                    build();
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
                .setTitle("AI 接口（OpenAI 兼容）")
                .setView(wrapper)
                .setPositiveButton("保存", (d, w) -> {
                    Prefs.setAi(base.getText().toString().trim(), key.getText().toString().trim(),
                            model.getText().toString().trim());
                    build();
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
                    build();
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
                        build();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void importFromClipboard() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || manager.getPrimaryClip() == null
                || manager.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = manager.getPrimaryClip().getItemAt(0).coerceToText(this);
        String source = text == null ? "" : text.toString();
        if (!source.contains("==UserScript==")) {
            Toast.makeText(this, "不是有效的用户脚本", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            UserScripts.Script script = UserScripts.install(this, source);
            Toast.makeText(this, "已安装：" + script.name, Toast.LENGTH_SHORT).show();
            build();
        } catch (Throwable t) {
            Toast.makeText(this, "安装失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void monetDialog() {
        final String[] labels = {"跟随壁纸", "跟随主页皮肤", "自定义颜色", "关闭（氢青经典）"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("主题色 · 莫奈")
                .setSingleChoiceItems(labels, Prefs.monetSource(), (dialog, which) -> {
                    dialog.dismiss();
                    if (which == Monet.SOURCE_CUSTOM) {
                        customSeedDialog();
                        return;
                    }
                    Prefs.setMonetSource(which);
                    BaseSearchWidget.refresh(this);
                    recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void customSeedDialog() {
        final EditText input = Ui.input(this, "#00B8C4");
        input.setText(String.format("#%06X", Prefs.monetSeed() & 0xFFFFFF));
        int pad = Ui.dp(this, 20);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(pad, Ui.dp(this, 8), pad, 0);
        wrapper.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle("自定义主题色")
                .setView(wrapper)
                .setPositiveButton("应用", (d, w) -> {
                    int parsed;
                    try {
                        parsed = Color.parseColor(input.getText().toString().trim());
                    } catch (Throwable t) {
                        Toast.makeText(this, "颜色格式不正确，请用 #RRGGBB", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Prefs.setMonetSeed(parsed | 0xFF000000);
                    Prefs.setMonetSource(Monet.SOURCE_CUSTOM);
                    BaseSearchWidget.refresh(this);
                    recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String skinSummary() {
        SkinStore.Skin skin = SkinStore.active(this);
        return skin == null ? "经典" : skin.name + " · " + skin.author;
    }

    private void openSkins() {
        startActivity(new Intent(this, SkinActivity.class));
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
                .setTitle("工具栏布局")
                .setMessage("调整顺序，或隐藏不常用的按钮。")
                .setView(scroll)
                .setPositiveButton("保存", (d, w) -> {
                    List<String> visible = new ArrayList<>();
                    for (String id : order) {
                        if (!hidden.contains(id)) visible.add(id);
                    }
                    if (!visible.contains("menu")) visible.add("menu");
                    Prefs.setToolbarItems(String.join(",", visible));
                    build();
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
                    build();
                    Toast.makeText(this, "重新加载页面后生效", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "无法打开保存位置选择器", Toast.LENGTH_SHORT).show();
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
                    runOnUiThread(() -> Toast.makeText(this, "已导入 " + count + " 条书签",
                            Toast.LENGTH_SHORT).show());
                } catch (Throwable t) {
                    runOnUiThread(() -> Toast.makeText(this, "导入失败",
                            Toast.LENGTH_SHORT).show());
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
                    runOnUiThread(() -> Toast.makeText(this, "已导出到所选位置",
                            Toast.LENGTH_SHORT).show());
                } catch (Throwable t) {
                    runOnUiThread(() -> Toast.makeText(this, "导出失败",
                            Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }
}

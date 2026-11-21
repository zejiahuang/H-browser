package com.h.browser.ui;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
import com.h.browser.data.Prefs;
import com.h.browser.download.DownloadActivity;
import com.h.browser.sniffer.MediaListActivity;
import com.h.browser.sync.SyncActivity;
import com.h.browser.userscript.UserScripts;
import com.h.browser.vault.VaultActivity;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private interface Saver {
        void save(String value);
    }

    private LinearLayout content;

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

        content.addView(Ui.section(this, "搜索与主页"));
        content.addView(Ui.item(this, "搜索引擎",
                Prefs.searchEngine(), () -> inputDialog("搜索引擎（用 %s 代表关键词）",
                Prefs.searchEngine(), Prefs::setSearchEngine)));
        content.addView(Ui.item(this, "主页", Prefs.homepage(),
                () -> inputDialog("主页地址", Prefs.homepage(), Prefs::setHomepage)));

        content.addView(Ui.section(this, "外观"));
        content.addView(Ui.item(this, "主题模式", themeName(),
                () -> new MaterialAlertDialogBuilder(this).setTitle("主题模式")
                        .setItems(new String[]{"跟随系统", "浅色", "深色"}, (d, which) -> {
                            Prefs.get().edit().putInt("theme_mode", which).apply();
                            AppCompatDelegate.setDefaultNightMode(which == 1
                                    ? AppCompatDelegate.MODE_NIGHT_NO
                                    : which == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                            recreate();
                        }).show()));

        content.addView(Ui.section(this, "浏览"));
        content.addView(Ui.item(this, "浏览器标识", uaName(),
                () -> new MaterialAlertDialogBuilder(this).setTitle("浏览器标识")
                        .setItems(new String[]{"Android（默认）", "桌面版", "自定义"}, (d, which) -> {
                            Prefs.setUaMode(which);
                            if (which == 2) {
                                inputDialog("自定义 UA", Prefs.customUa(), value -> {
                                    Prefs.setCustomUa(value);
                                    PprefsUaRefresh();
                                });
                            }
                        }).show()));
        content.addView(switchRow("默认无痕", "新标签不记录历史",
                Prefs.incognitoDefault(), Prefs::setIncognitoDefault));

        content.addView(Ui.section(this, "广告拦截"));
        content.addView(switchRow("启用拦截", AdBlock.ruleCount() + " 条规则",
                Prefs.adblockEnabled(), value -> {
                    Prefs.setAdblockEnabled(value);
                    AdBlock.init(this);
                }));
        content.addView(Ui.item(this, "订阅地址", Prefs.adblockSubscription(),
                () -> inputDialog("订阅地址", Prefs.adblockSubscription(), Prefs::setAdblockSubscription)));
        content.addView(Ui.item(this, "更新规则", "下载并替换本地规则", () -> {
            Toast.makeText(this, "开始更新…", Toast.LENGTH_SHORT).show();
            AdBlock.updateSubscription(this, Prefs.adblockSubscription(), (ok, message) ->
                    runOnUiThread(() -> {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        build();
                    }));
        }));

        content.addView(Ui.section(this, "用户脚本"));
        content.addView(Ui.item(this, "管理脚本", UserScripts.list().size() + " 个已安装", this::manageScripts));

        content.addView(Ui.section(this, "增强能力"));
        content.addView(Ui.item(this, "AI 接口", aiSummary(),
                () -> aiDialog()));
        content.addView(Ui.item(this, "AI 对话", "打开助手", () ->
                startActivity(new Intent(this, AiChatActivity.class))));
        content.addView(Ui.item(this, "翻译接口", Prefs.transUrl().isEmpty() ? "未配置" : Prefs.transUrl(),
                () -> transDialog()));
        content.addView(Ui.item(this, "朗读语速", Prefs.ttsSpeed() + "x",
                () -> new MaterialAlertDialogBuilder(this).setTitle("朗读语速")
                        .setItems(new String[]{"0.8x", "1.0x", "1.3x", "1.6x"}, (d, which) -> {
                            Prefs.setTtsSpeed(new float[]{0.8f, 1.0f, 1.3f, 1.6f}[which]);
                            build();
                        }).show()));
        content.addView(Ui.item(this, "媒体嗅探", "查看拦截到的音视频", () ->
                startActivity(new Intent(this, MediaListActivity.class))));
        content.addView(Ui.item(this, "下载管理", "系统下载任务", () ->
                startActivity(new Intent(this, DownloadActivity.class))));

        content.addView(Ui.section(this, "隐私与同步"));
        content.addView(Ui.item(this, "密码库", "本地加密保存账号", () ->
                startActivity(new Intent(this, VaultActivity.class))));
        content.addView(Ui.item(this, "WebDAV 同步", Prefs.davUrl().isEmpty() ? "未配置" : Prefs.davUrl(),
                () -> startActivity(new Intent(this, SyncActivity.class))));

        content.addView(Ui.section(this, "关于"));
        content.addView(Ui.item(this, "氢 (H browser)", "版本 " + version(), () ->
                new MaterialAlertDialogBuilder(this).setTitle("氢 (H browser)")
                        .setMessage("轻量 Material 3 Expressive 浏览器\n"
                                + "基于系统 WebView，无遥测、无内置账号服务。\n"
                                + "广告规则来自 EasyList（CC BY-SA 3.0）。\n"
                                + "许可证：Apache-2.0")
                        .setPositiveButton("好", null)
                        .show()));
        content.addView(Ui.item(this, "开源许可", "Apache-2.0 / EasyList CC BY-SA", () ->
                new MaterialAlertDialogBuilder(this).setTitle("开源许可")
                        .setMessage("本应用源代码采用 Apache License 2.0。\n\n"
                                + "广告拦截种子规则参考 EasyList 格式，"
                                + "EasyList 由社区维护，采用 CC BY-SA 3.0 许可。")
                        .setPositiveButton("好", null)
                        .show()));
    }

    private void PprefsUaRefresh() {
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
            return "0.1.0";
        }
    }

    private LinearLayout switchRow(String title, String summary, boolean checked,
                                   final BooleanSaver saver) {
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.ripple(this));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        row.setMinimumHeight(Ui.dp(this, 56));
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 16, R.color.on_surface));
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
}

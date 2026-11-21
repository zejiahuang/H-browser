package com.h.browser.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.h.browser.R;
import com.h.browser.data.Prefs;
import com.h.browser.theme.SkinStore;
import com.h.browser.widget.BaseSearchWidget;

import java.util.List;

/** 原生的主页皮肤中心：安装、应用、删除与社区索引。 */
public class SkinActivity extends AppCompatActivity {

    private static final int REQ_SKIN = 4101;

    private LinearLayout content;
    private LinearLayout card;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.topBar(this, "外观"));

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

        section("主页皮肤");
        List<SkinStore.Skin> skins = SkinStore.list(this);
        for (SkinStore.Skin skin : skins) {
            add(skinRow(skin));
        }

        section("导入主题");
        add(Ui.item(this, "从文件导入", "支持 .zip 主题包（HTML + CSS）", this::pickFile));
        add(Ui.item(this, "从网址安装", "粘贴包含 html / css 的主题 JSON 地址", this::installFromUrl));

        section("发现主题");
        add(Ui.item(this, "索引地址", Prefs.skinIndex(), () ->
                inputDialog("社区主题索引地址", Prefs.skinIndex(), value -> {
                    Prefs.setSkinIndex(value);
                    build();
                })));
        add(Ui.item(this, "刷新列表", "按索引获取社区主题（版权归原作者）", this::refreshIndex));
        for (int i = 0; i < SkinStore.market().size(); i++) {
            add(marketRow(i, SkinStore.market().get(i)));
        }
    }

    private void section(String label) {
        content.addView(Ui.section(this, label));
        card = Ui.card(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        content.addView(card, params);
    }

    private void add(View row) {
        if (card == null) {
            section("主页皮肤");
        }
        if (card.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor((Ui.color(this, R.color.outline_variant) & 0x00FFFFFF) | 0x33000000);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            params.setMargins(Ui.dp(this, 66), 0, 0, 0);
            card.addView(divider, params);
        }
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View skinRow(final SkinStore.Skin skin) {
        boolean active = skin.id.equals(Prefs.skinId());
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.ripple(this));
        row.setMinimumHeight(Ui.dp(this, 64));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        row.addView(swatch(skin), new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        LinearLayout texts = Ui.column(this);
        LinearLayout nameRow = Ui.row(this);
        TextView name = Ui.text(this, skin.name, 15, R.color.on_surface);
        nameRow.addView(name);
        if (active) {
            TextView badge = Ui.text(this, "使用中", 11, R.color.primary);
            badge.setBackground(rounded(Ui.color(this, R.color.primary_container), 8));
            badge.setPadding(Ui.dp(this, 6), Ui.dp(this, 1), Ui.dp(this, 6), Ui.dp(this, 1));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.setMarginStart(Ui.dp(this, 8));
            nameRow.addView(badge, badgeParams);
        }
        texts.addView(nameRow);
        String summary = skin.author == null || skin.author.isEmpty() ? "本地皮肤" : skin.author;
        TextView sub = Ui.text(this, summary, 12, R.color.on_surface_variant);
        sub.setSingleLine(true);
        texts.addView(sub);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.setMarginStart(Ui.dp(this, 12));
        row.addView(texts, textsParams);

        if (!skin.builtin) {
            ImageButton delete = new ImageButton(this);
            delete.setImageResource(R.drawable.ic_delete);
            delete.setBackground(Ui.ripple(this));
            delete.setContentDescription("删除皮肤");
            delete.setPadding(Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9));
            delete.setOnClickListener(v -> confirmDelete(skin));
            row.addView(delete, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        }

        if (!active) {
            TextView apply = Ui.text(this, "应用", 13, R.color.primary);
            apply.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
            apply.setBackground(Ui.ripple(this));
            apply.setOnClickListener(v -> applySkin(skin));
            row.addView(apply);
            row.setOnClickListener(v -> applySkin(skin));
        }
        return row;
    }

    private View marketRow(final int index, SkinStore.MarketEntry entry) {
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.ripple(this));
        row.setMinimumHeight(Ui.dp(this, 60));
        row.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, entry.name, 15, R.color.on_surface));
        TextView sub = Ui.text(this, entry.author.isEmpty() ? "社区主题" : entry.author,
                12, R.color.on_surface_variant);
        sub.setSingleLine(true);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView install = Ui.text(this, "安装", 13, R.color.primary);
        install.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        install.setBackground(Ui.ripple(this));
        install.setOnClickListener(v -> installMarket(index));
        row.addView(install);
        return row;
    }

    private View swatch(SkinStore.Skin skin) {
        TextView tile = new TextView(this);
        String name = skin.name == null || skin.name.isEmpty() ? "氢" : skin.name;
        tile.setText(name.substring(0, 1));
        tile.setTextSize(17);
        tile.setGravity(Gravity.CENTER);
        tile.setTextColor(Color.WHITE);
        tile.setBackground(rounded(skin.accent, 12));
        return tile;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(Ui.dp(this, radiusDp));
        return drawable;
    }

    private void applySkin(SkinStore.Skin skin) {
        Prefs.setSkinId(skin.id);
        BaseSearchWidget.refresh(this);
        Toast.makeText(this, "已应用：" + skin.name, Toast.LENGTH_SHORT).show();
        build();
    }

    private void confirmDelete(final SkinStore.Skin skin) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除皮肤")
                .setMessage("确定删除「" + skin.name + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    SkinStore.delete(this, skin.id);
                    BaseSearchWidget.refresh(this);
                    build();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQ_SKIN);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void installFromUrl() {
        inputDialog("主题 JSON 地址", "", value -> {
            if (value.isEmpty()) {
                return;
            }
            Toast.makeText(this, "开始安装…", Toast.LENGTH_SHORT).show();
            SkinStore.installFromUrl(this, value, (ok, message) ->
                    runOnUiThread(() -> {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        if (ok) {
                            BaseSearchWidget.refresh(this);
                        }
                        build();
                    }));
        });
    }

    private void refreshIndex() {
        Toast.makeText(this, "获取索引…", Toast.LENGTH_SHORT).show();
        SkinStore.fetchIndex(this, Prefs.skinIndex(), (ok, message) ->
                runOnUiThread(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    build();
                }));
    }

    private void installMarket(int index) {
        Toast.makeText(this, "开始安装…", Toast.LENGTH_SHORT).show();
        SkinStore.installMarket(this, index, (ok, message) ->
                runOnUiThread(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    if (ok) {
                        BaseSearchWidget.refresh(this);
                    }
                    build();
                }));
    }

    private void inputDialog(String title, String value, final Ui.Saver saver) {
        final EditText input = Ui.input(this, title);
        input.setText(value);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        int pad = Ui.dp(this, 20);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(pad, Ui.dp(this, 8), pad, 0);
        wrapper.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(wrapper)
                .setPositiveButton("保存", (d, w) -> saver.save(input.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SKIN || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        SkinStore.installZip(this, uri, (ok, message) ->
                runOnUiThread(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    if (ok) {
                        BaseSearchWidget.refresh(this);
                    }
                    build();
                }));
    }
}

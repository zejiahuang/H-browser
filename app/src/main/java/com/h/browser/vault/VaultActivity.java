package com.h.browser.vault;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.h.browser.R;
import com.h.browser.ui.Ui;

import java.util.ArrayList;
import java.util.List;

public class VaultActivity extends AppCompatActivity {

    private LinearLayout content;
    private final List<VaultStore.Entry> entries = new ArrayList<>();
    private boolean fillMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fillMode = getIntent().getBooleanExtra("fill", false);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.topBar(this, fillMode ? "选择账号" : "密码库"));
        ScrollView scroll = new ScrollView(this);
        content = Ui.column(this);
        content.setPadding(0, 0, 0, Ui.dp(this, 24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        entries.clear();
        entries.addAll(VaultStore.load(this));
        if (fillMode) {
            authenticate();
        } else {
            showList();
        }
    }

    private void authenticate() {
        BiometricManager manager = BiometricManager.from(this);
        int can = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            showList();
            return;
        }
        BiometricPrompt prompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        showList();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        Toast.makeText(VaultActivity.this, "未解锁密码库", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁密码库")
                .setSubtitle("验证后选择要填充的账号")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("取消")
                .build();
        prompt.authenticate(info);
    }

    private void showList() {
        content.removeAllViews();
        if (entries.isEmpty()) {
            content.addView(Ui.item(this, "还没有保存的账号", "点击添加，数据仅保存在本机", () -> edit(null)));
        } else {
            for (int i = 0; i < entries.size(); i++) {
                final int index = i;
                final VaultStore.Entry entry = entries.get(i);
                String title = entry.title.isEmpty() ? entry.user : entry.title;
                LinearLayout row = Ui.item(this, title, entry.user, () -> {
                    if (fillMode) {
                        Intent data = new Intent();
                        data.putExtra("username", entry.user);
                        data.putExtra("password", entry.pass);
                        setResult(Activity.RESULT_OK, data);
                        finish();
                    } else {
                        edit(entry);
                    }
                });
                row.setOnLongClickListener(v -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("删除账号")
                            .setMessage(title)
                            .setPositiveButton("删除", (d, w) -> {
                                entries.remove(index);
                                persist();
                                showList();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                });
                content.addView(row);
            }
        }
        if (!fillMode) {
            content.addView(Ui.item(this, "添加账号", "AES-GCM + 系统密钥库加密", () -> edit(null)));
        }
    }

    private void edit(final VaultStore.Entry existing) {
        LinearLayout wrapper = Ui.column(this);
        int pad = Ui.dp(this, 20);
        wrapper.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final EditText title = Ui.input(this, "名称，如 GitHub");
        final EditText user = Ui.input(this, "用户名 / 邮箱");
        final EditText pass = Ui.input(this, "密码");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setTransformationMethod(PasswordTransformationMethod.getInstance());
        if (existing != null) {
            title.setText(existing.title);
            user.setText(existing.user);
            pass.setText(existing.pass);
        }
        wrapper.addView(title);
        wrapper.addView(user);
        wrapper.addView(pass);
        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "添加账号" : "编辑账号")
                .setView(wrapper)
                .setPositiveButton("保存", (d, w) -> {
                    VaultStore.Entry entry = existing == null ? new VaultStore.Entry() : existing;
                    entry.title = title.getText().toString().trim();
                    entry.user = user.getText().toString().trim();
                    entry.pass = pass.getText().toString();
                    if (existing == null) {
                        entries.add(entry);
                    }
                    persist();
                    showList();
                    Toast.makeText(VaultActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void persist() {
        try {
            VaultStore.save(this, entries);
        } catch (Throwable t) {
            Toast.makeText(this, "保存失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

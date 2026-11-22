package com.h.browser.sync;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.h.browser.R;
import com.h.browser.data.Prefs;
import com.h.browser.ui.Ui;

public class SyncActivity extends AppCompatActivity {

    private EditText url;
    private EditText user;
    private EditText pass;
    private EditText backupPass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.topBar(this, "WebDAV 同步"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = Ui.column(this);
        content.setPadding(0, 0, 0, Ui.dp(this, 24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int pad = Ui.dp(this, 16);
        LinearLayout form = Ui.column(this);
        form.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 8));
        url = Ui.input(this, "目录或文件地址，如 https://dav.example.com/backup/");
        url.setText(Prefs.davUrl());
        user = Ui.input(this, "用户名");
        user.setText(Prefs.davUser());
        pass = Ui.input(this, "密码");
        pass.setText(Prefs.davPass());
        backupPass = Ui.input(this, "备份口令（可选，用于加密备份文件）");
        backupPass.setText(Prefs.davBackupPass());
        url.setSingleLine(false);
        form.addView(url);
        form.addView(user);
        form.addView(pass);
        form.addView(backupPass);
        content.addView(form);

        content.addView(Ui.item(this, "保存并备份", "上传设置、书签、脚本与广告规则", () -> {
            save();
            Toast.makeText(this, "备份中…", Toast.LENGTH_SHORT).show();
            WebDav.backup(this, (ok, message) ->
                    runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()));
        }));
        content.addView(Ui.item(this, "从云端恢复", "合并数据，不删除本地内容", () -> {
            save();
            Toast.makeText(this, "恢复中…", Toast.LENGTH_SHORT).show();
            WebDav.restore(this, (ok, message) ->
                    runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()));
        }));

        content.addView(Ui.text(this,
                "使用 WebDAV 的 Basic Auth，建议使用支持应用密码的服务（如 Nextcloud）。"
                        + "填写备份口令后，备份文件会以 AES-GCM 加密上传；恢复加密备份时必须填写同一口令。"
                        + "数据只在你的设备与服务器之间传输。",
                13, R.color.on_surface_variant));
        content.getChildAt(content.getChildCount() - 1).setPadding(pad, Ui.dp(this, 16), pad, 0);
    }

    private void save() {
        Prefs.setDav(url.getText().toString().trim(), user.getText().toString().trim(),
                pass.getText().toString());
        Prefs.setDavBackupPass(backupPass.getText().toString());
    }
}

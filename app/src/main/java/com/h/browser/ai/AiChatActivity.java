package com.h.browser.ai;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.h.browser.R;
import com.h.browser.data.Prefs;
import com.h.browser.ui.SettingsActivity;
import com.h.browser.ui.Ui;

public class AiChatActivity extends AppCompatActivity {

    private TextView transcript;
    private EditText input;
    private AppCompatImageButton send;
    private ScrollView scroll;
    private final StringBuilder history = new StringBuilder();
    private boolean streaming;
    private boolean contextAdded;
    private String pageText = "";
    private String pageUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.topBar(this, "AI 助手"));

        scroll = new ScrollView(this);
        transcript = Ui.text(this, "", 15, R.color.on_surface);
        transcript.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 16));
        scroll.addView(transcript);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 8));
        input = Ui.input(this, "向 AI 提问…");
        input.setSingleLine(false);
        input.setMaxLines(4);
        bar.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        send = new AppCompatImageButton(this);
        send.setImageResource(R.drawable.ic_forward);
        send.setContentDescription("发送");
        send.setBackground(Ui.ripple(this));
        send.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        send.setOnClickListener(v -> submit());
        bar.addView(send, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        root.addView(bar);

        pageText = getIntent().getStringExtra("text");
        pageUrl = getIntent().getStringExtra("url");
        if (pageText == null) {
            pageText = "";
        }
        if (pageUrl == null) {
            pageUrl = "";
        }
        append("AI", "你好，我是氢浏览器的 AI 助手。可以总结当前页面、解释选中内容或回答你的问题。");
        input.requestFocus();
    }

    private void append(String who, String text) {
        if (history.length() > 0) {
            history.append("\n\n");
        }
        history.append(who).append("：").append(text);
        transcript.setText(history.toString());
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void submit() {
        if (streaming) {
            return;
        }
        final String question = input.getText().toString().trim();
        if (question.isEmpty()) {
            return;
        }
        if (Prefs.aiBase().isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("未配置 AI 接口")
                    .setMessage("请先在设置中填写 OpenAI 兼容接口地址与 API Key。")
                    .setPositiveButton("去设置", (d, w) -> {
                        startActivity(new android.content.Intent(this, SettingsActivity.class));
                        finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        input.setText("");
        append("你", question);
        final String prompt;
        if (!contextAdded && !pageText.isEmpty()) {
            String clipped = pageText.length() > 6000 ? pageText.substring(0, 6000) : pageText;
            prompt = "网页地址：" + pageUrl + "\n网页内容：\n" + clipped
                    + "\n\n用户问题：" + question;
            contextAdded = true;
        } else {
            prompt = question;
        }
        streaming = true;
        send.setEnabled(false);
        append("AI", "");
        final int start = history.lastIndexOf("AI：") + 3;
        AiClient.stream(Prefs.aiBase(), Prefs.aiKey(), Prefs.aiModel(),
                "你是氢浏览器内置助手。回答简洁、准确，优先使用中文。",
                prompt, new AiClient.Listener() {
                    @Override
                    public void onDelta(final String text) {
                        runOnUiThread(() -> {
                            history.append(text);
                            transcript.setText(history.toString());
                            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
                        });
                    }

                    @Override
                    public void onDone() {
                        runOnUiThread(() -> {
                            streaming = false;
                            send.setEnabled(true);
                        });
                    }

                    @Override
                    public void onError(final String message) {
                        runOnUiThread(() -> {
                            streaming = false;
                            send.setEnabled(true);
                            if (history.length() > start) {
                                history.append("\n（出错：" + message + "）");
                            } else {
                                history.append("（" + message + "）");
                            }
                            transcript.setText(history.toString());
                        });
                    }
                });
    }
}

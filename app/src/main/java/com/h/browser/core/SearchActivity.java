package com.h.browser.core;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

import com.h.browser.R;
import com.h.browser.data.Prefs;

public class SearchActivity extends AppCompatActivity {

    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.init(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), dp(4), 0, dp(10));
        root.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("搜索或输入网址");
        input.setTextSize(16);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        AppCompatImageButton go = new AppCompatImageButton(this);
        go.setImageResource(R.drawable.ic_search);
        go.setContentDescription("搜索");
        go.setOnClickListener(v -> submit());
        row.addView(go, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(row);

        setContentView(root);

        String query = getIntent().getStringExtra(SearchManager.QUERY);
        if (query != null) {
            input.setText(query);
        }
        input.setOnEditorActionListener((v, actionId, event) -> {
            submit();
            return true;
        });
        input.requestFocus();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void submit() {
        String query = input.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("url", UrlUtils.toUrl(query));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}

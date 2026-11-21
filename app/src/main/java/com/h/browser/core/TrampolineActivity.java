package com.h.browser.core;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class TrampolineActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent source = getIntent();
        Uri data = source == null ? null : source.getData();
        if (data != null) {
            String scheme = data.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                Intent intent = new Intent(this, BrowserActivity.class);
                intent.setData(data);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } else if (scheme != null) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, data);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Throwable ignored) {
                }
            }
        }
        finish();
    }
}

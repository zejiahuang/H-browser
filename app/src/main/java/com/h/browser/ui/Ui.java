package com.h.browser.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.h.browser.R;

public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int color(Context context, int res) {
        return ContextCompat.getColor(context, res);
    }

    public static Drawable ripple(Context context) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return ContextCompat.getDrawable(context, value.resourceId);
    }

    public static void applySystemBars(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        if (Build.VERSION.SDK_INT >= 23) {
            boolean night = (activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                    activity.getWindow(), activity.getWindow().getDecorView());
            controller.setAppearanceLightStatusBars(!night);
            controller.setAppearanceLightNavigationBars(!night);
        }
    }

    public static LinearLayout screen(Activity activity) {
        applySystemBars(activity);
        LinearLayout root = column(activity);
        root.setBackgroundColor(color(activity, R.color.surface));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int type = WindowInsetsCompat.Type.systemBars();
            v.setPadding(0, insets.getInsets(type).top, 0, insets.getInsets(type).bottom);
            return insets;
        });
        activity.setContentView(root);
        return root;
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout topBar(Activity activity, String title) {
        LinearLayout bar = row(activity);
        bar.setMinimumHeight(dp(activity, 56));
        bar.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 12), dp(activity, 4));
        AppCompatImageButton back = new AppCompatImageButton(activity);
        back.setImageResource(R.drawable.ic_back);
        back.setBackground(ripple(activity));
        back.setContentDescription("返回");
        back.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        back.setOnClickListener(v -> activity.finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        TextView text = new TextView(activity);
        text.setText(title);
        text.setTextSize(20);
        text.setTextColor(color(activity, R.color.on_surface));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(dp(activity, 8));
        bar.addView(text, params);
        return bar;
    }

    public static TextView text(Context context, String value, float sizeSp, int colorRes) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color(context, colorRes));
        view.setLineSpacing(0f, 1.25f);
        return view;
    }

    public static TextView section(Context context, String label) {
        TextView view = text(context, label, 13, R.color.primary);
        view.setPadding(dp(context, 16), dp(context, 18), dp(context, 16), dp(context, 6));
        return view;
    }

    public static LinearLayout item(Context context, String title, String summary, Runnable action) {
        LinearLayout row = row(context);
        row.setBackground(ripple(context));
        row.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        row.setMinimumHeight(dp(context, 56));
        LinearLayout texts = column(context);
        TextView name = text(context, title, 16, R.color.on_surface);
        texts.addView(name);
        if (summary != null) {
            TextView sub = text(context, summary, 13, R.color.on_surface_variant);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (action != null) {
            row.setOnClickListener(v -> action.run());
        }
        return row;
    }

    public static EditText input(Context context, String hint) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setTextSize(16);
        input.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        return input;
    }

    public static String readAsset(Context context, String name) {
        try {
            java.io.InputStream in = context.getAssets().open(name);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            in.close();
            return new String(out.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }
}

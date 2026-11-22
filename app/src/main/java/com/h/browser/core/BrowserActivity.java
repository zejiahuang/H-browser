package com.h.browser.core;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.h.browser.R;
import com.h.browser.adblock.AdBlock;
import com.h.browser.ai.AiChatActivity;
import com.h.browser.data.Backup;
import com.h.browser.data.Db;
import com.h.browser.data.Prefs;
import com.h.browser.download.DownloadActivity;
import com.h.browser.download.Downloader;
import com.h.browser.reader.ReaderActivity;
import com.h.browser.scanner.ScanActivity;
import com.h.browser.sniffer.MediaListActivity;
import com.h.browser.sniffer.MediaSniffer;
import com.h.browser.sync.SyncActivity;
import com.h.browser.translate.Translator;
import com.h.browser.tts.ReadAloudService;
import com.h.browser.ui.BottomBlurStripView;
import com.h.browser.ui.G2Path;
import com.h.browser.ui.Glass;
import com.h.browser.ui.GlassDrawable;
import com.h.browser.ui.GlassPreset;
import com.h.browser.ui.GlassRenderer;
import com.h.browser.ui.GlassShadowView;
import com.h.browser.ui.HMarkView;
import com.h.browser.ui.InternalPages;
import com.h.browser.ui.NativeBridge;
import com.h.browser.ui.ProgressiveBlurView;
import com.h.browser.ui.SettingsActivity;
import com.h.browser.userscript.GmBridge;
import com.h.browser.userscript.UserScripts;
import com.h.browser.vault.VaultActivity;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BrowserActivity extends AppCompatActivity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_SCAN = 1002;
    private static final int REQ_FILL = 1003;
    private static final int REQ_IMPORT_BM = 1005;
    private static final int REQ_EXPORT_BM = 1006;
    private static final int REQ_EXPORT_HISTORY = 1007;
    private static final int MAX_TABS = 12;
    private static final String LINK_JS = "(function(){if(window.__hLinkMenu)return;window.__hLinkMenu=1;"
            + "document.addEventListener('contextmenu',function(e){var t=e.target;"
            + "var a=t&&t.closest?t.closest('a'):null;var img=t&&t.closest?t.closest('img'):null;"
            + "if(a||img){e.preventDefault();try{HNative.showLinkMenu(a?a.href:'',img?img.src:'');}catch(x){}}},true);})();";

    private LinearLayout root;
    private LinearLayout column;
    private FrameLayout webContainer;
    private FrameLayout pageHost;
    private FrameLayout homeOverlay;
    private View pageDim;
    private ProgressiveBlurView progressiveBlur;
    private SwipeRefreshLayout refreshLayout;
    private AutoCompleteTextView address;
    private TextView titleView;
    private boolean editingAddress;
    private ImageButton clearButton;
    private ProgressBar progress;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private TextView tabCount;
    private LinearLayout toolbar;
    private LinearLayout bottomBar;
    private LinearLayout addressRow;
    private ImageButton addressAction;
    private ImageButton addressLead;
    private GlassDrawable bottomBarGlass;
    private GlassShadowView barShadow;
    private BottomBlurStripView bottomStrip;
    private LinearLayout homeBar;
    private ImageButton homeBackButton;
    private ImageButton homeForwardButton;
    private TextView homeTabCount;
    private final List<GlassDrawable> homeBarGlass = new ArrayList<>();
    private int imeLift;
    private boolean fullscreen;
    private String toolbarPref;
    private LinearLayout findBar;
    private EditText findInput;
    private TextView findCount;
    private float touchStartX;
    private float touchStartY;
    private int edgeStart;
    private boolean edgeHandled;

    private final List<Tab> tabs = new ArrayList<>();
    private int current = -1;
    private int nextTabId = 1;
    private boolean settingAddress;
    private boolean restoredState;
    private ValueCallback<Uri> legacyFileCallback;
    private ArrayAdapter<String> suggestions;

    static final class Tab {
        final int id;
        final boolean incognito;
        WebView web;
        String url = "v://home";
        String title = "新标签";

        Tab(int id, boolean incognito) {
            this.id = id;
            this.incognito = incognito;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBars();
        AdBlock.init(this);
        UserScripts.init(this);
        buildUi();
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
            restoredState = true;
        }
        if (!restoredState) {
            root.post(() -> {
                if (isFinishing() || current >= 0) {
                    return;
                }
                if (!handleIntent(getIntent())) {
                    newTab(Prefs.homepage(), Prefs.incognitoDefault());
                }
            });
        }
    }

    private void applySystemBars() {
        if (Build.VERSION.SDK_INT >= 23) {
            boolean night = (getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.setAppearanceLightStatusBars(!night);
            controller.setAppearanceLightNavigationBars(!night);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2001);
        }
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.surface));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int barsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).top, 0,
                    barsBottom);
            imeLift = Math.max(0,
                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - barsBottom);
            updateBottomBarLift();
            return insets;
        });
        setContentView(root);

        FrameLayout stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(color(R.color.surface));
        stage.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        bottomStrip = new BottomBlurStripView(this);
        stage.addView(bottomStrip, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56), Gravity.BOTTOM));
        barShadow = new GlassShadowView(this);
        barShadow.setVisibility(View.GONE);
        stage.addView(barShadow, new FrameLayout.LayoutParams(0, 0));
        addressRow = new LinearLayout(this);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable pillBg = new GradientDrawable();
        // R93 夜间地址条是白色 14% 覆盖，日间约 #EFEFEF
        pillBg.setColor(Glass.night(this) ? 0x24FFFFFF : 0x99E8E8E8);
        pillBg.setCornerRadius(dp(20));
        addressRow.setBackground(pillBg);

        addressLead = toolButton(R.drawable.ic_search, "搜索或输入网址", v -> {
            Tab tab = currentTab();
            if (tab != null && (!UrlUtils.isInternal(tab.url) || isDataPage(tab.url))) {
                showSiteInfo();
            } else {
                beginEdit();
            }
        });
        addressRow.addView(addressLead, new LinearLayout.LayoutParams(dp(40), dp(40)));

        FrameLayout titleHost = new FrameLayout(this);
        titleView = new TextView(this);
        titleView.setTextSize(16);
        titleView.setTextColor(color(R.color.on_surface_variant));
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        titleView.setPadding(dp(8), 0, dp(8), 0);
        titleView.setText("主页");
        titleView.setBackground(selectableBackground());
        titleView.setOnClickListener(v -> beginEdit());
        titleHost.addView(titleView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40), Gravity.CENTER));

        address = new AutoCompleteTextView(this);
        address.setBackground(null);
        address.setHint("搜索或输入网址");
        address.setTextSize(16);
        address.setSingleLine(true);
        address.setThreshold(1);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setPadding(dp(6), 0, dp(4), 0);
        address.setTextColor(color(R.color.on_surface));
        address.setHintTextColor(color(R.color.on_surface_variant));
        address.setVisibility(View.GONE);
        titleHost.addView(address, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40), Gravity.CENTER));
        suggestions = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>());
        address.setAdapter(suggestions);
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                search(address.getText().toString());
                address.clearFocus();
                hideKeyboard();
                return true;
            }
            return false;
        });
        address.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                address.selectAll();
                updateClearButton();
            } else if (editingAddress) {
                endEdit();
            }
        });
        address.setOnItemClickListener((parent, view, position, id) -> {
            String value = suggestions.getItem(position);
            if (value != null) {
                search(value);
                address.clearFocus();
                hideKeyboard();
            }
        });
        address.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateClearButton();
                if (settingAddress || !address.hasFocus()) {
                    return;
                }
                String query = s.toString().trim();
                suggestions.clear();
                if (query.length() > 0) {
                    for (Db.Item item : Db.get(BrowserActivity.this).searchHistory(query, 8)) {
                        suggestions.add(item.url);
                    }
                    suggestions.notifyDataSetChanged();
                }
            }
        });
        addressRow.addView(titleHost, new LinearLayout.LayoutParams(0, dp(40), 1f));

        clearButton = toolButton(R.drawable.ic_close, "清除地址", v -> {
            address.setText("");
            address.requestFocus();
            showKeyboard();
        });
        clearButton.setVisibility(View.GONE);
        addressRow.addView(clearButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        addressAction = toolButton(R.drawable.ic_scan, "扫描二维码", v -> {
            Tab tab = currentTab();
            if (tab != null && (!UrlUtils.isInternal(tab.url) || isDataPage(tab.url))) {
                reloadOrStop();
            } else {
                startScan();
            }
        });
        addressRow.addView(addressAction, new LinearLayout.LayoutParams(dp(40), dp(40)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(false);
        progress.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(color(R.color.primary)));
        }
        column.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        findBar = buildFindBar();
        column.addView(findBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webContainer = new FrameLayout(this);
        refreshLayout = new SwipeRefreshLayout(this);
        refreshLayout.setColorSchemeColors(color(R.color.primary));
        refreshLayout.setEnabled(Prefs.pullRefresh());
        refreshLayout.setOnRefreshListener(() -> {
            WebView web = currentWeb();
            if (web != null) {
                web.reload();
            } else {
                refreshLayout.setRefreshing(false);
            }
        });
        pageHost = new FrameLayout(this);
        refreshLayout.addView(pageHost, new SwipeRefreshLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webContainer.addView(refreshLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressiveBlur = new ProgressiveBlurView(this);
        progressiveBlur.setVisibility(View.GONE);
        webContainer.addView(progressiveBlur, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(108), Gravity.TOP));

        // 夜间模式与 R93 一致：网页整体压暗约 30%，主页/内部页不受影响
        pageDim = new View(this);
        pageDim.setBackgroundColor(0xFF000000);
        pageDim.setAlpha(0.3f);
        pageDim.setVisibility(View.GONE);
        webContainer.addView(pageDim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildHomeOverlay();
        column.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(0, 0, 0, 0);
        bottomBar.setClipChildren(false);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        rowParams.setMargins(dp(17), dp(Glass.addressGap()), dp(17), 0);
        bottomBar.addView(addressRow, rowParams);

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(2), 0, dp(2), 0);
        bottomBar.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        stage.addView(bottomBar, bottomBarLayoutParams());
        bottomBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (Glass.floatingToolbar() && v.getWidth() > 0 && v.getHeight() > 0) {
                G2Path.applyOutline(v, dp(Glass.outerRadius()), Glass.curve() / 100f);
            }
            syncBarShadow(v);
        });
        buildToolbar();
        applyToolbarStyle();
        buildHomeBar(stage);
        updateBarMode();
    }

    private FrameLayout.LayoutParams bottomBarLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        int margin = Glass.floatingToolbar() ? dp(Glass.toolbarMargin()) : 0;
        params.setMargins(margin, 0, margin, margin);
        return params;
    }

    /**
     * 主页三段式悬浮栏（参考 mafari SafariSplit）：后退/前进、搜索、标签+菜单
     * 各自一块玻璃，仅主页显示；网页与编辑地址时回到两行整卡。
     */
    private void buildHomeBar(ViewGroup stage) {
        homeBar = new LinearLayout(this);
        homeBar.setOrientation(LinearLayout.HORIZONTAL);
        homeBar.setGravity(Gravity.CENTER_VERTICAL);
        homeBar.setClipChildren(false);
        homeBar.setClipToPadding(false);

        LinearLayout left = buildHomeSegment();
        homeBackButton = toolButton(R.drawable.ic_back, "后退", v -> goBack());
        left.addView(homeBackButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        homeForwardButton = toolButton(R.drawable.ic_forward, "前进", v -> goForward());
        left.addView(homeForwardButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout middle = buildHomeSegment();
        middle.setClickable(true);
        middle.setContentDescription("搜索或输入网址");
        middle.setOnClickListener(v -> beginEdit());
        androidx.appcompat.widget.AppCompatImageView searchIcon =
                new androidx.appcompat.widget.AppCompatImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(color(R.color.on_surface_variant));
        searchIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        searchIcon.setPadding(dp(11), dp(11), dp(5), dp(11));
        middle.addView(searchIcon, new LinearLayout.LayoutParams(dp(40), dp(44)));
        TextView hint = new TextView(this);
        hint.setText("搜索");
        hint.setTextSize(15);
        hint.setTextColor(color(R.color.on_surface_variant));
        hint.setSingleLine(true);
        middle.addView(hint, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton scan = toolButton(R.drawable.ic_scan, "扫描二维码", v -> startScan());
        middle.addView(scan, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout right = buildHomeSegment();
        FrameLayout tabHolder = new FrameLayout(this);
        tabHolder.addView(toolButton(R.drawable.ic_tabs_count, "标签页", v -> showTabs()),
                new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        homeTabCount = new TextView(this);
        homeTabCount.setTextSize(11);
        homeTabCount.setGravity(Gravity.CENTER);
        homeTabCount.setTextColor(color(R.color.on_surface));
        homeTabCount.setIncludeFontPadding(false);
        homeTabCount.setSingleLine(true);
        tabHolder.addView(homeTabCount, new FrameLayout.LayoutParams(
                dp(24), dp(24), Gravity.CENTER));
        right.addView(tabHolder, new LinearLayout.LayoutParams(dp(44), dp(44)));
        right.addView(toolButton(R.drawable.ic_menu, "菜单", v -> showMenu()),
                new LinearLayout.LayoutParams(dp(44), dp(44)));

        homeBar.addView(left, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        middleParams.setMargins(dp(8), 0, dp(8), 0);
        homeBar.addView(middle, middleParams);
        homeBar.addView(right, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
        // 三段同时入镜会互相污染快照，捕获时互相当作隐藏项
        for (GlassDrawable g : homeBarGlass) {
            g.addCaptureHide(left);
            g.addCaptureHide(middle);
            g.addCaptureHide(right);
        }
        homeBar.setVisibility(View.GONE);
        stage.addView(homeBar, bottomBarLayoutParams());
        updateTabCount();
    }

    private LinearLayout buildHomeSegment() {
        LinearLayout segment = new LinearLayout(this);
        segment.setOrientation(LinearLayout.HORIZONTAL);
        segment.setGravity(Gravity.CENTER_VERTICAL);
        segment.setClipChildren(false);
        segment.setClipToPadding(false);
        final int radiusDp = 26;
        if (Glass.enabled() && Build.VERSION.SDK_INT >= 26) {
            GlassDrawable glass = new GlassDrawable(getWindow(), true, radiusDp, Glass.curve());
            glass.setDrawShadow(false);
            homeBarGlass.add(glass);
            segment.setBackground(glass);
        } else if (Glass.enabled()) {
            segment.setBackground(Glass.panel(this, radiusDp));
        } else {
            GradientDrawable solid = new GradientDrawable();
            solid.setColor(color(R.color.surface_container));
            solid.setCornerRadius(dp(radiusDp));
            segment.setBackground(solid);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            segment.setElevation(dp(6));
        }
        segment.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                G2Path.applyOutline(v, dp(radiusDp), Glass.curve() / 100f));
        return segment;
    }

    private void updateBarMode() {
        Tab tab = currentTab();
        boolean home = tab != null && UrlUtils.isInternal(tab.url) && !isDataPage(tab.url);
        boolean split = home && !editingAddress && !fullscreen
                && homeBar != null && Glass.floatingToolbar();
        if (homeBar != null) {
            homeBar.setVisibility(split ? View.VISIBLE : View.GONE);
        }
        if (bottomBar != null) {
            bottomBar.setVisibility(split || fullscreen ? View.GONE : View.VISIBLE);
        }
        if (barShadow != null) {
            barShadow.setVisibility(!split && !fullscreen
                    && Glass.enabled() && Glass.floatingToolbar()
                    ? View.VISIBLE : View.GONE);
        }
        if (split) {
            if (bottomStrip != null) {
                bottomStrip.clear();
            }
            if (homeBackButton != null) {
                homeBackButton.setAlpha(1f);
            }
            if (homeForwardButton != null) {
                homeForwardButton.setAlpha(1f);
            }
            updateTabCount();
            for (GlassDrawable g : homeBarGlass) {
                g.refreshAfterIdle();
            }
        } else {
            syncBottomStrip();
        }
    }

    private void applyToolbarStyle() {
        if (bottomBar == null || toolbar == null) {
            return;
        }
        bottomBar.setLayoutParams(bottomBarLayoutParams());
        int radiusDp = Glass.floatingToolbar() ? Glass.outerRadius() : 0;
        float curve = Glass.curve() / 100f;
        boolean glass = Glass.enabled() && Glass.floatingToolbar();
        boolean snapshot = glass && Build.VERSION.SDK_INT >= 26;
        if (bottomBarGlass != null) {
            bottomBarGlass.release();
            bottomBarGlass = null;
        }
        if (snapshot) {
            bottomBarGlass = new GlassDrawable(getWindow(), true, radiusDp, Glass.curve());
            bottomBarGlass.addCaptureHide(barShadow);
            bottomBarGlass.addCaptureHide(bottomStrip);
            bottomBarGlass.setOnBuilt(() -> syncBarShadow(bottomBar));
            bottomBar.setBackground(bottomBarGlass);
        } else if (glass) {
            bottomBar.setBackground(Glass.panel(this, radiusDp));
        } else {
            GradientDrawable solid = new GradientDrawable();
            solid.setColor(color(R.color.surface_container));
            solid.setCornerRadius(dp(radiusDp));
            bottomBar.setBackground(solid);
        }
        if (barShadow != null) {
            barShadow.setVisibility(glass ? View.VISIBLE : View.GONE);
            if (glass) {
                GlassPreset p = Glass.preset();
                barShadow.setShape(radiusDp, Glass.curve());
                barShadow.setShadow(p.shadowAlpha, p.shadowSpreadDp + 8f, p.shadowSigmaDp);
                if (bottomBarGlass != null) {
                    bottomBarGlass.setDrawShadow(false);
                }
                bottomBar.post(() -> syncBarShadow(bottomBar));
            }
        }
        if (Build.VERSION.SDK_INT >= 21) {
            // 快照玻璃自带离屏阴影，关掉系统 elevation 避免双层叠加
            bottomBar.setElevation(dp(snapshot ? 0 : (glass ? 6 : 0)));
            bottomBar.setClipToOutline(false);
        }
        final int outlineRadius = radiusDp;
        final float outlineCurve = curve;
        bottomBar.post(() -> G2Path.applyOutline(bottomBar, dp(outlineRadius), outlineCurve));
        toolbar.setBackground(null);
        if (Build.VERSION.SDK_INT >= 21) {
            toolbar.setElevation(0);
        }
        bottomBar.invalidate();
    }

    private void syncBarShadow(View bar) {
        syncBottomStrip();
        if (barShadow == null || barShadow.getVisibility() != View.VISIBLE
                || bar.getWidth() < 2 || bar.getHeight() < 2) {
            return;
        }
        float spread = barShadow.spreadPx();
        int w = bar.getWidth() + Math.round(spread * 2);
        int h = bar.getHeight() + Math.round(spread * 2 + dp(6));
        int left = Math.round(bar.getLeft() - spread);
        int top = Math.round(bar.getTop() - spread);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) barShadow.getLayoutParams();
        if (lp.width != w || lp.height != h || lp.leftMargin != left
                || lp.topMargin != top) {
            lp.width = w;
            lp.height = h;
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.leftMargin = left;
            lp.topMargin = top;
            barShadow.setLayoutParams(lp);
        }
        barShadow.setTranslationY(bar.getTranslationY());
        if (bottomBarGlass != null && bottomBarGlass.backdrop() != null && Glass.progressive()) {
            // R93 的卡片阴影是压暗后的模糊页面，白天轻微、夜间更实
            barShadow.setBackdrop(bottomBarGlass.backdrop(),
                    spread - bottomBarGlass.offsetX(),
                    spread - bottomBarGlass.offsetY(),
                    Glass.night(this) ? 0.34f : 0.18f);
        } else {
            barShadow.clearBackdrop();
        }
    }

    /**
     * 底栏下方的模糊垫层（R93 导航区）：同一张窗口快照，压暗后铺满底部 23dp。
     * 快照已含夜间 30% 压暗，因此昼夜暗化系数接近，只差一点对比。
     */
    private void syncBottomStrip() {
        if (bottomStrip == null) {
            return;
        }
        boolean show = bottomBar != null && bottomBarGlass != null
                && bottomBarGlass.backdrop() != null && Glass.progressive()
                && bottomBar.getVisibility() == View.VISIBLE;
        if (!show) {
            bottomStrip.clear();
            return;
        }
        if (bottomStrip.getHeight() < 2) {
            // 首次布局尚未完成，等下一帧再对位
            bottomStrip.post(this::syncBottomStrip);
            return;
        }
        bottomStrip.setBackdrop(bottomBarGlass.backdrop(),
                bottomBar.getLeft() - bottomBarGlass.offsetX() - bottomStrip.getLeft(),
                bottomBar.getTop() - bottomBarGlass.offsetY() - bottomStrip.getTop(),
                Glass.night(this));
    }

    private void updateBottomBarLift() {
        if (bottomBar == null) {
            return;
        }
        float target = editingAddress ? -imeLift : 0f;
        if (homeBar != null && homeBar.getTranslationY() != target) {
            homeBar.animate().translationY(target).setDuration(180).start();
        }
        if (bottomBar.getTranslationY() != target) {
            bottomBar.animate().translationY(target).setDuration(180).start();
            if (barShadow != null) {
                barShadow.animate().translationY(target).setDuration(180).start();
            }
            if (bottomStrip != null) {
                bottomStrip.animate().translationY(target).setDuration(180).start();
            }
        }
    }

    private void buildHomeOverlay() {
        homeOverlay = new FrameLayout(this);
        homeOverlay.setBackgroundColor(color(R.color.surface));
        homeOverlay.setClickable(true);

        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER_HORIZONTAL);

        HMarkView logo = new HMarkView(this);
        group.addView(logo, new LinearLayout.LayoutParams(dp(78), dp(65)));

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable outline =
                new android.graphics.drawable.GradientDrawable();
        outline.setColor(Glass.night(this) ? 0xFF1C1C1C : 0xFFFFFFFF);
        outline.setStroke(Math.max(1, dp(1)),
                Glass.night(this) ? 0xFF3A4144 : 0xFFCCD3D4);
        outline.setCornerRadius(dp(23));
        pill.setBackground(outline);
        pill.setClickable(true);
        pill.setContentDescription("搜索或输入网址");
        pill.setOnClickListener(v -> beginEdit());
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        pillParams.setMargins(dp(24), dp(38), dp(24), 0);
        group.addView(pill, pillParams);

        FrameLayout.LayoutParams groupParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        homeOverlay.addView(group, groupParams);
        group.setTranslationY(-dp(112));
        // 首次冷启动 System WebView 初始化会阻塞主线程，先让原生主页层可见，
        // 等标签选定后再由 updateHomeOverlay 决定显隐。
        homeOverlay.setVisibility(View.VISIBLE);
        webContainer.addView(homeOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void updateHomeOverlay() {
        if (homeOverlay == null) {
            return;
        }
        Tab tab = currentTab();
        boolean show = tab == null || "v://home".equals(tab.url);
        int homeBg = Prefs.get().getInt("home_bg", 0);
        homeOverlay.setBackgroundColor(homeBg != 0 ? homeBg : color(R.color.surface));
        boolean page = tab != null && !UrlUtils.isInternal(tab.url);
        boolean dim = !show && page && Glass.night(this);
        if (pageDim != null) {
            pageDim.setVisibility(dim ? View.VISIBLE : View.GONE);
        }
        homeOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        updateProgressiveBlur();
    }

    private void updateProgressiveBlur() {
        if (progressiveBlur == null) {
            return;
        }
        // R93 没有顶栏，顶部渐进模糊失去了依附对象；「渐进式模糊」改为
        // 控制底栏外溢的模糊阴影（见 syncBarShadow），这里只保留清理。
        if (progressiveBlur.getVisibility() != View.GONE) {
            progressiveBlur.setVisibility(View.GONE);
        }
        progressiveBlur.detach();
        if (bottomBarGlass != null) {
            bottomBarGlass.refresh();
        }
    }

    private void buildToolbar() {
        if (toolbar == null) {
            return;
        }
        toolbarPref = Prefs.toolbarItems();
        toolbar.removeAllViews();
        backButton = null;
        forwardButton = null;
        boolean menu = false;
        String[] items = Prefs.toolbarItems().split(",");
        for (String item : items) {
            String id = item.trim();
            switch (id) {
                case "back":
                    backButton = toolButton(R.drawable.ic_back, "后退", v -> goBack());
                    toolbar.addView(backButton, weightParams());
                    break;
                case "forward":
                    forwardButton = toolButton(R.drawable.ic_forward, "前进", v -> goForward());
                    toolbar.addView(forwardButton, weightParams());
                    break;
                case "home":
                    toolbar.addView(toolButton(R.drawable.ic_home, "主页",
                            v -> handleInternal("v://home")), weightParams());
                    break;
                case "refresh":
                    toolbar.addView(toolButton(R.drawable.ic_refresh, "刷新",
                            v -> reloadOrStop()), weightParams());
                    break;
                case "tabs": {
                    FrameLayout holder = new FrameLayout(this);
                    holder.setLayoutParams(weightParams());
                    ImageButton tabsButton = toolButton(R.drawable.ic_tabs_count, "标签页",
                            v -> showTabs());
                    holder.addView(tabsButton, new FrameLayout.LayoutParams(
                            dp(44), dp(44), Gravity.CENTER));
                    tabCount = new TextView(this);
                    tabCount.setTextSize(11);
                    tabCount.setGravity(Gravity.CENTER);
                    tabCount.setTextColor(color(R.color.on_surface));
                    tabCount.setIncludeFontPadding(false);
                    tabCount.setSingleLine(true);
                    holder.addView(tabCount, new FrameLayout.LayoutParams(
                            dp(24), dp(24), Gravity.CENTER));
                    toolbar.addView(holder);
                    updateTabCount();
                    break;
                }
                case "newtab":
                    toolbar.addView(toolButton(R.drawable.ic_add, "新建标签",
                            v -> newTab(Prefs.homepage(), false)), weightParams());
                    break;
                case "menu":
                    toolbar.addView(toolButton(R.drawable.ic_menu, "菜单",
                            v -> showMenu()), weightParams());
                    menu = true;
                    break;
                default:
                    break;
            }
        }
        if (!menu) {
            toolbar.addView(toolButton(R.drawable.ic_more, "菜单", v -> showMenu()), weightParams());
        }
        updateUi();
    }

    private LinearLayout buildFindBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(color(R.color.surface_container));
        bar.setPadding(dp(12), dp(4), dp(4), dp(4));
        bar.setVisibility(View.GONE);
        findInput = new EditText(this);
        findInput.setHint("页内查找");
        findInput.setTextSize(15);
        findInput.setSingleLine(true);
        findInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        findInput.setBackground(null);
        findInput.setTextColor(color(R.color.on_surface));
        findInput.setHintTextColor(color(R.color.on_surface_variant));
        bar.addView(findInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        findCount = new TextView(this);
        findCount.setTextSize(12);
        findCount.setTextColor(color(R.color.on_surface_variant));
        bar.addView(findCount);
        bar.addView(toolButton(R.drawable.ic_back, "上一处", v -> findStep(false)),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        bar.addView(toolButton(R.drawable.ic_forward, "下一处", v -> findStep(true)),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        bar.addView(toolButton(R.drawable.ic_close, "关闭查找", v -> hideFindBar()),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        findInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                WebView web = currentWeb();
                if (web == null) {
                    return;
                }
                String query = s.toString();
                if (query.isEmpty()) {
                    web.clearMatches();
                    findCount.setText("");
                } else {
                    web.findAllAsync(query);
                }
            }
        });
        findInput.setOnEditorActionListener((v, actionId, event) -> {
            findStep(true);
            return true;
        });
        return bar;
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, dp(48), 1f);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int color(int res) {
        return ContextCompat.getColor(this, res);
    }

    private Drawable selectableBackground() {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);
        if (value.resourceId != 0) {
            return ContextCompat.getDrawable(this, value.resourceId);
        }
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return ContextCompat.getDrawable(this, value.resourceId);
    }

    private ImageButton toolButton(int icon, String description, View.OnClickListener listener) {
        androidx.appcompat.widget.AppCompatImageButton button =
                new androidx.appcompat.widget.AppCompatImageButton(this);
        button.setImageResource(icon);
        button.setBackground(selectableBackground());
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        if (Build.VERSION.SDK_INT >= 21) {
            int normal = color(R.color.on_surface);
            int disabled = (normal & 0x00FFFFFF) | 0x61000000;
            button.setImageTintList(new ColorStateList(
                    new int[][]{{-android.R.attr.state_enabled}, new int[]{}},
                    new int[]{disabled, normal}));
        }
        button.setOnClickListener(listener);
        return button;
    }

    private boolean handleIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        String action = intent.getAction();
        String url = null;
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            url = intent.getData().toString();
        } else if (Intent.ACTION_WEB_SEARCH.equals(action)) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            if (query != null) {
                url = UrlUtils.toUrl(query);
            }
        } else {
            String extra = intent.getStringExtra("url");
            if (extra != null && !extra.isEmpty()) {
                url = extra;
            }
        }
        if (url == null) {
            return false;
        }
        if (current >= 0) {
            loadUrl(currentTab(), url);
        } else {
            newTab(url, Prefs.incognitoDefault());
        }
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private Tab currentTab() {
        return current >= 0 && current < tabs.size() ? tabs.get(current) : null;
    }

    private WebView currentWeb() {
        Tab tab = currentTab();
        return tab == null ? null : tab.web;
    }

    private Tab tabFor(WebView view) {
        for (Tab tab : tabs) {
            if (tab.web == view) {
                return tab;
            }
        }
        return null;
    }

    private void newTab(String url, boolean incognito) {
        Tab tab = newEmptyTab(incognito);
        if (tab == null) {
            return;
        }
        loadUrl(tab, url == null || url.isEmpty() ? Prefs.homepage() : url);
        tab.web.postDelayed(() -> {
            if (tab.web.getContentHeight() == 0 && UrlUtils.isInternal(tab.url)
                    && !isDataPage(tab.url)) {
                handleInternal(tab, tab.url);
            }
        }, 500);
    }

    private Tab newEmptyTab(boolean incognito) {
        while (tabs.size() >= MAX_TABS) {
            int victim = current == 0 ? 1 : 0;
            closeTab(victim, false);
        }
        Tab tab = new Tab(nextTabId++, incognito);
        tab.web = createWebView(tab);
        tabs.add(tab);
        selectTab(tabs.size() - 1);
        return tab;
    }

    private WebView createWebView(final Tab tab) {
        WebView web = new ScrollWebView(this);
        ((ScrollWebView) web).setScrollListener(y -> {
            if (progressiveBlur != null) {
                progressiveBlur.onPageScroll();
            }
            if (bottomBarGlass != null) {
                bottomBarGlass.refreshAfterIdle();
            }
        });
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setAllowFileAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setTextZoom(Prefs.fontZoom());
        settings.setGeolocationEnabled(false);
        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(true);
        }
        if (Prefs.webDark() || Glass.night(this)) {
            try {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true);
            } catch (Throwable ignored) {
            }
        }
        int uaMode = Prefs.uaMode();
        if (uaMode == 1) {
            settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0 Safari/537.36 H/0.1");
        } else if (uaMode == 2 && !Prefs.customUa().isEmpty()) {
            settings.setUserAgentString(Prefs.customUa());
        }
        web.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(color(R.color.surface));
        if (Build.VERSION.SDK_INT >= 21) {
            web.setWebViewClient(new HClient21());
        } else {
            web.setWebViewClient(new HClient());
        }
        web.setWebChromeClient(new HChrome());
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                Downloader.enqueue(BrowserActivity.this, url, userAgent, contentDisposition, mimeType);
            }
        });
        web.addJavascriptInterface(new GmBridge(this, web), "HGM");
        web.addJavascriptInterface(new NativeBridge(this), "HNative");
        web.setOnLongClickListener(v -> showLinkMenu(web));
        web.setFindListener((active, numberOfMatches, done) -> {
            if (findBar == null || findBar.getVisibility() != View.VISIBLE) {
                return;
            }
            if (numberOfMatches == 0) {
                findCount.setText("无结果");
            } else {
                findCount.setText((active + 1) + "/" + numberOfMatches);
            }
        });
        return web;
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        if (current >= 0 && current < tabs.size()) {
            WebView old = tabs.get(current).web;
            if (old.getParent() instanceof ViewGroup) {
                ((ViewGroup) old.getParent()).removeView(old);
            }
            old.onPause();
        }
        current = index;
        Tab tab = tabs.get(index);
        if (tab.web.getParent() instanceof ViewGroup) {
            ((ViewGroup) tab.web.getParent()).removeView(tab.web);
        }
        pageHost.removeAllViews();
        pageHost.addView(tab.web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tab.web.onResume();
        progress.setVisibility(View.GONE);
        updateUi();
        address.clearFocus();
        updateProgressiveBlur();
    }

    private void closeTab(int index, boolean switchIfCurrent) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        Tab tab = tabs.remove(index);
        if (tab.web.getParent() != null) {
            ((ViewGroup) tab.web.getParent()).removeView(tab.web);
        }
        tab.web.stopLoading();
        tab.web.destroy();
        if (tabs.isEmpty()) {
            current = -1;
            newTab(Prefs.homepage(), Prefs.incognitoDefault());
            return;
        }
        if (index < current) {
            current--;
        } else if (index == current) {
            current = -1;
            if (switchIfCurrent) {
                selectTab(Math.min(index, tabs.size() - 1));
            } else {
                selectTab(Math.max(0, Math.min(index, tabs.size() - 1)));
            }
        } else if (current < 0) {
            selectTab(0);
        }
    }

    private void loadUrl(Tab tab, String url) {
        if (tab == null) {
            return;
        }
        if (url == null || url.isEmpty()) {
            url = Prefs.homepage();
        }
        if (UrlUtils.isInternal(url)) {
            handleInternal(tab, url);
            return;
        }
        applySiteSettings(tab.web, url);
        tab.url = url;
        tab.web.loadUrl(url);
        if (tab == currentTab()) {
            updateAddress();
            updateUi();
        }
    }

    private void handleInternal(String url) {
        handleInternal(currentTab(), url);
    }

    private void handleInternal(Tab tab, String url) {
        if (tab == null || isDataPage(url)) {
            return;
        }
        String path = url.substring(4);
        int slash = path.indexOf('/');
        if (slash >= 0) {
            path = path.substring(0, slash);
        }
        switch (path) {
            case "tabs":
                showTabs();
                return;
            case "search":
                address.requestFocus();
                showKeyboard();
                return;
            case "settings":
                openSettings();
                return;
            case "downloader":
            case "downloads":
                openDownloads();
                return;
            case "vault":
                openVault();
                return;
            case "sync":
                openSync();
                return;
            case "scanner":
                startScan();
                return;
            case "ai":
                startActivity(new Intent(this, AiChatActivity.class));
                return;
            case "media":
                startActivity(new Intent(this, MediaListActivity.class));
                return;
            default:
                break;
        }
        tab.url = url;
        tab.title = "";
        if (tab.web.getWidth() == 0 || tab.web.getHeight() == 0) {
            final String pending = url;
            tab.web.getViewTreeObserver().addOnPreDrawListener(
                    new android.view.ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            android.view.ViewTreeObserver vto = tab.web.getViewTreeObserver();
                            if (vto.isAlive()) {
                                vto.removeOnPreDrawListener(this);
                            }
                            if (tab.web.getWidth() > 0) {
                                handleInternal(tab, pending);
                            }
                            return true;
                        }
                    });
            updateAddress();
            return;
        }
        tab.web.loadDataWithBaseURL(url, InternalPages.render(this, url), "text/html", "utf-8", null);
        scheduleRepaint(tab.web);
        progress.setVisibility(View.GONE);
        updateAddress();
    }

    private void scheduleRepaint(final WebView web) {
        for (long delay : new long[]{350, 1600}) {
            web.postDelayed(() -> forceRepaint(web), delay);
        }
    }

    private void forceRepaint(WebView web) {
        if (web.getParent() == null) {
            return;
        }
        web.setVisibility(View.INVISIBLE);
        web.post(() -> {
            web.invalidate();
            web.postInvalidateOnAnimation();
            web.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            updateHomeOverlay();
            Tab tab = currentTab();
            if (tab != null && UrlUtils.isInternal(tab.url)) {
                forceRepaint(tab.web);
            }
        }
    }

    public void reloadInternal() {
        Tab tab = currentTab();
        if (tab == null) {
            return;
        }
        if (UrlUtils.isInternal(tab.url) && !isDataPage(tab.url)) {
            handleInternal(tab, tab.url);
        } else {
            tab.web.reload();
        }
    }

    public void refreshCurrentIfInternal() {
        Tab tab = currentTab();
        if (tab != null && UrlUtils.isInternal(tab.url) && !isDataPage(tab.url)) {
            handleInternal(tab, tab.url);
        }
    }

    public void search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        loadUrl(currentTab(), UrlUtils.toUrl(query));
        updateAddress();
    }

    public void openUrl(String url, boolean newTab) {
        if (url == null || url.isEmpty()) {
            return;
        }
        if (newTab) {
            newTab(url, Prefs.incognitoDefault());
        } else {
            loadUrl(currentTab(), url);
        }
    }

    private void goBack() {
        WebView web = currentWeb();
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else if (tabs.size() > 1) {
            closeTab(current, true);
        }
    }

    private void goForward() {
        WebView web = currentWeb();
        if (web != null && web.canGoForward()) {
            web.goForward();
        }
    }

    private void reloadOrStop() {
        WebView web = currentWeb();
        if (web == null) {
            return;
        }
        Tab tab = currentTab();
        if (tab != null && UrlUtils.isInternal(tab.url) && !isDataPage(tab.url)) {
            handleInternal(tab, tab.url);
        } else {
            web.reload();
        }
    }

    private void updateAddress() {
        Tab tab = currentTab();
        if (tab == null) {
            return;
        }
        boolean home = UrlUtils.isInternal(tab.url) && !isDataPage(tab.url);
        updateBarMode();
        if (titleView != null) {
            String title = tab.title;
            if (title == null || title.isEmpty() || title.equals(tab.url)) {
                title = home ? "主页" : tab.url;
            }
            if (tab.incognito) {
                title = "无痕 · " + title;
            }
            titleView.setText(title);
        }
        if (addressLead != null) {
            addressLead.setImageResource(home ? R.drawable.ic_search : R.drawable.ic_shield_check);
            addressLead.setContentDescription(home ? "搜索或输入网址" : "网站信息");
        }
        if (addressAction != null) {
            addressAction.setImageResource(home ? R.drawable.ic_scan : R.drawable.ic_refresh);
            addressAction.setContentDescription(home ? "扫描二维码" : "刷新");
        }
        updateHomeOverlay();
        if (editingAddress || address.hasFocus()) {
            updateClearButton();
            return;
        }
        String url = tab.url;
        settingAddress = true;
        address.setText(home ? "" : url);
        address.setSelection(address.getText().length());
        settingAddress = false;
        updateClearButton();
    }

    private void updateTabCount() {
        String count = String.valueOf(Math.max(1, tabs.size()));
        if (tabCount != null) {
            tabCount.setText(count);
        }
        if (homeTabCount != null) {
            homeTabCount.setText(count);
        }
    }

    private void updateClearButton() {
        if (clearButton == null) {
            return;
        }
        boolean show = editingAddress && address.getText().length() > 0;
        clearButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateUi() {
        Tab tab = currentTab();
        if (tab == null || tab.web == null) {
            return;
        }
        if (backButton != null) {
            backButton.setAlpha(1f);
        }
        if (forwardButton != null) {
            forwardButton.setAlpha(1f);
        }
        if (homeBackButton != null) {
            homeBackButton.setAlpha(1f);
        }
        if (homeForwardButton != null) {
            homeForwardButton.setAlpha(1f);
        }
        updateTabCount();
        updateAddress();
    }

    private void injectCosmetic(WebView web, String url) {
        if (UrlUtils.isInternal(url) || !AdBlock.isEnabled() || !siteAdAllowed(url)) {
            return;
        }
        String css = AdBlock.cosmeticCss(url);
        if (!css.isEmpty()) {
            web.evaluateJavascript(AdBlock.injectCssScript(css), null);
        }
    }

    private void showError(String failingUrl) {
        Tab tab = currentTab();
        if (tab == null || tab.web == null) {
            return;
        }
        String safe = UrlUtils.escapeHtml(failingUrl);
        String html = "<!doctype html><html><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:system-ui;background:#0E1415;color:#DEE4E4;margin:0;display:flex;"
                + "align-items:center;justify-content:center;height:100vh;text-align:center;padding:24px}"
                + "h1{font-size:26px;margin:0 0 8px}p{color:#BEC8C9;font-size:14px;word-break:break-all}"
                + "button{margin-top:18px;border:0;border-radius:20px;padding:12px 22px;background:#4FD8E4;"
                + "color:#00363B;font-size:15px;font-weight:700}</style></head><body><div>"
                + "<h1>无法打开这个页面</h1><p>" + safe + "</p>"
                + "<button onclick='HNative.reloadPage()'>重试</button></div></body></html>";
        tab.web.loadDataWithBaseURL("v://error", html, "text/html", "utf-8", null);
    }

    private static final class MenuAction {
        final int icon;
        final String label;
        final boolean enabled;
        final Runnable task;

        MenuAction(int icon, String label, boolean enabled, Runnable task) {
            this.icon = icon;
            this.label = label;
            this.enabled = enabled;
            this.task = task;
        }
    }

    private void showMenu() {
        if (editingAddress) {
            hideKeyboard();
            endEdit();
        }
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        float radius = dp(Glass.outerRadius());
        final GlassDrawable menuGlass;
        if (Glass.enabled() && Build.VERSION.SDK_INT >= 26) {
            menuGlass = new GlassDrawable(getWindow(), true, Glass.outerRadius(), Glass.curve());
            menuGlass.setTintAlpha(Glass.preset().menuDay, Glass.preset().menuNight);
            menuGlass.setDrawShadow(false);
            sheet.setBackground(menuGlass);
        } else {
            menuGlass = null;
            GradientDrawable sheetBg = Glass.enabled()
                    ? Glass.panel(this, Glass.outerRadius())
                    : new GradientDrawable();
            if (!Glass.enabled()) {
                sheetBg.setColor(color(R.color.surface_container));
            }
            sheetBg.setCornerRadii(new float[]{radius, radius, radius, radius,
                    radius, radius, radius, radius});
            sheet.setBackground(sheetBg);
        }
        sheet.setPadding(dp(8), dp(14), dp(8), 0);

        final List<List<MenuAction>> pages = buildMenuPages();
        final int[] currentPage = {0};

        final LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        sheet.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, dp(2), 0, dp(2));
        final List<View> dotViews = new ArrayList<>();

        // 挂在每个格子上：格子可点击会吞掉父容器的滑动事件
        final float[] swipeStart = new float[2];
        final Runnable[] closeRef = new Runnable[1];
        final View.OnTouchListener[] swipeRef = new View.OnTouchListener[1];
        swipeRef[0] = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                swipeStart[0] = event.getX();
                swipeStart[1] = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - swipeStart[0];
                float dy = event.getY() - swipeStart[1];
                if (dy > dp(64) && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                    if (closeRef[0] != null) {
                        closeRef[0].run();
                    }
                    return true;
                }
                if (Math.abs(dx) > dp(48) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    int next = currentPage[0] + (dx < 0 ? 1 : -1);
                    if (next >= 0 && next < pages.size()) {
                        currentPage[0] = next;
                        renderMenuPage(grid, dotViews, pages.get(next), next, dialog, swipeRef[0]);
                    }
                    return true;
                }
            }
            return false;
        };
        final View.OnTouchListener swipeListener = swipeRef[0];
        for (int i = 0; i < pages.size(); i++) {
            FrameLayout holder = new FrameLayout(this);
            holder.setBackground(selectableBackground());
            holder.setContentDescription("菜单第" + (i + 1) + "页");
            View dot = new View(this);
            holder.addView(dot, new FrameLayout.LayoutParams(dp(5), dp(5), Gravity.CENTER));
            final int index = i;
            holder.setOnTouchListener(swipeRef[0]);
            holder.setOnClickListener(v -> {
                currentPage[0] = index;
                renderMenuPage(grid, dotViews, pages.get(index), index, dialog, swipeListener);
            });
            dots.addView(holder, new LinearLayout.LayoutParams(dp(17), dp(17)));
            dotViews.add(dot);
        }
        sheet.addView(dots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dots.setOnTouchListener(swipeRef[0]);
        dots.setVisibility(pages.size() > 1 ? View.VISIBLE : View.GONE);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        footer.setPadding(dp(12), 0, dp(16), dp(2));
        footer.addView(toolButton(R.drawable.ic_power, "退出", v -> {
            dialog.dismiss();
            finish();
        }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        chevronParams.leftMargin = dp(12);
        footer.addView(toolButton(R.drawable.ic_chevron_down, "收起菜单",
                v -> {
                    if (closeRef[0] != null) {
                        closeRef[0].run();
                    }
                }), chevronParams);
        sheet.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        footer.setOnTouchListener(swipeRef[0]);

        renderMenuPage(grid, dotViews, pages.get(0), 0, dialog, swipeListener);

        FrameLayout floating = new FrameLayout(this);
        floating.setClipChildren(false);
        floating.setClipToPadding(false);
        final GlassShadowView menuShadow;
        if (menuGlass != null) {
            menuShadow = new GlassShadowView(this);
            GlassPreset p = Glass.preset();
            menuShadow.setShape(Glass.outerRadius(), Glass.curve());
            menuShadow.setShadow(p.shadowAlpha, p.shadowSpreadDp + 8f, p.shadowSigmaDp);
            floating.addView(menuShadow, new FrameLayout.LayoutParams(0, 0));
        } else {
            menuShadow = null;
        }
        View anchorBar = homeBar != null && homeBar.getVisibility() == View.VISIBLE
                ? homeBar : bottomBar;
        int barHeight = anchorBar == null ? dp(200) : anchorBar.getHeight();
        int barMargin = Glass.floatingToolbar() ? dp(Glass.toolbarMargin()) : 0;
        final int sidePadding = dp(14);
        floating.setPadding(sidePadding, 0, sidePadding, 0);
        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        // R93 菜单底边距底栏顶部约 15dp
        sheetParams.bottomMargin = barHeight + barMargin + dp(15);
        floating.addView(sheet, sheetParams);
        final Runnable closeMenu = () -> {
            if (!dialog.isShowing()) {
                return;
            }
            sheet.animate().translationY(dp(26)).alpha(0f).setDuration(160)
                    .withEndAction(dialog::dismiss).start();
            if (menuShadow != null) {
                menuShadow.animate().translationY(dp(26)).alpha(0f).setDuration(160).start();
            }
        };
        closeRef[0] = closeMenu;
        floating.setOnClickListener(v -> closeMenu.run());
        if (Build.VERSION.SDK_INT >= 21) {
            sheet.setElevation(dp(menuGlass != null ? 0 : 24));
            sheet.setClipToOutline(false);
        }
        final Runnable placeMenuShadow = () -> {
            if (menuShadow == null || sheet.getWidth() <= 0 || sheet.getHeight() <= 0) {
                return;
            }
            float spread = menuShadow.spreadPx();
            int sw = sheet.getWidth() + Math.round(spread * 2);
            int sh = sheet.getHeight() + Math.round(spread * 2 + dp(6));
            int left = Math.round(sheet.getLeft() - sidePadding - spread);
            int top = Math.round(sheet.getTop() - spread);
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) menuShadow.getLayoutParams();
            if (lp.width != sw || lp.height != sh || lp.leftMargin != left
                    || lp.topMargin != top) {
                lp.width = sw;
                lp.height = sh;
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                lp.leftMargin = left;
                lp.topMargin = top;
                menuShadow.setLayoutParams(lp);
            }
            if (menuGlass != null && menuGlass.backdrop() != null) {
                menuShadow.setBackdrop(menuGlass.backdrop(),
                        spread - menuGlass.offsetX(),
                        spread - menuGlass.offsetY(),
                        Glass.night(BrowserActivity.this) ? 0.34f : 0.18f);
            }
        };
        sheet.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (v.getWidth() > 0 && v.getHeight() > 0) {
                G2Path.applyOutline(v, dp(Glass.outerRadius()), Glass.curve() / 100f);
            }
            placeMenuShadow.run();
        });
        if (menuGlass != null) {
            menuGlass.setOnBuilt(() -> sheet.post(placeMenuShadow));
        }
        dialog.setContentView(floating);
        dialog.setOnShowListener(d -> {
            android.view.Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.TRANSPARENT));
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                w.setDimAmount(0f);
                w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.setGravity(Gravity.BOTTOM);
                if (Glass.frosted()) {
                    Glass.applyWindowBlur(w);
                }
            }
            sheet.setTranslationY(dp(26));
            sheet.animate().translationY(0f).setDuration(200).start();
            if (menuShadow != null) {
                menuShadow.setTranslationY(dp(26));
                menuShadow.animate().translationY(0f).setDuration(200).start();
            }
        });
        if (menuGlass != null) {
            dialog.setOnDismissListener(d -> menuGlass.release());
        }
        dialog.show();
        if (menuGlass != null) {
            sheet.post(menuGlass::refresh);
            sheet.postDelayed(menuGlass::refresh, 260);
        }
    }

    private List<List<MenuAction>> buildMenuPages() {
        return buildMenuPages(true);
    }

    private List<List<MenuAction>> buildMenuPages(boolean applyHidden) {
        boolean content = hasContentPage();
        if (applyHidden && Glass.menuSimple()) {
            List<MenuAction> simple = new ArrayList<>();
            simple.add(new MenuAction(R.drawable.ic_moon, "夜间模式", true, this::toggleNightMode));
            simple.add(new MenuAction(R.drawable.ic_star, "书签", true,
                    () -> openUrl("v://bookmarks", false)));
            simple.add(new MenuAction(R.drawable.ic_history, "历史", true,
                    () -> openUrl("v://history", false)));
            simple.add(new MenuAction(R.drawable.ic_download, "下载", true, this::openDownloads));
            simple.add(new MenuAction(R.drawable.ic_settings, "设置", true, this::openSettings));
            List<List<MenuAction>> pages = new ArrayList<>();
            pages.add(simple);
            return pages;
        }
        java.util.Set<String> hidden =
                Prefs.get().getStringSet("menu_hidden", new java.util.HashSet<>());

        List<MenuAction> first = new ArrayList<>();
        first.add(new MenuAction(R.drawable.ic_moon, "夜间模式", true, this::toggleNightMode));
        first.add(new MenuAction(R.drawable.ic_star, "书签", true,
                () -> openUrl("v://bookmarks", false)));
        first.add(new MenuAction(R.drawable.ic_history, "历史", true,
                () -> openUrl("v://history", false)));
        first.add(new MenuAction(R.drawable.ic_download, "下载", true, this::openDownloads));
        first.add(new MenuAction(R.drawable.ic_visibility_off, "隐身", true,
                () -> newTab(Prefs.homepage(), true)));
        first.add(new MenuAction(R.drawable.ic_share, "分享", true, this::sharePage));
        first.add(new MenuAction(R.drawable.ic_star_filled, "添加书签", true, this::toggleBookmark));
        first.add(new MenuAction(R.drawable.ic_desktop, "电脑模式", true, this::uaDialog));
        first.add(new MenuAction(R.drawable.ic_toolbox, "工具箱", true, this::toolboxDialog));
        first.add(new MenuAction(R.drawable.ic_settings, "设置", true, this::openSettings));

        List<MenuAction> second = new ArrayList<>();
        second.add(new MenuAction(R.drawable.ic_search, "页内查找", true, this::showFindBar));
        second.add(new MenuAction(R.drawable.ic_save, "保存", content, this::saveOffline));
        second.add(new MenuAction(R.drawable.ic_offline, "离线页面", true, this::showOfflinePages));
        second.add(new MenuAction(R.drawable.ic_translate, "翻译", content,
                this::translateSelection));
        second.add(new MenuAction(R.drawable.ic_source, "源码", content, this::viewSource));
        second.add(new MenuAction(R.drawable.ic_fullscreen, "全屏", true, this::toggleFullscreen));
        second.add(new MenuAction(R.drawable.ic_image, "有图模式", true, this::toggleImages));
        second.add(new MenuAction(R.drawable.ic_wave_square, "资源嗅探", true,
                () -> startActivity(new Intent(this, MediaListActivity.class))));
        second.add(new MenuAction(R.drawable.ic_shield_check, "网站设定", true,
                this::siteSettingsDialog));
        second.add(new MenuAction(R.drawable.ic_log, "网络日志", true, this::showNetLog));

        List<MenuAction> third = new ArrayList<>();
        third.add(new MenuAction(R.drawable.ic_scan, "扫描二维码", true, this::startScan));
        third.add(new MenuAction(R.drawable.ic_shortcut, "添加到桌面", true, this::addShortcut));
        third.add(new MenuAction(R.drawable.ic_tts, "朗读网页", content, this::readAloud));
        third.add(new MenuAction(R.drawable.ic_ai, "AI", true, this::askAi));
        third.add(new MenuAction(R.drawable.ic_rotate, "屏幕方向", true, this::orientationDialog));
        third.add(new MenuAction(R.drawable.ic_block, "广告拦截", true, this::toggleAdBlock));
        third.add(new MenuAction(R.drawable.ic_edit, "标记广告", content, this::markAd));
        third.add(new MenuAction(R.drawable.ic_font, "字体大小", content, this::showZoomDialog));
        third.add(new MenuAction(R.drawable.ic_report, "举报网站", content, this::reportSite));
        third.add(new MenuAction(R.drawable.ic_tune, "定制菜单", true, this::customizeMenu));

        List<List<MenuAction>> pages = new ArrayList<>();
        pages.add(first);
        pages.add(second);
        pages.add(third);
        if (applyHidden) {
            for (List<MenuAction> page : pages) {
                for (int i = page.size() - 1; i >= 0; i--) {
                    MenuAction action = page.get(i);
                    if (!"定制菜单".equals(action.label) && hidden.contains(action.label)) {
                        page.remove(i);
                    }
                }
            }
        }
        return pages;
    }

    private boolean isNightActive() {
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean hasContentPage() {
        Tab tab = currentTab();
        return tab != null && tab.web != null && !UrlUtils.isInternal(tab.url)
                && !"v://source".equals(tab.url);
    }

    private boolean isDataPage(String url) {
        return url != null
                && (url.startsWith("v://offline") || url.startsWith("v://source"));
    }

    private void renderMenuPage(LinearLayout grid, List<View> dotViews,
                                List<MenuAction> actions, int pageIndex,
                                final android.app.Dialog dialog,
                                final View.OnTouchListener swipeListener) {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < actions.size(); i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = dp(14);
                }
                grid.addView(row, rowParams);
            }
            row.addView(menuCell(dialog, actions.get(i), swipeListener),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        for (int i = 0; i < dotViews.size(); i++) {
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(i == pageIndex ? color(R.color.on_surface) : color(R.color.outline_variant));
            dotViews.get(i).setBackground(bg);
        }
    }

    private View menuCell(final android.app.Dialog dialog, final MenuAction action,
                          final View.OnTouchListener swipeListener) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setBackground(selectableBackground());
        cell.setPadding(dp(2), dp(12), dp(2), dp(10));
        cell.setOnTouchListener(swipeListener);
        ImageView image = new androidx.appcompat.widget.AppCompatImageView(this);
        image.setImageResource(action.icon);
        image.setColorFilter(color(R.color.on_surface));
        cell.addView(image, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = new TextView(this);
        label.setText(action.label);
        label.setTextSize(13);
        boolean highlight = "夜间模式".equals(action.label) && isNightActive();
        label.setTextColor(color(highlight ? R.color.primary : R.color.on_surface));
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(8);
        cell.addView(label, labelParams);
        if (!action.enabled) {
            cell.setAlpha(0.38f);
        } else {
            cell.setOnClickListener(v -> {
                dialog.dismiss();
                action.task.run();
            });
        }
        return cell;
    }

    private void saveOffline() {
        final WebView web = currentWeb();
        if (web == null) {
            return;
        }
        web.evaluateJavascript(
                "(function(){try{return JSON.stringify(document.documentElement.outerHTML)}"
                        + "catch(e){return 'null'}})()",
                value -> {
                    String html = decodeJsString(value);
                    if (html == null) {
                        toast("保存失败");
                        return;
                    }
                    try {
                        File dir = new File(getFilesDir(), "offline");
                        if (!dir.exists() && !dir.mkdirs()) {
                            toast("保存失败");
                            return;
                        }
                        Tab tab = currentTab();
                        String name = tab == null || tab.title == null || tab.title.isEmpty()
                                ? "page" : tab.title.replaceAll("[\\\\/:*?\"<>|]", "_");
                        File file = new File(dir, name + "-" + System.currentTimeMillis() + ".html");
                        try (OutputStream out = new java.io.FileOutputStream(file)) {
                            out.write(html.getBytes(StandardCharsets.UTF_8));
                        }
                        toast("已保存离线页面");
                    } catch (Throwable t) {
                        toast("保存失败");
                    }
                });
    }

    private void showOfflinePages() {
        File dir = new File(getFilesDir(), "offline");
        final File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            toast("还没有离线页面");
            return;
        }
        String[] labels = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName().replaceAll("\\.html?$", "")
                    .replaceAll("-\\d{10,}$", "");
            labels[i] = name;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("离线页面")
                .setItems(labels, (d, which) -> openOffline(files[which]))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void openOffline(File file) {
        try {
            InputStream in = new java.io.FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            in.close();
            String html = new String(out.toByteArray(), StandardCharsets.UTF_8);
            Tab tab = newEmptyTab(false);
            if (tab == null) {
                return;
            }
            tab.url = "v://offline";
            tab.title = file.getName().replaceAll("\\.html?$", "").replaceAll("-\\d{10,}$", "");
            tab.web.loadDataWithBaseURL("v://offline", html, "text/html", "utf-8", null);
            updateAddress();
        } catch (Throwable t) {
            toast("打开失败");
        }
    }

    private void viewSource() {
        final WebView web = currentWeb();
        if (web == null) {
            return;
        }
        web.evaluateJavascript(
                "(function(){try{return JSON.stringify(document.documentElement.outerHTML)}"
                        + "catch(e){return 'null'}})()",
                value -> {
                    String html = decodeJsString(value);
                    if (html == null) {
                        toast("无法读取源码");
                        return;
                    }
                    String escaped = html.replace("&", "&amp;")
                            .replace("<", "&lt;").replace(">", "&gt;");
                    Tab tab = newEmptyTab(false);
                    if (tab == null) {
                        return;
                    }
                    tab.url = "v://source";
                    tab.title = "源码";
                    tab.web.loadDataWithBaseURL("v://source",
                            "<!doctype html><html><head><meta name=viewport "
                                    + "content='width=device-width,initial-scale=1'>"
                                    + "<style>body{margin:0;background:#111;color:#ddd}"
                                    + "pre{white-space:pre-wrap;word-break:break-all;padding:14px;"
                                    + "font:12px/1.6 ui-monospace,monospace}</style></head><body><pre>"
                                    + escaped + "</pre></body></html>",
                            "text/html", "utf-8", null);
                });
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
            applySystemBars();
        }
        if (bottomBar != null) {
            updateBarMode();
        }
        toast(fullscreen ? "全屏 已开启，返回键退出" : "全屏 已关闭");
    }

    private void toggleImages() {
        boolean block = !Prefs.get().getBoolean("block_images", false);
        Prefs.get().edit().putBoolean("block_images", block).apply();
        for (Tab tab : tabs) {
            if (tab.web != null) {
                tab.web.getSettings().setBlockNetworkImage(block);
                tab.web.reload();
            }
        }
        toast(block ? "已切换无图模式" : "已切换有图模式");
    }

    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36 H/0.1";

    private void siteSettingsDialog() {
        Tab tab = currentTab();
        final String url = tab == null ? null : tab.url;
        if (url == null || UrlUtils.isInternal(url) || UrlUtils.host(url).isEmpty()) {
            toast("当前页面不支持网站设定");
            return;
        }
        final String host = UrlUtils.host(url);
        final boolean[] values = {
                Prefs.get().getBoolean("site_js_" + host, true),
                Prefs.get().getBoolean("site_img_" + host,
                        !Prefs.get().getBoolean("block_images", false)),
                Prefs.get().getBoolean("site_ua_" + host, false),
                Prefs.get().getBoolean("site_ad_" + host, true)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(host)
                .setMultiChoiceItems(new String[]{"JavaScript", "加载图片", "桌面版网页", "广告拦截"},
                        values, (d, which, checked) -> values[which] = checked)
                .setPositiveButton("保存", (d, w) -> {
                    Prefs.get().edit()
                            .putBoolean("site_js_" + host, values[0])
                            .putBoolean("site_img_" + host, values[1])
                            .putBoolean("site_ua_" + host, values[2])
                            .putBoolean("site_ad_" + host, values[3]).apply();
                    applySiteSettings(tab.web, url);
                    tab.web.reload();
                    toast("已保存网站设定");
                })
                .setNeutralButton("重置", (d, w) -> {
                    Prefs.get().edit()
                            .remove("site_js_" + host).remove("site_img_" + host)
                            .remove("site_ua_" + host).remove("site_ad_" + host).apply();
                    applySiteSettings(tab.web, url);
                    tab.web.reload();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean siteAdAllowed(String url) {
        String host = UrlUtils.host(url);
        return host.isEmpty() || Prefs.get().getBoolean("site_ad_" + host, true);
    }

    private void applySiteSettings(WebView web, String url) {
        if (web == null || url == null || UrlUtils.isInternal(url)) {
            return;
        }
        String host = UrlUtils.host(url);
        if (host.isEmpty()) {
            return;
        }
        android.webkit.WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(Prefs.get().getBoolean("site_js_" + host, true));
        boolean images = Prefs.get().getBoolean("site_img_" + host,
                !Prefs.get().getBoolean("block_images", false));
        settings.setBlockNetworkImage(!images);
        if (Prefs.get().getBoolean("site_ua_" + host, false)) {
            settings.setUserAgentString(DESKTOP_UA);
        } else {
            int uaMode = Prefs.uaMode();
            if (uaMode == 1) {
                settings.setUserAgentString(DESKTOP_UA);
            } else if (uaMode == 2 && !Prefs.customUa().isEmpty()) {
                settings.setUserAgentString(Prefs.customUa());
            } else {
                settings.setUserAgentString(null);
            }
        }
    }

    private void uaDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("浏览器标识")
                .setSingleChoiceItems(new String[]{"Android（默认）", "桌面版", "自定义"},
                        Prefs.uaMode(), (dialog, which) -> {
                            dialog.dismiss();
                            Prefs.setUaMode(which);
                            if (which == 2) {
                                final EditText input = new EditText(this);
                                input.setText(Prefs.customUa());
                                input.setSingleLine(false);
                                int pad = dp(20);
                                LinearLayout wrapper = new LinearLayout(this);
                                wrapper.setPadding(pad, dp(8), pad, 0);
                                wrapper.addView(input);
                                new MaterialAlertDialogBuilder(this).setTitle("自定义 UA")
                                        .setView(wrapper)
                                        .setPositiveButton("保存", (d2, w2) -> {
                                            Prefs.setCustomUa(input.getText().toString().trim());
                                            applyUserAgent();
                                            toast("已更新浏览器标识");
                                        })
                                        .setNegativeButton("取消", null).show();
                            } else {
                                applyUserAgent();
                                toast("已更新浏览器标识");
                            }
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyUserAgent() {
        int mode = Prefs.uaMode();
        String ua = mode == 1
                ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/122.0 Safari/537.36 H/0.3"
                : mode == 2 && !Prefs.customUa().isEmpty() ? Prefs.customUa() : null;
        for (Tab tab : tabs) {
            if (tab.web != null) {
                tab.web.getSettings().setUserAgentString(ua);
                tab.web.reload();
            }
        }
    }

    private void showSiteInfo() {
        Tab tab = currentTab();
        if (tab == null) {
            return;
        }
        String url = tab.url;
        boolean secure = url != null && url.startsWith("https://");
        String host;
        try {
            host = Uri.parse(url).getHost();
        } catch (Throwable t) {
            host = url;
        }
        String message = (host == null || host.isEmpty() ? url : host)
                + "\n\n连接" + (secure ? "安全（HTTPS）" : "不安全（HTTP）")
                + (tab.incognito ? "\n当前为无痕标签页" : "");
        new MaterialAlertDialogBuilder(this).setTitle("网站信息")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void showNetLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("已拦截 ").append(AdBlock.blockedCount()).append(" 次请求\n")
                .append("主机规则 ").append(AdBlock.ruleCount()).append(" 条\n");
        List<String> recent = AdBlock.recentBlocks();
        if (!recent.isEmpty()) {
            sb.append("\n最近拦截：\n");
            int limit = Math.min(recent.size(), 8);
            for (int i = 0; i < limit; i++) {
                sb.append("· ").append(recent.get(i)).append('\n');
            }
        }
        new MaterialAlertDialogBuilder(this).setTitle("网络日志")
                .setMessage(sb.toString().trim())
                .setPositiveButton("查看全部", (d, w) -> openUrl("v://log", false))
                .setNeutralButton("清空", (d, w) -> {
                    AdBlock.clearLog();
                    toast("已清空拦截日志");
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void addShortcut() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                android.content.pm.ShortcutManager manager =
                        (android.content.pm.ShortcutManager) getSystemService(SHORTCUT_SERVICE);
                if (manager == null) {
                    return;
                }
                android.content.pm.ShortcutInfo info =
                        new android.content.pm.ShortcutInfo.Builder(this, "h-home")
                                .setShortLabel("氢")
                                .setIcon(android.graphics.drawable.Icon.createWithResource(
                                        this, R.drawable.ic_launcher_foreground))
                                .setIntent(new Intent(this, BrowserActivity.class)
                                        .setAction(Intent.ACTION_MAIN))
                                .build();
                boolean ok = manager.requestPinShortcut(info, null);
                toast(ok ? "已添加到桌面" : "当前启动器不支持固定快捷方式");
            } else {
                Intent shortcut = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
                shortcut.putExtra(Intent.EXTRA_SHORTCUT_INTENT,
                        new Intent(this, BrowserActivity.class));
                shortcut.putExtra(Intent.EXTRA_SHORTCUT_NAME, "氢");
                shortcut.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.fromContext(this,
                                R.drawable.ic_launcher_foreground));
                sendBroadcast(shortcut);
                toast("已发送到桌面");
            }
        } catch (Throwable t) {
            toast("添加失败");
        }
    }

    private void toolboxDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("工具箱")
                .setItems(new String[]{"阅读模式", "朗读网页", "翻译选区", "资源嗅探",
                        "下载管理", "扫描二维码", "密码库", "AI 助手"}, (d, which) -> {
                    switch (which) {
                        case 0: openReader(); break;
                        case 1: readAloud(); break;
                        case 2: translateSelection(); break;
                        case 3: startActivity(new Intent(this, MediaListActivity.class)); break;
                        case 4: openDownloads(); break;
                        case 5: startScan(); break;
                        case 6: fillPassword(); break;
                        case 7: askAi(); break;
                        default: break;
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void orientationDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("屏幕方向")
                .setItems(new String[]{"跟随系统", "自动", "竖屏", "横屏"}, (d, which) -> {
                    int value;
                    switch (which) {
                        case 1:
                            value = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
                            break;
                        case 2:
                            value = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                            break;
                        case 3:
                            value = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                            break;
                        default:
                            value = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                            break;
                    }
                    setRequestedOrientation(value);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleAdBlock() {
        boolean enable = !Prefs.adblockEnabled();
        Prefs.setAdblockEnabled(enable);
        AdBlock.init(this);
        WebView web = currentWeb();
        if (web != null) {
            web.reload();
        }
        toast(enable ? "广告拦截已开启" : "广告拦截已关闭");
    }

    private void markAd() {
        WebView web = currentWeb();
        if (web == null) {
            return;
        }
        web.evaluateJavascript(
                "(function(){if(window.__hAdPick)return;window.__hAdPick=1;"
                        + "var bar=document.createElement('div');"
                        + "bar.textContent='点击要隐藏的广告元素';"
                        + "bar.style.cssText='position:fixed;left:0;right:0;bottom:0;z-index:2147483647;"
                        + "padding:12px;text-align:center;font:14px sans-serif;color:#fff;"
                        + "background:rgba(0,0,0,.85)';"
                        + "document.body.appendChild(bar);"
                        + "function sel(el){var p=[];while(el&&el.nodeType===1){"
                        + "var s=el.tagName.toLowerCase();if(el.id){p.unshift(s+'#'+el.id);break;}"
                        + "var c=el.className&&el.className.toString?el.className.toString().trim():'';"
                        + "if(c){s+='.'+c.split(/\\s+/).slice(0,2).join('.');}"
                        + "var n=1,prev=el;while((prev=prev.previousElementSibling)){"
                        + "if(prev.tagName===el.tagName)n++;}"
                        + "s+=':nth-of-type('+n+')';p.unshift(s);el=el.parentElement;}"
                        + "return p.join(' > ');}"
                        + "document.addEventListener('click',function(e){"
                        + "e.preventDefault();e.stopPropagation();"
                        + "var el=e.target;if(!el||!el.style)return;var s=sel(el);"
                        + "el.style.setProperty('display','none','important');"
                        + "try{var a=JSON.parse(localStorage.getItem('h_ad_rules')||'[]');"
                        + "a.push(s);localStorage.setItem('h_ad_rules',JSON.stringify(a));}catch(x){}"
                        + "window.__hAdPick=0;if(bar.parentNode)bar.parentNode.removeChild(bar);},true);"
                        + "setTimeout(function(){window.__hAdPick=0;"
                        + "if(bar.parentNode)bar.parentNode.removeChild(bar);},8000);})()",
                null);
        toast("标记模式：点击广告元素即可隐藏");
    }

    private void reportSite() {
        Tab tab = currentTab();
        String url = tab == null ? "" : tab.url;
        String report = "https://github.com/zejiahuang/H-browser/issues/new?title="
                + Uri.encode("网站问题：" + url)
                + "&body=" + Uri.encode(url);
        openUrl(report, false);
    }

    private void customizeMenu() {
        final List<List<MenuAction>> pages = buildMenuPages(false);
        final List<String> labels = new ArrayList<>();
        for (List<MenuAction> page : pages) {
            for (MenuAction action : page) {
                if (!"定制菜单".equals(action.label)) {
                    labels.add(action.label);
                }
            }
        }
        java.util.Set<String> hidden =
                Prefs.get().getStringSet("menu_hidden", new java.util.HashSet<>());
        final boolean[] checked = new boolean[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            checked[i] = !hidden.contains(labels.get(i));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("定制菜单")
                .setMultiChoiceItems(labels.toArray(new String[0]), checked,
                        (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("保存", (d, w) -> {
                    java.util.Set<String> hide = new java.util.HashSet<>();
                    for (int i = 0; i < labels.size(); i++) {
                        if (!checked[i]) {
                            hide.add(labels.get(i));
                        }
                    }
                    Prefs.get().edit().putStringSet("menu_hidden", hide).apply();
                    toast("菜单已更新");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleNightMode() {
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        Prefs.get().edit().putInt("theme_mode", night ? 1 : 2).apply();
        AppCompatDelegate.setDefaultNightMode(night
                ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
    }

    private void toggleDesktopMode() {
        boolean desktop = Prefs.uaMode() != 1;
        Prefs.setUaMode(desktop ? 1 : 0);
        WebView web = currentWeb();
        if (web != null) {
            if (desktop) {
                web.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36 H/0.1");
            } else {
                web.getSettings().setUserAgentString(null);
            }
            web.reload();
        }
        toast(desktop ? "已切换电脑版" : "已切换移动版");
    }

    private BottomSheetDialog newSheet() {
        return new BottomSheetDialog(this);
    }

    private LinearLayout listOf(BottomSheetDialog dialog) {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToOutline(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(20));
        if (Glass.enabled()) {
            android.graphics.drawable.GradientDrawable bg = Glass.panel(this, 24);
            bg.setCornerRadii(new float[]{
                    dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
            list.setBackground(bg);
        }
        scroll.addView(list);
        dialog.setContentView(scroll);
        dialog.setOnShowListener(d -> {
            View sheetView = ((android.app.Dialog) d).findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheetView != null) {
                sheetView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            if (dialog.getWindow() != null) {
                dialog.getWindow().setDimAmount(Glass.enabled() ? 0.28f : 0f);
                Glass.applyWindowBlur(dialog.getWindow());
            }
        });
        return list;
    }

    private void item(LinearLayout parent, final android.app.Dialog dialog, int icon,
                      String text, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        row.setBackground(selectableBackground());
        ImageView image = new androidx.appcompat.widget.AppCompatImageView(this);
        image.setImageResource(icon);
        row.addView(image, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16);
        label.setTextColor(color(R.color.on_surface));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.setMarginStart(dp(16));
        row.addView(label, labelParams);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        parent.addView(row);
    }

    private void showTabs() {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Glass.night(this) ? 0xFF1B2223 : 0xFFFFFFFF);
        bg.setCornerRadius(dp(Glass.outerRadius()));
        sheet.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) {
            sheet.setClipToOutline(false);
        }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(10), dp(8), dp(8));
        TextView title = new TextView(this);
        title.setText("标签页 · " + tabs.size());
        title.setTextSize(18);
        title.setPadding(dp(12), dp(8), dp(12), dp(12));
        title.setTextColor(color(R.color.on_surface));
        list.addView(title);
        item(list, dialog, R.drawable.ic_add, "新建标签", () -> newTab(Prefs.homepage(), false));
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            Tab tab = tabs.get(i);
            String label = tab.title == null || tab.title.isEmpty()
                    ? tab.url : tab.title;
            if (tab.incognito) {
                label = "无痕 · " + label;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(56));
            row.setPadding(dp(12), dp(4), dp(4), dp(4));
            row.setBackground(selectableBackground());
            TextView text = new TextView(this);
            text.setText(label);
            text.setTextSize(15);
            text.setSingleLine(true);
            text.setTextColor(color(index == current ? R.color.primary : R.color.on_surface));
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ImageButton close = toolButton(R.drawable.ic_close, "关闭标签", v -> {
                closeTab(index, true);
                dialog.dismiss();
            });
            row.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
            row.setOnClickListener(v -> {
                selectTab(index);
                dialog.dismiss();
            });
            list.addView(row);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToOutline(false);
        scroll.addView(list);
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout floating = new FrameLayout(this);
        floating.setPadding(dp(14), 0, dp(14), 0);
        int barHeight = bottomBar == null ? dp(200) : bottomBar.getHeight();
        int barMargin = Glass.floatingToolbar() ? dp(Glass.toolbarMargin()) : 0;
        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        sheetParams.bottomMargin = barHeight + barMargin + dp(15);
        floating.addView(sheet, sheetParams);
        floating.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(floating);
        dialog.setOnShowListener(d -> {
            android.view.Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.TRANSPARENT));
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                w.setDimAmount(0f);
                w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.setGravity(Gravity.BOTTOM);
            }
            sheet.setTranslationY(dp(26));
            sheet.animate().translationY(0f).setDuration(200).start();
        });
        dialog.show();
    }

    private boolean showLinkMenu(final WebView web) {
        WebView.HitTestResult result = web.getHitTestResult();
        if (result == null) {
            return false;
        }
        int type = result.getType();
        String extra = result.getExtra();
        if (extra == null) {
            return false;
        }
        String link = null;
        String image = null;
        if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                || type == WebView.HitTestResult.IMAGE_ANCHOR_TYPE) {
            link = extra;
        } else if (type == WebView.HitTestResult.IMAGE_TYPE) {
            image = extra;
        }
        if (link == null && image == null) {
            return false;
        }
        showLinkSheet(link, image);
        return true;
    }

    public void showLinkSheet(final String link, final String image) {
        if ((link == null || link.isEmpty()) && (image == null || image.isEmpty())) {
            return;
        }
        final String target = link != null && !link.isEmpty() ? link : image;
        final BottomSheetDialog dialog = newSheet();
        LinearLayout list = listOf(dialog);
        if (link != null && !link.isEmpty()) {
            item(list, dialog, R.drawable.ic_forward, "打开链接", () -> openUrl(link, false));
            item(list, dialog, R.drawable.ic_add, "在新标签打开", () -> openUrl(link, true));
        }
        item(list, dialog, R.drawable.ic_share, "复制链接", () -> copy(target));
        item(list, dialog, R.drawable.ic_download, "下载", () -> Downloader.enqueue(this, target,
                currentWeb() == null ? null : currentWeb().getSettings().getUserAgentString(), null, null));
        item(list, dialog, R.drawable.ic_share, "分享", () -> share(target));
        if (image != null && !image.isEmpty()) {
            item(list, dialog, R.drawable.ic_search, "以图搜图", () -> {
                try {
                    openUrl("https://lens.google.com/uploadbyurl?url="
                            + URLEncoder.encode(image, "UTF-8"), true);
                } catch (Throwable t) {
                    toast("无法打开图片搜索");
                }
            });
            item(list, dialog, R.drawable.ic_download, "保存图片", () -> Downloader.enqueue(this, image,
                    null, null, null));
        }
        dialog.show();
    }

    private void showFindBar() {
        findBar.setVisibility(View.VISIBLE);
        findInput.requestFocus();
        String selected = "";
        WebView web = currentWeb();
        if (web != null) {
            showSoftInput(findInput);
        }
        if (!selected.isEmpty()) {
            findInput.setText(selected);
        }
    }

    private void hideFindBar() {
        findBar.setVisibility(View.GONE);
        findInput.setText("");
        findCount.setText("");
        WebView web = currentWeb();
        if (web != null) {
            web.clearMatches();
            web.requestFocus();
        }
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(findInput.getWindowToken(), 0);
        }
    }

    private void findStep(boolean forward) {
        WebView web = currentWeb();
        if (web == null || findInput.getText().toString().isEmpty()) {
            return;
        }
        web.findNext(forward);
    }

    private void showSoftInput(View view) {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void showZoomDialog() {
        final int[] values = {80, 90, 100, 110, 125, 150, 175, 200};
        String[] labels = new String[values.length];
        int checked = 2;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i] + "%" + (values[i] == 100 ? "（默认）" : "");
            if (values[i] == Prefs.fontZoom()) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("网页缩放")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    Prefs.setFontZoom(values[which]);
                    for (Tab tab : tabs) {
                        if (tab.web != null) {
                            tab.web.getSettings().setTextZoom(values[which]);
                        }
                    }
                    dialog.dismiss();
                    toast("已应用 " + values[which] + "%");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void copy(String text) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("link", text));
            toast("已复制");
        }
    }

    private void share(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(intent, "分享"));
        } catch (Throwable ignored) {
        }
    }

    public void sharePage() {
        Tab tab = currentTab();
        if (tab != null) {
            share(tab.url);
        }
    }

    private void toggleBookmark() {
        Tab tab = currentTab();
        if (tab == null || UrlUtils.isInternal(tab.url)) {
            toast("内部页面不能收藏");
            return;
        }
        if (Db.get(this).isBookmarked(tab.url)) {
            Db.get(this).removeBookmark(tab.url);
            toast("已取消收藏");
        } else {
            Db.get(this).addBookmark(tab.web.getTitle(), tab.url);
            toast("已收藏");
        }
    }

    private void evaluateText(final TextCallback callback) {
        WebView web = currentWeb();
        if (web == null) {
            callback.onText("");
            return;
        }
        web.evaluateJavascript("(function(){return document.body?document.body.innerText:'';})()", value -> {
            String text = decodeJsString(value);
            callback.onText(text == null ? "" : text);
        });
    }

    private interface TextCallback {
        void onText(String text);
    }

    private String decodeJsString(String value) {
        if (value == null) {
            return "";
        }
        try {
            Object parsed = new JSONTokener(value).nextValue();
            return parsed == null ? "" : String.valueOf(parsed);
        } catch (Throwable t) {
            return "";
        }
    }

    private void openReader() {
        final WebView web = currentWeb();
        if (web == null) {
            return;
        }
        String script = readAsset("extractor.js");
        web.evaluateJavascript(script, value -> {
            String json = decodeJsString(value);
            try {
                JSONObject object = new JSONObject(json);
                String title = object.optString("title");
                String html = object.optString("html");
                if (html == null || html.trim().isEmpty()) {
                    toast("没有提取到正文");
                    return;
                }
                Tab tab = currentTab();
                Intent intent = new Intent(this, ReaderActivity.class);
                intent.putExtra("title", title);
                intent.putExtra("html", html);
                intent.putExtra("url", tab == null ? "" : tab.url);
                startActivity(intent);
            } catch (Throwable t) {
                toast("正文提取失败");
            }
        });
    }

    public void readAloud() {
        evaluateText(text -> {
            if (text == null || text.trim().isEmpty()) {
                toast("没有可朗读的文字");
                return;
            }
            requestNotificationPermission();
            ReadAloudService.start(this, text.length() > 30000 ? text.substring(0, 30000) : text);
            toast("开始朗读");
        });
    }

    private void translateSelection() {
        WebView web = currentWeb();
        if (web == null) {
            return;
        }
        web.evaluateJavascript("(function(){return String(window.getSelection());})()", value -> {
            String selection = decodeJsString(value);
            if (selection != null && !selection.trim().isEmpty()) {
                translateDialog(selection);
            } else {
                evaluateText(text -> {
                    if (text.trim().isEmpty()) {
                        toast("没有可翻译的文字");
                    } else {
                        translateDialog(text.length() > 5000 ? text.substring(0, 5000) : text);
                    }
                });
            }
        });
    }

    private void translateDialog(final String text) {
        final String[] labels = {"中文", "English", "日本語"};
        final String[] codes = {"zh", "en", "ja"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("翻译为")
                .setItems(labels, (dialog, which) -> {
                    toast("翻译中…");
                    Translator.translate(this, text, codes[which], new Translator.Callback() {
                        @Override
                        public void onSuccess(final String result) {
                            runOnUiThread(() -> new MaterialAlertDialogBuilder(BrowserActivity.this)
                                    .setTitle("翻译结果")
                                    .setMessage(result)
                                    .setPositiveButton("复制", (d, w) -> copy(result))
                                    .setNegativeButton("关闭", null)
                                    .show());
                        }

                        @Override
                        public void onError(final String message) {
                            runOnUiThread(() -> toast(message));
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void askAi() {
        evaluateText(text -> {
            Tab tab = currentTab();
            Intent intent = new Intent(this, AiChatActivity.class);
            intent.putExtra("text", text != null && text.length() > 8000 ? text.substring(0, 8000) : text);
            intent.putExtra("url", tab == null ? "" : tab.url);
            startActivity(intent);
        });
    }

    private void fillPassword() {
        String url = currentTab() == null ? "" : currentTab().url;
        Intent intent = new Intent(this, VaultActivity.class);
        intent.putExtra("fill", true);
        intent.putExtra("url", url);
        startActivityForResult(intent, REQ_FILL);
    }

    private void injectCredentials(String username, String password) {
        WebView web = currentWeb();
        if (web == null) {
            return;
        }
        String js = "(function(){var u=" + JSONObject.quote(username) + ",p=" + JSONObject.quote(password) + ";"
                + "var pws=document.querySelectorAll('input[type=password]');if(!pws.length)return;"
                + "var pw=pws[0];pw.value=p;pw.dispatchEvent(new Event('input',{bubbles:true}));"
                + "var f=pw.form||document;var us=f.querySelector('input[type=text],input[type=email],input[name=username],input[name=email]');"
                + "if(us){us.value=u;us.dispatchEvent(new Event('input',{bubbles:true}));}pw.focus();})();";
        web.evaluateJavascript(js, null);
        toast("已填充账号密码");
    }

    public void startScan() {
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 2002);
            return;
        }
        startActivityForResult(new Intent(this, ScanActivity.class), REQ_SCAN);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2002 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startActivityForResult(new Intent(this, ScanActivity.class), REQ_SCAN);
        }
    }

    public void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    public void openDownloads() {
        startActivity(new Intent(this, DownloadActivity.class));
    }

    public void openVault() {
        startActivity(new Intent(this, VaultActivity.class));
    }

    public void openSync() {
        startActivity(new Intent(this, SyncActivity.class));
    }

    public void toast(String message) {
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show();
    }

    private void focusAddress() {
        beginEdit();
    }

    private void beginEdit() {
        if (address == null) {
            return;
        }
        editingAddress = true;
        updateBarMode();
        titleView.setVisibility(View.GONE);
        address.setVisibility(View.VISIBLE);
        Tab tab = currentTab();
        String url = tab == null ? "" : tab.url;
        boolean home = tab == null || UrlUtils.isInternal(url);
        settingAddress = true;
        address.setText(home ? "" : url);
        address.setSelection(address.getText().length());
        settingAddress = false;
        address.requestFocus();
        showKeyboard();
        updateBottomBarLift();
        updateClearButton();
    }

    private void endEdit() {
        editingAddress = false;
        updateBarMode();
        address.setVisibility(View.GONE);
        titleView.setVisibility(View.VISIBLE);
        updateBottomBarLift();
        updateAddress();
    }

    private void showKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(address.getWindowToken(), 0);
        }
    }

    private String readAsset(String name) {
        try {
            InputStream in = getAssets().open(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            in.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "JSON.stringify({title:'',html:''})";
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (editingAddress && bottomBar != null
                && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int[] loc = new int[2];
            bottomBar.getLocationOnScreen(loc);
            if (event.getRawY() < loc[1]) {
                hideKeyboard();
                endEdit();
            }
        }
        if (!Prefs.edgeGesture() || tabs.isEmpty()) {
            return super.dispatchTouchEvent(event);
        }
        int edge = dp(28);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                edgeStart = touchStartX < edge ? -1
                        : touchStartX > getWindow().getDecorView().getWidth() - edge ? 1 : 0;
                edgeHandled = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!edgeHandled && edgeStart != 0) {
                    float dx = event.getX() - touchStartX;
                    float dy = event.getY() - touchStartY;
                    if (Math.abs(dx) > dp(64) && Math.abs(dy) < dp(72)
                            && dx * edgeStart > 0) {
                        WebView web = currentWeb();
                        if (web != null) {
                            if (edgeStart < 0 && web.canGoBack()) {
                                web.goBack();
                                edgeHandled = true;
                                getWindow().getDecorView().performHapticFeedback(
                                        android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                                return true;
                            }
                            if (edgeStart > 0 && web.canGoForward()) {
                                web.goForward();
                                edgeHandled = true;
                                getWindow().getDecorView().performHapticFeedback(
                                        android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                                return true;
                            }
                        }
                    }
                }
                break;
            default:
                edgeHandled = false;
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            toggleFullscreen();
            return;
        }
        if (editingAddress) {
            hideKeyboard();
            endEdit();
            return;
        }
        WebView web = currentWeb();
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else if (tabs.size() > 1) {
            closeTab(current, true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        WebView web = currentWeb();
        if (web != null) {
            web.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        WebView web = currentWeb();
        if (web != null) {
            web.onResume();
        }
        if (toolbar != null && !Prefs.toolbarItems().equals(toolbarPref)) {
            buildToolbar();
        }
        if (refreshLayout != null) {
            refreshLayout.setEnabled(Prefs.pullRefresh());
        }
        applyToolbarStyle();
        updateHomeOverlay();
        updateUi();
    }

    @Override
    protected void onDestroy() {
        if (bottomBarGlass != null) {
            bottomBarGlass.release();
            bottomBarGlass = null;
        }
        for (Tab tab : tabs) {
            if (tab.web != null) {
                tab.web.destroy();
            }
        }
        tabs.clear();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        String[] urls = new String[tabs.size()];
        boolean[] incognito = new boolean[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            urls[i] = tabs.get(i).url;
            incognito[i] = tabs.get(i).incognito;
        }
        outState.putStringArray("urls", urls);
        outState.putBooleanArray("incognito", incognito);
        outState.putInt("current", current);
    }

    private void restoreState(Bundle state) {
        String[] urls = state.getStringArray("urls");
        boolean[] incognito = state.getBooleanArray("incognito");
        if (urls == null || urls.length == 0) {
            return;
        }
        for (int i = 0; i < urls.length; i++) {
            newTab(urls[i], incognito != null && incognito.length > i && incognito[i]);
        }
        int index = state.getInt("current", 0);
        selectTab(Math.max(0, Math.min(index, tabs.size() - 1)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            Uri uri = resultCode == Activity.RESULT_OK && data != null ? data.getData() : null;
            if (legacyFileCallback != null) {
                legacyFileCallback.onReceiveValue(uri);
                legacyFileCallback = null;
            }
        } else if (requestCode == REQ_SCAN) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String url = data.getStringExtra("url");
                if (url != null && !url.isEmpty()) {
                    openUrl(UrlUtils.toUrl(url), false);
                }
            }
        } else if (requestCode == REQ_FILL) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                injectCredentials(data.getStringExtra("username"), data.getStringExtra("password"));
            }
        } else if (requestCode == REQ_IMPORT_BM) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                final Uri uri = data.getData();
                new Thread(() -> {
                    int count = 0;
                    String error = null;
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        if (in != null) {
                            count = Backup.importBookmarks(this, in);
                        }
                    } catch (Throwable t) {
                        error = t.getMessage();
                    }
                    final int imported = count;
                    final String failure = error;
                    runOnUiThread(() -> toast(failure == null
                            ? "已导入 " + imported + " 个书签" : "导入失败：" + failure));
                }, "bookmark-import").start();
            }
        } else if (requestCode == REQ_EXPORT_BM || requestCode == REQ_EXPORT_HISTORY) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                final Uri uri = data.getData();
                final boolean history = requestCode == REQ_EXPORT_HISTORY;
                new Thread(() -> {
                    String error = null;
                    try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                        if (out != null) {
                            if (history) {
                                Backup.exportHistory(this, out);
                            } else {
                                Backup.exportBookmarks(this, out);
                            }
                        }
                    } catch (Throwable t) {
                        error = t.getMessage();
                    }
                    final String failure = error;
                    runOnUiThread(() -> toast(failure == null ? "已导出"
                            : "导出失败：" + failure));
                }, "backup-export").start();
            }
        }
    }

    public void importBookmarksFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_IMPORT_BM);
        } catch (Throwable t) {
            toast("没有可用的文件选择器");
        }
    }

    public void exportBookmarksFile() {
        createDocument("氢·书签.html", REQ_EXPORT_BM);
    }

    public void exportHistoryFile() {
        createDocument("氢·历史.html", REQ_EXPORT_HISTORY);
    }

    private void createDocument(String name, int request) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(intent, request);
        } catch (Throwable t) {
            toast("没有可用的保存位置");
        }
    }

    private class HClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleNavigation(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Tab tab = tabFor(view);
            if (tab == null) {
                return;
            }
            applySiteSettings(view, url);
            if (!"about:blank".equals(url)) {
                tab.url = url;
            }
            if (tab == currentTab()) {
                progress.setProgress(0);
                progress.setVisibility(View.VISIBLE);
                updateAddress();
                if (progressiveBlur != null) {
                    progressiveBlur.onPageStarted();
                }
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Tab tab = tabFor(view);
            if (tab == null) {
                return;
            }
            if (!"about:blank".equals(url)) {
                tab.url = url;
            }
            String title = view.getTitle();
            if (title != null && !"about:blank".equals(title)) {
                tab.title = title;
            }
            if (!UrlUtils.isInternal(tab.url) && !tab.incognito) {
                Db.get(BrowserActivity.this).addHistory(title, url);
            }
            boolean home = UrlUtils.isInternal(tab.url);
            if (home) {
                scheduleRepaint(view);
            }
            if (!home) {
                injectCosmetic(view, url);
                UserScripts.inject(view, url);
                view.evaluateJavascript(LINK_JS, null);
                view.evaluateJavascript(
                        "(function(){try{var a=JSON.parse(localStorage.getItem('h_ad_rules')||'[]');"
                                + "if(!a.length)return;var st=document.getElementById('h-ad-rules');"
                                + "if(!st){st=document.createElement('style');st.id='h-ad-rules';"
                                + "(document.head||document.documentElement).appendChild(st);}"
                                + "st.textContent=a.join(',')+'{display:none!important}';}"
                                + "catch(e){}})()", null);
            }
            if (tab == currentTab()) {
                progress.setVisibility(View.GONE);
                refreshLayout.setRefreshing(false);
                updateUi();
                updateProgressiveBlur();
                if (progressiveBlur != null) {
                    progressiveBlur.onPageFinished();
                }
                if (bottomBarGlass != null) {
                    bottomBarGlass.refresh();
                }
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return intercept(url, false);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (errorCode == WebViewClient.ERROR_UNKNOWN && failingUrl != null
                    && failingUrl.startsWith("v://")) {
                return;
            }
            Tab tab = tabFor(view);
            if (tab != null && tab == currentTab() && failingUrl != null
                    && !failingUrl.startsWith("v://")) {
                showError(failingUrl);
                progress.setVisibility(View.GONE);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
            new MaterialAlertDialogBuilder(BrowserActivity.this)
                    .setTitle("安全警告")
                    .setMessage("此网站的证书存在问题。继续访问可能有风险。")
                    .setPositiveButton("继续", (d, w) -> handler.proceed())
                    .setNegativeButton("取消", (d, w) -> handler.cancel())
                    .setCancelable(false)
                    .show();
        }

        boolean handleNavigation(WebView view, String url) {
            if (url == null) {
                return false;
            }
            if (url.startsWith("v://")) {
                Tab tab = tabFor(view);
                if (tab != null) {
                    handleInternal(tab, url);
                }
                return true;
            }
            String scheme = null;
            try {
                scheme = Uri.parse(url).getScheme();
            } catch (Throwable ignored) {
            }
            if ("http".equals(scheme) || "https".equals(scheme) || "about".equals(scheme)
                    || "blob".equals(scheme) || "data".equals(scheme) || "file".equals(scheme)
                    || "javascript".equals(scheme)) {
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    applySiteSettings(view, url);
                }
                return false;
            }
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Throwable ignored) {
            }
            return true;
        }
    }

    private class HClient21 extends HClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleNavigation(view, request.getUrl().toString());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return intercept(request.getUrl().toString(), request.isForMainFrame());
        }
    }

    private WebResourceResponse intercept(String url, boolean mainFrame) {
        if (url == null || url.startsWith("v://")) {
            return null;
        }
        if (UrlUtils.isMedia(url)) {
            MediaSniffer.add(url);
        }
        if (!mainFrame && siteAdAllowed(url) && AdBlock.shouldBlock(url)) {
            return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        }
        return null;
    }

    private class HChrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (view == currentWeb()) {
                progress.setProgress(newProgress);
                if (newProgress >= 100) {
                    progress.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            Tab tab = tabFor(view);
            if (tab != null && title != null && !"about:blank".equals(title)) {
                tab.title = title;
                if (tab == currentTab()) {
                    updateAddress();
                }
            }
        }

        public void openFileChooser(ValueCallback<Uri> uploadMsg) {
            legacyFileCallback = uploadMsg;
            startActivityForResult(Intent.createChooser(fileIntent(), "选择文件"), REQ_FILE);
        }

        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
            openFileChooser(uploadMsg);
        }

        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
            openFileChooser(uploadMsg);
        }
    }

    private Intent fileIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        return intent;
    }
}

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
import android.graphics.BitmapFactory;
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
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.VelocityTracker;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
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
import androidx.webkit.WebViewFeature;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.h.browser.R;
import com.h.browser.BuildConfig;
import com.h.browser.adblock.AdBlock;
import com.h.browser.ai.AiChatActivity;
import com.h.browser.data.Backup;
import com.h.browser.data.Db;
import com.h.browser.data.Prefs;
import com.h.browser.download.DownloadActivity;
import com.h.browser.download.Downloader;
import com.h.browser.net.WebViewProxy;
import com.h.browser.privacy.Injection;
import com.h.browser.privacy.PermissionStore;
import com.h.browser.privacy.PrivacyNav;
import com.h.browser.privacy.PrivacyPrefs;
import com.h.browser.privacy.PrivacyStats;
import com.h.browser.privacy.SslExceptionStore;
import com.h.browser.privacy.TrustedChannel;
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
import com.h.browser.ui.MenuDefs;
import com.h.browser.ui.NativeBridge;
import com.h.browser.ui.ProgressiveBlurView;
import com.h.browser.ui.SettingsActivity;
import com.h.browser.userscript.GmBridge;
import com.h.browser.userscript.UserScripts;
import com.h.browser.vault.VaultActivity;

import org.json.JSONArray;
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
import java.util.Set;

public class BrowserActivity extends AppCompatActivity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_SCAN = 1002;
    private static final int REQ_FILL = 1003;
    private static final int REQ_IMPORT_BM = 1005;
    private static final int REQ_EXPORT_BM = 1006;
    private static final int REQ_EXPORT_HISTORY = 1007;
    private static final int REQ_PERM_MEDIA = 1008;
    private static final int REQ_PERM_LOCATION = 1009;
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
    private ImageView homeBgImage;
    private View homeBgDim;
    private HMarkView homeLogo;
    private LinearLayout homePill;
    private TextView homePillHint;
    private int homeBgVersionCache = -1;
    private int lastScrollY;
    private boolean barHidden;
    private android.app.Dialog menuDialog;
    private Boolean webDarkApplied;
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
    private Runnable pendingPermGrant;
    private Runnable pendingPermDeny;
    private boolean sslDialogShowing;
    private String privacySignature;
    private volatile boolean pickerActive;

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
        String urlBeforeLoad;
        boolean needLoad;

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
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(Prefs.get().getBoolean("debug_web", false));
        }
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
                if (!handleIntent(getIntent()) && !restoreSession()) {
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
                submitAddress();
                return true;
            }
            return false;
        });
        // 硬件键盘的回车不走 IME action，会先触发焦点转移并清空输入，这里直接拦截
        address.setOnKeyListener((v, keyCode, event) -> {
            if (event != null && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                submitAddress();
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
            if (barHidden) {
                barHidden = false;
                updateBottomBarLift();
            }
            WebView web = currentWeb();
            if (web != null) {
                web.reload();
            } else {
                refreshLayout.setRefreshing(false);
            }
        });
        // 默认实现只检查直接子 View（pageHost 是个 FrameLayout，永远“到顶”），
        // 会在页面中部抢走向下拖拽手势；改为查询当前 WebView 的真实滚动位置。
        refreshLayout.setOnChildScrollUpCallback((parent, child) -> {
            WebView web = currentWeb();
            return web != null && web.canScrollVertically(-1);
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
        boolean home = tab != null && isHomeTab(tab);
        boolean split = home && !editingAddress && !fullscreen
                && homeBar != null && Glass.floatingToolbar();
        if ((split || fullscreen) && barHidden) {
            barHidden = false;
            updateBottomBarLift();
        }
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

    private boolean isHomeTab(Tab tab) {
        if (tab == null) {
            return false;
        }
        if (UrlUtils.isHome(tab.url)) {
            return true;
        }
        if (!UrlUtils.isInternal(tab.url)) {
            return false;
        }
        try {
            return tab.web != null && UrlUtils.isHome(tab.web.getUrl());
        } catch (Throwable t) {
            return false;
        }
    }

    private void assignPageUrl(Tab tab, String url) {
        if (tab == null || url == null || "about:blank".equals(url)
                || url.startsWith("data:") || url.startsWith("blob:")) {
            return;
        }
        tab.url = url;
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
        float hiddenOffset = bottomBar.getHeight() > 0
                ? bottomBar.getHeight() + dp(Glass.toolbarMargin()) + dp(12)
                : dp(120);
        float target = editingAddress ? -imeLift : (barHidden ? hiddenOffset : 0f);
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

    private void handlePageScroll(int y) {
        Tab tab = currentTab();
        boolean page = tab != null && tab.web != null
                && !UrlUtils.isInternal(tab.url) && !isDataPage(tab.url);
        if (!Prefs.barAutoHide() || fullscreen || editingAddress || !page) {
            lastScrollY = y;
            if (barHidden) {
                barHidden = false;
                updateBottomBarLift();
            }
            return;
        }
        int delta = y - lastScrollY;
        if (y <= dp(24)) {
            if (barHidden) {
                barHidden = false;
                updateBottomBarLift();
            }
        } else if (delta > dp(10) && y > dp(180)) {
            if (!barHidden) {
                barHidden = true;
                updateBottomBarLift();
            }
        } else if (delta < -dp(6)) {
            if (barHidden) {
                barHidden = false;
                updateBottomBarLift();
            }
        }
        lastScrollY = y;
    }

    private void buildHomeOverlay() {
        homeOverlay = new FrameLayout(this);
        homeOverlay.setBackgroundColor(color(R.color.surface));
        homeOverlay.setClickable(true);

        homeBgImage = new ImageView(this);
        homeBgImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        homeBgImage.setVisibility(View.GONE);
        homeBgImage.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        homeOverlay.addView(homeBgImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        homeBgDim = new View(this);
        homeBgDim.setBackgroundColor(0xFF000000);
        homeBgDim.setVisibility(View.GONE);
        homeBgDim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        homeOverlay.addView(homeBgDim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER_HORIZONTAL);

        homeLogo = new HMarkView(this);
        group.addView(homeLogo, new LinearLayout.LayoutParams(dp(78), dp(65)));

        homePill = new LinearLayout(this);
        homePill.setOrientation(LinearLayout.HORIZONTAL);
        homePill.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable outline =
                new android.graphics.drawable.GradientDrawable();
        outline.setColor(Glass.night(this) ? 0xB31C1C1C : 0xCCFFFFFF);
        outline.setStroke(Math.max(1, dp(1)),
                Glass.night(this) ? 0x664A5154 : 0x44AEB7B8);
        outline.setCornerRadius(dp(23));
        homePill.setBackground(outline);
        homePill.setClickable(true);
        homePill.setContentDescription("搜索或输入网址");
        homePill.setOnClickListener(v -> beginEdit());
        homePillHint = new TextView(this);
        homePillHint.setText("搜索或输入网址");
        homePillHint.setTextSize(15);
        homePillHint.setSingleLine(true);
        homePillHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        homePillHint.setTextColor(Glass.night(this) ? 0xFFDEE4E4 : 0xFF3F4849);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hintParams.setMarginStart(dp(20));
        hintParams.setMarginEnd(dp(16));
        homePill.addView(homePillHint, hintParams);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        pillParams.setMargins(dp(24), dp(38), dp(24), 0);
        group.addView(homePill, pillParams);

        FrameLayout.LayoutParams groupParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        homeOverlay.addView(group, groupParams);
        group.setTranslationY(-dp(136));
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
        boolean show = tab == null || isHomeTab(tab);
        int homeBg = Prefs.get().getInt("home_bg", 0);
        homeOverlay.setBackgroundColor(homeBg != 0 ? homeBg : color(R.color.surface));
        boolean hasImage = new File(getFilesDir(), "home_bg.jpg").exists();
        if (homeBgImage != null) {
            homeBgImage.setVisibility(hasImage ? View.VISIBLE : View.GONE);
            loadHomeBackground(hasImage);
        }
        if (homeBgDim != null) {
            homeBgDim.setVisibility(hasImage ? View.VISIBLE : View.GONE);
            homeBgDim.setAlpha(Math.max(0, Math.min(80, Prefs.homeBgDim())) / 100f);
        }
        if (homeLogo != null) {
            homeLogo.setVisibility(Prefs.homeLogo() ? View.VISIBLE : View.GONE);
            float scale = Prefs.homeLogoScale() / 100f;
            homeLogo.setScaleX(scale);
            homeLogo.setScaleY(scale);
        }
        if (homePill != null) {
            homePill.setVisibility(Prefs.homeSearch() ? View.VISIBLE : View.GONE);
        }
        if (homePillHint != null) {
            homePillHint.setText(Prefs.homeSearchHint());
        }
        boolean page = tab != null && !UrlUtils.isInternal(tab.url);
        boolean dim = !show && page && Glass.night(this);
        if (pageDim != null) {
            pageDim.setVisibility(dim ? View.VISIBLE : View.GONE);
        }
        homeOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        updateProgressiveBlur();
    }

    private void loadHomeBackground(boolean exists) {
        int version = Prefs.get().getInt("home_bg_version", 0);
        if (version == homeBgVersionCache) {
            return;
        }
        homeBgVersionCache = version;
        if (!exists) {
            homeBgImage.setImageDrawable(null);
            return;
        }
        final File file = new File(getFilesDir(), "home_bg.jpg");
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1600) {
                    sample *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            } catch (Throwable ignored) {
            }
            final Bitmap decoded = bitmap;
            if (decoded == null) {
                return;
            }
            runOnUiThread(() -> {
                if (homeBgImage != null && !isFinishing()) {
                    homeBgImage.setImageBitmap(decoded);
                }
            });
        }, "home-bg-load").start();
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
                case "bookmarks":
                    toolbar.addView(toolButton(R.drawable.ic_star, "书签",
                            v -> openUrl("v://bookmarks", false)), weightParams());
                    break;
                case "downloads":
                    toolbar.addView(toolButton(R.drawable.ic_download, "下载",
                            v -> openDownloads()), weightParams());
                    break;
                case "share":
                    toolbar.addView(toolButton(R.drawable.ic_share, "分享",
                            v -> sharePage()), weightParams());
                    break;
                case "search":
                    toolbar.addView(toolButton(R.drawable.ic_search, "搜索",
                            v -> beginEdit()), weightParams());
                    break;
                case "find":
                    toolbar.addView(toolButton(R.drawable.ic_log, "页内查找",
                            v -> showFindBar()), weightParams());
                    break;
                case "settings":
                    toolbar.addView(toolButton(R.drawable.ic_settings, "设置",
                            v -> openSettings()), weightParams());
                    break;
                case "ai":
                    toolbar.addView(toolButton(R.drawable.ic_ai, "AI",
                            v -> askAi()), weightParams());
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

    public boolean currentPageInternal() {
        Tab tab = currentTab();
        if (tab == null) {
            return false;
        }
        if (UrlUtils.isInternal(tab.url)) {
            return true;
        }
        return tab.web != null && UrlUtils.isInternal(tab.web.getUrl());
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
            handlePageScroll(y);
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
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= 16) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setTextZoom(Prefs.fontZoom());
        settings.setGeolocationEnabled(true);
        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(true);
        }
        try {
            CookieManager.getInstance().setAcceptThirdPartyCookies(
                    web, !PrivacyPrefs.blockThirdPartyCookies());
        } catch (Throwable ignored) {
        }
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
                WebSettingsCompat.setWebAuthenticationSupport(settings,
                        WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER);
                if (WebSettingsCompat.getWebAuthenticationSupport(settings)
                        == WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE) {
                    WebSettingsCompat.setWebAuthenticationSupport(settings,
                            WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP);
                }
            }
        } catch (Throwable ignored) {
        }
        if (Prefs.webDark() || Glass.night(this)) {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true);
                } else if (Build.VERSION.SDK_INT >= 29) {
                    settings.setForceDark(WebSettings.FORCE_DARK_ON);
                }
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
        web.setWebChromeClient(Build.VERSION.SDK_INT >= 21 ? new HChrome21(web) : new HChrome(web));
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                Downloader.enqueue(BrowserActivity.this, url, userAgent, contentDisposition, mimeType);
                // 纯下载不会触发页面导航，loadUrl 乐观写入的地址要还原成发起下载前的页面，
                // 否则底栏停在下载链接、主页层被隐藏，与 WebView 实际内容不一致
                Tab tab = tabFor(web);
                if (tab != null && url.equals(tab.url)
                        && tab.urlBeforeLoad != null && !tab.urlBeforeLoad.isEmpty()) {
                    tab.url = tab.urlBeforeLoad;
                    tab.urlBeforeLoad = null;
                    if (tab == currentTab()) {
                        updateAddress();
                    }
                }
            }
        });
        web.addJavascriptInterface(new GmBridge(this, web), "HGM");
        web.addJavascriptInterface(new NativeBridge(this), "HNative");
        Injection.install(web, this);
        TrustedChannel.install(web, tab.incognito);
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
        if (tab.needLoad) {
            tab.needLoad = false;
            loadUrl(tab, tab.url);
        }
        if (tab.web.getParent() instanceof ViewGroup) {
            ((ViewGroup) tab.web.getParent()).removeView(tab.web);
        }
        pageHost.removeAllViews();
        pageHost.addView(tab.web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tab.web.onResume();
        progress.setVisibility(View.GONE);
        barHidden = false;
        lastScrollY = 0;
        updateUi();
        address.clearFocus();
        updateBottomBarLift();
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
        url = PrivacyNav.strip(url);
        url = PrivacyNav.upgrade(url);
        applySiteSettings(tab.web, url);
        tab.urlBeforeLoad = tab.url;
        tab.url = url;
        java.util.Map<String, String> headers = PrivacyNav.headers();
        if (headers.isEmpty()) {
            tab.web.loadUrl(url);
        } else {
            tab.web.loadUrl(url, headers);
        }
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

    private void submitAddress() {
        String query = address.getText().toString();
        if (query.trim().isEmpty()) {
            address.clearFocus();
            hideKeyboard();
            return;
        }
        search(query);
        hideKeyboard();
        address.clearFocus();
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
            titleView.setText(addressLabel(tab, home));
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

    private String addressLabel(Tab tab, boolean home) {
        if (home) {
            return tab.incognito ? "无痕 · 主页" : "主页";
        }
        String url = tab.url == null ? "" : tab.url;
        String title = tab.title;
        String host = UrlUtils.host(url);
        boolean internal = UrlUtils.isInternal(url);
        int style = Prefs.addressStyle();
        String label;
        if (style == 0 || internal) {
            label = title == null || title.isEmpty() || title.equals(url)
                    ? (host.isEmpty() ? url : host) : title;
        } else if (style == 2) {
            int scheme = url.indexOf("://");
            label = scheme >= 0 ? url.substring(scheme + 3) : url;
        } else {
            label = host.startsWith("www.") ? host.substring(4) : host;
            if (label.isEmpty()) {
                label = title == null || title.isEmpty() ? url : title;
            }
        }
        return tab.incognito ? "无痕 · " + label : label;
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

    private void applyWebDarkToTabs(boolean reload) {
        boolean enable = Prefs.webDark() || Glass.night(this);
        for (Tab tab : tabs) {
            if (tab.web == null) {
                continue;
            }
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                            tab.web.getSettings(), enable);
                } else if (Build.VERSION.SDK_INT >= 29) {
                    tab.web.getSettings().setForceDark(enable
                            ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
                }
            } catch (Throwable ignored) {
            }
            if (reload && !UrlUtils.isInternal(tab.url)) {
                tab.web.reload();
            }
        }
    }

    private void injectCosmetic(WebView web, String url) {
        if (UrlUtils.isInternal(url) || !AdBlock.isEnabled() || !siteAdAllowed(url)) {
            return;
        }
        String script = AdBlock.injectScript(url);
        if (!script.isEmpty()) {
            web.evaluateJavascript(script, null);
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

    private static final class MenuGestureLayout extends FrameLayout {
        interface Callback {
            void onHorizontal(float dx);

            void onPageSwipe(float dx);

            void onDrag(float dy);

            void onDragRelease(float dy, float velocityY);
        }

        private final int slop;
        private float downX;
        private float downY;
        private boolean horizontal;
        private boolean vertical;
        private Callback callback;
        private VelocityTracker tracker;

        MenuGestureLayout(Context context) {
            super(context);
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
            setClickable(true);
        }

        void setCallback(Callback callback) {
            this.callback = callback;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    horizontal = false;
                    vertical = false;
                    recycleTracker();
                    tracker = VelocityTracker.obtain();
                    tracker.addMovement(ev);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (tracker != null) {
                        tracker.addMovement(ev);
                    }
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    if (!horizontal && !vertical) {
                        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                            horizontal = true;
                        } else if (dy > slop && dy > Math.abs(dx) * 1.2f) {
                            vertical = true;
                        }
                    }
                    return horizontal || vertical;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    recycleTracker();
                    return false;
                default:
                    return false;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (tracker != null) {
                tracker.addMovement(ev);
            }
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    horizontal = false;
                    vertical = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    if (!horizontal && !vertical) {
                        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                            horizontal = true;
                        } else if (dy > slop && dy > Math.abs(dx) * 1.2f) {
                            vertical = true;
                        }
                    }
                    if (callback != null) {
                        if (horizontal) {
                            callback.onHorizontal(dx);
                        } else if (vertical) {
                            callback.onDrag(Math.max(0f, dy));
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    float velocityY = 0f;
                    if (tracker != null) {
                        tracker.computeCurrentVelocity(1000);
                        velocityY = tracker.getYVelocity();
                    }
                    recycleTracker();
                    if (callback != null) {
                        if (horizontal) {
                            callback.onPageSwipe(dx);
                        } else if (vertical) {
                            callback.onDragRelease(Math.max(0f, dy), velocityY);
                        } else {
                            performClick();
                        }
                    }
                    horizontal = false;
                    vertical = false;
                    return true;
                }
                default:
                    return true;
            }
        }

        private void recycleTracker() {
            if (tracker != null) {
                tracker.recycle();
                tracker = null;
            }
        }
    }

    private static final class MenuAction {
        final String id;
        final int icon;
        final String label;
        final boolean enabled;
        final Runnable task;

        MenuAction(String id, int icon, String label, boolean enabled, Runnable task) {
            this.id = id;
            this.icon = icon;
            this.label = label;
            this.enabled = enabled;
            this.task = task;
        }
    }

    private void showMenu() {
        if (menuDialog != null) {
            return;
        }
        if (editingAddress) {
            hideKeyboard();
            endEdit();
        }
        if (barHidden) {
            barHidden = false;
            updateBottomBarLift();
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
        sheet.setClickable(true);

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

        final Runnable[] closeRef = new Runnable[1];
        for (int i = 0; i < pages.size(); i++) {
            FrameLayout holder = new FrameLayout(this);
            holder.setBackground(selectableBackground());
            holder.setContentDescription("菜单第" + (i + 1) + "页");
            View dot = new View(this);
            holder.addView(dot, new FrameLayout.LayoutParams(dp(5), dp(5), Gravity.CENTER));
            final int index = i;
            holder.setOnClickListener(v -> {
                if (currentPage[0] == index) {
                    return;
                }
                int direction = index > currentPage[0] ? 1 : -1;
                currentPage[0] = index;
                animateMenuPage(grid, dotViews, pages, index, dialog, direction);
            });
            dots.addView(holder, new LinearLayout.LayoutParams(dp(17), dp(17)));
            dotViews.add(dot);
        }
        sheet.addView(dots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        renderMenuPage(grid, dotViews, pages.get(0), 0, dialog);

        MenuGestureLayout floating = new MenuGestureLayout(this);
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
        int barHeight = anchorBar == null || anchorBar.getHeight() <= 0
                ? dp(56) : anchorBar.getHeight();
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
            float distance = Math.max(0f, sheet.getTranslationY()) + dp(70);
            sheet.animate().translationY(distance).alpha(0f).setDuration(160)
                    .withEndAction(dialog::dismiss).start();
            if (menuShadow != null) {
                menuShadow.animate().translationY(distance).alpha(0f).setDuration(160).start();
            }
        };
        closeRef[0] = closeMenu;
        floating.setOnClickListener(v -> closeMenu.run());
        floating.setCallback(new MenuGestureLayout.Callback() {
            @Override
            public void onHorizontal(float dx) {
                if (pages.size() > 1) {
                    grid.setTranslationX(dx * 0.32f);
                }
            }

            @Override
            public void onPageSwipe(float dx) {
                if (pages.size() <= 1) {
                    return;
                }
                int direction = dx < 0 ? 1 : -1;
                int next = currentPage[0] + direction;
                if (Math.abs(dx) < dp(36) || next < 0 || next >= pages.size()) {
                    grid.animate().translationX(0f).setDuration(120).start();
                    return;
                }
                currentPage[0] = next;
                animateMenuPage(grid, dotViews, pages, next, dialog, direction);
            }

            @Override
            public void onDrag(float dy) {
                sheet.setTranslationY(dy);
                sheet.setAlpha(1f - Math.min(0.45f,
                        dy / Math.max(1f, sheet.getHeight()) * 0.8f));
                if (menuShadow != null) {
                    menuShadow.setTranslationY(dy);
                }
            }

            @Override
            public void onDragRelease(float dy, float velocityY) {
                if (dy > dp(88) || velocityY > 1100f) {
                    closeMenu.run();
                    return;
                }
                sheet.animate().translationY(0f).alpha(1f).setDuration(160).start();
                if (menuShadow != null) {
                    menuShadow.animate().translationY(0f).alpha(1f).setDuration(160).start();
                }
            }
        });
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
        menuDialog = dialog;
        // 窗口参数必须在 show() 之前设置，否则首帧会按默认居中小窗绘制，
        // 出现先闪到一侧再跳到正确位置的跳变。
        android.view.Window menuWindow = dialog.getWindow();
        if (menuWindow != null) {
            menuWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            menuWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            menuWindow.setDimAmount(0f);
            menuWindow.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            menuWindow.setGravity(Gravity.BOTTOM);
            menuWindow.setWindowAnimations(0);
            if (Glass.frosted()) {
                Glass.applyWindowBlur(menuWindow);
            }
        }
        floating.setAlpha(0f);
        sheet.setTranslationY(dp(26));
        if (menuShadow != null) {
            menuShadow.setTranslationY(dp(26));
        }
        dialog.setOnShowListener(d -> {
            floating.animate().alpha(1f).setDuration(120).start();
            sheet.animate().translationY(0f).setDuration(200).start();
            if (menuShadow != null) {
                menuShadow.animate().translationY(0f).setDuration(200).start();
            }
        });
        dialog.setOnDismissListener(d -> {
            menuDialog = null;
            if (menuGlass != null) {
                menuGlass.release();
            }
        });
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
        Set<String> hidden = MenuDefs.hiddenIds();
        int limit = applyHidden && Glass.menuSimple()
                ? MenuDefs.SIMPLE_COUNT : Integer.MAX_VALUE;
        List<MenuAction> actions = new ArrayList<>();
        for (String id : MenuDefs.order()) {
            if (applyHidden && !MenuDefs.CUSTOMIZE_ID.equals(id) && hidden.contains(id)) {
                continue;
            }
            MenuAction action = buildMenuAction(id, content);
            if (action == null) {
                continue;
            }
            actions.add(action);
            if (actions.size() >= limit) {
                break;
            }
        }
        if (actions.isEmpty()) {
            actions.add(buildMenuAction(MenuDefs.CUSTOMIZE_ID, content));
        }
        List<List<MenuAction>> pages = new ArrayList<>();
        if (applyHidden && Glass.menuSimple()) {
            pages.add(actions);
            return pages;
        }
        for (int start = 0; start < actions.size(); start += MenuDefs.PAGE_SIZE) {
            pages.add(new ArrayList<>(actions.subList(start,
                    Math.min(actions.size(), start + MenuDefs.PAGE_SIZE))));
        }
        return pages;
    }

    private MenuAction buildMenuAction(String id, boolean content) {
        MenuDefs.Item item = MenuDefs.find(id);
        if (item == null) {
            return null;
        }
        Runnable task;
        switch (id) {
            case "night":
                task = this::toggleNightMode;
                break;
            case "bookmarks":
                task = () -> openUrl("v://bookmarks", false);
                break;
            case "history":
                task = () -> openUrl("v://history", false);
                break;
            case "downloads":
                task = this::openDownloads;
                break;
            case "incognito":
                task = () -> newTab(Prefs.homepage(), true);
                break;
            case "share":
                task = this::sharePage;
                break;
            case "add_bookmark":
                task = this::toggleBookmark;
                break;
            case "desktop":
                task = this::uaDialog;
                break;
            case "toolbox":
                task = this::toolboxDialog;
                break;
            case "settings":
                task = this::openSettings;
                break;
            case "find":
                task = this::showFindBar;
                break;
            case "save":
                task = this::saveOffline;
                break;
            case "offline":
                task = this::showOfflinePages;
                break;
            case "translate":
                task = this::translateSelection;
                break;
            case "source":
                task = this::viewSource;
                break;
            case "fullscreen":
                task = this::toggleFullscreen;
                break;
            case "images":
                task = this::toggleImages;
                break;
            case "sniffer":
                task = () -> startActivity(new Intent(this, MediaListActivity.class));
                break;
            case "site_settings":
                task = this::siteSettingsDialog;
                break;
            case "netlog":
                task = this::showNetLog;
                break;
            case "scan":
                task = this::startScan;
                break;
            case "shortcut":
                task = this::addShortcut;
                break;
            case "tts":
                task = this::readAloud;
                break;
            case "ai":
                task = this::askAi;
                break;
            case "rotate":
                task = this::orientationDialog;
                break;
            case "adblock":
                task = this::toggleAdBlock;
                break;
            case "mark_ad":
                task = this::markAd;
                break;
            case "font":
                task = this::showZoomDialog;
                break;
            case "report":
                task = this::reportSite;
                break;
            case "privacy":
                task = this::showPrivacyReport;
                break;
            case MenuDefs.CUSTOMIZE_ID:
                task = this::customizeMenu;
                break;
            default:
                return null;
        }
        boolean enabled = !item.needsContent || content;
        return new MenuAction(item.id, item.icon, item.label, enabled, task);
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

    private void animateMenuPage(LinearLayout grid, List<View> dotViews,
                                 List<List<MenuAction>> pages, int pageIndex,
                                 android.app.Dialog dialog, int direction) {
        int width = Math.max(grid.getWidth(), dp(260));
        float out = direction >= 0 ? -width * 0.22f : width * 0.22f;
        grid.animate().cancel();
        grid.animate().translationX(out).alpha(0.2f).setDuration(90).withEndAction(() -> {
            grid.setTranslationX(-out);
            grid.setAlpha(0.2f);
            renderMenuPage(grid, dotViews, pages.get(pageIndex), pageIndex, dialog);
            grid.animate().translationX(0f).alpha(1f).setDuration(140).start();
        }).start();
    }

    private void renderMenuPage(LinearLayout grid, List<View> dotViews,
                                List<MenuAction> actions, int pageIndex,
                                final android.app.Dialog dialog) {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < actions.size(); i++) {
            if (i % MenuDefs.COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = dp(10);
                }
                grid.addView(row, rowParams);
            }
            row.addView(menuCell(dialog, actions.get(i)),
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

    private View menuCell(final android.app.Dialog dialog, final MenuAction action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setBackground(selectableBackground());
        cell.setPadding(dp(2), dp(10), dp(2), dp(8));
        ImageView image = new androidx.appcompat.widget.AppCompatImageView(this);
        image.setImageResource(action.icon);
        image.setColorFilter(color(R.color.on_surface));
        cell.addView(image, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = new TextView(this);
        label.setText(action.label);
        label.setTextSize(12);
        boolean highlight = "night".equals(action.id) && isNightActive();
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
        final WebView web = tab.web;
        final boolean[] changed = {false};
        final BottomSheetDialog dialog = newSheet();
        LinearLayout list = listOf(dialog);
        sheetTitle(list, host, "网站设定 · 仅对当前网站生效");
        sheetToggle(list, R.drawable.ic_source, "JavaScript", "关闭后本页脚本不再执行",
                Prefs.get().getBoolean("site_js_" + host, true), checked -> {
                    Prefs.get().edit().putBoolean("site_js_" + host, checked).apply();
                    applySiteSettings(web, url);
                    changed[0] = true;
                });
        sheetToggle(list, R.drawable.ic_image, "加载图片", "关闭后本页不下载图片",
                Prefs.get().getBoolean("site_img_" + host,
                        !Prefs.get().getBoolean("block_images", false)), checked -> {
                    Prefs.get().edit().putBoolean("site_img_" + host, checked).apply();
                    applySiteSettings(web, url);
                    changed[0] = true;
                });
        sheetToggle(list, R.drawable.ic_desktop, "桌面版网页", "以电脑浏览器标识请求本页",
                Prefs.get().getBoolean("site_ua_" + host, false), checked -> {
                    Prefs.get().edit().putBoolean("site_ua_" + host, checked).apply();
                    applySiteSettings(web, url);
                    changed[0] = true;
                });
        sheetToggle(list, R.drawable.ic_block, "广告拦截", "关闭后本站不再过滤广告与跟踪器",
                Prefs.get().getBoolean("site_ad_" + host, true), checked -> {
                    Prefs.get().edit().putBoolean("site_ad_" + host, checked).apply();
                    changed[0] = true;
                });
        sheetToggle(list, R.drawable.ic_shield_check, "清理跟踪参数",
                "关闭后本站链接保留 utm 等跟踪参数",
                !"false".equals(Prefs.get().getString("site_strip_" + host, null)), checked -> {
                    android.content.SharedPreferences.Editor editor = Prefs.get().edit();
                    if (checked) {
                        editor.remove("site_strip_" + host);
                    } else {
                        editor.putString("site_strip_" + host, "false");
                    }
                    editor.apply();
                });
        sheetToggle(list, R.drawable.ic_lock, "仅 HTTPS",
                "关闭后本站允许以 HTTP 访问",
                !"true".equals(Prefs.get().getString("site_http_" + host, null)), checked -> {
                    android.content.SharedPreferences.Editor editor = Prefs.get().edit();
                    if (checked) {
                        editor.remove("site_http_" + host);
                    } else {
                        editor.putString("site_http_" + host, "true");
                    }
                    editor.apply();
                });
        sheetNote(list, permissionSummary(host));
        sheetButtonRow(list, "重置本站权限", color(R.color.primary), () -> {
            PermissionStore.clearHost(this, host);
            toast("已重置本站权限");
        });
        sheetButtonRow(list, "重置网站设定", color(R.color.primary), () -> {
            Prefs.get().edit()
                    .remove("site_js_" + host).remove("site_img_" + host)
                    .remove("site_ua_" + host).remove("site_ad_" + host)
                    .remove("site_strip_" + host).remove("site_http_" + host).apply();
            applySiteSettings(web, url);
            if (web != null) {
                web.reload();
            }
            dialog.dismiss();
            toast("已重置网站设定");
        });
        sheetButtonRow(list, "清除本站数据（Cookie/存储）", color(R.color.error),
                () -> clearSiteData(host, web));
        dialog.setOnDismissListener(d -> {
            if (changed[0] && web != null) {
                web.reload();
            }
        });
        dialog.show();
    }

    private String permissionSummary(String host) {
        String[] types = {"geolocation", "camera", "microphone"};
        String[] names = {"位置", "相机", "麦克风"};
        StringBuilder sb = new StringBuilder("本站权限：");
        for (int i = 0; i < types.length; i++) {
            int mode = PermissionStore.mode(this, types[i], host);
            String value = mode == PermissionStore.ALLOW ? "允许"
                    : mode == PermissionStore.DENY ? "拒绝" : "询问";
            if (i > 0) {
                sb.append(" · ");
            }
            sb.append(names[i]).append(' ').append(value);
        }
        return sb.toString();
    }

    private void clearSiteData(String host, WebView web) {
        try {
            CookieManager cm = CookieManager.getInstance();
            for (String scheme : new String[]{"https", "http"}) {
                String base = scheme + "://" + host;
                String cookie = cm.getCookie(base);
                if (cookie == null) {
                    continue;
                }
                for (String pair : cookie.split(";")) {
                    String name = pair.split("=", 2)[0].trim();
                    if (!name.isEmpty()) {
                        cm.setCookie(base, name + "=; Max-Age=0; Path=/");
                    }
                }
            }
            cm.flush();
        } catch (Throwable ignored) {
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                android.webkit.WebStorage storage = android.webkit.WebStorage.getInstance();
                storage.deleteOrigin("https://" + host);
                storage.deleteOrigin("http://" + host);
            } catch (Throwable ignored) {
            }
        }
        if (web != null) {
            web.reload();
        }
        toast("已清除 " + host + " 的 Cookie 与站点存储");
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
        String display = host == null || host.isEmpty() ? String.valueOf(url) : host;
        final BottomSheetDialog dialog = newSheet();
        LinearLayout list = listOf(dialog);
        sheetTitle(list, display, "网站信息");
        sheetNote(list, "连接" + (secure ? "安全（HTTPS）" : "不安全（HTTP）")
                + (tab.incognito ? "\n无痕标签页：不记录历史与隐私统计" : "")
                + (url == null || url.isEmpty() ? "" : "\n" + url));
        if (!UrlUtils.isInternal(url)) {
            sheetButtonRow(list, "网站设定", color(R.color.primary), () -> {
                dialog.dismiss();
                siteSettingsDialog();
            });
            sheetButtonRow(list, "网络日志", color(R.color.primary), () -> {
                dialog.dismiss();
                showNetLog();
            });
        }
        dialog.show();
    }

    private void showNetLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("已拦截 ").append(AdBlock.blockedCount()).append(" 次请求\n")
                .append("规则 ").append(AdBlock.ruleCount()).append(" 条\n")
                .append(AdBlock.statsSummary()).append('\n');
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

    private void showPrivacyReport() {
        final BottomSheetDialog dialog = newSheet();
        LinearLayout list = listOf(dialog);
        sheetTitle(list, "隐私报告",
                "今日拦截 " + PrivacyStats.blockedToday()
                        + " · 指纹读取 " + PrivacyStats.fingerprintsToday()
                        + " · 清参 " + PrivacyStats.strippedToday());
        StringBuilder stats = new StringBuilder();
        stats.append("广告与跟踪器：今日 ").append(PrivacyStats.blockedToday())
                .append(" 次，累计 ").append(PrivacyStats.blockedTotal()).append(" 次（")
                .append(AdBlock.statsSummary()).append("）\n")
                .append("指纹读取：今日 ").append(PrivacyStats.fingerprintsToday())
                .append(" 次，累计 ").append(PrivacyStats.fingerprintsTotal()).append(" 次\n")
                .append("清理跟踪参数：今日 ").append(PrivacyStats.strippedToday())
                .append(" 次，累计 ").append(PrivacyStats.strippedTotal()).append(" 次\n")
                .append("权限询问：今日 ").append(PrivacyStats.permissionsToday()).append(" 次");
        org.json.JSONArray frequent = PrivacyStats.frequentPermissions(3);
        if (frequent.length() > 0) {
            stats.append("\n\n近 24 小时频繁请求敏感权限的站点：");
            for (int i = 0; i < frequent.length(); i++) {
                org.json.JSONObject event = frequent.optJSONObject(i);
                if (event == null) {
                    continue;
                }
                String kind = event.optString("kind", "");
                String name = "geolocation".equals(kind) ? "位置"
                        : kind.startsWith("camera") && kind.contains("microphone") ? "相机+麦克风"
                        : "camera".equals(kind) ? "相机"
                        : "microphone".equals(kind) ? "麦克风" : kind;
                stats.append("\n· ").append(event.optString("host", ""))
                        .append("（").append(name).append(" ×")
                        .append(event.optInt("count", 0)).append("）");
            }
        }
        sheetNote(list, stats.toString());
        sheetToggle(list, R.drawable.ic_shield_check, "不跟踪（DNT / GPC）",
                "请求头与页面均发送隐私信号", PrivacyPrefs.dnt(), checked -> {
                    PrivacyPrefs.setDnt(checked);
                    refreshPrivacyInjection();
                });
        sheetToggle(list, R.drawable.ic_visibility_off, "WebRTC 防泄露",
                "阻止网页利用 WebRTC 探测真实 IP", PrivacyPrefs.blockWebRtc(), checked -> {
                    PrivacyPrefs.setBlockWebRtc(checked);
                    refreshPrivacyInjection();
                });
        sheetToggle(list, R.drawable.ic_lock, "仅 HTTPS",
                "HTTP 页面自动升级；失败可手动继续", PrivacyPrefs.httpsOnly(),
                PrivacyPrefs::setHttpsOnly);
        sheetToggle(list, R.drawable.ic_block, "阻止第三方 Cookie",
                "关闭可提高兼容性；默认允许", PrivacyPrefs.blockThirdPartyCookies(), checked -> {
                    PrivacyPrefs.setBlockThirdPartyCookies(checked);
                    for (Tab tab : tabs) {
                        if (tab.web != null) {
                            CookieManager.getInstance().setAcceptThirdPartyCookies(
                                    tab.web, !checked);
                        }
                    }
                });
        sheetToggle(list, R.drawable.ic_tune, "清理跟踪参数",
                "剥离 utm、fbclid 等参数，保留登录回调", PrivacyPrefs.stripTracking(),
                PrivacyPrefs::setStripTracking);
        sheetToggle(list, R.drawable.ic_shield_check, "指纹保护",
                "Canvas / WebGL 微噪声，站点可单独豁免", PrivacyPrefs.fingerprintMaster(),
                checked -> {
                    Prefs.get().edit().putBoolean("privacy_fp", checked).apply();
                    refreshPrivacyInjection();
                });
        sheetNote(list, privateDnsSummary());
        sheetButtonRow(list, "清空隐私统计", color(R.color.primary), () -> {
            PrivacyStats.reset();
            dialog.dismiss();
            toast("已清空隐私统计");
        });
        sheetButtonRow(list, "反跟踪订阅与规则", color(R.color.primary), () -> {
            dialog.dismiss();
            openUrl("v://log", false);
        });
        dialog.show();
    }

    private void refreshPrivacyInjection() {
        for (Tab tab : tabs) {
            if (tab.web != null) {
                Injection.install(tab.web, this);
            }
        }
        WebView web = currentWeb();
        if (web != null) {
            web.reload();
        }
    }

    private String privateDnsSummary() {
        if (Build.VERSION.SDK_INT < 28) {
            return "系统 Private DNS：当前系统版本不支持";
        }
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            android.net.LinkProperties lp = cm == null || cm.getActiveNetwork() == null
                    ? null : cm.getLinkProperties(cm.getActiveNetwork());
            if (lp == null) {
                return "系统 Private DNS：无活动网络";
            }
            String name = lp.getPrivateDnsServerName();
            return "系统 Private DNS：" + (lp.isPrivateDnsActive()
                    ? (name == null ? "已启用（自动）" : "已启用 · " + name)
                    : "未启用（跟随系统设置）");
        } catch (Throwable t) {
            return "系统 Private DNS：无法读取";
        }
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
        pickerActive = true;
        web.postDelayed(() -> pickerActive = false, 16000);
        web.evaluateJavascript(
                "(function(){if(window.__hAdPick)return;window.__hAdPick=1;"
                        + "var bar=document.createElement('div');"
                        + "bar.textContent='点击要隐藏的广告元素，点此提示条退出';"
                        + "bar.style.cssText='position:fixed;left:12px;right:12px;"
                        + "top:calc(env(safe-area-inset-top,0px) + 12px);z-index:2147483647;"
                        + "padding:12px 14px;border-radius:14px;text-align:center;"
                        + "font:14px -apple-system,sans-serif;color:#fff;background:rgba(20,24,26,.92);"
                        + "box-shadow:0 6px 24px rgba(0,0,0,.35)';"
                        + "document.documentElement.appendChild(bar);"
                        + "function escId(v){return String(v).replace(/([^a-zA-Z0-9_-])/g,'\\\\$1');}"
                        + "function stable(c){return c&&c.length<40&&/^[a-zA-Z0-9_-]+$/.test(c);}"
                        + "function sel(el){var parts=[],node=el,d=0;"
                        + "while(node&&node.nodeType===1&&d<5){var s=node.tagName.toLowerCase();"
                        + "if(node.id){var test='#'+escId(node.id);try{"
                        + "if(document.querySelectorAll(test).length===1){parts.unshift(test);break;}}catch(x){}}"
                        + "var cls=[];for(var i=0;i<node.classList.length&&cls.length<2;i++){"
                        + "if(stable(node.classList[i]))cls.push(node.classList[i]);}"
                        + "if(cls.length)s+='.'+cls.join('.');"
                        + "var p=node.parentElement;if(p){var same=0,idx=0;"
                        + "for(var j=0;j<p.children.length;j++){var c=p.children[j];"
                        + "if(c.tagName===node.tagName){same++;if(c===node)idx=same;}}"
                        + "if(same>1)s+=':nth-of-type('+idx+')';}"
                        + "parts.unshift(s);var cand=parts.join(' > ');"
                        + "try{if(document.querySelectorAll(cand).length<=3)return cand;}catch(x){}"
                        + "node=p;d++;}return parts.join(' > ')||el.tagName.toLowerCase();}"
                        + "function done(){window.__hAdPick=0;"
                        + "if(bar.parentNode)bar.parentNode.removeChild(bar);"
                        + "document.removeEventListener('click',pick,true);}"
                        + "function pick(e){"
                        + "if(e.target===bar||bar.contains(e.target)){e.preventDefault();"
                        + "e.stopPropagation();done();return;}"
                        + "e.preventDefault();e.stopPropagation();var el=e.target;"
                        + "if(!el||el.nodeType!==1)return;var s=sel(el);"
                        + "try{el.style.setProperty('display','none','important');}catch(x){}"
                        + "try{HNative.saveAdRule(location.hostname,s);}catch(x){}done();}"
                        + "document.addEventListener('click',pick,true);setTimeout(done,15000);})()",
                null);
        toast("标记模式：点选广告元素，点击顶部提示条退出");
    }

    public void saveAdRule(String host, String selector) {
        if (!pickerActive) {
            return;
        }
        Tab tab = currentTab();
        if (tab == null || tab.web == null) {
            return;
        }
        String currentHost = UrlUtils.host(tab.url);
        if (currentHost.isEmpty() || host == null
                || !currentHost.equalsIgnoreCase(host.trim())) {
            return;
        }
        pickerActive = false;
        final WebView web = tab.web;
        final String url = tab.url;
        new Thread(() -> {
            boolean ok = AdBlock.saveUserRule(BrowserActivity.this, currentHost, selector);
            runOnUiThread(() -> {
                if (!ok) {
                    toast("规则无效或已达 500 条上限");
                    return;
                }
                toast("已保存广告规则");
                if (web != null && url != null) {
                    web.evaluateJavascript(AdBlock.injectScript(url), null);
                }
            });
        }, "adblock-save-rule").start();
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
        MenuDefs.showEditor(this, () -> toast("菜单已更新"));
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
            if (!Glass.solid()) {
                // sheet 内容密集，玻璃本体保持半透明会让背景文字与正文互相干扰，
                // 这里用接近不透明的同色底，边框仍沿用玻璃描边。
                int fill = Glass.night(this) ? 0xF2181F20 : 0xF2FCFDFD;
                bg.setColors(new int[]{fill, fill});
            }
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

    private TextView sheetTitle(LinearLayout list, String title, String subtitle) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(18), dp(16), dp(18), dp(8));
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(18);
        name.setTextColor(color(R.color.on_surface));
        head.addView(name);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(13);
            sub.setTextColor(color(R.color.on_surface_variant));
            sub.setPadding(0, dp(6), 0, 0);
            head.addView(sub);
        }
        list.addView(head);
        return name;
    }

    private void sheetToggle(LinearLayout list, int icon, String text, String subtitle,
                             boolean checked, java.util.function.Consumer<Boolean> change) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(18), dp(6), dp(18), dp(6));
        row.setBackground(selectableBackground());
        ImageView image = new androidx.appcompat.widget.AppCompatImageView(this);
        image.setImageResource(icon);
        row.addView(image, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.setMarginStart(dp(16));
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16);
        label.setTextColor(color(R.color.on_surface));
        texts.addView(label);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(12);
            sub.setTextColor(color(R.color.on_surface_variant));
            sub.setPadding(0, dp(2), 0, 0);
            texts.addView(sub);
        }
        row.addView(texts, textsParams);
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(checked);
        toggle.setClickable(false);
        toggle.setFocusable(false);
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> {
            toggle.setChecked(!toggle.isChecked());
            change.accept(toggle.isChecked());
        });
        list.addView(row);
    }

    private void sheetButtonRow(LinearLayout list, String text, int textColor, Runnable action) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setTextColor(textColor);
        button.setBackground(selectableBackground());
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setOnClickListener(v -> action.run());
        list.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void sheetNote(LinearLayout list, String text) {
        TextView note = new TextView(this);
        note.setText(text);
        note.setTextSize(13);
        note.setTextColor(color(R.color.on_surface_variant));
        note.setLineSpacing(0, 1.3f);
        note.setPadding(dp(18), dp(10), dp(18), dp(6));
        list.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
            return;
        }
        if (requestCode == REQ_PERM_MEDIA || requestCode == REQ_PERM_LOCATION) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                }
            }
            Runnable grant = pendingPermGrant;
            Runnable deny = pendingPermDeny;
            pendingPermGrant = null;
            pendingPermDeny = null;
            if (granted) {
                if (grant != null) {
                    grant.run();
                }
            } else if (deny != null) {
                deny.run();
            }
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
        saveSession();
        WebView web = currentWeb();
        if (web != null) {
            web.onPause();
        }
    }

    private void saveSession() {
        try {
            JSONArray arr = new JSONArray();
            int cur = -1;
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
                if (tab.incognito) {
                    continue;
                }
                String u = tab.url;
                if (u == null || u.isEmpty() || u.startsWith("about:")) {
                    continue;
                }
                JSONObject o = new JSONObject();
                o.put("url", u);
                o.put("title", tab.title == null ? "" : tab.title);
                if (i == current) {
                    cur = arr.length();
                }
                arr.put(o);
            }
            Prefs.get().edit()
                    .putString("tabs_json", arr.toString())
                    .putInt("tabs_current", Math.max(0, cur))
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private boolean restoreSession() {
        String json = Prefs.get().getString("tabs_json", "");
        if (json.isEmpty()) {
            return false;
        }
        List<String> urls = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String u = o.optString("url", "");
                if (u.isEmpty()) {
                    continue;
                }
                urls.add(u);
                titles.add(o.optString("title", ""));
            }
        } catch (Throwable t) {
            return false;
        }
        if (urls.isEmpty()) {
            return false;
        }
        for (int i = 0; i < urls.size(); i++) {
            Tab tab = newEmptyTab(false);
            tab.url = urls.get(i);
            tab.title = titles.get(i).isEmpty() ? tab.url : titles.get(i);
            tab.needLoad = true;
        }
        int index = Math.max(0, Math.min(Prefs.get().getInt("tabs_current", 0), tabs.size() - 1));
        Tab target = tabs.get(index);
        target.needLoad = false;
        selectTab(index);
        loadUrl(target, target.url);
        return true;
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
        boolean webDark = Prefs.webDark();
        if (webDarkApplied == null) {
            webDarkApplied = webDark;
        } else if (webDarkApplied != webDark) {
            webDarkApplied = webDark;
            applyWebDarkToTabs(true);
        }
        applyToolbarStyle();
        String signature = (PrivacyPrefs.dnt() ? "1" : "0")
                + (PrivacyPrefs.blockWebRtc() ? "1" : "0")
                + (PrivacyPrefs.fingerprintMaster() ? "1" : "0")
                + (PrivacyPrefs.fingerprint("canvas") ? "1" : "0")
                + (PrivacyPrefs.fingerprint("webgl") ? "1" : "0")
                + PrivacyPrefs.fingerprintExemptions().toString();
        if (privacySignature == null) {
            privacySignature = signature;
        } else if (!privacySignature.equals(signature)) {
            privacySignature = signature;
            for (Tab tab : tabs) {
                if (tab.web != null) {
                    Injection.install(tab.web, this);
                }
            }
        }
        updateHomeOverlay();
        updateBarMode();
        updateUi();
    }

    @Override
    protected void onDestroy() {
        boolean clearOnExit = !isChangingConfigurations()
                && Prefs.get().getBoolean("clear_on_exit", false);
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
        if (clearOnExit) {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
            WebStorage.getInstance().deleteAllData();
            WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword();
        }
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
            assignPageUrl(tab, url);
            injectCosmetic(view, url);
            if (tab == currentTab()) {
                progress.setProgress(0);
                progress.setVisibility(View.VISIBLE);
                updateAddress();
                lastScrollY = 0;
                if (barHidden) {
                    barHidden = false;
                    updateBottomBarLift();
                }
                if (progressiveBlur != null) {
                    progressiveBlur.onPageStarted();
                }
            }
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            Tab tab = tabFor(view);
            if (tab == null) {
                return;
            }
            injectCosmetic(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Tab tab = tabFor(view);
            if (tab == null) {
                return;
            }
            assignPageUrl(tab, url);
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
            return intercept(view, url, false);
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
        public void onReceivedSslError(WebView view, final SslErrorHandler handler, final SslError error) {
            final String host = UrlUtils.host(error.getUrl());
            Tab tab = tabFor(view);
            final boolean incognito = tab != null && tab.incognito;
            if (SslExceptionStore.allows(host, error, incognito)) {
                handler.proceed();
                return;
            }
            if (sslDialogShowing) {
                handler.cancel();
                return;
            }
            sslDialogShowing = true;
            String fingerprint = SslExceptionStore.fingerprint(error);
            String shown = fingerprint.isEmpty() ? "无法读取"
                    : fingerprint.length() > 48 ? fingerprint.substring(0, 48) + "…" : fingerprint;
            final boolean[] answered = {false};
            final BottomSheetDialog sheet = newSheet();
            LinearLayout list = listOf(sheet);
            sheetTitle(list, "安全警告", host.isEmpty() ? "无法验证此网站的身份" : host);
            sheetNote(list, "问题：" + SslExceptionStore.errorName(error.getPrimaryError())
                    + "\n证书 SHA-256：" + shown
                    + "\n\n继续访问可能导致密码或数据被窃取。放行仅在当前会话对相同证书生效。");
            sheetButtonRow(list, "返回（推荐）", color(R.color.primary), () -> {
                answered[0] = true;
                sheet.dismiss();
                handler.cancel();
            });
            sheetButtonRow(list, "仍要继续", color(R.color.error), () -> {
                answered[0] = true;
                sheet.dismiss();
                SslExceptionStore.remember(host, error, incognito);
                handler.proceed();
            });
            sheet.setCancelable(false);
            sheet.setOnDismissListener(d -> {
                sslDialogShowing = false;
                if (!answered[0]) {
                    handler.cancel();
                }
            });
            sheet.show();
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                              String host, String realm) {
            if (WebViewProxy.shouldAutoAuth(host)) {
                handler.proceed(WebViewProxy.authUser(), WebViewProxy.authPass());
                return;
            }
            super.onReceivedHttpAuthRequest(view, handler, host, realm);
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
                    || "blob".equals(scheme) || "data".equals(scheme)
                    || "javascript".equals(scheme)) {
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    String target = PrivacyNav.upgrade(PrivacyNav.strip(url));
                    if (!target.equals(url)) {
                        view.loadUrl(target, PrivacyNav.headers());
                        return true;
                    }
                    applySiteSettings(view, url);
                }
                return false;
            }
            if ("mailto".equals(scheme) || "tel".equals(scheme) || "sms".equals(scheme)) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Throwable ignored) {
                }
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
            return intercept(view, request.getUrl().toString(), request.isForMainFrame());
        }
    }

    private WebResourceResponse intercept(WebView view, String url, boolean mainFrame) {
        if (url == null || url.startsWith("v://")) {
            return null;
        }
        if (UrlUtils.isMedia(url) && Prefs.get().getBoolean("auto_sniff", true)) {
            MediaSniffer.add(url);
        }
        Tab tab = tabFor(view);
        String pageUrl = tab != null && tab.url != null ? tab.url : view.getUrl();
        boolean incognito = tab != null && tab.incognito;
        if (!mainFrame && siteAdAllowed(pageUrl)
                && AdBlock.shouldBlock(url, pageUrl, false, incognito)) {
            return blockedResponse(url);
        }
        return null;
    }

    private WebResourceResponse blockedResponse(String url) {
        String type = com.h.browser.adblock.Rules.requestType(url);
        String mime;
        switch (type) {
            case "image":
                mime = "image/png";
                break;
            case "script":
                mime = "application/javascript";
                break;
            case "stylesheet":
                mime = "text/css";
                break;
            case "media":
                mime = "video/mp4";
                break;
            case "font":
                mime = "font/woff2";
                break;
            default:
                mime = "text/plain";
                break;
        }
        return new WebResourceResponse(mime, "utf-8",
                new ByteArrayInputStream(new byte[0]));
    }

    private class HChrome extends WebChromeClient {
        final WebView hostView;

        HChrome(WebView view) {
            hostView = view;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(final String origin,
                                                       final GeolocationPermissions.Callback callback) {
            final String host = UrlUtils.host(origin);
            if (host.isEmpty()) {
                callback.invoke(origin, false, false);
                return;
            }
            int mode = PermissionStore.mode(BrowserActivity.this, "geolocation", host);
            if (mode == PermissionStore.ALLOW) {
                grantLocation(origin, callback, null);
                return;
            }
            if (mode == PermissionStore.DENY) {
                callback.invoke(origin, false, false);
                return;
            }
            final boolean[] answered = {false};
            final BottomSheetDialog sheet = newSheet();
            LinearLayout list = listOf(sheet);
            sheetTitle(list, "位置权限", host + " 想要获取你的位置");
            sheetButtonRow(list, "仅本次", color(R.color.primary), () -> {
                answered[0] = true;
                sheet.dismiss();
                recordSitePermission(hostView, host, "geolocation", "once");
                grantLocation(origin, callback, null);
            });
            sheetButtonRow(list, "始终允许", color(R.color.primary), () -> {
                answered[0] = true;
                sheet.dismiss();
                recordSitePermission(hostView, host, "geolocation", "allow");
                grantLocation(origin, callback,
                        () -> PermissionStore.set(BrowserActivity.this,
                                "geolocation", host, PermissionStore.ALLOW));
            });
            sheetButtonRow(list, "拒绝", color(R.color.error), () -> {
                answered[0] = true;
                sheet.dismiss();
                recordSitePermission(hostView, host, "geolocation", "deny");
                PermissionStore.set(BrowserActivity.this, "geolocation", host,
                        PermissionStore.DENY);
                callback.invoke(origin, false, false);
            });
            sheet.setOnDismissListener(d -> {
                if (!answered[0]) {
                    callback.invoke(origin, false, false);
                }
            });
            sheet.show();
        }

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

    @android.annotation.TargetApi(21)
    private class HChrome21 extends HChrome {
        HChrome21(WebView view) {
            super(view);
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            Uri originUri = request.getOrigin();
            final String host = originUri == null ? "" : UrlUtils.host(originUri.toString());
            String[] resources = request.getResources();
            boolean video = false;
            boolean audio = false;
            for (String resource : resources) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                    video = true;
                } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                    audio = true;
                }
            }
            if (host.isEmpty() || (!video && !audio)) {
                denyPermissionRequest(request);
                return;
            }
            final boolean wantsVideo = video;
            final boolean wantsAudio = audio;
            final String type = video && audio ? "camera-microphone"
                    : video ? "camera" : "microphone";
            int mode = PermissionStore.mode(BrowserActivity.this, type, host);
            if (mode == PermissionStore.ALLOW) {
                grantMediaPermission(request, host, type, wantsVideo, wantsAudio, null);
                return;
            }
            if (mode == PermissionStore.DENY) {
                denyPermissionRequest(request);
                return;
            }
            String label = video && audio ? "相机和麦克风" : video ? "相机" : "麦克风";
            final boolean[] answered = {false};
            final BottomSheetDialog sheet = newSheet();
            LinearLayout list = listOf(sheet);
            sheetTitle(list, label + "权限", host + " 想要使用你的" + label);
            sheetButtonRow(list, "仅本次", color(R.color.primary), () -> {
                answered[0] = true;
                sheet.dismiss();
                recordSitePermission(hostView, host, type, "once");
                grantMediaPermission(request, host, type, wantsVideo, wantsAudio, null);
            });
            sheetButtonRow(list, "始终允许", color(R.color.primary), () -> {
                answered[0] = true;
                sheet.dismiss();
                recordSitePermission(hostView, host, type, "allow");
                grantMediaPermission(request, host, type, wantsVideo, wantsAudio,
                        () -> PermissionStore.set(BrowserActivity.this, type, host,
                                PermissionStore.ALLOW));
            });
            sheetButtonRow(list, "拒绝", color(R.color.error), () -> {
                answered[0] = true;
                sheet.dismiss();
                recordSitePermission(hostView, host, type, "deny");
                PermissionStore.set(BrowserActivity.this, type, host, PermissionStore.DENY);
                denyPermissionRequest(request);
            });
            sheet.setOnDismissListener(d -> {
                if (!answered[0]) {
                    denyPermissionRequest(request);
                }
            });
            sheet.show();
        }
    }

    private void grantLocation(final String origin,
                               final GeolocationPermissions.Callback callback,
                               final Runnable persist) {
        Runnable grant = () -> {
            if (persist != null) {
                persist.run();
            }
            try {
                callback.invoke(origin, true, false);
            } catch (Throwable ignored) {
            }
        };
        if (Build.VERSION.SDK_INT < 23
                || hasRuntimePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            grant.run();
            return;
        }
        pendingPermGrant = grant;
        pendingPermDeny = () -> {
            try {
                callback.invoke(origin, false, false);
            } catch (Throwable ignored) {
            }
        };
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERM_LOCATION);
    }

    private void grantMediaPermission(final PermissionRequest request, final String host,
                                      final String type, final boolean video, final boolean audio,
                                      final Runnable persist) {
        Runnable grant = () -> {
            if (persist != null) {
                persist.run();
            }
            try {
                request.grant(request.getResources());
            } catch (Throwable ignored) {
            }
        };
        List<String> missing = new ArrayList<>();
        if (video && !hasRuntimePermission(Manifest.permission.CAMERA)) {
            missing.add(Manifest.permission.CAMERA);
        }
        if (audio && !hasRuntimePermission(Manifest.permission.RECORD_AUDIO)) {
            missing.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT < 23 || missing.isEmpty()) {
            grant.run();
            return;
        }
        pendingPermGrant = grant;
        pendingPermDeny = () -> denyPermissionRequest(request);
        requestPermissions(missing.toArray(new String[0]), REQ_PERM_MEDIA);
    }

    private void denyPermissionRequest(PermissionRequest request) {
        try {
            request.deny();
        } catch (Throwable ignored) {
        }
    }

    private boolean hasRuntimePermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void recordSitePermission(WebView view, String host, String type, String decision) {
        Tab tab = view == null ? null : tabFor(view);
        if (tab != null && tab.incognito) {
            return;
        }
        PrivacyStats.recordPermission(host, type, decision);
    }

    private Intent fileIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        return intent;
    }
}

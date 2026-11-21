package com.h.browser.core;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
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
import androidx.webkit.WebViewAssetLoader;

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
import com.h.browser.theme.SkinStore;
import com.h.browser.translate.Translator;
import com.h.browser.tts.ReadAloudService;
import com.h.browser.ui.InternalPages;
import com.h.browser.ui.NativeBridge;
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
    private static final int REQ_SKIN = 1004;
    private static final int REQ_IMPORT_BM = 1005;
    private static final int REQ_EXPORT_BM = 1006;
    private static final int REQ_EXPORT_HISTORY = 1007;
    private static final int MAX_TABS = 12;
    private static final String LINK_JS = "(function(){if(window.__hLinkMenu)return;window.__hLinkMenu=1;"
            + "document.addEventListener('contextmenu',function(e){var t=e.target;"
            + "var a=t&&t.closest?t.closest('a'):null;var img=t&&t.closest?t.closest('img'):null;"
            + "if(a||img){e.preventDefault();try{HNative.showLinkMenu(a?a.href:'',img?img.src:'');}catch(x){}}},true);})();";

    private LinearLayout root;
    private FrameLayout webContainer;
    private FrameLayout pageHost;
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
    private String toolbarPref;
    private LinearLayout findBar;
    private EditText findInput;
    private TextView findCount;
    private WebViewAssetLoader assetLoader;
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
            int type = WindowInsetsCompat.Type.systemBars();
            v.setPadding(0, insets.getInsets(type).top, 0, insets.getInsets(type).bottom);
            return insets;
        });
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(color(R.color.surface));
        bar.setPadding(dp(4), dp(2), dp(4), dp(2));

        bar.addView(toolButton(R.drawable.ic_search, "搜索或输入网址", v -> beginEdit()),
                new LinearLayout.LayoutParams(dp(44), dp(44)));

        FrameLayout titleHost = new FrameLayout(this);
        titleView = new TextView(this);
        titleView.setTextSize(16);
        titleView.setTextColor(color(R.color.on_surface));
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText("主页");
        titleView.setBackground(selectableBackground());
        titleView.setPadding(dp(8), 0, dp(8), 0);
        titleView.setOnClickListener(v -> beginEdit());
        titleHost.addView(titleView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44), Gravity.CENTER));

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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44), Gravity.CENTER));
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
        bar.addView(titleHost, new LinearLayout.LayoutParams(0, dp(44), 1f));

        clearButton = toolButton(R.drawable.ic_close, "清除地址", v -> {
            address.setText("");
            address.requestFocus();
            showKeyboard();
        });
        clearButton.setVisibility(View.GONE);
        bar.addView(clearButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        ImageButton reload = toolButton(R.drawable.ic_refresh, "刷新", v -> reloadOrStop());
        bar.addView(reload, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(false);
        progress.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(color(R.color.primary)));
        }
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        findBar = buildFindBar();
        root.addView(findBar, new LinearLayout.LayoutParams(
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
        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(color(R.color.surface));
        toolbar.setPadding(dp(4), 0, dp(4), 0);
        buildToolbar();
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
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
                    toolbar.addView(toolButton(R.drawable.ic_more, "菜单",
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
        while (tabs.size() >= MAX_TABS) {
            int victim = current == 0 ? 1 : 0;
            closeTab(victim, false);
        }
        Tab tab = new Tab(nextTabId++, incognito);
        tab.web = createWebView(tab);
        tabs.add(tab);
        selectTab(tabs.size() - 1);
        loadUrl(tab, url == null || url.isEmpty() ? Prefs.homepage() : url);
    }

    private WebView createWebView(final Tab tab) {
        WebView web = new WebView(this);
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
        if (Prefs.webDark()) {
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

    private WebViewAssetLoader assetLoader() {
        if (assetLoader == null) {
            assetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/skin/", new WebViewAssetLoader.InternalStoragePathHandler(
                            this, new File(getFilesDir(), "skins")))
                    .build();
        }
        return assetLoader;
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
        if (tab == null) {
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
        if ("home".equals(path)) {
            SkinStore.Skin skin = SkinStore.active(this);
            String html = SkinStore.inlineHtml(this);
            if (skin != null && html != null) {
                tab.url = url;
                tab.web.loadDataWithBaseURL(SkinStore.pageUrl(skin.id), html,
                        "text/html", "utf-8", null);
                progress.setVisibility(View.GONE);
                updateAddress();
                return;
            }
        }
        tab.url = url;
        tab.web.loadDataWithBaseURL(url, InternalPages.render(this, url), "text/html", "utf-8", null);
        progress.setVisibility(View.GONE);
        updateAddress();
    }

    public void reloadInternal() {
        Tab tab = currentTab();
        if (tab == null) {
            return;
        }
        if (UrlUtils.isInternal(tab.url)) {
            handleInternal(tab, tab.url);
        } else {
            tab.web.reload();
        }
    }

    public void refreshCurrentIfInternal() {
        Tab tab = currentTab();
        if (tab != null && UrlUtils.isInternal(tab.url)) {
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
        if (tab != null && UrlUtils.isInternal(tab.url)) {
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
        if (titleView != null) {
            String title = tab.title;
            if (title == null || title.isEmpty() || title.equals(tab.url)) {
                title = UrlUtils.isInternal(tab.url) || SkinStore.isSkinUrl(tab.url)
                        ? "主页" : tab.url;
            }
            titleView.setText(title);
        }
        if (editingAddress || address.hasFocus()) {
            updateClearButton();
            return;
        }
        String url = tab.url;
        boolean home = UrlUtils.isInternal(url) || SkinStore.isSkinUrl(url);
        settingAddress = true;
        address.setText(home ? "" : url);
        address.setSelection(address.getText().length());
        settingAddress = false;
        updateClearButton();
    }

    private void updateTabCount() {
        if (tabCount != null) {
            tabCount.setText(String.valueOf(Math.max(1, tabs.size())));
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
        boolean canBack = tab.web.canGoBack();
        if (backButton != null) {
            backButton.setEnabled(canBack);
            backButton.setAlpha(canBack ? 1f : 0.38f);
        }
        boolean canForward = tab.web.canGoForward();
        if (forwardButton != null) {
            forwardButton.setEnabled(canForward);
            forwardButton.setAlpha(canForward ? 1f : 0.38f);
        }
        updateTabCount();
        updateAddress();
    }

    private void injectCosmetic(WebView web, String url) {
        if (UrlUtils.isInternal(url) || !AdBlock.isEnabled()) {
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

    private void showMenu() {
        final BottomSheetDialog dialog = newSheet();
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable sheetBg =
                new android.graphics.drawable.GradientDrawable();
        sheetBg.setColor(color(R.color.surface_container));
        float radius = dp(24);
        sheetBg.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        sheet.setBackground(sheetBg);
        sheet.setPadding(dp(8), dp(12), dp(8), dp(20));

        View handle = new View(this);
        android.graphics.drawable.GradientDrawable handleBg =
                new android.graphics.drawable.GradientDrawable();
        handleBg.setColor(color(R.color.outline_variant));
        handleBg.setCornerRadius(dp(2));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(32), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(10);
        sheet.addView(handle, handleParams);

        List<MenuAction> actions = new ArrayList<>();
        actions.add(new MenuAction(R.drawable.ic_add, "新建标签",
                () -> newTab(Prefs.homepage(), false)));
        actions.add(new MenuAction(R.drawable.ic_visibility_off, "无痕标签",
                () -> newTab(Prefs.homepage(), true)));
        actions.add(new MenuAction(R.drawable.ic_star, "收藏", this::toggleBookmark));
        actions.add(new MenuAction(R.drawable.ic_history, "历史",
                () -> openUrl("v://history", false)));
        actions.add(new MenuAction(R.drawable.ic_download, "下载", this::openDownloads));
        actions.add(new MenuAction(R.drawable.ic_share, "分享", this::sharePage));
        actions.add(new MenuAction(R.drawable.ic_search, "查找", this::showFindBar));
        actions.add(new MenuAction(R.drawable.ic_edit, "缩放", this::showZoomDialog));
        actions.add(new MenuAction(R.drawable.ic_reader, "阅读", this::openReader));
        actions.add(new MenuAction(R.drawable.ic_tts, "朗读", this::readAloud));
        actions.add(new MenuAction(R.drawable.ic_translate, "翻译", this::translateSelection));
        actions.add(new MenuAction(R.drawable.ic_ai, "问 AI", this::askAi));
        actions.add(new MenuAction(R.drawable.ic_scan, "扫码", this::startScan));
        actions.add(new MenuAction(R.drawable.ic_lock, "密码", this::fillPassword));
        actions.add(new MenuAction(R.drawable.ic_moon, "夜间", this::toggleNightMode));
        actions.add(new MenuAction(R.drawable.ic_desktop, "电脑版", this::toggleDesktopMode));
        actions.add(new MenuAction(R.drawable.ic_palette, "外观",
                () -> openUrl("v://skins", false)));
        actions.add(new MenuAction(R.drawable.ic_settings, "设置", this::openSettings));
        menuGrid(sheet, dialog, actions);

        dialog.setContentView(sheet);
        dialog.setOnShowListener(d -> {
            View sheetView = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheetView != null) {
                sheetView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        });
        dialog.show();
    }

    private static final class MenuAction {
        final int icon;
        final String label;
        final Runnable task;

        MenuAction(int icon, String label, Runnable task) {
            this.icon = icon;
            this.label = label;
            this.task = task;
        }
    }

    private void menuGrid(LinearLayout parent, final BottomSheetDialog dialog,
                          List<MenuAction> actions) {
        LinearLayout row = null;
        for (int i = 0; i < actions.size(); i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                parent.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            row.addView(menuCell(dialog, actions.get(i)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private View menuCell(final BottomSheetDialog dialog, final MenuAction action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setBackground(selectableBackground());
        cell.setPadding(dp(2), dp(12), dp(2), dp(10));
        ImageView image = new androidx.appcompat.widget.AppCompatImageView(this);
        image.setImageResource(action.icon);
        image.setColorFilter(color(R.color.on_surface));
        cell.addView(image, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = new TextView(this);
        label.setText(action.label);
        label.setTextSize(11);
        label.setTextColor(color(R.color.on_surface));
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(6);
        cell.addView(label, labelParams);
        cell.setOnClickListener(v -> {
            dialog.dismiss();
            action.task.run();
        });
        return cell;
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
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(20));
        scroll.addView(list);
        dialog.setContentView(scroll);
        return list;
    }

    private void item(LinearLayout parent, final BottomSheetDialog dialog, int icon,
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
        final BottomSheetDialog dialog = newSheet();
        LinearLayout list = listOf(dialog);
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
                    ? (UrlUtils.isInternal(tab.url) ? tab.url : tab.url) : tab.title;
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
        titleView.setVisibility(View.GONE);
        address.setVisibility(View.VISIBLE);
        Tab tab = currentTab();
        String url = tab == null ? "" : tab.url;
        boolean home = tab == null || UrlUtils.isInternal(url) || SkinStore.isSkinUrl(url);
        settingAddress = true;
        address.setText(home ? "" : url);
        address.setSelection(address.getText().length());
        settingAddress = false;
        address.requestFocus();
        showKeyboard();
        updateClearButton();
    }

    private void endEdit() {
        editingAddress = false;
        address.setVisibility(View.GONE);
        titleView.setVisibility(View.VISIBLE);
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
        updateUi();
    }

    @Override
    protected void onDestroy() {
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
        } else if (requestCode == REQ_SKIN) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                toast("正在导入主题…");
                SkinStore.installZip(this, data.getData(), (ok, message) -> runOnUiThread(() -> {
                    toast(message);
                    if (ok) {
                        reloadInternal();
                    }
                }));
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

    public void pickSkinFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_SKIN);
        } catch (Throwable t) {
            toast("没有可用的文件选择器");
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
            tab.url = url;
            if (tab == currentTab()) {
                progress.setProgress(0);
                progress.setVisibility(View.VISIBLE);
                updateAddress();
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Tab tab = tabFor(view);
            if (tab == null) {
                return;
            }
            tab.url = url;
            String title = view.getTitle();
            if (title != null) {
                tab.title = title;
            }
            if (!UrlUtils.isInternal(url) && !SkinStore.isSkinUrl(url) && !tab.incognito) {
                Db.get(BrowserActivity.this).addHistory(title, url);
            }
            boolean home = UrlUtils.isInternal(url) || SkinStore.isSkinUrl(url);
            if (!home) {
                injectCosmetic(view, url);
                UserScripts.inject(view, url);
                view.evaluateJavascript(LINK_JS, null);
            }
            if (tab == currentTab()) {
                progress.setVisibility(View.GONE);
                refreshLayout.setRefreshing(false);
                updateUi();
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
        if (SkinStore.isSkinUrl(url)) {
            WebResourceResponse local = assetLoader().shouldInterceptRequest(Uri.parse(url));
            if (local != null) {
                return local;
            }
        }
        if (UrlUtils.isMedia(url)) {
            MediaSniffer.add(url);
        }
        if (!mainFrame && AdBlock.shouldBlock(url)) {
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
            if (tab != null && title != null) {
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

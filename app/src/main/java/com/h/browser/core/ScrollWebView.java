package com.h.browser.core;

import android.content.Context;
import android.webkit.WebView;

public class ScrollWebView extends WebView {

    public interface Listener {
        void onScrollChanged(int y);
    }

    private Listener scrollListener;

    public ScrollWebView(Context context) {
        super(context);
    }

    public void setScrollListener(Listener listener) {
        this.scrollListener = listener;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (scrollListener != null) {
            scrollListener.onScrollChanged(t);
        }
    }
}

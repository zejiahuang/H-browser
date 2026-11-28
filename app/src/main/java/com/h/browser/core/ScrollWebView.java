package com.h.browser.core;

import android.content.Context;
import android.os.Build;
import android.view.ActionMode;
import android.view.MotionEvent;
import android.webkit.WebView;

public class ScrollWebView extends WebView {

    public interface Listener {
        void onScrollChanged(int y);
    }

    public interface TextMenuProvider {
        ActionMode.Callback wrap(ActionMode.Callback callback);
    }

    private Listener scrollListener;
    private TextMenuProvider textMenuProvider;
    private float touchX;
    private float touchY;

    public ScrollWebView(Context context) {
        super(context);
    }

    public void setScrollListener(Listener listener) {
        this.scrollListener = listener;
    }

    public void setTextMenuProvider(TextMenuProvider provider) {
        this.textMenuProvider = provider;
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        if (textMenuProvider != null && Build.VERSION.SDK_INT >= 23) {
            ActionMode.Callback wrapped = textMenuProvider.wrap(callback);
            if (wrapped != null) {
                return super.startActionMode(wrapped, type);
            }
        }
        return super.startActionMode(callback, type);
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback) {
        return startActionMode(callback, ActionMode.TYPE_PRIMARY);
    }

    public float getTouchX() {
        return touchX;
    }

    public float getTouchY() {
        return touchY;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchX = event.getX();
            touchY = event.getY();
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (scrollListener != null) {
            scrollListener.onScrollChanged(t);
        }
    }
}

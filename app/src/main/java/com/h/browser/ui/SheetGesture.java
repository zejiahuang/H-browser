package com.h.browser.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * 底栏弹层/菜单共用的手势层：水平翻页、向下拖拽关闭、点按关背景。
 * 从 BrowserActivity.MenuGestureLayout 抽出，行为保持不变。
 */
public class SheetGesture extends FrameLayout {

    public interface Callback {
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

    public SheetGesture(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
    }

    public void setCallback(Callback callback) {
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

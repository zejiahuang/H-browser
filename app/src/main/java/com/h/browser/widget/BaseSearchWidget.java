package com.h.browser.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import com.h.browser.R;
import com.h.browser.core.SearchActivity;

public abstract class BaseSearchWidget extends AppWidgetProvider {

    protected abstract int layout();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        Intent intent = new Intent(context, SearchActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(context, 0, intent, flags);
        for (int id : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layout());
            views.setOnClickPendingIntent(R.id.widget_root, pending);
            manager.updateAppWidget(id, views);
        }
    }

    public static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        refreshOne(context, manager, SearchWidgetProvider.class);
        refreshOne(context, manager, TransparentSearchWidgetProvider.class);
    }

    private static void refreshOne(Context context, AppWidgetManager manager,
                                   Class<? extends AppWidgetProvider> type) {
        try {
            android.content.ComponentName name = new android.content.ComponentName(context, type);
            int[] ids = manager.getAppWidgetIds(name);
            if (ids != null && ids.length > 0) {
                Intent intent = new Intent(context, type);
                intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                context.sendBroadcast(intent);
            }
        } catch (Throwable ignored) {
        }
    }
}

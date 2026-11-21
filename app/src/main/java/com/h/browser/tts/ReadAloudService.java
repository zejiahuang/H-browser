package com.h.browser.tts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.h.browser.R;
import com.h.browser.core.BrowserActivity;
import com.h.browser.data.Prefs;

import java.util.Locale;

public class ReadAloudService extends Service {

    public static final String ACTION_STOP = "com.h.browser.tts.STOP";
    private static final String CHANNEL = "read_aloud";
    private static final int NOTIFICATION_ID = 42;

    private TextToSpeech tts;
    private String pending;

    public static void start(Context context, String text) {
        try {
            Intent intent = new Intent(context, ReadAloudService.class);
            intent.putExtra("text", text);
            context.startService(intent);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String text = intent == null ? null : intent.getStringExtra("text");
        if (text == null || text.trim().isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        pending = text.length() > 30000 ? text.substring(0, 30000) : text;
        startForeground(NOTIFICATION_ID, buildNotification());
        ensureTts();
        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, ReadAloudService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        Intent open = new Intent(this, BrowserActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openIntent = PendingIntent.getActivity(this, 2, open,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= 26) {
            return Builder26.build(this, openIntent, stopIntent);
        }
        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_tts)
                .setContentTitle("正在朗读")
                .setContentText("氢 · 点击返回浏览器")
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(0, "停止", stopIntent)
                .setPriority(Notification.PRIORITY_LOW);
        return builder.build();
    }

    private static final class Builder26 {
        static Notification build(ReadAloudService service, PendingIntent openIntent,
                                  PendingIntent stopIntent) {
            NotificationManager manager = (NotificationManager)
                    service.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL,
                        service.getString(R.string.channel_read_aloud),
                        NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
            Notification.Builder builder = new Notification.Builder(service, CHANNEL);
            builder.setSmallIcon(R.drawable.ic_tts)
                    .setContentTitle("正在朗读")
                    .setContentText("氢 · 点击返回浏览器")
                    .setContentIntent(openIntent)
                    .setOngoing(true)
                    .addAction(0, "停止", stopIntent);
            return builder.build();
        }
    }

    private void ensureTts() {
        if (tts != null) {
            speak();
            return;
        }
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setSpeechRate(Prefs.ttsSpeed());
                if (tts.isLanguageAvailable(Locale.CHINA) >= TextToSpeech.LANG_AVAILABLE) {
                    tts.setLanguage(Locale.CHINA);
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        stopSelf();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        stopSelf();
                    }
                });
                speak();
            } else {
                android.widget.Toast.makeText(this, "系统缺少可用的语音引擎", android.widget.Toast.LENGTH_SHORT).show();
                stopSelf();
            }
        });
    }

    private void speak() {
        if (tts == null || pending == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            tts.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "h-page");
        } else {
            tts.speak(pending, TextToSpeech.QUEUE_FLUSH, null);
        }
        pending = null;
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

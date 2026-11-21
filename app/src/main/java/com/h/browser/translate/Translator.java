package com.h.browser.translate;

import android.content.Context;

import com.h.browser.data.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class Translator {

    public interface Callback {
        void onSuccess(String result);

        void onError(String message);
    }

    private Translator() {
    }

    public static void translate(final Context context, final String text, final String target,
                                 final Callback callback) {
        final String endpoint = Prefs.transUrl();
        final String key = Prefs.transKey();
        if (endpoint == null || endpoint.isEmpty()) {
            callback.onError("请先在设置中配置翻译接口");
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String url = endpoint;
                String body;
                if (url.contains("%s")) {
                    url = url.replace("%s", URLEncoder.encode(text, "UTF-8"));
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                } else {
                    JSONObject payload = new JSONObject();
                    payload.put("q", text);
                    payload.put("source", "auto");
                    payload.put("target", target);
                    payload.put("format", "text");
                    if (key != null && !key.isEmpty()) {
                        payload.put("api_key", key);
                    }
                    body = payload.toString();
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    if (key != null && !key.isEmpty()) {
                        connection.setRequestProperty("Authorization", "Bearer " + key);
                    }
                    OutputStream out = connection.getOutputStream();
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                    out.close();
                }
                int code = connection.getResponseCode();
                InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String raw = readAll(in);
                if (code >= 400) {
                    callback.onError("翻译失败：HTTP " + code);
                    return;
                }
                JSONObject object = new JSONObject(raw);
                String result = object.optString("translatedText", "");
                if (result.isEmpty()) {
                    result = object.optString("translation", "");
                }
                if (result.isEmpty()) {
                    JSONArray choices = object.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        result = choices.getJSONObject(0).optJSONObject("message") == null ? ""
                                : choices.getJSONObject(0).getJSONObject("message").optString("content", "");
                    }
                }
                if (result.isEmpty()) {
                    callback.onError("接口未返回翻译结果");
                } else {
                    callback.onSuccess(result);
                }
            } catch (Throwable t) {
                callback.onError("翻译失败：" + t.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "translate").start();
    }

    private static String readAll(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            in.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }
}

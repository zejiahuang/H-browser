package com.h.browser.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class AiClient {

    public interface Listener {
        void onDelta(String text);

        void onDone();

        void onError(String message);
    }

    private AiClient() {
    }

    public static void stream(final String base, final String key, final String model,
                              final String system, final String prompt, final Listener listener) {
        if (base == null || base.trim().isEmpty()) {
            listener.onError("请先在设置中配置 AI 接口");
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = base.trim();
                if (endpoint.endsWith("/")) {
                    endpoint = endpoint.substring(0, endpoint.length() - 1);
                }
                if (!endpoint.endsWith("/chat/completions")) {
                    endpoint = endpoint + "/chat/completions";
                }
                JSONObject payload = new JSONObject();
                payload.put("model", model == null || model.isEmpty() ? "gpt-4o-mini" : model);
                payload.put("stream", true);
                JSONArray messages = new JSONArray();
                if (system != null && !system.isEmpty()) {
                    messages.put(new JSONObject().put("role", "system").put("content", system));
                }
                messages.put(new JSONObject().put("role", "user").put("content", prompt));
                payload.put("messages", messages);

                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(90000);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "text/event-stream");
                if (key != null && !key.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + key);
                }
                OutputStream out = connection.getOutputStream();
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                out.close();

                int code = connection.getResponseCode();
                if (code >= 400) {
                    listener.onError("HTTP " + code + " " + readAll(connection.getErrorStream()));
                    return;
                }
                InputStream in = connection.getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                boolean received = false;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    try {
                        JSONObject object = new JSONObject(data);
                        JSONArray choices = object.optJSONArray("choices");
                        if (choices == null || choices.length() == 0) {
                            continue;
                        }
                        JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                        if (delta == null) {
                            continue;
                        }
                        String text = delta.optString("content", "");
                        if (!text.isEmpty()) {
                            received = true;
                            listener.onDelta(text);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                reader.close();
                if (received) {
                    listener.onDone();
                } else {
                    listener.onError("接口没有返回内容");
                }
            } catch (Throwable t) {
                listener.onError("请求失败：" + t.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "ai-stream").start();
    }

    private static String readAll(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            in.close();
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            return text.length() > 300 ? text.substring(0, 300) : text;
        } catch (Throwable t) {
            return "";
        }
    }
}

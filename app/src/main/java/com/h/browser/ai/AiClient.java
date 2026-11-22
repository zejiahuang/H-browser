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

    public static final class Handle {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;

        public void cancel() {
            cancelled = true;
            HttpURLConnection c = connection;
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    private AiClient() {
    }

    public static Handle stream(final String base, final String key, final String model,
                                final String system, final JSONArray messages,
                                final Listener listener) {
        final Handle handle = new Handle();
        if (base == null || base.trim().isEmpty()) {
            listener.onError("请先在设置中配置 AI 接口");
            return handle;
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
                JSONArray all = new JSONArray();
                if (system != null && !system.isEmpty()) {
                    all.put(new JSONObject().put("role", "system").put("content", system));
                }
                for (int i = 0; i < messages.length(); i++) {
                    all.put(messages.get(i));
                }
                payload.put("messages", all);

                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                handle.connection = connection;
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
                if (handle.cancelled) {
                    return;
                }

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
                while (!handle.cancelled && (line = reader.readLine()) != null) {
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
                if (handle.cancelled) {
                    return;
                }
                if (received) {
                    listener.onDone();
                } else {
                    listener.onError("接口没有返回内容");
                }
            } catch (Throwable t) {
                if (!handle.cancelled) {
                    listener.onError("请求失败：" + t.getMessage());
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "ai-stream").start();
        return handle;
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

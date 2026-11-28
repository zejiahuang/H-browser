package com.h.browser.net;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** 与上游代理（HTTP CONNECT / SOCKS5）建立隧道，支持可选认证。 */
public final class UpstreamProxy {

    private static final int MAX_HEADER = 8192;

    private UpstreamProxy() {
    }

    public static Socket open(String type, String host, int port, String user, String pass,
                              String targetHost, int targetPort, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            if ("socks5".equalsIgnoreCase(type)) {
                socks5(socket, user, pass, targetHost, targetPort);
            } else {
                http(socket, user, pass, targetHost, targetPort);
            }
            socket.setSoTimeout(0);
            return socket;
        } catch (IOException e) {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
            throw e;
        }
    }

    private static void http(Socket socket, String user, String pass,
                             String host, int port) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("CONNECT ").append(host).append(':').append(port).append(" HTTP/1.1\r\n");
        sb.append("Host: ").append(host).append(':').append(port).append("\r\n");
        if (user != null && !user.isEmpty()) {
            String token = Base64.encodeToString(
                    (user + ":" + pass).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            sb.append("Proxy-Authorization: Basic ").append(token).append("\r\n");
        }
        sb.append("\r\n");
        OutputStream out = socket.getOutputStream();
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        InputStream in = socket.getInputStream();
        String status = readLine(in);
        int code = 0;
        if (status != null) {
            String[] parts = status.split(" ");
            if (parts.length >= 2) {
                try {
                    code = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (code != 200) {
            throw new IOException("HTTP 代理拒绝连接（" + code + "）");
        }
        String line;
        int total = 0;
        do {
            line = readLine(in);
            if (line == null) {
                throw new IOException("HTTP 代理响应被截断");
            }
            total += line.length() + 2;
        } while (!line.isEmpty() && total < MAX_HEADER);
    }

    private static void socks5(Socket socket, String user, String pass,
                               String host, int port) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        boolean auth = user != null && !user.isEmpty();
        byte[] greeting = auth
                ? new byte[]{0x05, 0x02, 0x00, 0x02}
                : new byte[]{0x05, 0x01, 0x00};
        out.write(greeting);
        out.flush();
        byte[] sel = readFully(in, 2);
        if (sel[0] != 0x05) {
            throw new IOException("SOCKS5 上游协议错误");
        }
        if (sel[1] == 0x02) {
            if (!auth) {
                throw new IOException("SOCKS5 上游要求认证");
            }
            byte[] u = user.getBytes(StandardCharsets.UTF_8);
            byte[] p = (pass == null ? "" : pass).getBytes(StandardCharsets.UTF_8);
            if (u.length > 255 || p.length > 255) {
                throw new IOException("SOCKS5 凭据过长");
            }
            ByteArrayOutputStream req = new ByteArrayOutputStream();
            req.write(0x01);
            req.write(u.length);
            req.write(u);
            req.write(p.length);
            req.write(p);
            out.write(req.toByteArray());
            out.flush();
            byte[] rep = readFully(in, 2);
            if (rep[1] != 0x00) {
                throw new IOException("SOCKS5 认证失败");
            }
        } else if (sel[1] != 0x00) {
            throw new IOException("SOCKS5 上游不接受认证方式");
        }
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        request.write(0x05);
        request.write(0x01);
        request.write(0x00);
        if (!writeAddress(host, request)) {
            throw new IOException("SOCKS5 目标地址无效");
        }
        request.write((port >> 8) & 0xff);
        request.write(port & 0xff);
        out.write(request.toByteArray());
        out.flush();
        byte[] head = readFully(in, 4);
        if (head[0] != 0x05) {
            throw new IOException("SOCKS5 上游协议错误");
        }
        if (head[1] != 0x00) {
            throw new IOException("SOCKS5 上游拒绝连接（" + head[1] + "）");
        }
        int skip;
        switch (head[3]) {
            case 0x01:
                skip = 4;
                break;
            case 0x04:
                skip = 16;
                break;
            case 0x03:
                skip = 1 + readFully(in, 1)[0];
                break;
            default:
                throw new IOException("SOCKS5 上游返回未知地址类型");
        }
        readFully(in, skip + 2);
    }

    private static boolean writeAddress(String host, ByteArrayOutputStream sink) throws IOException {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String h = host;
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (isIpv4(h)) {
            sink.write(0x01);
            String[] parts = h.split("\\.");
            for (String part : parts) {
                sink.write(Integer.parseInt(part) & 0xff);
            }
            return true;
        }
        if (h.indexOf(':') >= 0 && isIpv6(h)) {
            try {
                byte[] raw = java.net.InetAddress.getByName(h).getAddress();
                if (raw.length == 16) {
                    sink.write(0x04);
                    sink.write(raw);
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }
        byte[] name = h.getBytes(StandardCharsets.UTF_8);
        if (name.length == 0 || name.length > 255) {
            return false;
        }
        sink.write(0x03);
        sink.write(name.length);
        sink.write(name);
        return true;
    }

    private static boolean isIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6(String host) {
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c != ':' && !(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')
                    && !(c >= 'A' && c <= 'F')) {
                return false;
            }
        }
        return host.indexOf(':') >= 0;
    }

    private static byte[] readFully(InputStream in, int size) throws IOException {
        byte[] buffer = new byte[size];
        int offset = 0;
        while (offset < size) {
            int n = in.read(buffer, offset, size - offset);
            if (n < 0) {
                throw new IOException("上游连接提前关闭");
            }
            offset += n;
        }
        return buffer;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                out.write(b);
            }
            if (out.size() > MAX_HEADER) {
                throw new IOException("上游响应头过长");
            }
        }
        if (b < 0 && out.size() == 0) {
            return null;
        }
        return out.toString("ISO-8859-1");
    }
}

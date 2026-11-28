package com.h.browser.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 仅监听 127.0.0.1 的本地中转：对 WebView 表现为 HTTP 代理（CONNECT/绝对 URI），
 * 对内以 HTTP CONNECT 或 SOCKS5 连上游。WebView 的 ProxyConfig 不支持 socks://，
 * 这里把 SOCKS5 上游折叠成 http://127.0.0.1:port。
 */
public final class Socks5Relay {

    private static final int MAX_ACTIVE = 32;
    private static final int HANDSHAKE_TIMEOUT_MS = 15000;
    private static final int UPSTREAM_TIMEOUT_MS = 20000;
    private static final int MAX_HEAD = 65536;
    private static final int LOG_MAX = 40;

    private static ServerSocket server;
    private static Thread acceptor;
    private static volatile int localPort;
    private static volatile String upType = "http";
    private static volatile String upHost = "";
    private static volatile int upPort;
    private static volatile String upUser = "";
    private static volatile String upPass = "";
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();

    private Socks5Relay() {
    }

    public static synchronized int start(String type, String host, int port,
                                         String user, String pass) {
        if (host == null || host.isEmpty() || port <= 0 || port > 65535) {
            return 0;
        }
        String t = "socks5".equalsIgnoreCase(type) ? "socks5" : "http";
        String u = user == null ? "" : user;
        String p = pass == null ? "" : pass;
        if (server != null && !server.isClosed() && localPort > 0
                && t.equals(upType) && host.equals(upHost) && port == upPort
                && u.equals(upUser) && p.equals(upPass)) {
            return localPort;
        }
        stop();
        upType = t;
        upHost = host;
        upPort = port;
        upUser = u;
        upPass = p;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 16);
            localPort = server.getLocalPort();
            Thread thread = new Thread(Socks5Relay::acceptLoop, "h-relay");
            thread.setDaemon(true);
            acceptor = thread;
            thread.start();
            log("relay 127.0.0.1:" + localPort + " → " + t + " " + host + ":" + port);
            return localPort;
        } catch (Throwable e) {
            log("relay failed: " + e.getMessage());
            stop();
            return 0;
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            try {
                server.close();
            } catch (Throwable ignored) {
            }
        }
        server = null;
        localPort = 0;
        if (acceptor != null) {
            acceptor.interrupt();
            acceptor = null;
        }
    }

    public static int port() {
        return localPort;
    }

    public static String lastEvent() {
        synchronized (EVENTS) {
            return EVENTS.isEmpty() ? "" : EVENTS.peekLast();
        }
    }

    private static void acceptLoop() {
        ServerSocket srv = server;
        while (srv != null && !srv.isClosed()) {
            Socket client;
            try {
                client = srv.accept();
            } catch (Throwable e) {
                break;
            }
            if (ACTIVE.get() >= MAX_ACTIVE) {
                log("drop: 活跃连接已达上限");
                closeQuietly(client);
                continue;
            }
            ACTIVE.incrementAndGet();
            Thread worker = new Thread(() -> handle(client), "h-relay-conn");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private static void handle(Socket client) {
        String target = "";
        try {
            client.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            InputStream raw = client.getInputStream();
            BufferedInputStream in = new BufferedInputStream(raw);
            int first = in.read();
            if (first < 0) {
                return;
            }
            if (first == 0x05) {
                target = socks5(client, in);
            } else {
                target = http(client, in, first);
            }
        } catch (Throwable e) {
            log("conn error: " + shortMessage(e));
        } finally {
            closeQuietly(client);
            ACTIVE.decrementAndGet();
        }
    }

    /** 返回隧道目标描述；null/空表示失败。成功后自行完成管道 pump。 */
    private static String socks5(Socket client, BufferedInputStream in) throws IOException {
        int methods = in.read();
        if (methods <= 0) {
            return null;
        }
        boolean wantAuth = !upUser.isEmpty();
        boolean offerNone = false;
        boolean offerAuth = false;
        for (int i = 0; i < methods; i++) {
            int m = in.read();
            if (m == 0x00) {
                offerNone = true;
            } else if (m == 0x02) {
                offerAuth = true;
            }
        }
        int chosen = wantAuth ? (offerAuth ? 0x02 : 0xff) : (offerNone ? 0x00 : 0xff);
        OutputStream out = client.getOutputStream();
        out.write(new byte[]{0x05, (byte) chosen});
        out.flush();
        if (chosen == 0xff) {
            log("socks5: 无可用认证方式");
            return null;
        }
        if (chosen == 0x02) {
            byte[] ver = readFully(in, 2);
            int ulen = ver[1] & 0xff;
            byte[] uname = readFully(in, ulen);
            int plen = in.read();
            byte[] pwd = plen < 0 ? new byte[0] : readFully(in, plen);
            boolean ok = constantTimeEquals(uname, upUser.getBytes(StandardCharsets.UTF_8))
                    & constantTimeEquals(pwd, upPass.getBytes(StandardCharsets.UTF_8));
            out.write(new byte[]{0x01, (byte) (ok ? 0x00 : 0x01)});
            out.flush();
            if (!ok) {
                log("socks5: 本地认证失败");
                return null;
            }
        }
        byte[] req = readFully(in, 4);
        if (req[1] != 0x01) {
            socksReply(out, 0x07);
            log("socks5: 仅允许 CONNECT");
            return null;
        }
        String host;
        if (req[3] == 0x01) {
            byte[] a = readFully(in, 4);
            host = (a[0] & 0xff) + "." + (a[1] & 0xff) + "." + (a[2] & 0xff) + "." + (a[3] & 0xff);
        } else if (req[3] == 0x03) {
            int len = in.read();
            if (len <= 0) {
                return null;
            }
            host = new String(readFully(in, len), StandardCharsets.UTF_8);
        } else if (req[3] == 0x04) {
            host = InetAddress.getByAddress(readFully(in, 16)).getHostAddress();
        } else {
            socksReply(out, 0x08);
            return null;
        }
        byte[] pb = readFully(in, 2);
        int port = ((pb[0] & 0xff) << 8) | (pb[1] & 0xff);
        Socket remote;
        try {
            remote = UpstreamProxy.open(upType, upHost, upPort, upUser, upPass,
                    host, port, UPSTREAM_TIMEOUT_MS);
        } catch (IOException e) {
            socksReply(out, 0x05);
            log("socks5: " + host + ":" + port + " → " + shortMessage(e));
            return null;
        }
        socksReply(out, 0x00);
        log("socks5 " + host + ":" + port);
        tunnel(client, in, remote);
        return host + ":" + port;
    }

    /** WebView 的 HTTP 代理请求：CONNECT 或绝对 URI。 */
    private static String http(Socket client, BufferedInputStream in, int first) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        head.write(first);
        int match = 0;
        while (true) {
            int b = in.read();
            if (b < 0) {
                return null;
            }
            head.write(b);
            if ((match == 0 || match == 2) && b == '\r') {
                match++;
            } else if ((match == 1 || match == 3) && b == '\n') {
                match++;
                if (match == 4) {
                    break;
                }
            } else {
                match = 0;
            }
            if (head.size() > MAX_HEAD) {
                return null;
            }
        }
        String text = new String(head.toByteArray(), StandardCharsets.ISO_8859_1);
        String[] lines = text.split("\r\n");
        if (lines.length == 0) {
            return null;
        }
        String[] parts = lines[0].split(" ");
        if (parts.length < 3) {
            return null;
        }
        String method = parts[0];
        String target = parts[1];
        String version = parts[2];
        String host;
        int port;
        String path = null;
        boolean isHttps = false;
        if ("CONNECT".equalsIgnoreCase(method)) {
            int colon = target.lastIndexOf(':');
            if (colon <= 0) {
                write(client, "HTTP/1.1 400 Bad Request\r\n\r\n");
                return null;
            }
            host = stripBrackets(target.substring(0, colon));
            port = parsePort(target.substring(colon + 1), 443);
            isHttps = true;
        } else {
            URI uri;
            try {
                uri = new URI(target);
            } catch (Throwable e) {
                write(client, "HTTP/1.1 400 Bad Request\r\n\r\n");
                return null;
            }
            if (uri.getHost() == null) {
                write(client, "HTTP/1.1 400 Bad Request\r\n\r\n");
                return null;
            }
            host = stripBrackets(uri.getHost());
            boolean tls = "https".equalsIgnoreCase(uri.getScheme());
            isHttps = tls;
            port = uri.getPort() > 0 ? uri.getPort() : (tls ? 443 : 80);
            String p = uri.getRawPath();
            if (p == null || p.isEmpty()) {
                p = "/";
            }
            if (uri.getRawQuery() != null) {
                p = p + "?" + uri.getRawQuery();
            }
            path = p;
        }
        if (host.isEmpty() || port <= 0 || port > 65535) {
            write(client, "HTTP/1.1 400 Bad Request\r\n\r\n");
            return null;
        }
        Socket remote;
        try {
            remote = UpstreamProxy.open(upType, upHost, upPort, upUser, upPass,
                    host, port, UPSTREAM_TIMEOUT_MS);
        } catch (IOException e) {
            write(client, "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n");
            log("http: " + host + ":" + port + " → " + shortMessage(e));
            return null;
        }
        if ("CONNECT".equalsIgnoreCase(method)) {
            write(client, "HTTP/1.1 200 Connection Established\r\n\r\n");
            tunnel(client, in, remote);
        } else {
            StringBuilder forward = new StringBuilder();
            forward.append(method).append(' ').append(path).append(' ').append(version)
                    .append("\r\n");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.isEmpty()) {
                    continue;
                }
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("proxy-authorization:")
                        || lower.startsWith("proxy-connection:")) {
                    continue;
                }
                forward.append(line).append("\r\n");
            }
            forward.append("\r\n");
            OutputStream out = remote.getOutputStream();
            out.write(forward.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            tunnel(client, in, remote);
        }
        return host + ":" + port + (isHttps ? " (tls)" : "");
    }

    private static void tunnel(Socket client, InputStream clientIn, Socket remote) {
        try {
            client.setSoTimeout(0);
        } catch (Throwable ignored) {
        }
        Thread uplink = new Thread(() -> copy(clientIn, remote), "h-relay-up");
        uplink.setDaemon(true);
        uplink.start();
        try {
            copy(remote.getInputStream(), client);
        } catch (IOException ignored) {
        }
    }

    private static void copy(InputStream in, Socket outSocket) {
        byte[] buffer = new byte[8192];
        try {
            OutputStream out = outSocket.getOutputStream();
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(outSocket);
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void socksReply(OutputStream out, int code) {
        try {
            out.write(new byte[]{0x05, (byte) code, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
            out.flush();
        } catch (Throwable ignored) {
        }
    }

    private static void write(Socket client, String text) {
        try {
            OutputStream out = client.getOutputStream();
            out.write(text.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        } catch (Throwable ignored) {
        }
    }

    private static byte[] readFully(InputStream in, int size) throws IOException {
        if (size < 0) {
            throw new IOException("negative size");
        }
        byte[] buffer = new byte[size];
        int offset = 0;
        while (offset < size) {
            int n = in.read(buffer, offset, size - offset);
            if (n < 0) {
                throw new IOException("connection closed");
            }
            offset += n;
        }
        return buffer;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            byte x = i < a.length ? a[i] : 0;
            byte y = i < b.length ? b[i] : 0;
            diff |= x ^ y;
        }
        return diff == 0;
    }

    private static int parsePort(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Throwable e) {
            return fallback;
        }
    }

    private static String stripBrackets(String host) {
        String h = host.trim();
        if (h.startsWith("[") && h.endsWith("]")) {
            return h.substring(1, h.length() - 1);
        }
        return h;
    }

    /** 日志只记录目标与错误摘要，不含凭据。 */
    private static void log(String event) {
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        synchronized (EVENTS) {
            EVENTS.addLast(stamp + " " + event);
            while (EVENTS.size() > LOG_MAX) {
                EVENTS.removeFirst();
            }
        }
    }

    private static String shortMessage(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isEmpty() ? t.getClass().getSimpleName() : m;
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }
}

package com.h.browser.privacy;

import android.net.http.SslError;
import android.net.http.SslCertificate;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SslExceptionStore {

    private static final Map<String, Entry> NORMAL = new HashMap<>();
    private static final Map<String, Entry> INCOGNITO = new HashMap<>();

    private SslExceptionStore() {
    }

    private static final class Entry {
        final String fingerprint;
        final int errorMask;

        Entry(String fingerprint, int errorMask) {
            this.fingerprint = fingerprint;
            this.errorMask = errorMask;
        }
    }

    public static boolean allows(String host, SslError error, boolean incognito) {
        if (host == null || host.isEmpty() || error == null) {
            return false;
        }
        Entry entry = (incognito ? INCOGNITO : NORMAL).get(host);
        if (entry == null) {
            return false;
        }
        String fingerprint = fingerprint(error);
        int bit = 1 << error.getPrimaryError();
        return entry.fingerprint.equals(fingerprint) && (entry.errorMask & bit) != 0;
    }

    public static void remember(String host, SslError error, boolean incognito) {
        if (host == null || host.isEmpty() || error == null) {
            return;
        }
        (incognito ? INCOGNITO : NORMAL).put(host,
                new Entry(fingerprint(error), 1 << error.getPrimaryError()));
    }

    public static String fingerprint(SslError error) {
        try {
            SslCertificate certificate = error.getCertificate();
            X509Certificate x509 = certificate == null ? null : certificate.getX509Certificate();
            if (x509 != null) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(x509.getEncoded());
                StringBuilder sb = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    sb.append(String.format(Locale.US, "%02X", b));
                }
                return sb.toString();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static String errorName(int code) {
        switch (code) {
            case SslError.SSL_NOTYETVALID:
                return "证书尚未生效";
            case SslError.SSL_EXPIRED:
                return "证书已过期";
            case SslError.SSL_IDMISMATCH:
                return "证书与域名不匹配";
            case SslError.SSL_UNTRUSTED:
                return "证书颁发机构不受信任";
            case SslError.SSL_DATE_INVALID:
                return "证书日期无效";
            case SslError.SSL_INVALID:
                return "证书无效";
            case SslError.SSL_MAX_ERROR:
                return "证书错误";
            default:
                return "未知证书错误（" + code + "）";
        }
    }
}

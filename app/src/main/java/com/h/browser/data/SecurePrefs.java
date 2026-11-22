package com.h.browser.data;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecurePrefs {

    private static final String ALIAS = "h_secure_prefs_key";

    private SecurePrefs() {
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        try {
            byte[] bytes = plain.getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT < 23) {
                return "b64:" + Base64.encodeToString(bytes, Base64.NO_WRAP);
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(bytes);
            byte[] out = new byte[1 + iv.length + encrypted.length];
            out[0] = (byte) iv.length;
            System.arraycopy(iv, 0, out, 1, iv.length);
            System.arraycopy(encrypted, 0, out, 1 + iv.length, encrypted.length);
            return "gcm:" + Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Throwable t) {
            return "";
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        try {
            if (stored.startsWith("b64:")) {
                return new String(Base64.decode(stored.substring(4), Base64.NO_WRAP),
                        StandardCharsets.UTF_8);
            }
            if (stored.startsWith("gcm:")) {
                if (Build.VERSION.SDK_INT < 23) {
                    return "";
                }
                byte[] data = Base64.decode(stored.substring(4), Base64.NO_WRAP);
                if (data.length < 2) {
                    return "";
                }
                int ivLength = data[0] & 0xff;
                if (ivLength <= 0 || ivLength + 1 > data.length) {
                    return "";
                }
                byte[] iv = new byte[ivLength];
                byte[] encrypted = new byte[data.length - 1 - ivLength];
                System.arraycopy(data, 1, iv, 0, ivLength);
                System.arraycopy(data, 1 + ivLength, encrypted, 0, encrypted.length);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) {
            KeyStore.Entry entry = store.getEntry(ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}

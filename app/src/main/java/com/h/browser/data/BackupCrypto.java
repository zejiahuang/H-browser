package com.h.browser.data;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class BackupCrypto {

    public static final String PREFIX = "HBK1:";
    private static final int ITERATIONS = 120000;

    private BackupCrypto() {
    }

    public static boolean isEncrypted(String text) {
        return text != null && text.startsWith(PREFIX);
    }

    public static String encrypt(String plain, String password) throws Exception {
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);
        byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, plain, password.toCharArray(), salt, iv);
        byte[] out = new byte[salt.length + iv.length + encrypted.length];
        System.arraycopy(salt, 0, out, 0, salt.length);
        System.arraycopy(iv, 0, out, salt.length, iv.length);
        System.arraycopy(encrypted, 0, out, salt.length + iv.length, encrypted.length);
        return PREFIX + Base64.encodeToString(out, Base64.NO_WRAP);
    }

    public static String decrypt(String stored, String password) throws Exception {
        byte[] all = Base64.decode(stored.substring(PREFIX.length()), Base64.NO_WRAP);
        if (all.length < 16 + 12 + 16) {
            throw new IllegalArgumentException("备份文件不完整");
        }
        byte[] salt = Arrays.copyOfRange(all, 0, 16);
        byte[] iv = Arrays.copyOfRange(all, 16, 28);
        byte[] body = Arrays.copyOfRange(all, 28, all.length);
        byte[] plain = cipher(Cipher.DECRYPT_MODE, body, password.toCharArray(), salt, iv);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static byte[] cipher(int mode, Object input, char[] password,
                                 byte[] salt, byte[] iv) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        SecretKeySpec key = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        spec.clearPassword();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, iv));
        if (input instanceof String) {
            return cipher.doFinal(((String) input).getBytes(StandardCharsets.UTF_8));
        }
        return cipher.doFinal((byte[]) input);
    }
}

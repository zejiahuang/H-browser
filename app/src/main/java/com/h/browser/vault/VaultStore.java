package com.h.browser.vault;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class VaultStore {

    public static final class Entry {
        public String title = "";
        public String user = "";
        public String pass = "";
    }

    private static final String ALIAS = "h_vault_key";
    private static final String FILE = "vault.bin";

    private VaultStore() {
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE);
    }

    public static List<Entry> load(Context context) {
        List<Entry> entries = new ArrayList<>();
        File target = file(context);
        if (!target.exists()) {
            return entries;
        }
        try {
            byte[] data = read(target);
            String json;
            if (Build.VERSION.SDK_INT >= 23) {
                json = new String(Crypto23.decrypt(data), StandardCharsets.UTF_8);
            } else {
                json = new String(Base64.decode(data, Base64.NO_WRAP), StandardCharsets.UTF_8);
            }
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Entry entry = new Entry();
                entry.title = object.optString("title", "");
                entry.user = object.optString("user", "");
                entry.pass = object.optString("pass", "");
                entries.add(entry);
            }
        } catch (Throwable ignored) {
        }
        return entries;
    }

    public static void save(Context context, List<Entry> entries) throws Exception {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            array.put(new JSONObject()
                    .put("title", entry.title)
                    .put("user", entry.user)
                    .put("pass", entry.pass));
        }
        byte[] plain = array.toString().getBytes(StandardCharsets.UTF_8);
        byte[] data = Build.VERSION.SDK_INT >= 23
                ? Crypto23.encrypt(plain)
                : Base64.encode(plain, Base64.NO_WRAP);
        File temp = new File(context.getFilesDir(), FILE + ".tmp");
        FileOutputStream out = new FileOutputStream(temp);
        out.write(data);
        out.getFD().sync();
        out.close();
        File target = file(context);
        if (!temp.renameTo(target)) {
            throw new IllegalStateException("无法写入密码库");
        }
    }

    private static byte[] read(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileInputStream in = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    private static final class Crypto23 {
        static byte[] encrypt(byte[] plain) throws Exception {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plain);
            byte[] result = new byte[1 + iv.length + encrypted.length];
            result[0] = (byte) iv.length;
            System.arraycopy(iv, 0, result, 1, iv.length);
            System.arraycopy(encrypted, 0, result, 1 + iv.length, encrypted.length);
            return result;
        }

        static byte[] decrypt(byte[] data) throws Exception {
            if (data.length < 2) {
                throw new IllegalStateException("密码库数据损坏");
            }
            int ivLength = data[0] & 0xff;
            byte[] iv = new byte[ivLength];
            byte[] encrypted = new byte[data.length - 1 - ivLength];
            System.arraycopy(data, 1, iv, 0, ivLength);
            System.arraycopy(data, 1 + ivLength, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
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
}

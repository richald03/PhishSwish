package com.example.phishswish.utils;

import android.util.Base64;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoManager {
    private static final String TAG = "CryptoManager";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int SALT_LENGTH_BYTE = 16;
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH_BIT = 256;

    // In a real production app, this should be stored more securely (e.g. Keystore)
    // or derived from user-specific hardware-backed keys.
    // For this implementation, we use a consistent "app secret" to allow decryption
    // across sessions and devices if the user logs in elsewhere.
    private static final String APP_SECRET = "PhishSwishSecureAppSecret2024!";
    private static final String APP_SALT = "PhishSwishSalt77";

    private static SecretKey secretKey;

    static {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(APP_SECRET.toCharArray(), APP_SALT.getBytes(), ITERATION_COUNT, KEY_LENGTH_BIT);
            SecretKey tmp = factory.generateSecret(spec);
            secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SecretKey", e);
        }
    }

    public static String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.encodeToString(byteBuffer.array(), Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return plainText; // Return original if fails as safety
        }
    }

    public static String decrypt(String encryptedText) {
        if (encryptedText == null) return null;
        try {
            byte[] decoded = Base64.decode(encryptedText, Base64.DEFAULT);
            if (decoded.length < IV_LENGTH_BYTE) return encryptedText;

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            // If decryption fails, it might be plain text or corrupted. 
            // Return original for safety as per requirements.
            return encryptedText;
        }
    }
}

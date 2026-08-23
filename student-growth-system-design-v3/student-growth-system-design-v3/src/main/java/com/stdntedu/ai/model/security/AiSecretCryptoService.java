package com.stdntedu.ai.model.security;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiSecretCryptoService {
    public static final String ALGORITHM = "AES-256-GCM";
    public static final int KEY_VERSION = 1;
    public static final int NONCE_BYTES = 12;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final String configuredMasterKey;
    private final SecureRandom random = new SecureRandom();
    private volatile SecretKeySpec masterKey;

    public AiSecretCryptoService(@Value("${STDNTEDU_AI_SECRET_MASTER_KEY:}") String configuredMasterKey) {
        this.configuredMasterKey = configuredMasterKey;
    }

    public EncryptedSecret encrypt(String secretRef, char[] plaintext) {
        requireReference(secretRef);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(plaintext));
        byte[] plainBytes = new byte[encoded.remaining()];
        encoded.get(plainBytes);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(secretRef.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plainBytes);
            return new EncryptedSecret(encrypted, nonce, ALGORITHM, KEY_VERSION);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AI secret encryption failed", ex);
        } finally {
            Arrays.fill(plainBytes, (byte) 0);
        }
    }

    public char[] decrypt(String secretRef, byte[] encryptedValue, byte[] nonce) {
        requireReference(secretRef);
        if (nonce == null || nonce.length != NONCE_BYTES) {
            throw new IllegalStateException("AI secret nonce is invalid");
        }
        byte[] plainBytes = null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(secretRef.getBytes(StandardCharsets.UTF_8));
            plainBytes = cipher.doFinal(encryptedValue);
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plainBytes));
            char[] result = new char[decoded.remaining()];
            decoded.get(result);
            return result;
        } catch (AEADBadTagException ex) {
            throw new IllegalStateException("AI secret authentication failed", ex);
        } catch (GeneralSecurityException | CharacterCodingException ex) {
            throw new IllegalStateException("AI secret decryption failed", ex);
        } finally {
            if (plainBytes != null) Arrays.fill(plainBytes, (byte) 0);
        }
    }

    public String masterKeyFingerprint() {
        byte[] encoded = key().getEncoded();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("stdntedu-ai-master-key-v1".getBytes(StandardCharsets.UTF_8));
            digest.update(encoded);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private SecretKeySpec key() {
        SecretKeySpec current = masterKey;
        if (current != null) return current;
        synchronized (this) {
            if (masterKey == null) masterKey = loadKey();
            return masterKey;
        }
    }

    private SecretKeySpec loadKey() {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredMasterKey == null ? "" : configuredMasterKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("AI secret master key is not valid Base64", ex);
        }
        try {
            if (decoded.length != 32) {
                throw new IllegalStateException("AI secret master key must decode to exactly 32 bytes");
            }
            return new SecretKeySpec(decoded.clone(), "AES");
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    private void requireReference(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new IllegalArgumentException("secretRef is required");
        }
    }

    public record EncryptedSecret(byte[] encryptedValue, byte[] nonce, String algorithm, int keyVersion) { }
}

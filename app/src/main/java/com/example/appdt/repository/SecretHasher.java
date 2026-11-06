package com.example.appdt.repository;

import android.util.Base64;
import android.os.Build;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.GeneralSecurityException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Versioned PBKDF2 hashes with a per-secret random salt. */
final class SecretHasher {
    private static final String SHA256 = "PBKDF2WithHmacSHA256";
    private static final String SHA1 = "PBKDF2WithHmacSHA1";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int PASSWORD_ROUNDS = 120_000;
    private static final int OTP_ROUNDS = 20_000;
    private final SecureRandom random = new SecureRandom();

    String hashPassword(String value) throws GeneralSecurityException {
        return hash(value, PASSWORD_ROUNDS);
    }

    String hashOtp(String value) throws GeneralSecurityException {
        return hash(value, OTP_ROUNDS);
    }

    boolean matches(String value, String stored) throws GeneralSecurityException {
        if (stored == null) return false;
        String[] parts = stored.split("\\$", -1);
        boolean legacy = parts.length == 4 && "v1".equals(parts[0]);
        if (!legacy && (parts.length != 5 || !"v2".equals(parts[0]))) return false;
        String algorithm = legacy ? SHA256 : algorithmForTag(parts[1]);
        if (algorithm == null) return false;
        int offset = legacy ? 0 : 1;
        int rounds;
        byte[] salt;
        byte[] expected;
        try {
            rounds = Integer.parseInt(parts[1 + offset]);
            if (rounds < OTP_ROUNDS || rounds > PASSWORD_ROUNDS) return false;
            salt = Base64.decode(parts[2 + offset], Base64.NO_WRAP);
            expected = Base64.decode(parts[3 + offset], Base64.NO_WRAP);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (salt.length != SALT_BYTES || expected.length != HASH_BITS / 8) return false;
        byte[] actual = derive(value, salt, rounds, algorithm);
        return MessageDigest.isEqual(expected, actual);
    }

    private String hash(String value, int rounds) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        String tag = Build.VERSION.SDK_INT >= 26 ? "sha256" : "sha1";
        byte[] hash = derive(value, salt, rounds, algorithmForTag(tag));
        return "v2$" + tag + "$" + rounds + "$" + Base64.encodeToString(salt, Base64.NO_WRAP)
                + "$" + Base64.encodeToString(hash, Base64.NO_WRAP);
    }

    private static String algorithmForTag(String tag) {
        if ("sha256".equals(tag)) return SHA256;
        if ("sha1".equals(tag)) return SHA1;
        return null;
    }

    private byte[] derive(String value, byte[] salt, int rounds, String algorithm)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(value.toCharArray(), salt, rounds, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}

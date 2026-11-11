package com.example.appdt.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Android Keystore-backed SharedPreferences with hashed store names, caching, and recovery. */
public final class SecurePrefs {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_KEYSET_ALIAS =
            "__androidx_security_crypto_encrypted_prefs_keyset__";
    private static final String VALUE_KEYSET_ALIAS =
            "__androidx_security_crypto_encrypted_prefs_value_keyset__";
    private static final Object LOCK = new Object();
    private static final Map<String, SharedPreferences> CACHE = new ConcurrentHashMap<>();
    private static MasterKey cachedMasterKey;

    private SecurePrefs() { }

    public static SharedPreferences open(Context context, String name) {
        SharedPreferences existing = CACHE.get(name);
        if (existing != null) return existing;
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            existing = CACHE.get(name);
            if (existing != null) return existing;
            Map<String, ?> legacySnapshot = extractLegacyPlaintextIfPresent(app, name);
            SharedPreferences prefs = openWithRecovery(app, name);
            if (legacySnapshot != null && !legacySnapshot.isEmpty()) {
                copyLegacyEntries(prefs, legacySnapshot);
            }
            CACHE.put(name, prefs);
            return prefs;
        }
    }

    private static SharedPreferences openWithRecovery(Context app, String name) {
        try {
            return createEncrypted(app, name);
        } catch (GeneralSecurityException | IOException | RuntimeException firstError) {
            cachedMasterKey = null;
            app.deleteSharedPreferences(name);
            try {
                return createEncrypted(app, name);
            } catch (GeneralSecurityException | IOException | RuntimeException secondError) {
                resetMasterKeyAndDependentStores(app, name);
                try {
                    return createEncrypted(app, name);
                } catch (GeneralSecurityException | IOException | RuntimeException finalError) {
                    throw new IllegalStateException("Unable to initialize encrypted storage", finalError);
                }
            }
        }
    }

    private static SharedPreferences createEncrypted(Context app, String name)
            throws GeneralSecurityException, IOException {
        if (cachedMasterKey == null) {
            cachedMasterKey = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
        }
        return EncryptedSharedPreferences.create(
                app,
                name,
                cachedMasterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    private static Map<String, ?> extractLegacyPlaintextIfPresent(Context app, String name) {
        File prefsFile = new File(new File(app.getDataDir(), "shared_prefs"), name + ".xml");
        if (!prefsFile.exists()) return null;
        SharedPreferences raw = app.getSharedPreferences(name, Context.MODE_PRIVATE);
        Map<String, ?> entries = raw.getAll();
        if (entries.isEmpty()
                || entries.containsKey(KEY_KEYSET_ALIAS)
                || entries.containsKey(VALUE_KEYSET_ALIAS)) {
            return null;
        }
        Map<String, ?> snapshot = new HashMap<>(entries);
        app.deleteSharedPreferences(name);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private static void copyLegacyEntries(SharedPreferences target, Map<String, ?> entries) {
        SharedPreferences.Editor editor = target.edit();
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(entry.getKey(), (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(entry.getKey(), (Float) value);
            } else if (value instanceof Set<?>) {
                editor.putStringSet(entry.getKey(), (Set<String>) value);
            }
        }
        editor.commit();
    }

    private static void resetMasterKeyAndDependentStores(Context app, String currentName) {
        cachedMasterKey = null;
        for (String cachedName : CACHE.keySet()) {
            app.deleteSharedPreferences(cachedName);
        }
        CACHE.clear();
        app.deleteSharedPreferences(currentName);
        app.deleteSharedPreferences("documind_firebase_session");
        app.deleteSharedPreferences("documind_firebase_otp_deadlines");
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS);
            }
        } catch (GeneralSecurityException | IOException ignored) { }
    }

    public static String hashedName(String prefix, String rawIdentifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((rawIdentifier == null ? "" : rawIdentifier).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(prefix.length() + 32);
            hex.append(prefix);
            for (int i = 0; i < 16; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}

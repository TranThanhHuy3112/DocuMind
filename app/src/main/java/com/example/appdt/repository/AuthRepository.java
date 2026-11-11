package com.example.appdt.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.util.Patterns;

import com.example.appdt.model.AuthResult;
import com.example.appdt.model.OtpChallenge;
import com.example.appdt.model.UserModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Firebase Auth in production; the local OTP store is retained for isolated tests. */
public final class AuthRepository {
    public interface Clock { long now(); }

    public static final long OTP_LIFETIME_MS = 60_000L;
    public static final long OTP_LOCK_MS = 180_000L;
    public static final int MAX_OTP_ATTEMPTS = 5;
    public static final int MAX_PASSWORD_LENGTH = 128;
    private static final String USERS = "users";
    private static final String PENDING = "pending_registrations";
    private static final String RESET_PENDING = "pending_password_resets";
    private static final String SESSION = "session_user_id";
    private static final Map<String, String> TRANSIENT_SESSIONS = new ConcurrentHashMap<>();
    private static volatile boolean localStoreForInstrumentation;

    private final SharedPreferences preferences;
    private final String storeName;
    private final Clock clock;
    private final SecretHasher hasher = new SecretHasher();
    private final SecureRandom random = new SecureRandom();
    private FirebaseAuthGateway firebase;

    public AuthRepository(Context context) {
        this(context, "documind_auth", System::currentTimeMillis, !localStoreForInstrumentation);
    }

    /** Keeps emulator UI tests off the production Firebase project. Debug builds only. */
    public static void setLocalStoreForInstrumentation(Context context, boolean enabled) {
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0)
            throw new IllegalStateException("Local test store is available only in debug builds");
        localStoreForInstrumentation = enabled;
    }

    static boolean usesLocalTestStore() { return localStoreForInstrumentation; }

    /** Named store and clock permit isolated tests without touching a user's account data. */
    public AuthRepository(Context context, String preferencesName, Clock clock) {
        this(context, preferencesName, clock, false);
    }

    private AuthRepository(Context context, String preferencesName, Clock clock, boolean useFirebase) {
        storeName = preferencesName;
        this.clock = clock;
        if (useFirebase) {
            preferences = null;
            firebase = new FirebaseAuthGateway(context);
        } else {
            preferences = context.getApplicationContext()
                    .getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
            firebase = null;
        }
    }

    public synchronized AuthResult<OtpChallenge> register(String fullName, String email,
                                                           String password, String confirmation) {
        if (firebase != null) return firebase.register(fullName, email, password, confirmation);
        String name = fullName == null ? "" : fullName.trim();
        String normalized = normalizeEmail(email);
        if (name.isEmpty() || name.length() > 80) return AuthResult.error(AuthResult.Error.NAME_REQUIRED);
        if (!isValidEmail(normalized)) return AuthResult.error(AuthResult.Error.INVALID_EMAIL);
        if (!isStrongPassword(password)) return AuthResult.error(AuthResult.Error.WEAK_PASSWORD);
        if (!password.equals(confirmation)) {
            return AuthResult.error(AuthResult.Error.PASSWORD_MISMATCH);
        }
        if (findUser(readUsers(), normalized) != null) {
            return AuthResult.error(AuthResult.Error.EMAIL_EXISTS);
        }
        List<PendingRegistration> pending = readPending();
        PendingRegistration prior = findPending(pending, normalized);
        long now = clock.now();
        if (prior != null && prior.lockedUntil > now) {
            return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, prior.lockedUntil);
        }
        if (prior != null && prior.sentAt > 0 && prior.sentAt + OTP_LIFETIME_MS > now) {
            return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0,
                    prior.sentAt + OTP_LIFETIME_MS);
        }
        if (prior != null && prior.expiresAt > now) {
            return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, prior.expiresAt);
        }
        try {
            PendingRegistration next = new PendingRegistration();
            next.fullName = name;
            next.email = normalized;
            next.passwordHash = hasher.hashPassword(password);
            next.createdAt = prior == null ? now : prior.createdAt;
            next.sentAt = now;
            next.attempts = prior == null || prior.lockedUntil > 0 ? 0 : prior.attempts;
            String code = newCode();
            next.otpHash = hasher.hashOtp(code);
            next.expiresAt = now + OTP_LIFETIME_MS;
            if (prior != null) pending.remove(prior);
            pending.add(next);
            if (!writePending(pending)) return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
            return AuthResult.success(new OtpChallenge(normalized, code, next.expiresAt));
        } catch (GeneralSecurityException | JSONException exception) {
            return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
        }
    }

    public synchronized AuthResult<OtpChallenge> generateOtp(String email) {
        if (firebase != null) return firebase.sendCode(email);
        return generateOtpFor(email, PENDING);
    }

    /** Refresh Firebase Auth after the user opens the verification link in email. */
    public synchronized AuthResult<UserModel> checkEmailVerified(String email) {
        if (firebase != null) return firebase.verifyCode(email, "");
        return AuthResult.error(AuthResult.Error.EMAIL_UNVERIFIED);
    }

    public synchronized AuthResult<OtpChallenge> generateResetOtp(String email) {
        if (firebase != null) return firebase.requestReset(email);
        return generateOtpFor(email, RESET_PENDING);
    }

    private AuthResult<OtpChallenge> generateOtpFor(String email, String key) {
        String normalized = normalizeEmail(email);
        AuthResult.Error missing = key.equals(PENDING)
                ? AuthResult.Error.NO_PENDING_REGISTRATION : AuthResult.Error.NO_PENDING_RESET;
        PendingRegistration pending = findPending(readPending(key), normalized);
        if (pending == null) return AuthResult.error(missing);
        long now = clock.now();
        if (pending.lockedUntil > now) {
            return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, pending.lockedUntil);
        }
        if (pending.sentAt > 0 && pending.sentAt + OTP_LIFETIME_MS > now) {
            return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, pending.sentAt + OTP_LIFETIME_MS);
        }
        try {
            List<PendingRegistration> all = readPending(key);
            pending = findPending(all, normalized);
            if (pending == null) return AuthResult.error(missing);
            if (pending.lockedUntil > 0) {
                pending.attempts = 0;
                pending.lockedUntil = 0;
            }
            String code = newCode();
            pending.otpHash = hasher.hashOtp(code);
            pending.sentAt = now;
            pending.expiresAt = now + OTP_LIFETIME_MS;
            if (!writePending(key, all)) return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
            return AuthResult.success(new OtpChallenge(normalized, code, pending.expiresAt));
        } catch (GeneralSecurityException | JSONException exception) {
            return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
        }
    }

    public synchronized AuthResult<UserModel> verifyOtp(String email, String inputCode) {
        if (firebase != null) return firebase.verifyCode(email, inputCode);
        String normalized = normalizeEmail(email);
        List<PendingRegistration> all = readPending();
        PendingRegistration pending = findPending(all, normalized);
        if (pending == null) return AuthResult.error(AuthResult.Error.NO_PENDING_REGISTRATION);
        long now = clock.now();
        if (pending.lockedUntil > now) {
            return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, pending.lockedUntil);
        }
        if (pending.lockedUntil > 0) {
            pending.lockedUntil = 0;
            pending.attempts = 0;
        }
        if (now >= pending.expiresAt) {
            return AuthResult.error(AuthResult.Error.OTP_EXPIRED);
        }
        try {
            boolean correct = inputCode != null && inputCode.matches("[0-9]{6}")
                    && hasher.matchesOtp(inputCode, pending.otpHash);
            if (!correct) {
                pending.attempts++;
                if (pending.attempts >= MAX_OTP_ATTEMPTS) {
                    pending.lockedUntil = now + OTP_LOCK_MS;
                }
                if (!writePending(all)) return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
                if (pending.lockedUntil > now) {
                    return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0,
                            pending.lockedUntil);
                }
                return AuthResult.error(AuthResult.Error.INVALID_OTP,
                        MAX_OTP_ATTEMPTS - pending.attempts, 0L);
            }
            List<UserModel> users = readUsers();
            if (findUser(users, normalized) != null) {
                return AuthResult.error(AuthResult.Error.EMAIL_EXISTS);
            }
            UserModel user = new UserModel(UUID.randomUUID().toString(), pending.fullName,
                    normalized, pending.passwordHash, "", true, pending.createdAt);
            users.add(user);
            all.remove(pending);
            SharedPreferences.Editor edit = preferences.edit();
            edit.putString(USERS, encodeUsers(users));
            edit.putString(PENDING, encodePending(all));
            edit.putString(SESSION, user.getId());
            if (!edit.commit()) return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
            TRANSIENT_SESSIONS.remove(storeName);
            return AuthResult.success(user);
        } catch (GeneralSecurityException | JSONException exception) {
            return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
        }
    }

    public synchronized AuthResult<OtpChallenge> requestPasswordReset(String email) {
        if (firebase != null) return firebase.requestReset(email);
        String normalized = normalizeEmail(email);
        if (!isValidEmail(normalized)) return AuthResult.error(AuthResult.Error.INVALID_EMAIL);
        UserModel user = findUser(readUsers(), normalized);
        if (user == null) return AuthResult.error(AuthResult.Error.ACCOUNT_NOT_FOUND);
        List<PendingRegistration> all = readPending(RESET_PENDING);
        PendingRegistration prior = findPending(all, normalized);
        long now = clock.now();
        if (prior != null && prior.lockedUntil > now) {
            return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, prior.lockedUntil);
        }
        if (prior != null && prior.sentAt > 0 && prior.sentAt + OTP_LIFETIME_MS > now) {
            return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0,
                    prior.sentAt + OTP_LIFETIME_MS);
        }
        try {
            PendingRegistration next = new PendingRegistration();
            next.email = normalized;
            next.fullName = user.getFullName();
            next.passwordHash = user.getPasswordHash();
            next.createdAt = now;
            next.sentAt = now;
            next.attempts = prior == null || prior.lockedUntil > 0 ? 0 : prior.attempts;
            String code = newCode();
            next.otpHash = hasher.hashOtp(code);
            next.expiresAt = now + OTP_LIFETIME_MS;
            if (prior != null) all.remove(prior);
            all.add(next);
            if (!writePending(RESET_PENDING, all)) {
                return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
            }
            return AuthResult.success(new OtpChallenge(normalized, code, next.expiresAt));
        } catch (GeneralSecurityException | JSONException exception) {
            return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
        }
    }

    public synchronized AuthResult<UserModel> resetPassword(String email, String code,
                                                              String newPassword,
                                                              String confirmation) {
        if (firebase != null) return firebase.completeReset(email, code, newPassword, confirmation);
        if (!isStrongPassword(newPassword)) {
            return AuthResult.error(AuthResult.Error.WEAK_PASSWORD);
        }
        if (!newPassword.equals(confirmation)) {
            return AuthResult.error(AuthResult.Error.PASSWORD_MISMATCH);
        }
        String normalized = normalizeEmail(email);
        List<PendingRegistration> all = readPending(RESET_PENDING);
        PendingRegistration pending = findPending(all, normalized);
        if (pending == null) return AuthResult.error(AuthResult.Error.NO_PENDING_RESET);
        long now = clock.now();
        if (pending.lockedUntil > now) {
            return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, pending.lockedUntil);
        }
        if (pending.lockedUntil > 0) {
            pending.lockedUntil = 0;
            pending.attempts = 0;
        }
        if (now >= pending.expiresAt) return AuthResult.error(AuthResult.Error.OTP_EXPIRED);
        try {
            boolean correct = code != null && code.matches("[0-9]{6}")
                    && hasher.matchesOtp(code, pending.otpHash);
            if (!correct) {
                pending.attempts++;
                if (pending.attempts >= MAX_OTP_ATTEMPTS) {
                    pending.lockedUntil = now + OTP_LOCK_MS;
                }
                if (!writePending(RESET_PENDING, all)) {
                    return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
                }
                if (pending.lockedUntil > now) {
                    return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0,
                            pending.lockedUntil);
                }
                return AuthResult.error(AuthResult.Error.INVALID_OTP,
                        MAX_OTP_ATTEMPTS - pending.attempts, 0L);
            }
            List<UserModel> users = readUsers();
            UserModel user = findUser(users, normalized);
            if (user == null) return AuthResult.error(AuthResult.Error.ACCOUNT_NOT_FOUND);
            UserModel updated = new UserModel(user.getId(), user.getFullName(), user.getEmail(),
                    hasher.hashPassword(newPassword), user.getAvatarUrl(),
                    user.isEmailVerified(), user.getCreatedAt());
            users.set(users.indexOf(user), updated);
            all.remove(pending);
            SharedPreferences.Editor edit = preferences.edit();
            edit.putString(USERS, encodeUsers(users));
            edit.putString(RESET_PENDING, encodePending(all));
            edit.remove(SESSION);
            if (!edit.commit()) return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
            TRANSIENT_SESSIONS.remove(storeName);
            return AuthResult.success(updated);
        } catch (GeneralSecurityException | JSONException exception) {
            return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
        }
    }

    public synchronized AuthResult<UserModel> login(String email, String password) {
        return login(email, password, true);
    }

    public synchronized AuthResult<UserModel> login(String email, String password,
                                                    boolean rememberSession) {
        if (firebase != null) return firebase.login(email, password, rememberSession);
        String normalized = normalizeEmail(email);
        if (!isValidEmail(normalized)) return AuthResult.error(AuthResult.Error.INVALID_EMAIL);
        UserModel user = findUser(readUsers(), normalized);
        if (password == null || password.length() > MAX_PASSWORD_LENGTH) {
            return AuthResult.error(AuthResult.Error.INVALID_CREDENTIALS);
        }
        if (user == null) {
            PendingRegistration pending = findPending(readPending(), normalized);
            if (pending == null) {
                hasher.runDummyWork();
                return AuthResult.error(AuthResult.Error.INVALID_CREDENTIALS);
            }
            try {
                return hasher.matchesPassword(password, pending.passwordHash)
                        ? AuthResult.error(AuthResult.Error.EMAIL_UNVERIFIED)
                        : AuthResult.error(AuthResult.Error.INVALID_CREDENTIALS);
            } catch (GeneralSecurityException exception) {
                return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
            }
        }
        try {
            if (!hasher.matchesPassword(password, user.getPasswordHash())) {
                return AuthResult.error(AuthResult.Error.INVALID_CREDENTIALS);
            }
        } catch (GeneralSecurityException exception) {
            return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
        }
        if (!user.isEmailVerified()) return AuthResult.error(AuthResult.Error.EMAIL_UNVERIFIED);
        if (!saveCurrentUser(user, rememberSession)) {
            return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
        }
        return AuthResult.success(user);
    }

    public synchronized boolean saveCurrentUser(UserModel user, boolean rememberSession) {
        if (firebase != null) return firebase.setRememberSession(rememberSession);
        if (user == null || !user.isEmailVerified()) return false;
        UserModel stored = findUser(readUsers(), user.getEmail());
        if (stored == null || !stored.getId().equals(user.getId())
                || !stored.isEmailVerified()) return false;
        boolean saved = rememberSession
                ? preferences.edit().putString(SESSION, stored.getId()).commit()
                : preferences.edit().remove(SESSION).commit();
        if (!saved) return false;
        if (rememberSession) TRANSIENT_SESSIONS.remove(storeName);
        else TRANSIENT_SESSIONS.put(storeName, stored.getId());
        return true;
    }

    public synchronized UserModel getCurrentUser() {
        if (firebase != null) return firebase.currentUser();
        String id = TRANSIENT_SESSIONS.get(storeName);
        if (id == null) id = preferences.getString(SESSION, "");
        if (id == null || id.isEmpty()) return null;
        for (UserModel user : readUsers()) {
            if (id.equals(user.getId()) && user.isEmailVerified()) return user;
        }
        return null;
    }

    public synchronized boolean isLoggedIn() { return getCurrentUser() != null; }

    public synchronized void updateDisplayName(String input, Consumer<Boolean> callback) {
        String name = input == null ? "" : input.trim();
        if (name.isEmpty() || name.length() > 80) { callback.accept(false); return; }
        if (firebase != null) { firebase.updateDisplayName(name, callback); return; }
        UserModel current = getCurrentUser();
        if (current == null) { callback.accept(false); return; }
        List<UserModel> users = readUsers();
        UserModel stored = findUser(users, current.getEmail());
        if (stored == null) { callback.accept(false); return; }
        UserModel updated = new UserModel(stored.getId(), name, stored.getEmail(),
                stored.getPasswordHash(), stored.getAvatarUrl(), stored.isEmailVerified(),
                stored.getCreatedAt());
        users.set(users.indexOf(stored), updated);
        try {
            callback.accept(preferences.edit().putString(USERS, encodeUsers(users)).commit());
        } catch (JSONException error) { callback.accept(false); }
    }

    public synchronized void logout() {
        if (firebase != null) { firebase.logout(); return; }
        TRANSIENT_SESSIONS.remove(storeName);
        preferences.edit().remove(SESSION).commit();
    }

    public synchronized long getOtpExpiresAt(String email) {
        if (firebase != null) return firebase.expiresAt(email);
        PendingRegistration pending = findPending(readPending(), normalizeEmail(email));
        return pending == null ? 0L : pending.expiresAt;
    }

    public synchronized long getOtpLockedUntil(String email) {
        if (firebase != null) return firebase.lockedUntil(email);
        PendingRegistration pending = findPending(readPending(), normalizeEmail(email));
        return pending == null ? 0L : pending.lockedUntil;
    }

    public synchronized long getResetExpiresAt(String email) {
        if (firebase != null) return firebase.resetExpiresAt(email);
        PendingRegistration pending = findPending(readPending(RESET_PENDING),
                normalizeEmail(email));
        return pending == null ? 0L : pending.expiresAt;
    }

    public synchronized long getResetLockedUntil(String email) {
        if (firebase != null) return firebase.resetLockedUntil(email);
        PendingRegistration pending = findPending(readPending(RESET_PENDING),
                normalizeEmail(email));
        return pending == null ? 0L : pending.lockedUntil;
    }

    public static boolean isValidEmail(String email) {
        return email != null && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public static boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > MAX_PASSWORD_LENGTH) {
            return false;
        }
        boolean upper = false, lower = false, digit = false;
        for (int i = 0; i < password.length(); i++) {
            char character = password.charAt(i);
            upper |= Character.isUpperCase(character);
            lower |= Character.isLowerCase(character);
            digit |= Character.isDigit(character);
        }
        return upper && lower && digit;
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String newCode() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    private List<UserModel> readUsers() {
        List<UserModel> result = new ArrayList<>();
        JSONArray array = readArray(USERS);
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) continue;
            String id = item.optString("id", "");
            String email = item.optString("email", "");
            String hash = item.optString("passwordHash", "");
            if (id.isEmpty() || !isValidEmail(email) || hash.isEmpty()) continue;
            result.add(new UserModel(id, item.optString("fullName", ""), email, hash,
                    item.optString("avatarUrl", ""), item.optBoolean("emailVerified", false),
                    item.optLong("createdAt", 0L)));
        }
        return result;
    }

    private List<PendingRegistration> readPending() {
        return readPending(PENDING);
    }

    private List<PendingRegistration> readPending(String key) {
        List<PendingRegistration> result = new ArrayList<>();
        JSONArray array = readArray(key);
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) continue;
            PendingRegistration pending = new PendingRegistration();
            pending.email = item.optString("email", "");
            pending.fullName = item.optString("fullName", "");
            pending.passwordHash = item.optString("passwordHash", "");
            pending.otpHash = item.optString("otpHash", "");
            pending.createdAt = item.optLong("createdAt", 0L);
            pending.sentAt = item.optLong("sentAt", 0L);
            pending.expiresAt = item.optLong("expiresAt", 0L);
            pending.attempts = item.optInt("attempts", 0);
            pending.lockedUntil = item.optLong("lockedUntil", 0L);
            if (isValidEmail(pending.email) && !pending.passwordHash.isEmpty()
                    && !pending.otpHash.isEmpty()) result.add(pending);
        }
        return result;
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(preferences.getString(key, "[]"));
        } catch (JSONException exception) {
            return new JSONArray();
        }
    }

    private boolean writePending(List<PendingRegistration> pending) throws JSONException {
        return writePending(PENDING, pending);
    }

    private boolean writePending(String key, List<PendingRegistration> pending)
            throws JSONException {
        return preferences.edit().putString(key, encodePending(pending)).commit();
    }

    private static String encodeUsers(List<UserModel> users) throws JSONException {
        JSONArray array = new JSONArray();
        for (UserModel user : users) {
            JSONObject item = new JSONObject();
            item.put("id", user.getId());
            item.put("fullName", user.getFullName());
            item.put("email", user.getEmail());
            item.put("passwordHash", user.getPasswordHash());
            item.put("avatarUrl", user.getAvatarUrl());
            item.put("emailVerified", user.isEmailVerified());
            item.put("createdAt", user.getCreatedAt());
            array.put(item);
        }
        return array.toString();
    }

    private static String encodePending(List<PendingRegistration> pending) throws JSONException {
        JSONArray array = new JSONArray();
        for (PendingRegistration registration : pending) {
            JSONObject item = new JSONObject();
            item.put("email", registration.email);
            item.put("fullName", registration.fullName);
            item.put("passwordHash", registration.passwordHash);
            item.put("otpHash", registration.otpHash);
            item.put("createdAt", registration.createdAt);
            item.put("sentAt", registration.sentAt);
            item.put("expiresAt", registration.expiresAt);
            item.put("attempts", registration.attempts);
            item.put("lockedUntil", registration.lockedUntil);
            array.put(item);
        }
        return array.toString();
    }

    private static UserModel findUser(List<UserModel> users, String email) {
        for (UserModel user : users) if (user.getEmail().equals(email)) return user;
        return null;
    }

    private static PendingRegistration findPending(List<PendingRegistration> pending,
                                                    String email) {
        for (PendingRegistration item : pending) if (item.email.equals(email)) return item;
        return null;
    }

    private static final class PendingRegistration {
        String fullName;
        String email;
        String passwordHash;
        String otpHash;
        long createdAt;
        long sentAt;
        long expiresAt;
        int attempts;
        long lockedUntil;
    }
}

package com.example.appdt.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Patterns;
import android.widget.Toast;

import com.example.appdt.DocuMindApplication;
import com.example.appdt.R;
import com.example.appdt.model.AuthResult;
import com.example.appdt.model.OtpChallenge;
import com.example.appdt.model.UserModel;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Firebase is the source of truth for production accounts. Never store a password or OTP here. */
final class FirebaseAuthGateway {
    private static final long EMAIL_RESEND_COOLDOWN_MS = 60_000L;
    private static final long TOO_MANY_REQUESTS_LOCK_MS = 180_000L;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final Context context;
    private final SharedPreferences deadlines;
    private final SharedPreferences session;

    FirebaseAuthGateway(Context context) {
        this.context = context.getApplicationContext();
        auth.setLanguageCode("vi");
        deadlines = SecurePrefs.open(this.context, "documind_firebase_otp_deadlines");
        session = SecurePrefs.open(this.context, "documind_firebase_session");
    }

    AuthResult<OtpChallenge> register(String fullName, String email, String password,
                                      String confirmation) {
        String name = fullName == null ? "" : fullName.trim();
        String address = AuthRepository.normalizeEmail(email);
        if (name.isEmpty() || name.length() > 80) return AuthResult.error(AuthResult.Error.NAME_REQUIRED);
        if (!Patterns.EMAIL_ADDRESS.matcher(address).matches())
            return AuthResult.error(AuthResult.Error.INVALID_EMAIL);
        if (!AuthRepository.isStrongPassword(password))
            return AuthResult.error(AuthResult.Error.WEAK_PASSWORD);
        if (!password.equals(confirmation))
            return AuthResult.error(AuthResult.Error.PASSWORD_MISMATCH);
        try {
            FirebaseUser user = Tasks.await(auth.createUserWithEmailAndPassword(address, password),
                    30, TimeUnit.SECONDS).getUser();
            if (user == null) return AuthResult.error(AuthResult.Error.STORAGE_ERROR);
            session.edit().putBoolean("remember", true).remove("boot_id").commit();
            Tasks.await(user.updateProfile(new UserProfileChangeRequest.Builder()
                    .setDisplayName(name).build()), 30, TimeUnit.SECONDS);
            firestore.collection("users").document(user.getUid()).set(
                    new Profile(name, address, System.currentTimeMillis()))
                    .addOnFailureListener(error -> Toast.makeText(this.context,
                            R.string.cloud_profile_error, Toast.LENGTH_LONG).show());
            AuthResult<OtpChallenge> delivery = sendCode(address);
            return delivery.getStatus() == AuthResult.Status.SUCCESS ? delivery
                    : AuthResult.success(new OtpChallenge(address, null, 0L));
        } catch (Exception error) {
            return failure(error);
        }
    }

    AuthResult<UserModel> login(String email, String password, boolean rememberSession) {
        String address = AuthRepository.normalizeEmail(email);
        if (!Patterns.EMAIL_ADDRESS.matcher(address).matches())
            return AuthResult.error(AuthResult.Error.INVALID_EMAIL);
        if (password == null || password.length() > AuthRepository.MAX_PASSWORD_LENGTH)
            return AuthResult.error(AuthResult.Error.INVALID_CREDENTIALS);
        try {
            FirebaseUser user = Tasks.await(auth.signInWithEmailAndPassword(address, password),
                    30, TimeUnit.SECONDS).getUser();
            if (user == null) return AuthResult.error(AuthResult.Error.INVALID_CREDENTIALS);
            Tasks.await(user.reload(), 30, TimeUnit.SECONDS);
            SharedPreferences.Editor editor = session.edit().putBoolean("remember", rememberSession);
            if (rememberSession) editor.remove("boot_id");
            else editor.putString("boot_id", DocuMindApplication.PROCESS_BOOT_ID);
            editor.commit();
            if (!user.isEmailVerified()) return AuthResult.error(AuthResult.Error.EMAIL_UNVERIFIED);
            Tasks.await(user.getIdToken(true), 30, TimeUnit.SECONDS);
            return AuthResult.success(toModel(user));
        } catch (Exception error) {
            return failure(error);
        }
    }

    AuthResult<OtpChallenge> sendCode(String email) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !AuthRepository.normalizeEmail(email).equals(AuthRepository.normalizeEmail(user.getEmail())))
            return AuthResult.error(AuthResult.Error.NO_PENDING_REGISTRATION);
        String uid = user.getUid();
        try {
            long lockRetryAt = activeDeadline("verify_lock_" + uid);
            if (lockRetryAt > 0L) {
                return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, lockRetryAt);
            }
            long cooldownRetryAt = activeDeadline(uid);
            if (cooldownRetryAt > 0L) {
                return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, cooldownRetryAt);
            }
            Tasks.await(user.sendEmailVerification(), 30, TimeUnit.SECONDS);
            long expiresAt = storeDeadline(uid, EMAIL_RESEND_COOLDOWN_MS);
            return AuthResult.success(new OtpChallenge(AuthRepository.normalizeEmail(email), null, expiresAt));
        } catch (Exception error) {
            AuthResult.Error kind = map(error);
            if (kind == AuthResult.Error.OTP_LOCKED) {
                long retryAt = storeDeadline("verify_lock_" + uid, TOO_MANY_REQUESTS_LOCK_MS);
                return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, retryAt);
            }
            return AuthResult.error(kind);
        }
    }

    AuthResult<UserModel> verifyCode(String email, String code) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !AuthRepository.normalizeEmail(email).equals(AuthRepository.normalizeEmail(user.getEmail())))
            return AuthResult.error(AuthResult.Error.NO_PENDING_REGISTRATION);
        try {
            Tasks.await(user.reload(), 30, TimeUnit.SECONDS);
            if (!user.isEmailVerified()) return AuthResult.error(AuthResult.Error.EMAIL_UNVERIFIED);
            if (!session.getBoolean("remember", false)) {
                session.edit().putString("boot_id", DocuMindApplication.PROCESS_BOOT_ID).commit();
            }
            Tasks.await(user.getIdToken(true), 30, TimeUnit.SECONDS);
            return AuthResult.success(toModel(user));
        } catch (Exception error) {
            return failure(error);
        }
    }

    AuthResult<OtpChallenge> requestReset(String email) {
        String address = AuthRepository.normalizeEmail(email);
        if (!Patterns.EMAIL_ADDRESS.matcher(address).matches())
            return AuthResult.error(AuthResult.Error.INVALID_EMAIL);
        try {
            long lockRetryAt = activeDeadline("reset_lock_" + address);
            if (lockRetryAt > 0L) {
                return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, lockRetryAt);
            }
            long cooldownRetryAt = activeDeadline("reset_" + address);
            if (cooldownRetryAt > 0L) {
                return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, cooldownRetryAt);
            }
            Tasks.await(auth.sendPasswordResetEmail(address), 30, TimeUnit.SECONDS);
            long expiresAt = storeDeadline("reset_" + address, EMAIL_RESEND_COOLDOWN_MS);
            return AuthResult.success(new OtpChallenge(address, null, expiresAt));
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            if (cause instanceof FirebaseAuthException
                    && "ERROR_USER_NOT_FOUND".equals(((FirebaseAuthException) cause).getErrorCode())) {
                long expiresAt = storeDeadline("reset_" + address, EMAIL_RESEND_COOLDOWN_MS);
                return AuthResult.success(new OtpChallenge(address, null, expiresAt));
            }
            AuthResult.Error kind = map(error);
            if (kind == AuthResult.Error.OTP_LOCKED) {
                long retryAt = storeDeadline("reset_lock_" + address, TOO_MANY_REQUESTS_LOCK_MS);
                return AuthResult.error(AuthResult.Error.OTP_LOCKED, 0, retryAt);
            }
            return AuthResult.error(kind);
        }
    }

    AuthResult<UserModel> completeReset(String email, String code, String password,
                                        String confirmation) {
        return AuthResult.error(AuthResult.Error.NO_PENDING_RESET);
    }

    long resetExpiresAt(String email) {
        return activeDeadline("reset_" + AuthRepository.normalizeEmail(email));
    }

    long resetLockedUntil(String email) {
        return activeDeadline("reset_lock_" + AuthRepository.normalizeEmail(email));
    }

    long lockedUntil(String email) {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && AuthRepository.normalizeEmail(email).equals(AuthRepository.normalizeEmail(user.getEmail()))
                ? activeDeadline("verify_lock_" + user.getUid()) : 0L;
    }

    UserModel currentUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return null;
        if (!session.getBoolean("remember", false)
                && !DocuMindApplication.PROCESS_BOOT_ID.equals(session.getString("boot_id", ""))) {
            if (user.isEmailVerified()) {
                auth.signOut();
                session.edit().remove("boot_id").commit();
                return null;
            }
        }
        return user.isEmailVerified() ? toModel(user) : null;
    }

    boolean setRememberSession(boolean rememberSession) {
        if (auth.getCurrentUser() == null) return false;
        SharedPreferences.Editor editor = session.edit().putBoolean("remember", rememberSession);
        if (rememberSession) editor.remove("boot_id");
        else editor.putString("boot_id", DocuMindApplication.PROCESS_BOOT_ID);
        return editor.commit();
    }

    void logout() {
        session.edit().remove("remember").remove("boot_id").commit();
        auth.signOut();
    }

    void updateDisplayName(String name, Consumer<Boolean> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || name == null || name.isEmpty() || name.length() > 80) {
            callback.accept(false);
            return;
        }
        firestore.collection("users").document(user.getUid())
                .update("fullName", name)
                .addOnSuccessListener(ignored -> syncAuthDisplayName(user, name, callback))
                .addOnFailureListener(error -> {
                    if (error instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) error).getCode()
                            == FirebaseFirestoreException.Code.NOT_FOUND) {
                        String email = user.getEmail() == null
                                ? "" : AuthRepository.normalizeEmail(user.getEmail());
                        firestore.collection("users").document(user.getUid())
                                .set(new Profile(name, email, System.currentTimeMillis()))
                                .addOnSuccessListener(created ->
                                        syncAuthDisplayName(user, name, callback))
                                .addOnFailureListener(ignored -> callback.accept(false));
                    } else {
                        callback.accept(false);
                    }
                });
    }

    private static void syncAuthDisplayName(FirebaseUser user, String name,
                                            Consumer<Boolean> callback) {
        user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build())
                .addOnSuccessListener(ignored -> user.getIdToken(true)
                        .addOnCompleteListener(token -> callback.accept(token.isSuccessful())))
                .addOnFailureListener(ignored -> callback.accept(false));
    }

    long expiresAt(String email) {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && AuthRepository.normalizeEmail(email).equals(AuthRepository.normalizeEmail(user.getEmail()))
                ? activeDeadline(user.getUid()) : 0L;
    }

    private int bootCount() {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    private long storeDeadline(String key, long durationMs) {
        long now = System.currentTimeMillis();
        long expiresAt = now + durationMs;
        long elapsedAt = SystemClock.elapsedRealtime() + durationMs;
        deadlines.edit()
                .putLong(key, expiresAt)
                .putLong(key + "_mono", elapsedAt)
                .putInt(key + "_boot", bootCount())
                .commit();
        return expiresAt;
    }

    private long activeDeadline(String key) {
        long now = System.currentTimeMillis();
        long wall = deadlines.getLong(key, 0L);
        long mono = deadlines.getLong(key + "_mono", 0L);
        int savedBoot = deadlines.getInt(key + "_boot", -1);
        int currentBoot = bootCount();
        long elapsedNow = SystemClock.elapsedRealtime();
        if (savedBoot != -1 && savedBoot == currentBoot && mono > 0L && mono > elapsedNow) {
            long remaining = Math.min(mono - elapsedNow, TOO_MANY_REQUESTS_LOCK_MS);
            long cappedWall = wall > now ? Math.min(wall, now + TOO_MANY_REQUESTS_LOCK_MS) : 0L;
            return Math.max(cappedWall, now + remaining);
        }
        return wall > now ? Math.min(wall, now + TOO_MANY_REQUESTS_LOCK_MS) : 0L;
    }

    private static UserModel toModel(FirebaseUser user) {
        long createdAt = user.getMetadata() == null ? 0L : user.getMetadata().getCreationTimestamp();
        String email = user.getEmail() == null ? "" : user.getEmail();
        String name = user.getDisplayName() == null ? email : user.getDisplayName();
        return new UserModel(user.getUid(), name, email, "", "", user.isEmailVerified(), createdAt);
    }

    private static AuthResult.Error map(Exception error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        if (cause instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) cause).getErrorCode();
            if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) return AuthResult.Error.EMAIL_EXISTS;
            if ("ERROR_USER_NOT_FOUND".equals(code) || "ERROR_WRONG_PASSWORD".equals(code)
                    || "ERROR_INVALID_CREDENTIAL".equals(code))
                return AuthResult.Error.INVALID_CREDENTIALS;
            if ("ERROR_TOO_MANY_REQUESTS".equals(code)) return AuthResult.Error.OTP_LOCKED;
        }
        return AuthResult.Error.STORAGE_ERROR;
    }

    private static <T> AuthResult<T> failure(Exception error) {
        AuthResult.Error kind = map(error);
        return AuthResult.error(kind);
    }

    public static final class Profile {
        public String fullName;
        public String email;
        public long createdAt;
        public Profile() { }
        public Profile(String fullName, String email, long createdAt) {
            this.fullName = fullName;
            this.email = email;
            this.createdAt = createdAt;
        }
    }
}

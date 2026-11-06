package com.example.appdt.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Patterns;
import android.widget.Toast;

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
import com.google.firebase.firestore.SetOptions;

import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Firebase is the source of truth for production accounts. Never store a password or OTP here. */
final class FirebaseAuthGateway {
    private static final long EMAIL_RESEND_COOLDOWN_MS = 60_000L;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final Context context;
    private final SharedPreferences deadlines;
    private final SharedPreferences session;
    private static String temporaryUid;

    FirebaseAuthGateway(Context context) {
        this.context = context.getApplicationContext();
        auth.setLanguageCode("vi");
        deadlines = context.getApplicationContext().getSharedPreferences(
                "documind_firebase_otp_deadlines", Context.MODE_PRIVATE);
        session = context.getApplicationContext().getSharedPreferences(
                "documind_firebase_session", Context.MODE_PRIVATE);
    }

    AuthResult<OtpChallenge> register(String fullName, String email, String password,
                                      String confirmation) {
        String name = fullName == null ? "" : fullName.trim();
        String address = AuthRepository.normalizeEmail(email);
        if (name.isEmpty()) return AuthResult.error(AuthResult.Error.NAME_REQUIRED);
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
            session.edit().putBoolean("remember", true).apply();
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
        try {
            FirebaseUser user = Tasks.await(auth.signInWithEmailAndPassword(address, password),
                    30, TimeUnit.SECONDS).getUser();
            if (user == null) return AuthResult.error(AuthResult.Error.INVALID_CREDENTIALS);
            Tasks.await(user.reload(), 30, TimeUnit.SECONDS);
            session.edit().putBoolean("remember", rememberSession).apply();
            temporaryUid = rememberSession ? null : user.getUid();
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
        try {
            long expiresAt = System.currentTimeMillis() + EMAIL_RESEND_COOLDOWN_MS;
            if (deadlines.getLong(user.getUid(), 0L) > System.currentTimeMillis())
                return AuthResult.error(AuthResult.Error.OTP_LOCKED);
            Tasks.await(user.sendEmailVerification(), 30, TimeUnit.SECONDS);
            deadlines.edit().putLong(user.getUid(), expiresAt).apply();
            return AuthResult.success(new OtpChallenge(AuthRepository.normalizeEmail(email), null, expiresAt));
        } catch (Exception error) {
            return failure(error);
        }
    }

    AuthResult<UserModel> verifyCode(String email, String code) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !AuthRepository.normalizeEmail(email).equals(AuthRepository.normalizeEmail(user.getEmail())))
            return AuthResult.error(AuthResult.Error.NO_PENDING_REGISTRATION);
        try {
            Tasks.await(user.reload(), 30, TimeUnit.SECONDS);
            if (!user.isEmailVerified()) return AuthResult.error(AuthResult.Error.EMAIL_UNVERIFIED);
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
            long expiresAt = System.currentTimeMillis() + EMAIL_RESEND_COOLDOWN_MS;
            if (deadlines.getLong("reset_" + address, 0L) > System.currentTimeMillis())
                return AuthResult.error(AuthResult.Error.OTP_LOCKED);
            Tasks.await(auth.sendPasswordResetEmail(address), 30, TimeUnit.SECONDS);
            deadlines.edit().putLong("reset_" + address, expiresAt).apply();
            return AuthResult.success(new OtpChallenge(address, null, expiresAt));
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            if (cause instanceof FirebaseAuthException
                    && "ERROR_USER_NOT_FOUND".equals(((FirebaseAuthException) cause).getErrorCode())) {
                long expiresAt = System.currentTimeMillis() + EMAIL_RESEND_COOLDOWN_MS;
                deadlines.edit().putLong("reset_" + address, expiresAt).apply();
                return AuthResult.success(new OtpChallenge(address, null, expiresAt));
            }
            return failure(error);
        }
    }

    AuthResult<UserModel> completeReset(String email, String code, String password,
                                        String confirmation) {
        return AuthResult.error(AuthResult.Error.NO_PENDING_RESET);
    }

    long resetExpiresAt(String email) {
        return deadlines.getLong("reset_" + AuthRepository.normalizeEmail(email), 0L);
    }

    long resetLockedUntil(String email) {
        return deadlines.getLong("reset_lock_" + AuthRepository.normalizeEmail(email), 0L);
    }

    UserModel currentUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && !session.getBoolean("remember", true)
                && !user.getUid().equals(temporaryUid)) {
            auth.signOut();
            return null;
        }
        return user != null && user.isEmailVerified() ? toModel(user) : null;
    }

    void logout() {
        temporaryUid = null;
        session.edit().remove("remember").apply();
        auth.signOut();
    }

    void updateDisplayName(String name, Consumer<Boolean> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { callback.accept(false); return; }
        user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build())
                .addOnSuccessListener(ignored -> user.getIdToken(true)
                        .addOnSuccessListener(token -> {
                            Map<String, Object> fields = new HashMap<>();
                            fields.put("fullName", name);
                            firestore.collection("users").document(user.getUid())
                                    .set(fields, SetOptions.merge())
                                    .addOnCompleteListener(done -> callback.accept(done.isSuccessful()));
                        }).addOnFailureListener(error -> callback.accept(false)))
                .addOnFailureListener(error -> callback.accept(false));
    }

    long expiresAt(String email) {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && AuthRepository.normalizeEmail(email).equals(AuthRepository.normalizeEmail(user.getEmail()))
                ? deadlines.getLong(user.getUid(), 0L) : 0L;
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

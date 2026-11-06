package com.example.appdt;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.appdt.model.AuthResult;
import com.example.appdt.model.OtpChallenge;
import com.example.appdt.model.UserModel;
import com.example.appdt.repository.AuthRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AuthRepositoryTest {
    private static final String PASSWORD = "StrongPass1";

    @Test public void validatesRegistrationAndKeepsOnlyHashedSecrets() throws JSONException {
        IsolatedStore store = new IsolatedStore();
        try {
            AuthRepository auth = store.repository();
            assertError(AuthResult.Error.NAME_REQUIRED,
                    auth.register(" ", "user@example.com", PASSWORD, PASSWORD));
            assertError(AuthResult.Error.INVALID_EMAIL,
                    auth.register("User", "abc@", PASSWORD, PASSWORD));
            assertError(AuthResult.Error.WEAK_PASSWORD,
                    auth.register("User", "user@example.com", "weak123", "weak123"));
            assertError(AuthResult.Error.PASSWORD_MISMATCH,
                    auth.register("User", "user@example.com", PASSWORD, "OtherPass1"));

            AuthResult<OtpChallenge> registration = auth.register(" User Name ",
                    " USER@example.com ", PASSWORD, PASSWORD);
            assertEquals(AuthResult.Status.SUCCESS, registration.getStatus());
            assertEquals("user@example.com", registration.getValue().getEmail());
            assertTrue(registration.getValue().getCode().matches("[0-9]{6}"));
            assertEquals(store.clock.now() + AuthRepository.OTP_LIFETIME_MS,
                    registration.getValue().getExpiresAt());
            String raw = store.context.getSharedPreferences(store.name, Context.MODE_PRIVATE)
                    .getString("pending_registrations", "");
            assertFalse(raw.contains(PASSWORD));
            assertNotEquals(registration.getValue().getCode(),
                    new JSONArray(raw).getJSONObject(0).getString("otpHash"));
            assertFalse(auth.isLoggedIn());

            AuthResult<UserModel> verified = auth.verifyOtp("user@example.com",
                    registration.getValue().getCode());
            assertEquals(AuthResult.Status.SUCCESS, verified.getStatus());
            assertEquals("User Name", verified.getValue().getFullName());
            assertTrue(verified.getValue().isEmailVerified());
            assertNotEquals(PASSWORD, verified.getValue().getPasswordHash());
            assertTrue(auth.isLoggedIn());

            AuthRepository reopened = store.repository();
            assertNotNull(reopened.getCurrentUser());
            assertEquals(verified.getValue().getId(), reopened.getCurrentUser().getId());
            reopened.logout();
            assertNull(reopened.getCurrentUser());
            assertError(AuthResult.Error.INVALID_CREDENTIALS,
                    reopened.login("user@example.com", "WrongPass1"));
            assertEquals(AuthResult.Status.SUCCESS,
                    reopened.login("USER@example.com", PASSWORD).getStatus());
            assertError(AuthResult.Error.EMAIL_EXISTS,
                    reopened.register("Other", "user@example.com", PASSWORD, PASSWORD));
        } finally {
            store.clear();
        }
    }

    @Test public void expiredOtpRequiresResendAndFiveFailuresLockForThreeMinutes() {
        IsolatedStore store = new IsolatedStore();
        try {
            AuthRepository auth = store.repository();
            OtpChallenge first = auth.register("User", "user@example.com", PASSWORD,
                    PASSWORD).getValue();
            store.clock.advance(AuthRepository.OTP_LIFETIME_MS);
            assertError(AuthResult.Error.OTP_EXPIRED,
                    auth.verifyOtp(first.getEmail(), first.getCode()));
            OtpChallenge second = auth.generateOtp(first.getEmail()).getValue();
            assertNotNull(second);
            String wrong = second.getCode().equals("000000") ? "111111" : "000000";
            for (int attempt = 1; attempt < AuthRepository.MAX_OTP_ATTEMPTS; attempt++) {
                AuthResult<UserModel> result = auth.verifyOtp(second.getEmail(), wrong);
                assertError(AuthResult.Error.INVALID_OTP, result);
                assertEquals(AuthRepository.MAX_OTP_ATTEMPTS - attempt,
                        result.getAttemptsRemaining());
            }
            AuthResult<UserModel> locked = auth.verifyOtp(second.getEmail(), wrong);
            assertError(AuthResult.Error.OTP_LOCKED, locked);
            assertEquals(store.clock.now() + AuthRepository.OTP_LOCK_MS,
                    locked.getRetryAt());
            assertError(AuthResult.Error.OTP_LOCKED,
                    auth.verifyOtp(second.getEmail(), second.getCode()));
            assertError(AuthResult.Error.OTP_LOCKED, auth.generateOtp(second.getEmail()));
            assertError(AuthResult.Error.OTP_LOCKED,
                    auth.register("User", second.getEmail(), PASSWORD, PASSWORD));
            store.clock.advance(AuthRepository.OTP_LOCK_MS);
            OtpChallenge renewed = auth.generateOtp(second.getEmail()).getValue();
            assertNotNull(renewed);
            assertEquals(AuthResult.Status.SUCCESS,
                    auth.verifyOtp(renewed.getEmail(), renewed.getCode()).getStatus());
        } finally {
            store.clear();
        }
    }

    @Test public void pendingLoginRememberChoiceAndPasswordResetFollowLocalRules() {
        IsolatedStore store = new IsolatedStore();
        try {
            AuthRepository auth = store.repository();
            String email = "user@example.com";
            String replacement = "NewStrongPass2";
            OtpChallenge registration = auth.register("User", email, PASSWORD,
                    PASSWORD).getValue();
            assertError(AuthResult.Error.INVALID_CREDENTIALS,
                    auth.login(email, "WrongPass1"));
            assertError(AuthResult.Error.EMAIL_UNVERIFIED,
                    auth.login(email, PASSWORD));
            assertEquals(AuthResult.Status.SUCCESS,
                    auth.verifyOtp(email, registration.getCode()).getStatus());
            auth.logout();

            assertEquals(AuthResult.Status.SUCCESS,
                    auth.login(email, PASSWORD, false).getStatus());
            assertTrue(auth.isLoggedIn());
            assertFalse(store.context.getSharedPreferences(store.name, Context.MODE_PRIVATE)
                    .contains("session_user_id"));
            auth.logout();
            assertFalse(auth.isLoggedIn());
            assertEquals(AuthResult.Status.SUCCESS,
                    auth.login(email, PASSWORD, true).getStatus());
            assertTrue(store.context.getSharedPreferences(store.name, Context.MODE_PRIVATE)
                    .contains("session_user_id"));

            assertError(AuthResult.Error.ACCOUNT_NOT_FOUND,
                    auth.requestPasswordReset("missing@example.com"));
            OtpChallenge reset = auth.requestPasswordReset(email).getValue();
            assertNotNull(reset);
            assertError(AuthResult.Error.WEAK_PASSWORD,
                    auth.resetPassword(email, reset.getCode(), "weak", "weak"));
            assertError(AuthResult.Error.PASSWORD_MISMATCH,
                    auth.resetPassword(email, reset.getCode(), replacement, "OtherPass1"));
            String wrong = reset.getCode().equals("000000") ? "111111" : "000000";
            for (int attempt = 1; attempt < AuthRepository.MAX_OTP_ATTEMPTS; attempt++) {
                assertError(AuthResult.Error.INVALID_OTP,
                        auth.resetPassword(email, wrong, replacement, replacement));
            }
            assertError(AuthResult.Error.OTP_LOCKED,
                    auth.resetPassword(email, wrong, replacement, replacement));
            assertError(AuthResult.Error.OTP_LOCKED, auth.generateResetOtp(email));
            store.clock.advance(AuthRepository.OTP_LOCK_MS);
            OtpChallenge renewed = auth.generateResetOtp(email).getValue();
            assertNotNull(renewed);
            assertEquals(AuthResult.Status.SUCCESS,
                    auth.resetPassword(email, renewed.getCode(), replacement,
                            replacement).getStatus());
            assertFalse(auth.isLoggedIn());
            assertError(AuthResult.Error.INVALID_CREDENTIALS, auth.login(email, PASSWORD));
            assertEquals(AuthResult.Status.SUCCESS,
                    auth.login(email, replacement).getStatus());
        } finally {
            store.clear();
        }
    }

    private static void assertError(AuthResult.Error expected, AuthResult<?> result) {
        assertEquals(AuthResult.Status.ERROR, result.getStatus());
        assertEquals(expected, result.getError());
    }

    private static final class IsolatedStore {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final String name = "documind_auth_test_" + UUID.randomUUID();
        final MutableClock clock = new MutableClock();

        AuthRepository repository() { return new AuthRepository(context, name, clock); }

        void clear() {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    private static final class MutableClock implements AuthRepository.Clock {
        private long time = 1_700_000_000_000L;
        @Override public long now() { return time; }
        void advance(long milliseconds) { time += milliseconds; }
    }
}

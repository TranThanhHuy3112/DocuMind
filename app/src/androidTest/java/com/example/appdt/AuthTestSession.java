package com.example.appdt;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;

import com.example.appdt.model.AuthResult;
import com.example.appdt.model.OtpChallenge;
import com.example.appdt.model.UserModel;
import com.example.appdt.repository.AuthRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** Creates one temporary signed-in user for UI tests and removes only that user. */
public final class AuthTestSession implements AutoCloseable {
    private static final String STORE = "documind_auth";
    private final AuthRepository repository;
    private final SharedPreferences preferences;
    private final UserModel previous;
    private final boolean previousRemembered;
    private final String testEmail;
    private final String testId;

    private AuthTestSession(AuthRepository repository, SharedPreferences preferences,
                            UserModel previous, boolean previousRemembered,
                            String testEmail, String testId) {
        this.repository = repository;
        this.preferences = preferences;
        this.previous = previous;
        this.previousRemembered = previousRemembered;
        this.testEmail = testEmail;
        this.testId = testId;
    }

    public static AuthTestSession start() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AuthRepository.setLocalStoreForInstrumentation(context, true);
        try {
            SharedPreferences preferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
            AuthRepository repository = new AuthRepository(context);
            UserModel previous = repository.getCurrentUser();
            boolean remembered = preferences.contains("session_user_id");
            String email = "codex-ui-" + UUID.randomUUID() + "@example.com";
            AuthResult<OtpChallenge> challenge = repository.register("Kiểm thử", email,
                    "TestPass123", "TestPass123");
            if (challenge.getStatus() != AuthResult.Status.SUCCESS) {
                throw new AssertionError("Could not prepare temporary account: "
                        + challenge.getError());
            }
            AuthResult<UserModel> verified = repository.verifyOtp(email,
                    challenge.getValue().getCode());
            if (verified.getStatus() != AuthResult.Status.SUCCESS) {
                throw new AssertionError("Could not verify temporary account: "
                        + verified.getError());
            }
            return new AuthTestSession(repository, preferences, previous, remembered,
                    email, verified.getValue().getId());
        } catch (RuntimeException | Error error) {
            AuthRepository.setLocalStoreForInstrumentation(context, false);
            throw error;
        }
    }

    public String getEmail() { return testEmail; }

    @Override public void close() {
        try {
            repository.logout();
            SharedPreferences.Editor edit = preferences.edit();
            edit.putString("users", without("users", "id", testId).toString());
            edit.putString("pending_registrations",
                    without("pending_registrations", "email", testEmail).toString());
            edit.putString("pending_password_resets",
                    without("pending_password_resets", "email", testEmail).toString());
            if (!edit.commit()) throw new AssertionError("Could not remove test account");
            if (previous != null && !repository.saveCurrentUser(previous, previousRemembered)) {
                throw new AssertionError("Could not restore previous session");
            }
        } catch (JSONException exception) {
            throw new AssertionError("Could not parse auth test data", exception);
        } finally {
            AuthRepository.setLocalStoreForInstrumentation(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), false);
        }
    }

    private JSONArray without(String key, String field, String value) throws JSONException {
        JSONArray source = new JSONArray(preferences.getString(key, "[]"));
        JSONArray result = new JSONArray();
        for (int index = 0; index < source.length(); index++) {
            JSONObject item = source.optJSONObject(index);
            if (item != null && !value.equals(item.optString(field))) result.put(item);
        }
        return result;
    }
}

package com.example.appdt;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.appdt.ui.auth.LoginActivity;
import com.example.appdt.ui.auth.RegisterActivity;
import com.example.appdt.ui.auth.ResetPasswordActivity;
import com.example.appdt.ui.auth.VerifyOtpActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class AuthScreensTest {
    @Test public void registerShowsFieldErrorsAndLivePasswordMismatch() {
        try (ActivityScenario<RegisterActivity> scenario =
                     ActivityScenario.launch(RegisterActivity.class)) {
            onView(withId(R.id.register_continue)).perform(click());
            scenario.onActivity(activity -> {
                TextInputLayout name = activity.findViewById(R.id.register_name_layout);
                TextInputLayout email = activity.findViewById(R.id.register_email_layout);
                TextInputLayout password = activity.findViewById(R.id.register_password_layout);
                assertEquals(activity.getString(R.string.auth_error_name), name.getError());
                assertEquals(activity.getString(R.string.auth_error_email), email.getError());
                assertEquals(activity.getString(R.string.auth_error_password), password.getError());
                ((TextInputEditText) activity.findViewById(R.id.register_password))
                        .setText("StrongPass1");
                ((TextInputEditText) activity.findViewById(R.id.register_confirmation))
                        .setText("OtherPass1");
                TextInputLayout confirmation = activity.findViewById(
                        R.id.register_confirmation_layout);
                assertEquals(activity.getString(R.string.auth_error_confirm),
                        confirmation.getError());
            });
        }
    }

    @Test public void loginValidatesEmailAndForgotLinkOpensResetScreen() {
        try (ActivityScenario<LoginActivity> ignored = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.login_submit)).perform(click());
            onView(withId(R.id.login_email_layout)).check(matches(isDisplayed()));
            onView(withId(R.id.login_forgot)).perform(click());
            onView(withId(R.id.reset_email)).check(matches(isDisplayed()));
        }
    }

    @Test public void resetRejectsMalformedEmailBeforeSendingLink() {
        try (ActivityScenario<ResetPasswordActivity> scenario =
                     ActivityScenario.launch(ResetPasswordActivity.class)) {
            onView(withId(R.id.reset_request)).perform(click());
            scenario.onActivity(activity -> {
                TextInputLayout email = activity.findViewById(R.id.reset_email_layout);
                assertEquals(activity.getString(R.string.auth_error_email), email.getError());
            });
        }
    }

    @Test public void verificationAndResetUseLinksInsteadOfCodeFields() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                VerifyOtpActivity.class);
        intent.putExtra(VerifyOtpActivity.EXTRA_EMAIL, "demo@example.com");
        try (ActivityScenario<VerifyOtpActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withId(R.id.verify_submit)).check(matches(withText(R.string.auth_verify_button)));
            onView(withId(R.id.verify_resend)).check(matches(isDisplayed()));
            onView(withId(R.id.verify_back)).check(matches(isDisplayed()));
        }
        try (ActivityScenario<ResetPasswordActivity> ignored =
                     ActivityScenario.launch(ResetPasswordActivity.class)) {
            onView(withId(R.id.reset_request)).check(matches(withText(R.string.auth_reset_request)));
            onView(withId(R.id.reset_back)).check(matches(isDisplayed()));
        }
    }
}

package com.example.appdt;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.appdt.repository.AuthRepository;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertFalse;

@RunWith(AndroidJUnit4.class)
public class AuthGateTest {
    private static AuthTestSession authSession;

    @BeforeClass public static void signIn() { authSession = AuthTestSession.start(); }
    @AfterClass public static void restoreSession() { authSession.close(); }

    @Test public void accountShowsCurrentUserAndLogoutOpensLogin() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_account)).perform(click());
            onView(withId(R.id.account_name)).check(matches(withText("Kiểm thử")));
            onView(withId(R.id.account_email)).check(matches(isDisplayed()));
            onView(withId(R.id.account_logout)).perform(click());
            onView(withId(R.id.login_email)).check(matches(isDisplayed()));
            assertFalse(new AuthRepository(context).isLoggedIn());
        }
    }
}

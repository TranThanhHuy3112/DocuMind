package com.example.appdt;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.appdt.model.AccentColor;
import com.example.appdt.repository.SettingsRepository;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class AccountAppearanceTest {
    private static AuthTestSession authSession;
    @BeforeClass public static void signIn() { authSession = AuthTestSession.start(); }
    @AfterClass public static void restoreSession() { authSession.close(); }

    @Test public void accountControlsPersistThemeAndAccent() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SettingsRepository settings = new SettingsRepository(context);
        AccentColor originalAccent = settings.getAccent();
        int originalMode = settings.getNightMode();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_account)).perform(click());
            onView(withId(R.id.account_accent_blue)).perform(click());
            assertEquals(AccentColor.BLUE, settings.getAccent());
            onView(withId(R.id.account_accent_blue)).check(matches(isChecked()));
            onView(withId(R.id.account_dark_mode)).perform(click());
            int selectedMode = settings.getNightMode();
            scenario.recreate();
            assertEquals(selectedMode, settings.getNightMode());
            assertEquals(AccentColor.BLUE, settings.getAccent());
            onView(withId(R.id.account_accent_blue)).check(matches(isChecked()));
        } finally {
            settings.setAccent(originalAccent);
            settings.setNightMode(originalMode);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    AppCompatDelegate.setDefaultNightMode(originalMode));
        }
    }
}

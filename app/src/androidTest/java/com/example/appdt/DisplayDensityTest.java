package com.example.appdt;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.appdt.model.DisplayDensity;
import com.example.appdt.repository.SettingsRepository;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DisplayDensityTest {
    private static AuthTestSession authSession;
    @BeforeClass public static void signIn() { authSession = AuthTestSession.start(); }
    @AfterClass public static void restoreSession() { authSession.close(); }

    @Test public void compactDensityChangesListSpacingAndSurvivesReopen() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SettingsRepository settings = new SettingsRepository(context);
        DisplayDensity original = settings.getDisplayDensity();
        settings.setDisplayDensity(DisplayDensity.COMFORTABLE);
        try {
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                onView(withId(R.id.nav_community)).perform(click());
                assertPostPadding(scenario, 15);
                onView(withId(R.id.nav_account)).perform(click());
                onView(withId(R.id.account_density_compact)).perform(click());
                assertEquals(DisplayDensity.COMPACT, settings.getDisplayDensity());
                scenario.recreate();
                onView(withId(R.id.account_density_compact)).check(matches(isChecked()));
                onView(withId(R.id.nav_community)).perform(click());
                assertPostPadding(scenario, 10);
            }
            try (ActivityScenario<MainActivity> reopened = ActivityScenario.launch(MainActivity.class)) {
                onView(withId(R.id.nav_community)).perform(click());
                assertPostPadding(reopened, 10);
            }
        } finally {
            settings.setDisplayDensity(original);
        }
    }

    private void assertPostPadding(ActivityScenario<MainActivity> scenario, int expectedDp) {
        onView(withId(R.id.community_posts)).check(matches(isDisplayed()));
        scenario.onActivity(activity -> {
            RecyclerView posts = activity.findViewById(R.id.community_posts);
            assertTrue("A sample post should be visible", posts.getChildCount() > 0);
            View card = posts.getChildAt(0);
            View content = ((ViewGroup) card).getChildAt(0);
            int expectedPx = Math.round(expectedDp
                    * activity.getResources().getDisplayMetrics().density);
            assertEquals(expectedPx, content.getPaddingTop());
        });
    }
}

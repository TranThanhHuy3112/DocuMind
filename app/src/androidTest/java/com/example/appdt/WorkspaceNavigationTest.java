package com.example.appdt;

import android.content.res.Configuration;
import android.util.TypedValue;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.appdt.model.AccentColor;
import com.example.appdt.repository.SettingsRepository;
import com.example.appdt.repository.WorkspaceRepository;
import com.example.appdt.model.ToolKind;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class WorkspaceNavigationTest {
    private static AuthTestSession authSession;
    @BeforeClass public static void signIn() { authSession = AuthTestSession.start(); }
    @AfterClass public static void restoreSession() { authSession.close(); }

    private SettingsRepository settings;
    private AccentColor originalAccent;
    private int originalNightMode;

    @Before
    public void saveAppearance() {
        settings = new SettingsRepository(InstrumentationRegistry.getInstrumentation()
                .getTargetContext());
        originalAccent = settings.getAccent();
        originalNightMode = settings.getNightMode();
    }

    @After
    public void restoreAppearance() {
        settings.setAccent(originalAccent);
        settings.setNightMode(originalNightMode);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                AppCompatDelegate.setDefaultNightMode(originalNightMode));
    }

    @Test
    public void fourTabsAndRecreationKeepSelectedScreen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            int[] tabs = {R.id.nav_files, R.id.nav_community, R.id.nav_account, R.id.nav_home};
            int[] titles = {R.string.files_title, R.string.community_title,
                    R.string.account_title, R.string.home_heading};
            int[] titleViews = {R.id.files_title_text, R.id.section_title,
                    R.id.section_title, R.id.home_heading};
            for (int i = 0; i < tabs.length; i++) {
                onView(withId(tabs[i])).perform(click());
                onView(withId(titleViews[i])).check(matches(withText(titles[i])));
                scenario.recreate();
                onView(withId(titleViews[i])).check(matches(withText(titles[i])));
                final int expectedTab = tabs[i];
                scenario.onActivity(activity -> {
                    BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
                    assertEquals(expectedTab, nav.getSelectedItemId());
                });
            }
        }
    }

    @Test
    public void homeGatesSourceToolsAndSearchOpensFiles() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_home)).perform(click());
            if (!WorkspaceRepository.getInstance().hasReadyDocumentFor(ToolKind.CHAT)) {
                onView(withContentDescription(R.string.task_chat)).perform(scrollTo(), click());
                onView(withText(R.string.source_not_ready)).check(matches(isDisplayed()));
            }
            onView(withContentDescription(R.string.task_search)).perform(scrollTo(), click());
            onView(withId(R.id.files_title_text)).check(matches(withText(R.string.files_title)));
        }
    }

    @Test
    public void fiveAccentChoicesApplyAndKeepCurrentTab() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_files)).perform(click());
            AccentColor[] accents = AccentColor.values();
            int[] colors = {R.color.accent_purple, R.color.accent_blue,
                    R.color.accent_green, R.color.accent_pink, R.color.accent_orange};
            for (int i = 0; i < accents.length; i++) {
                onView(withContentDescription(R.string.choose_accent)).perform(click());
                onView(withText(accents[i].getLabel())).perform(click());
                onView(withId(R.id.files_title_text)).check(matches(withText(R.string.files_title)));
                assertEquals(accents[i], settings.getAccent());
                final int expectedColor = colors[i];
                scenario.onActivity(activity -> {
                    TypedValue value = new TypedValue();
                    activity.getTheme().resolveAttribute(
                            com.google.android.material.R.attr.colorPrimary, value, true);
                    assertEquals(activity.getColor(expectedColor), value.data);
                });
            }
            scenario.recreate();
            onView(withId(R.id.files_title_text)).check(matches(withText(R.string.files_title)));
        }
    }

    @Test
    public void savedDarkThemeSurvivesRecreationWithoutLosingTab() {
        settings.setNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_account)).perform(click());
            onView(withContentDescription(R.string.switch_to_light)).check(matches(isDisplayed()));
            onView(withId(R.id.section_title)).check(matches(withText(R.string.account_title)));
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(Configuration.UI_MODE_NIGHT_YES,
                    activity.getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK));
            onView(withContentDescription(R.string.switch_to_light)).check(matches(isDisplayed()));
            onView(withId(R.id.section_title)).check(matches(withText(R.string.account_title)));
        }
    }
}

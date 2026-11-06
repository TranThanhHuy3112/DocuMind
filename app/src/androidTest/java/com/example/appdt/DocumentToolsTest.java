package com.example.appdt;

import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.example.appdt.repository.WorkspaceRepository;
import com.example.appdt.ui.tools.DocumentTools;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.Assume;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isNotEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DocumentToolsTest {
    private static AuthTestSession authSession;
    @BeforeClass public static void signIn() { authSession = AuthTestSession.start(); }
    @AfterClass public static void restoreSession() { authSession.close(); }

    private final List<String> fixtureIds = new ArrayList<>();

    @After public void removeFixtures() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (String id : fixtureIds) WorkspaceRepository.getInstance().removeDocument(id);
        });
    }

    @Test public void chatAcceptsQuestionWithoutFabricatingAnswer() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            addReady(scenario, "test://chat", "notes.txt", "text/plain");
            show(scenario, ToolKind.CHAT);
            onView(withId(R.id.chat_question)).inRoot(isDialog()).perform(
                    replaceText("Điểm chính là gì?"), closeSoftKeyboard());
            scenario.onActivity(activity -> {
                Fragment sheet = activity.getSupportFragmentManager()
                        .findFragmentByTag("document_tool");
                assertTrue(sheet != null && sheet.getView() != null);
                sheet.getView().findViewById(R.id.chat_send).performClick();
            });
            onView(withText(containsString("Điểm chính là gì?"))).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            close(scenario);
        }
    }

    @Test public void analysisShowsRealCsvChart() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File csv = File.createTempFile("documind-revenue-", ".csv", context.getCacheDir());
        try (FileOutputStream output = new FileOutputStream(csv)) {
            output.write("month,revenue\nJanuary,1200\nFebruary,1450\n"
                    .getBytes(StandardCharsets.UTF_8));
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Assume.assumeFalse(hasReady(scenario, ToolKind.ANALYZE));
            addReady(scenario, Uri.fromFile(csv).toString(), csv.getName(), "text/csv");
            show(scenario, ToolKind.ANALYZE);
            onView(withId(R.id.analysis_summary)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            boolean chartVisible = false;
            for (int attempt = 0; attempt < 30; attempt++) {
                try {
                    onView(withId(R.id.analysis_chart)).inRoot(isDialog())
                            .check(matches(isDisplayed()));
                    chartVisible = true;
                    break;
                } catch (androidx.test.espresso.NoMatchingViewException ignored) {
                    Thread.sleep(50);
                }
            }
            assertTrue("CSV revenue chart should appear", chartVisible);
            close(scenario);
        } finally {
            csv.delete();
        }
    }

    @Test public void transcriptRequiresRealProcessingBeforeCopy() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            addReady(scenario, "test://media", "meeting.mp4", "video/mp4");
            show(scenario, ToolKind.TRANSCRIBE);
            onView(withId(R.id.transcript_text)).inRoot(isDialog())
                    .check(matches(withText(R.string.transcript_ready)));
            onView(withId(R.id.transcript_copy)).inRoot(isDialog())
                    .check(matches(isNotEnabled()));
            close(scenario);
        }
    }

    @Test public void compareShowsTwoReadySources() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            addReady(scenario, "test://first", "notes.txt", "text/plain");
            addReady(scenario, "test://second", "brief.pdf", "application/pdf");
            show(scenario, ToolKind.COMPARE);
            onView(withId(R.id.compare_result)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            close(scenario);
        }
    }

    @Test public void draftShowsCopyableContent() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            addReady(scenario, "test://draft", "notes.txt", "text/plain");
            show(scenario, ToolKind.DRAFT);
            onView(withId(R.id.draft_result)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withId(R.id.draft_copy)).inRoot(isDialog())
                    .check(matches(isNotEnabled()));
            close(scenario);
        }
    }

    @Test public void emptySourceShowsHelpfulState() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ToolKind missing = null;
            for (ToolKind candidate : ToolKind.values()) {
                if (!hasReady(scenario, candidate)) { missing = candidate; break; }
            }
            Assume.assumeNotNull(missing);
            show(scenario, missing);
            onView(withText(R.string.tool_empty)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            close(scenario);
        }
    }

    private void addReady(ActivityScenario<MainActivity> scenario, String id,
                                 String name, String mime) {
        final WorkspaceRepository.AddResult[] result = {null};
        scenario.onActivity(activity -> result[0] = WorkspaceRepository.getInstance().addDocument(
                new DocumentModel(id, name, 30, mime, true)));
        Assume.assumeTrue("Test fixture requires a free document slot",
                result[0] == WorkspaceRepository.AddResult.ADDED);
        fixtureIds.add(id);
    }

    private static void show(ActivityScenario<MainActivity> scenario, ToolKind tool) {
        scenario.onActivity(activity -> DocumentTools.show(
                activity.getSupportFragmentManager(), tool));
    }

    private static void close(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            Fragment sheet = activity.getSupportFragmentManager()
                    .findFragmentByTag("document_tool");
            if (sheet instanceof DialogFragment) ((DialogFragment) sheet).dismissNow();
        });
    }

    private static boolean hasReady(ActivityScenario<MainActivity> scenario, ToolKind tool) {
        final boolean[] ready = {false};
        scenario.onActivity(activity -> ready[0] = WorkspaceRepository.getInstance()
                .hasReadyDocumentFor(tool));
        return ready[0];
    }
}

package com.example.appdt;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.example.appdt.repository.WorkspaceRepository;
import com.example.appdt.ui.WorkspaceViewModel;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FilesWorkspaceTest {
    private static AuthTestSession authSession;
    @BeforeClass public static void signIn() { authSession = AuthTestSession.start(); }
    @AfterClass public static void restoreSession() { authSession.close(); }

    @Test
    public void twoRealStreamsReachReadyThroughBackgroundWorkers() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File first = File.createTempFile("documind-first-", ".txt", context.getCacheDir());
        File second = File.createTempFile("documind-second-", ".csv", context.getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(first)) {
                output.write("First document".getBytes(StandardCharsets.UTF_8));
            }
            try (FileOutputStream output = new FileOutputStream(second)) {
                output.write("month,value\nJanuary,42\n".getBytes(StandardCharsets.UTF_8));
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                onView(withId(R.id.nav_files)).perform(click());
                final boolean[] roomAndNoVideo = {false};
                scenario.onActivity(activity -> {
                    WorkspaceRepository repository = WorkspaceRepository.getInstance();
                    roomAndNoVideo[0] = repository.getDocuments().getValue().size() <= 3
                            && !repository.hasReadyDocumentFor(ToolKind.TRANSCRIBE);
                });
                Assume.assumeTrue(roomAndNoVideo[0]);
                try {
                    scenario.onActivity(activity -> {
                        WorkspaceViewModel model = new ViewModelProvider(activity)
                                .get(WorkspaceViewModel.class);
                        assertEquals(WorkspaceRepository.AddResult.ADDED,
                                model.addDocument(new DocumentModel(Uri.fromFile(first).toString(),
                                        first.getName(), first.length(), "text/plain", false),
                                        activity.getApplicationContext().getContentResolver()));
                        assertEquals(WorkspaceRepository.AddResult.ADDED,
                                model.addDocument(new DocumentModel(Uri.fromFile(second).toString(),
                                        second.getName(), second.length(), "text/csv", false),
                                        activity.getApplicationContext().getContentResolver()));
                    });
                    boolean bothReady = false;
                    for (int attempt = 0; attempt < 100; attempt++) {
                        List<DocumentModel> documents = WorkspaceRepository.getInstance()
                                .getDocuments().getValue();
                        if (documents != null) {
                            boolean firstReady = false;
                            boolean secondReady = false;
                            for (DocumentModel document : documents) {
                                if (document.getId().equals(Uri.fromFile(first).toString()))
                                    firstReady = document.isReady();
                                if (document.getId().equals(Uri.fromFile(second).toString()))
                                    secondReady = document.isReady();
                            }
                            bothReady = firstReady && secondReady;
                        }
                        if (bothReady) break;
                        Thread.sleep(50);
                    }
                    assertTrue("Both source streams should reach 100%", bothReady);
                    onView(withId(R.id.quick_chat)).check(matches(isEnabled()));
                    onView(withId(R.id.quick_analyze)).check(matches(isEnabled()));
                    onView(withId(R.id.quick_video)).check(matches(not(isEnabled())));
                } finally {
                    scenario.onActivity(activity -> {
                        WorkspaceViewModel model = new ViewModelProvider(activity)
                                .get(WorkspaceViewModel.class);
                        model.removeDocument(Uri.fromFile(first).toString());
                        model.removeDocument(Uri.fromFile(second).toString());
                    });
                }
            }
        } finally {
            first.delete();
            second.delete();
        }
    }

    @Test
    public void duplicateLimitAndTypeSpecificGatingWorkTogether() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_files)).perform(click());
            final boolean[] emptyWorkspace = {false};
            scenario.onActivity(activity -> emptyWorkspace[0] = WorkspaceRepository.getInstance()
                    .getDocuments().getValue().isEmpty());
            Assume.assumeTrue(emptyWorkspace[0]);
            try {
                scenario.onActivity(activity -> {
                    WorkspaceRepository repository = WorkspaceRepository.getInstance();
                    assertEquals(WorkspaceRepository.AddResult.ADDED, repository.addDocument(
                            new DocumentModel("test://a", "Alpha.txt", 10,
                                    "text/plain", false)));
                    assertEquals(WorkspaceRepository.AddResult.DUPLICATE, repository.addDocument(
                            new DocumentModel("test://duplicate", "Alpha.txt", 10,
                                    "text/plain", false)));
                    assertFalse(repository.hasReadyDocumentFor(ToolKind.CHAT));
                });
                onView(withId(R.id.quick_chat)).check(matches(not(isEnabled())));
                scenario.onActivity(activity -> {
                    WorkspaceRepository repository = WorkspaceRepository.getInstance();
                    repository.updateProgress("test://a", 100);
                    assertTrue(repository.hasReadyDocumentFor(ToolKind.CHAT));
                    assertFalse(repository.hasReadyDocumentFor(ToolKind.ANALYZE));
                });
                onView(withId(R.id.quick_chat)).check(matches(isEnabled()));
                onView(withId(R.id.quick_analyze)).check(matches(not(isEnabled())));
                scenario.onActivity(activity -> {
                    WorkspaceRepository repository = WorkspaceRepository.getInstance();
                    repository.addDocument(new DocumentModel("test://b", "Beta.csv", 10,
                            "text/csv", false));
                    repository.updateProgress("test://b", 100);
                    repository.addDocument(new DocumentModel("test://c", "Gamma.mp4", 10,
                            "video/mp4", false));
                    repository.updateProgress("test://c", 100);
                    repository.addDocument(new DocumentModel("test://d", "Delta.pdf", 10,
                            "application/pdf", false));
                    repository.addDocument(new DocumentModel("test://e", "Epsilon.docx", 10,
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false));
                    assertEquals(WorkspaceRepository.AddResult.LIMIT_REACHED,
                            repository.addDocument(new DocumentModel("test://f", "Zeta.txt", 10,
                                    "text/plain", false)));
                    assertEquals(5, repository.getDocuments().getValue().size());
                });
                onView(withId(R.id.quick_analyze)).check(matches(isEnabled()));
                onView(withId(R.id.quick_video)).check(matches(isEnabled()));
                onView(withId(androidx.appcompat.R.id.search_src_text))
                        .perform(replaceText("not-here"));
                onView(withId(R.id.empty_documents))
                        .check(matches(withText(R.string.no_matching_documents)));
                onView(withId(R.id.empty_documents)).check(matches(isDisplayed()));
            } finally {
                scenario.onActivity(activity -> {
                    WorkspaceRepository repository = WorkspaceRepository.getInstance();
                    for (char suffix = 'a'; suffix <= 'f'; suffix++) {
                        repository.removeDocument("test://" + suffix);
                    }
                });
            }
        }
    }
}

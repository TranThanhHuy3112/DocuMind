package com.example.appdt;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Button;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.appdt.model.PostCategory;
import com.example.appdt.model.PostModel;
import com.example.appdt.repository.CommunityRepository;
import com.example.appdt.utils.CommunityShare;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class CommunityFlowTest {
    private static AuthTestSession authSession;
    @BeforeClass public static void signIn() { authSession = AuthTestSession.start(); }
    @AfterClass public static void restoreSession() { authSession.close(); }

    @Test public void createsPostAndAddsInlineComment() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_community)).perform(click());
            onView(withId(R.id.community_new_post)).perform(click());
            onView(withId(R.id.create_post_title)).inRoot(isDialog()).perform(
                    replaceText("Câu hỏi kiểm thử"), closeSoftKeyboard());
            onView(withId(R.id.create_post_content)).inRoot(isDialog()).perform(
                    replaceText("Tôi muốn trao đổi về cách đọc tài liệu."), closeSoftKeyboard());
            onView(withId(R.id.create_post_submit)).inRoot(isDialog()).perform(click());
            onView(withText("Câu hỏi kiểm thử")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                CommunityRepository repository = CommunityRepository.getInstance();
                PostModel created = repository.getPosts().getValue().get(0);
                assertEquals("Câu hỏi kiểm thử", created.getTitle());
                assertEquals(PostCategory.STUDY, created.getCategory());
                RecyclerView list = activity.findViewById(R.id.community_posts);
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
                assertNotNull(holder);
                TextInputEditText input = holder.itemView.findViewById(R.id.post_comment_input);
                input.setText("Bình luận kiểm thử");
                Button send = holder.itemView.findViewById(R.id.post_comment_send);
                send.performClick();
                PostModel updated = repository.getPosts().getValue().get(0);
                assertEquals(1, updated.getComments().size());
                assertEquals("Bình luận kiểm thử", updated.getComments().get(0).getContent());
                repository.removePost(created.getId());
            });
        }
    }

    @Test public void helpfulVoteCountsEachUserOnce() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_community)).perform(click());
            scenario.onActivity(activity -> {
                CommunityRepository repository = CommunityRepository.getInstance();
                PostModel post = repository.addPost("Lượt hữu ích", PostCategory.DATA,
                        "Kiểm thử giới hạn một lượt mỗi người.", null);
                assertNotNull(post);
                assertTrue(repository.markHelpful(post.getId(), "reader-a"));
                assertFalse(repository.markHelpful(post.getId(), "reader-a"));
                assertTrue(repository.markHelpful(post.getId(), "reader-b"));
                assertEquals(2, repository.getPosts().getValue().get(0).getHelpfulCount());
                repository.removePost(post.getId());
            });
        }
    }

    @Test public void shareButtonCreatesAndroidChooserIntent() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                new IntentFilter(Intent.ACTION_CHOOSER),
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_community)).perform(click());
            final String[] ownId = {null};
            try {
                scenario.onActivity(activity -> {
                    PostModel post = CommunityRepository.getInstance().addPost(
                            "Share test", PostCategory.OTHER, "Share content", null);
                    assertNotNull(post);
                    ownId[0] = post.getId();
                    Intent send = CommunityShare.createSendIntent(activity, post);
                    assertEquals(Intent.ACTION_SEND, send.getAction());
                    assertEquals("text/plain", send.getType());
                    assertTrue(send.getStringExtra(Intent.EXTRA_TEXT).contains(post.getTitle()));
                });
                onView(withText("Share test")).check(matches(isDisplayed()));
                scenario.onActivity(activity -> {
                    RecyclerView list = activity.findViewById(R.id.community_posts);
                    RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
                    assertNotNull(holder);
                    holder.itemView.findViewById(R.id.post_share).performClick();
                });
                instrumentation.waitForIdleSync();
                assertEquals(1, monitor.getHits());
            } finally {
                if (ownId[0] != null) scenario.onActivity(activity ->
                        CommunityRepository.getInstance().removePost(ownId[0]));
            }
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }
}

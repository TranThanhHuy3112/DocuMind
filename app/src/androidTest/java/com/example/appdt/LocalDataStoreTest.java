package com.example.appdt;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.appdt.model.CommentModel;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.PostAttachment;
import com.example.appdt.model.PostCategory;
import com.example.appdt.model.PostModel;
import com.example.appdt.model.RecentActivityModel;
import com.example.appdt.repository.LocalDataStore;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LocalDataStoreTest {
    @Test public void metadataCommunityVotesAndCommentsSurviveStoreReopen() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String preferencesName = "documind_persistence_test";
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .edit().clear().commit();
        try {
            LocalDataStore first = new LocalDataStore(context, preferencesName);
            DocumentModel document = new DocumentModel("content://test/1", "notes.txt", 52,
                    "text/plain", true);
            first.saveDocuments(Collections.singletonList(document));
            first.saveActivities(Collections.singletonList(new RecentActivityModel(3L,
                    "Đã thêm", "notes.txt", "Vừa xong")));
            PostAttachment attachment = new PostAttachment(Uri.parse("content://test/1"),
                    "notes.txt", "text/plain");
            PostModel post = new PostModel("post-1", "Câu hỏi", PostCategory.STUDY,
                    "Bạn", 1234L, "Nội dung", attachment,
                    Collections.singletonList(new CommentModel("comment-1", "Bạn",
                            "Bình luận", 1235L)), Collections.singleton("local-user"));
            first.savePosts(Collections.singletonList(post));

            LocalDataStore reopened = new LocalDataStore(context, preferencesName);
            List<DocumentModel> documents = reopened.loadDocuments();
            assertEquals(1, documents.size());
            assertEquals(document.getPath(), documents.get(0).getPath());
            assertFalse("Sources are re-read before becoming ready", documents.get(0).isReady());
            assertEquals("Đã thêm", reopened.loadActivities().get(0).getTitle());
            List<PostModel> posts = reopened.loadPosts();
            assertNotNull(posts);
            assertEquals(1, posts.size());
            assertEquals(PostCategory.STUDY, posts.get(0).getCategory());
            assertEquals("notes.txt", posts.get(0).getAttachment().getName());
            assertEquals("Bình luận", posts.get(0).getComments().get(0).getContent());
            assertTrue(posts.get(0).hasVoted("local-user"));
        } finally {
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                    .edit().clear().commit();
        }
    }
}

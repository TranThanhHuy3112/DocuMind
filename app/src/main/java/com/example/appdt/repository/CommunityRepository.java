package com.example.appdt.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.appdt.model.CommentModel;
import com.example.appdt.model.PostAttachment;
import com.example.appdt.model.PostCategory;
import com.example.appdt.model.PostModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class CommunityRepository {
    public static final String LOCAL_USER_ID = "local-user";
    public static final int MAX_TITLE = 120;
    public static final int MAX_CONTENT = 2000;
    public static final int MAX_COMMENT = 500;
    private static final CommunityRepository INSTANCE = new CommunityRepository();

    private final MutableLiveData<List<PostModel>> posts =
            new MutableLiveData<>(initialPosts());
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private LocalDataStore localData;
    private CloudCommunityStore cloud;
    private volatile boolean loading;
    private boolean listening;
    private long listenerEpoch;
    private String activeUid;

    private CommunityRepository() { }

    public static CommunityRepository getInstance() { return INSTANCE; }
    public LiveData<List<PostModel>> getPosts() { return posts; }
    public LiveData<String> getError() { return error; }
    public void clearError() { error.setValue(null); }
    public synchronized boolean isLoading() { return loading; }

    public synchronized void initialize(Context context) {
        if (localData != null) return;
        localData = new LocalDataStore(context);
        List<PostModel> saved = localData.loadPosts();
        if (saved != null) posts.setValue(saved);
    }

    public synchronized void initialize(Context context, String uid) {
        if (uid.equals(activeUid)) return;
        if (cloud != null) cloud.close();
        cloud = null;
        listening = false;
        listenerEpoch++;
        activeUid = uid;
        localData = new LocalDataStore(context, "documind_local_data_" + uid);
        List<PostModel> saved = localData.loadPosts();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (!AuthRepository.usesLocalTestStore() && user != null
                && uid.equals(user.getUid()) && user.isEmailVerified()) {
            loading = false;
            posts.setValue(Collections.emptyList());
            cloud = new CloudCommunityStore(uid, message -> {
                if (!uid.equals(activeUid) || !listening) return;
                loading = false;
                error.setValue(message);
                posts.setValue(posts.getValue());
            });
        } else {
            loading = false;
            posts.setValue(saved == null ? initialPosts() : saved);
        }
    }

    public synchronized boolean supportsSharedAttachments() { return cloud == null; }
    public synchronized String voterId() { return cloud == null ? LOCAL_USER_ID : activeUid; }

    public synchronized void startListening() {
        if (cloud == null || listening) return;
        listening = true;
        loading = true;
        String uid = activeUid;
        long epoch = ++listenerEpoch;
        cloud.observe(incoming -> {
            if (!uid.equals(activeUid) || !listening || epoch != listenerEpoch) return;
            loading = false;
            posts.setValue(incoming);
        });
    }

    public synchronized void stopListening() {
        if (cloud != null) cloud.close();
        listening = false;
        listenerEpoch++;
        loading = false;
    }

    public synchronized void clearSession() {
        if (cloud != null) cloud.close();
        cloud = null;
        listening = false;
        listenerEpoch++;
        loading = false;
        activeUid = null;
        posts.setValue(Collections.emptyList());
    }

    public synchronized PostModel addPost(String title, PostCategory category, String content,
                                          PostAttachment attachment) {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanContent = content == null ? "" : content.trim();
        if (cleanTitle.isEmpty() || cleanTitle.length() > MAX_TITLE
                || cleanContent.isEmpty() || cleanContent.length() > MAX_CONTENT
                || category == null) return null;
        if (cloud != null && attachment != null) return null;
        String id = cloud == null ? UUID.randomUUID().toString()
                : activeUid + "_" + UUID.randomUUID();
        PostModel post = new PostModel(id, cleanTitle, category,
                "Bạn", System.currentTimeMillis(), cleanContent, attachment,
                Collections.emptyList(), Collections.emptySet());
        List<PostModel> updated = new ArrayList<>();
        updated.add(post);
        List<PostModel> current = posts.getValue();
        if (current != null) updated.addAll(current);
        posts.setValue(Collections.unmodifiableList(updated));
        savePosts(updated);
        if (cloud != null) cloud.add(post);
        return post;
    }

    public synchronized boolean addComment(String postId, String content) {
        String cleanContent = content == null ? "" : content.trim();
        if (cleanContent.isEmpty() || cleanContent.length() > MAX_COMMENT) return false;
        CommentModel comment = new CommentModel(UUID.randomUUID().toString(), "Bạn",
                cleanContent, System.currentTimeMillis());
        boolean added = replace(postId, post -> post.withComment(comment));
        if (added && cloud != null) cloud.comment(postId, comment);
        return added;
    }

    public synchronized boolean markHelpful(String postId, String userId) {
        if (userId == null || userId.trim().isEmpty()) return false;
        if (cloud != null && !activeUid.equals(userId)) return false;
        boolean added = replace(postId, post -> post.hasVoted(userId) ? null
                : post.withHelpfulVote(userId));
        if (added && cloud != null) cloud.vote(postId);
        return added;
    }

    public synchronized boolean removePost(String postId) {
        if (cloud != null && !postId.startsWith(activeUid + "_")) return false;
        List<PostModel> current = posts.getValue();
        if (current == null) return false;
        List<PostModel> updated = new ArrayList<>(current);
        boolean removed = updated.removeIf(post -> post.getId().equals(postId));
        if (removed) {
            posts.setValue(Collections.unmodifiableList(updated));
            savePosts(updated);
            if (cloud != null) cloud.delete(postId);
        }
        return removed;
    }

    private boolean replace(String postId, Change change) {
        List<PostModel> current = posts.getValue();
        if (current == null) return false;
        for (int index = 0; index < current.size(); index++) {
            PostModel existing = current.get(index);
            if (!existing.getId().equals(postId)) continue;
            PostModel replacement = change.apply(existing);
            if (replacement == null) return false;
            List<PostModel> updated = new ArrayList<>(current);
            updated.set(index, replacement);
            posts.setValue(Collections.unmodifiableList(updated));
            savePosts(updated);
            return true;
        }
        return false;
    }

    private interface Change { PostModel apply(PostModel post); }

    private void savePosts(List<PostModel> current) {
        if (cloud == null && localData != null) localData.savePosts(current);
    }

    private static List<PostModel> initialPosts() {
        long now = System.currentTimeMillis();
        List<PostModel> samples = new ArrayList<>();
        samples.add(new PostModel("sample-welcome", "Bắt đầu trao đổi cùng DocuMind",
                PostCategory.STUDY, "DocuMind · Bài mẫu", now - 3_600_000L,
                "Đăng câu hỏi hoặc chia sẻ cách bạn đọc và phân tích tài liệu. "
                        + "Các bài trong bản thử được giữ trong phiên trên thiết bị của bạn.", null,
                Collections.singletonList(new CommentModel("sample-comment", "DocuMind",
                        "Bạn có thể viết bình luận trực tiếp dưới mỗi bài đăng.",
                        now - 3_000_000L)), Collections.emptySet()));
        samples.add(new PostModel("sample-csv", "Bạn thường kiểm tra dữ liệu CSV thế nào?",
                PostCategory.DATA, "DocuMind · Bài mẫu", now - 7_200_000L,
                "Chia sẻ một bước làm sạch dữ liệu giúp bạn tránh đọc sai biểu đồ hoặc số liệu.",
                null, Collections.emptyList(), Collections.emptySet()));
        return Collections.unmodifiableList(samples);
    }
}

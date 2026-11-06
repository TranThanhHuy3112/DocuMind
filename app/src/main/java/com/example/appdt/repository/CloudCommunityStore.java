package com.example.appdt.repository;

import com.example.appdt.model.CommentModel;
import com.example.appdt.model.PostCategory;
import com.example.appdt.model.PostModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Shared text posts use Firestore; binary attachments stay local on Spark. */
final class CloudCommunityStore {
    private final CollectionReference posts = FirebaseFirestore.getInstance()
            .collection("communityPosts");
    private final String uid;
    private final Consumer<String> onError;
    private ListenerRegistration listener;

    CloudCommunityStore(String uid, Consumer<String> onError) {
        this.uid = uid;
        this.onError = onError;
    }

    void observe(Consumer<List<PostModel>> receiver) {
        listener = posts.orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) { onError.accept("Không thể tải bài viết. Hãy kiểm tra mạng rồi thử lại."); return; }
                    if (snapshot == null) return;
                    List<PostModel> result = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        PostModel post = parse(document);
                        if (post != null) result.add(post);
                    }
                    receiver.accept(Collections.unmodifiableList(result));
                });
    }

    void add(PostModel post) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Map<String, Object> fields = new HashMap<>();
        fields.put("authorUid", uid);
        fields.put("author", user.getDisplayName() == null ? "" : user.getDisplayName());
        fields.put("title", post.getTitle());
        fields.put("category", post.getCategory().name());
        fields.put("content", post.getContent());
        fields.put("createdAt", post.getCreatedAt());
        fields.put("comments", Collections.emptyList());
        fields.put("voters", Collections.emptyList());
        posts.document(post.getId()).set(fields).addOnFailureListener(error ->
                onError.accept("Không thể đăng bài. Hãy kiểm tra mạng rồi thử lại."));
    }

    void comment(String postId, CommentModel comment) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Map<String, Object> value = new HashMap<>();
        value.put("id", comment.getId());
        value.put("authorUid", uid);
        value.put("author", user.getDisplayName() == null ? "" : user.getDisplayName());
        value.put("content", comment.getContent());
        value.put("createdAt", comment.getCreatedAt());
        posts.document(postId).update("comments", FieldValue.arrayUnion(value))
                .addOnFailureListener(error -> onError.accept("Không thể gửi bình luận."));
    }

    void vote(String postId) {
        posts.document(postId).update("voters", FieldValue.arrayUnion(uid))
                .addOnFailureListener(error -> onError.accept("Không thể ghi lượt hữu ích."));
    }

    void delete(String postId) {
        posts.document(postId).delete().addOnFailureListener(error ->
                onError.accept("Không thể xóa bài viết."));
    }

    void close() {
        if (listener != null) listener.remove();
        listener = null;
    }

    @SuppressWarnings("unchecked")
    private PostModel parse(DocumentSnapshot item) {
        String title = item.getString("title");
        String content = item.getString("content");
        String authorUid = item.getString("authorUid");
        String authorName = item.getString("author");
        String categoryName = item.getString("category");
        Long time = item.getLong("createdAt");
        if (title == null || content == null || authorUid == null || time == null) return null;
        PostCategory category;
        try { category = PostCategory.valueOf(categoryName); }
        catch (Exception ignored) { category = PostCategory.OTHER; }
        List<CommentModel> comments = new ArrayList<>();
        Object rawComments = item.get("comments");
        if (rawComments instanceof List) {
            for (Object raw : (List<?>) rawComments) {
                if (!(raw instanceof Map)) continue;
                Map<String, Object> value = (Map<String, Object>) raw;
                Object id = value.get("id"), body = value.get("content"), when = value.get("createdAt");
                if (!(id instanceof String) || !(body instanceof String) || !(when instanceof Number)) continue;
                String commenter = uid.equals(value.get("authorUid")) ? "Bạn"
                        : String.valueOf(value.get("author"));
                comments.add(new CommentModel((String) id, commenter, (String) body,
                        ((Number) when).longValue()));
            }
        }
        Set<String> voters = new HashSet<>();
        Object rawVoters = item.get("voters");
        if (rawVoters instanceof List) {
            for (Object voter : (List<?>) rawVoters) if (voter instanceof String) voters.add((String) voter);
        }
        return new PostModel(item.getId(), title, category,
                uid.equals(authorUid) ? "Bạn" : authorName == null ? "Người dùng" : authorName,
                time, content, null, comments, voters);
    }
}

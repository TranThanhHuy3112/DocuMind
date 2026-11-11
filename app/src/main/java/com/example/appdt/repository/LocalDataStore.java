package com.example.appdt.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.example.appdt.model.CommentModel;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.PostAttachment;
import com.example.appdt.model.PostCategory;
import com.example.appdt.model.PostModel;
import com.example.appdt.model.RecentActivityModel;
import com.example.appdt.utils.DocumentPicker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stores local metadata and community content. Document bytes stay with their providers. */
public final class LocalDataStore {
    private static final String DOCUMENTS = "documents";
    private static final String ACTIVITIES = "activities";
    private static final String POSTS = "posts";
    private final SharedPreferences preferences;

    public static LocalDataStore forAccount(Context context, String uid) {
        return new LocalDataStore(SecurePrefs.open(context,
                SecurePrefs.hashedName("documind_local_", uid)));
    }

    public LocalDataStore(Context context, String preferencesName) {
        this(context.getApplicationContext()
                .getSharedPreferences(preferencesName, Context.MODE_PRIVATE));
    }

    private LocalDataStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public List<DocumentModel> loadDocuments() {
        List<DocumentModel> result = new ArrayList<>();
        JSONArray values = readArray(DOCUMENTS);
        for (int index = 0; index < values.length() && result.size() <
                WorkspaceRepository.MAX_DOCUMENTS; index++) {
            JSONObject item = values.optJSONObject(index);
            if (item == null) continue;
            String id = item.optString("id", "");
            String path = item.optString("path", "");
            String name = item.optString("name", "");
            if (id.isEmpty() || path.isEmpty() || name.isEmpty()
                    || !DocumentPicker.isAllowedPickedUri(Uri.parse(path))) continue;
            result.add(new DocumentModel(id, name, item.optLong("size", -1L),
                    item.optString("extension", "file"),
                    item.optString("mime", "application/octet-stream"),
                    0, false, path));
        }
        return Collections.unmodifiableList(result);
    }

    public void saveDocuments(List<DocumentModel> documents) {
        JSONArray values = new JSONArray();
        for (DocumentModel document : documents) {
            try {
                JSONObject item = new JSONObject();
                item.put("id", document.getId());
                item.put("path", document.getPath());
                item.put("name", document.getName());
                item.put("size", document.getSize());
                item.put("extension", document.getExtension());
                item.put("mime", document.getMimeType());
                values.put(item);
            } catch (JSONException ignored) { }
        }
        preferences.edit().putString(DOCUMENTS, values.toString()).commit();
    }

    public List<RecentActivityModel> loadActivities() {
        List<RecentActivityModel> result = new ArrayList<>();
        JSONArray values = readArray(ACTIVITIES);
        for (int index = 0; index < values.length() && result.size() < 6; index++) {
            JSONObject item = values.optJSONObject(index);
            if (item == null) continue;
            String title = item.optString("title", "");
            if (title.isEmpty()) continue;
            result.add(new RecentActivityModel(item.optLong("id"), title,
                    item.optString("detail", ""), item.optString("time", "")));
        }
        return Collections.unmodifiableList(result);
    }

    public void saveActivities(List<RecentActivityModel> activities) {
        JSONArray values = new JSONArray();
        for (RecentActivityModel activity : activities) {
            try {
                JSONObject item = new JSONObject();
                item.put("id", activity.getId());
                item.put("title", activity.getTitle());
                item.put("detail", activity.getDetail());
                item.put("time", activity.getTimeLabel());
                values.put(item);
            } catch (JSONException ignored) { }
        }
        preferences.edit().putString(ACTIVITIES, values.toString()).commit();
    }

    /** Returns null only when the app has never saved a community feed. */
    public List<PostModel> loadPosts() {
        if (!preferences.contains(POSTS)) return null;
        List<PostModel> result = new ArrayList<>();
        JSONArray values = readArray(POSTS);
        for (int index = 0; index < values.length(); index++) {
            JSONObject item = values.optJSONObject(index);
            if (item == null) continue;
            String id = item.optString("id", "");
            String title = item.optString("title", "");
            String author = item.optString("author", "");
            if (id.isEmpty() || title.isEmpty() || author.isEmpty()) continue;
            PostCategory category;
            try {
                category = PostCategory.valueOf(item.optString("category", "OTHER"));
            } catch (IllegalArgumentException error) {
                category = PostCategory.OTHER;
            }
            PostAttachment attachment = null;
            JSONObject file = item.optJSONObject("attachment");
            if (file != null && !file.optString("uri", "").isEmpty()) {
                Uri parsedUri = Uri.parse(file.optString("uri"));
                if (DocumentPicker.isAllowedPickedUri(parsedUri)) {
                    attachment = new PostAttachment(parsedUri,
                            file.optString("name", ""),
                            file.optString("mime", "application/octet-stream"));
                }
            }
            List<CommentModel> comments = new ArrayList<>();
            JSONArray savedComments = item.optJSONArray("comments");
            if (savedComments != null) {
                for (int commentIndex = 0; commentIndex < savedComments.length(); commentIndex++) {
                    JSONObject comment = savedComments.optJSONObject(commentIndex);
                    if (comment == null || comment.optString("id", "").isEmpty()) continue;
                    comments.add(new CommentModel(comment.optString("id"),
                            comment.optString("author", ""), comment.optString("content", ""),
                            comment.optLong("createdAt")));
                }
            }
            Set<String> voters = new HashSet<>();
            JSONArray savedVoters = item.optJSONArray("voters");
            if (savedVoters != null) {
                for (int voterIndex = 0; voterIndex < savedVoters.length(); voterIndex++) {
                    String voter = savedVoters.optString(voterIndex, "");
                    if (!voter.isEmpty()) voters.add(voter);
                }
            }
            boolean own = item.optBoolean("own", "Bạn".equals(author));
            result.add(new PostModel(id, title, category, author,
                    item.optLong("createdAt"), item.optString("content", ""),
                    attachment, comments, voters, own));
        }
        return Collections.unmodifiableList(result);
    }

    public void savePosts(List<PostModel> posts) {
        JSONArray values = new JSONArray();
        for (PostModel post : posts) {
            try {
                JSONObject item = new JSONObject();
                item.put("id", post.getId());
                item.put("title", post.getTitle());
                item.put("category", post.getCategory().name());
                item.put("author", post.getAuthor());
                item.put("createdAt", post.getCreatedAt());
                item.put("content", post.getContent());
                item.put("own", post.isOwn());
                PostAttachment attachment = post.getAttachment();
                if (attachment != null) {
                    JSONObject file = new JSONObject();
                    file.put("uri", attachment.getUri().toString());
                    file.put("name", attachment.getName());
                    file.put("mime", attachment.getMimeType());
                    item.put("attachment", file);
                }
                JSONArray comments = new JSONArray();
                for (CommentModel comment : post.getComments()) {
                    JSONObject saved = new JSONObject();
                    saved.put("id", comment.getId());
                    saved.put("author", comment.getAuthor());
                    saved.put("content", comment.getContent());
                    saved.put("createdAt", comment.getCreatedAt());
                    comments.put(saved);
                }
                item.put("comments", comments);
                JSONArray voters = new JSONArray();
                for (String voter : post.getHelpfulVoters()) voters.put(voter);
                item.put("voters", voters);
                values.put(item);
            } catch (JSONException ignored) { }
        }
        preferences.edit().putString(POSTS, values.toString()).commit();
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(preferences.getString(key, "[]"));
        } catch (JSONException error) {
            return new JSONArray();
        }
    }
}

package com.example.appdt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PostModel {
    private final String id;
    private final String title;
    private final PostCategory category;
    private final String author;
    private final long createdAt;
    private final String content;
    private final PostAttachment attachment;
    private final List<CommentModel> comments;
    private final Set<String> helpfulVoters;

    public PostModel(String id, String title, PostCategory category, String author,
                     long createdAt, String content, PostAttachment attachment,
                     List<CommentModel> comments, Set<String> helpfulVoters) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.category = Objects.requireNonNull(category);
        this.author = Objects.requireNonNull(author);
        this.createdAt = createdAt;
        this.content = Objects.requireNonNull(content);
        this.attachment = attachment;
        this.comments = Collections.unmodifiableList(new ArrayList<>(comments));
        this.helpfulVoters = Collections.unmodifiableSet(new HashSet<>(helpfulVoters));
    }

    public PostModel withComment(CommentModel comment) {
        List<CommentModel> updated = new ArrayList<>(comments);
        updated.add(comment);
        return new PostModel(id, title, category, author, createdAt, content,
                attachment, updated, helpfulVoters);
    }

    public PostModel withHelpfulVote(String userId) {
        Set<String> updated = new HashSet<>(helpfulVoters);
        updated.add(userId);
        return new PostModel(id, title, category, author, createdAt, content,
                attachment, comments, updated);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public PostCategory getCategory() { return category; }
    public String getAuthor() { return author; }
    public long getCreatedAt() { return createdAt; }
    public String getContent() { return content; }
    public PostAttachment getAttachment() { return attachment; }
    public List<CommentModel> getComments() { return comments; }
    public int getHelpfulCount() { return helpfulVoters.size(); }
    public Set<String> getHelpfulVoters() { return helpfulVoters; }
    public boolean hasVoted(String userId) { return helpfulVoters.contains(userId); }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PostModel)) return false;
        PostModel post = (PostModel) other;
        return createdAt == post.createdAt && id.equals(post.id) && title.equals(post.title)
                && category == post.category && author.equals(post.author)
                && content.equals(post.content) && Objects.equals(attachment, post.attachment)
                && comments.equals(post.comments) && helpfulVoters.equals(post.helpfulVoters);
    }

    @Override public int hashCode() {
        return Objects.hash(id, title, category, author, createdAt, content, attachment,
                comments, helpfulVoters);
    }
}

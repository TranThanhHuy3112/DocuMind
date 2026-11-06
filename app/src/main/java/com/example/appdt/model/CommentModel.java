package com.example.appdt.model;

import java.util.Objects;

public final class CommentModel {
    private final String id;
    private final String author;
    private final String content;
    private final long createdAt;

    public CommentModel(String id, String author, String content, long createdAt) {
        this.id = Objects.requireNonNull(id);
        this.author = Objects.requireNonNull(author);
        this.content = Objects.requireNonNull(content);
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public long getCreatedAt() { return createdAt; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CommentModel)) return false;
        CommentModel comment = (CommentModel) other;
        return createdAt == comment.createdAt && id.equals(comment.id)
                && author.equals(comment.author) && content.equals(comment.content);
    }

    @Override public int hashCode() {
        return Objects.hash(id, author, content, createdAt);
    }
}

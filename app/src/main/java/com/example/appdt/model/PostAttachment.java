package com.example.appdt.model;

import android.net.Uri;

import java.util.Objects;

public final class PostAttachment {
    private final Uri uri;
    private final String name;
    private final String mimeType;

    public PostAttachment(Uri uri, String name, String mimeType) {
        this.uri = Objects.requireNonNull(uri);
        this.name = Objects.requireNonNull(name);
        this.mimeType = Objects.requireNonNull(mimeType);
    }

    public Uri getUri() { return uri; }
    public String getName() { return name; }
    public String getMimeType() { return mimeType; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PostAttachment)) return false;
        PostAttachment attachment = (PostAttachment) other;
        return uri.equals(attachment.uri) && name.equals(attachment.name)
                && mimeType.equals(attachment.mimeType);
    }

    @Override public int hashCode() {
        return Objects.hash(uri, name, mimeType);
    }
}

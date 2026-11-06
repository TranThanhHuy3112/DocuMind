package com.example.appdt.model;

import java.util.Locale;
import java.util.Objects;

public final class DocumentModel {
    private final String id;
    private final String name;
    private final long size;
    private final String extension;
    private final String mimeType;
    private final int progress;
    private final boolean ready;
    private final boolean failed;
    private final String path;

    public DocumentModel(String path, String name, long size, String mimeType, boolean ready) {
        this(path, name, size, extensionOf(name), mimeType, ready ? 100 : 0, ready, false, path);
    }

    public DocumentModel(String id, String name, long size, String extension, String mimeType,
                         int progress, boolean ready, String path) {
        this(id, name, size, extension, mimeType, progress, ready, false, path);
    }

    private DocumentModel(String id, String name, long size, String extension, String mimeType,
                          int progress, boolean ready, boolean failed, String path) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.size = size;
        this.extension = Objects.requireNonNull(extension);
        this.mimeType = Objects.requireNonNull(mimeType);
        this.progress = Math.max(0, Math.min(100, progress));
        this.ready = ready && this.progress == 100;
        this.failed = failed;
        this.path = Objects.requireNonNull(path);
    }

    public DocumentModel withProgress(int value) {
        return new DocumentModel(id, name, size, extension, mimeType, value,
                value == 100, false, path);
    }

    public DocumentModel withFailure() {
        return new DocumentModel(id, name, size, extension, mimeType, progress,
                false, true, path);
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1
                ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "file";
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public long getSize() { return size; }
    public String getExtension() { return extension; }
    public String getMimeType() { return mimeType; }
    public int getProgress() { return progress; }
    public boolean isReady() { return ready; }
    public boolean isFailed() { return failed; }
    public String getPath() { return path; }
}

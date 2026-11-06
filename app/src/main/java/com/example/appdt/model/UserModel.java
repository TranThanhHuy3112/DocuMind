package com.example.appdt.model;

/** Immutable account record. The password hash is never a plaintext password. */
public final class UserModel {
    private final String id;
    private final String fullName;
    private final String email;
    private final String passwordHash;
    private final String avatarUrl;
    private final boolean emailVerified;
    private final long createdAt;

    public UserModel(String id, String fullName, String email, String passwordHash,
                     String avatarUrl, boolean emailVerified, long createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.avatarUrl = avatarUrl;
        this.emailVerified = emailVerified;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getAvatarUrl() { return avatarUrl; }
    public boolean isEmailVerified() { return emailVerified; }
    public long getCreatedAt() { return createdAt; }
}

package com.example.appdt.model;

/** Generated code for a future delivery adapter; it must not be shown as verified email. */
public final class OtpChallenge {
    private final String email;
    private final String code;
    private final long expiresAt;

    public OtpChallenge(String email, String code, long expiresAt) {
        this.email = email;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public String getEmail() { return email; }
    public String getCode() { return code; }
    public long getExpiresAt() { return expiresAt; }
}

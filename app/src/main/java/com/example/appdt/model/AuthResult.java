package com.example.appdt.model;

/** Structured result so screens can map validation errors to individual input fields. */
public final class AuthResult<T> {
    public enum Status { LOADING, SUCCESS, ERROR }
    public enum Error {
        NONE, NAME_REQUIRED, INVALID_EMAIL, WEAK_PASSWORD, PASSWORD_MISMATCH,
        EMAIL_EXISTS, ACCOUNT_NOT_FOUND, NO_PENDING_REGISTRATION, NO_PENDING_RESET,
        INVALID_CREDENTIALS, EMAIL_UNVERIFIED,
        INVALID_OTP, OTP_EXPIRED, OTP_LOCKED, STORAGE_ERROR
    }

    private final Status status;
    private final Error error;
    private final T value;
    private final int attemptsRemaining;
    private final long retryAt;

    private AuthResult(Status status, Error error, T value, int attemptsRemaining, long retryAt) {
        this.status = status;
        this.error = error;
        this.value = value;
        this.attemptsRemaining = attemptsRemaining;
        this.retryAt = retryAt;
    }

    public static <T> AuthResult<T> loading() {
        return new AuthResult<>(Status.LOADING, Error.NONE, null, 0, 0L);
    }

    public static <T> AuthResult<T> success(T value) {
        return new AuthResult<>(Status.SUCCESS, Error.NONE, value, 0, 0L);
    }

    public static <T> AuthResult<T> error(Error error) {
        return new AuthResult<>(Status.ERROR, error, null, 0, 0L);
    }

    public static <T> AuthResult<T> error(Error error, int attemptsRemaining, long retryAt) {
        return new AuthResult<>(Status.ERROR, error, null, attemptsRemaining, retryAt);
    }

    public Status getStatus() { return status; }
    public Error getError() { return error; }
    public T getValue() { return value; }
    public int getAttemptsRemaining() { return attemptsRemaining; }
    public long getRetryAt() { return retryAt; }
}

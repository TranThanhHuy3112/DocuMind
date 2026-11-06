package com.example.appdt.ui.auth;

import android.app.Application;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.appdt.model.AuthResult;
import com.example.appdt.model.OtpChallenge;
import com.example.appdt.model.UserModel;
import com.example.appdt.repository.AuthRepository;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** UI state for the later Login, Register and Verify screens. */
public final class AuthViewModel extends AndroidViewModel {
    private final AuthRepository repository;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<AuthResult<UserModel>> loginState = new MutableLiveData<>();
    private final MutableLiveData<AuthResult<OtpChallenge>> registerState =
            new MutableLiveData<>();
    private final MutableLiveData<AuthResult<UserModel>> otpState = new MutableLiveData<>();
    private final MutableLiveData<AuthResult<OtpChallenge>> resendState =
            new MutableLiveData<>();
    private final MutableLiveData<AuthResult<OtpChallenge>> resetRequestState =
            new MutableLiveData<>();
    private final MutableLiveData<AuthResult<OtpChallenge>> resetResendState =
            new MutableLiveData<>();
    private final MutableLiveData<AuthResult<UserModel>> resetCompletionState =
            new MutableLiveData<>();
    private final MutableLiveData<Long> countdownTimeRemaining = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> otpLockedUntil = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<UserModel> currentUser;
    private CountDownTimer countdown;
    private int activeOperations;
    private boolean cleared;

    public AuthViewModel(@NonNull Application application) {
        this(application, new AuthRepository(application));
    }

    AuthViewModel(@NonNull Application application, @NonNull AuthRepository repository) {
        super(application);
        this.repository = repository;
        currentUser = new MutableLiveData<>(repository.getCurrentUser());
    }

    public LiveData<AuthResult<UserModel>> getLoginState() { return loginState; }
    public LiveData<AuthResult<OtpChallenge>> getRegisterState() { return registerState; }
    public LiveData<AuthResult<UserModel>> getOtpState() { return otpState; }
    public LiveData<AuthResult<OtpChallenge>> getResendState() { return resendState; }
    public LiveData<AuthResult<OtpChallenge>> getResetRequestState() {
        return resetRequestState;
    }
    public LiveData<AuthResult<OtpChallenge>> getResetResendState() {
        return resetResendState;
    }
    public LiveData<AuthResult<UserModel>> getResetCompletionState() {
        return resetCompletionState;
    }
    public LiveData<Long> getCountdownTimeRemaining() { return countdownTimeRemaining; }
    public LiveData<Long> getOtpLockedUntil() { return otpLockedUntil; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<UserModel> getCurrentUser() { return currentUser; }

    public void register(String fullName, String email, String password, String confirmation) {
        execute(registerState, () -> repository.register(fullName, email, password, confirmation),
                result -> {
                    if (result.getStatus() == AuthResult.Status.SUCCESS) {
                        startCountdown(result.getValue().getExpiresAt());
                    }
                });
    }

    public void login(String email, String password) {
        login(email, password, true);
    }

    public void login(String email, String password, boolean rememberSession) {
        execute(loginState, () -> repository.login(email, password, rememberSession), result -> {
            if (result.getStatus() == AuthResult.Status.SUCCESS) {
                currentUser.setValue(result.getValue());
            }
        });
    }

    public void verifyOtp(String email, String code) {
        execute(otpState, () -> repository.verifyOtp(email, code), result -> {
            if (result.getStatus() == AuthResult.Status.SUCCESS) {
                stopCountdown();
                otpLockedUntil.setValue(0L);
                currentUser.setValue(result.getValue());
            } else if (result.getError() == AuthResult.Error.OTP_LOCKED) {
                otpLockedUntil.setValue(result.getRetryAt());
            }
        });
    }

    public void checkEmailVerified(String email) {
        execute(otpState, () -> repository.checkEmailVerified(email), result -> {
            if (result.getStatus() == AuthResult.Status.SUCCESS) {
                stopCountdown();
                currentUser.setValue(result.getValue());
            }
        });
    }

    public void resendOtp(String email) {
        execute(resendState, () -> repository.generateOtp(email), result -> {
            if (result.getStatus() == AuthResult.Status.SUCCESS) {
                otpLockedUntil.setValue(0L);
                startCountdown(result.getValue().getExpiresAt());
            } else if (result.getError() == AuthResult.Error.OTP_LOCKED) {
                otpLockedUntil.setValue(result.getRetryAt());
            }
        });
    }

    public void requestPasswordReset(String email) {
        execute(resetRequestState, () -> repository.requestPasswordReset(email), result -> {
            if (result.getStatus() == AuthResult.Status.SUCCESS) {
                otpLockedUntil.setValue(0L);
                startCountdown(result.getValue().getExpiresAt());
            } else if (result.getError() == AuthResult.Error.OTP_LOCKED) {
                otpLockedUntil.setValue(result.getRetryAt());
            }
        });
    }

    public void resendResetOtp(String email) {
        execute(resetResendState, () -> repository.generateResetOtp(email), result -> {
            if (result.getStatus() == AuthResult.Status.SUCCESS) {
                otpLockedUntil.setValue(0L);
                startCountdown(result.getValue().getExpiresAt());
            } else if (result.getError() == AuthResult.Error.OTP_LOCKED) {
                otpLockedUntil.setValue(result.getRetryAt());
            }
        });
    }

    public void resetPassword(String email, String code, String newPassword,
                              String confirmation) {
        execute(resetCompletionState,
                () -> repository.resetPassword(email, code, newPassword, confirmation), result -> {
                    if (result.getStatus() == AuthResult.Status.SUCCESS) {
                        stopCountdown();
                        otpLockedUntil.setValue(0L);
                        currentUser.setValue(null);
                    } else if (result.getError() == AuthResult.Error.OTP_LOCKED) {
                        otpLockedUntil.setValue(result.getRetryAt());
                    }
                });
    }

    public void resumeResetCountdown(String email) {
        executeCountdownLookup(() -> new long[] {repository.getResetExpiresAt(email),
                repository.getResetLockedUntil(email)});
    }

    /** Call from Verify screen onStart to continue the persisted expiry after rotation. */
    public void resumeCountdown(String email) {
        executeCountdownLookup(() -> new long[] {repository.getOtpExpiresAt(email),
                repository.getOtpLockedUntil(email)});
    }

    /** Call from Verify screen onStop; onCleared also cancels the timer. */
    public void stopCountdown() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
    }

    public void logout() {
        executeLogout();
    }

    private <T> void execute(MutableLiveData<AuthResult<T>> state,
                             Callable<AuthResult<T>> action, ResultConsumer<T> after) {
        mainHandler.post(() -> {
            if (cleared) return;
            activeOperations++;
            isLoading.setValue(true);
            state.setValue(AuthResult.loading());
            worker.execute(() -> {
                AuthResult<T> result;
                try {
                    result = action.call();
                } catch (Exception exception) {
                    result = AuthResult.error(AuthResult.Error.STORAGE_ERROR);
                }
                AuthResult<T> finished = result;
                mainHandler.post(() -> {
                    if (cleared) return;
                    state.setValue(finished);
                    after.accept(finished);
                    activeOperations--;
                    isLoading.setValue(activeOperations > 0);
                });
            });
        });
    }

    private void executeCountdownLookup(Callable<long[]> action) {
        worker.execute(() -> {
            long[] state;
            try {
                state = action.call();
            } catch (Exception exception) {
                state = new long[] {0L, 0L};
            }
            long[] value = state;
            mainHandler.post(() -> {
                if (!cleared) {
                    otpLockedUntil.setValue(value[1]);
                    startCountdown(value[0]);
                }
            });
        });
    }

    private void executeLogout() {
        worker.execute(() -> {
            repository.logout();
            mainHandler.post(() -> { if (!cleared) currentUser.setValue(null); });
        });
    }

    private void startCountdown(long expiresAt) {
        stopCountdown();
        long remaining = Math.max(0L, expiresAt - System.currentTimeMillis());
        countdownTimeRemaining.setValue((remaining + 999L) / 1000L);
        if (remaining == 0L) return;
        countdown = new CountDownTimer(remaining, 1000L) {
            @Override public void onTick(long millisUntilFinished) {
                countdownTimeRemaining.setValue((millisUntilFinished + 999L) / 1000L);
            }

            @Override public void onFinish() {
                countdown = null;
                countdownTimeRemaining.setValue(0L);
            }
        }.start();
    }

    @Override protected void onCleared() {
        cleared = true;
        stopCountdown();
        mainHandler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onCleared();
    }

    private interface ResultConsumer<T> { void accept(AuthResult<T> result); }
}

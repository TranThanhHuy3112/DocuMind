package com.example.appdt.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.appdt.MainActivity;
import com.example.appdt.R;
import com.example.appdt.databinding.ActivityVerifyOtpBinding;
import com.example.appdt.model.AuthResult;
import com.example.appdt.model.OtpChallenge;
import com.example.appdt.model.UserModel;
import com.example.appdt.repository.AuthRepository;

/** Firebase hosts the email action page; this screen refreshes the signed-in user afterward. */
public final class VerifyOtpActivity extends BaseAuthActivity {
    public static final String EXTRA_EMAIL = "email";
    public static final String EXTRA_SEND_FAILED = "verification_link_send_failed";
    private static final String NAVIGATED = "navigated_to_main";

    private ActivityVerifyOtpBinding binding;
    private AuthViewModel viewModel;
    private String email;
    private long secondsLeft;
    private boolean loading;
    private boolean navigated;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        email = getIntent().getStringExtra(EXTRA_EMAIL);
        if (!AuthRepository.isValidEmail(email)) {
            finish();
            return;
        }
        binding = ActivityVerifyOtpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        navigated = savedInstanceState != null && savedInstanceState.getBoolean(NAVIGATED);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        binding.verifySubtitle.setText(getString(R.string.auth_verify_subtitle, email));
        if (getIntent().getBooleanExtra(EXTRA_SEND_FAILED, false)) {
            showMessage(binding.verifyMessage, R.string.auth_verify_send_failed);
        }
        binding.verifySubmit.setOnClickListener(ignored -> viewModel.checkEmailVerified(email));
        binding.verifyResend.setOnClickListener(ignored -> viewModel.resendOtp(email));
        binding.verifyBack.setOnClickListener(ignored -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra(EXTRA_EMAIL, email);
            startActivity(intent);
            finish();
        });
        viewModel.getCountdownTimeRemaining().observe(this, value -> {
            secondsLeft = value == null ? 0L : value;
            renderControls();
        });
        viewModel.getIsLoading().observe(this, value -> {
            loading = Boolean.TRUE.equals(value);
            binding.verifyProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
            renderControls();
        });
        viewModel.getOtpState().observe(this, this::renderVerification);
        viewModel.getResendState().observe(this, this::renderResend);
    }

    private void renderVerification(AuthResult<UserModel> result) {
        if (result.getStatus() == AuthResult.Status.SUCCESS && !navigated) {
            navigated = true;
            Toast.makeText(this, R.string.auth_verified_email, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else if (result.getStatus() == AuthResult.Status.ERROR) {
            int msg;
            if (result.getError() == AuthResult.Error.EMAIL_UNVERIFIED) {
                msg = R.string.auth_verify_pending;
            } else if (result.getError() == AuthResult.Error.OTP_LOCKED) {
                msg = R.string.auth_error_otp_locked;
            } else {
                msg = R.string.auth_error_storage;
            }
            showMessage(binding.verifyMessage, msg);
        }
    }

    private void renderResend(AuthResult<OtpChallenge> result) {
        if (result.getStatus() == AuthResult.Status.SUCCESS) {
            showMessage(binding.verifyMessage, R.string.auth_verify_resend_done);
        } else if (result.getStatus() == AuthResult.Status.ERROR) {
            showMessage(binding.verifyMessage, result.getError() == AuthResult.Error.OTP_LOCKED
                    ? R.string.auth_error_otp_locked : R.string.auth_error_storage);
        }
    }

    private void renderControls() {
        if (binding == null) return;
        binding.verifySubmit.setEnabled(!loading);
        binding.verifyResend.setEnabled(!loading && secondsLeft == 0);
        binding.verifyBack.setEnabled(!loading);
        binding.verifyResend.setText(secondsLeft > 0
                ? getString(R.string.auth_resend_wait, secondsLeft)
                : getString(R.string.auth_resend_now));
    }

    @Override protected void onStart() {
        super.onStart();
        if (viewModel != null) viewModel.resumeCountdown(email);
    }

    @Override protected void onStop() {
        if (viewModel != null) viewModel.stopCountdown();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(NAVIGATED, navigated);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}

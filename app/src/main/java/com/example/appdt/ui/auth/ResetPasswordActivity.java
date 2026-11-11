package com.example.appdt.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.appdt.R;
import com.example.appdt.databinding.ActivityResetPasswordBinding;
import com.example.appdt.model.AuthResult;
import com.example.appdt.model.OtpChallenge;
import com.example.appdt.repository.AuthRepository;
import com.google.android.material.color.MaterialColors;

/** Firebase opens its hosted password change page from the reset email. */
public final class ResetPasswordActivity extends BaseAuthActivity {
    private static final String EMAIL = "reset_email";
    private ActivityResetPasswordBinding binding;
    private AuthViewModel viewModel;
    private String email;
    private String lastCheckedEmail = "";
    private long secondsLeft;
    private boolean loading;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResetPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        email = savedInstanceState == null
                ? getIntent().getStringExtra(VerifyOtpActivity.EXTRA_EMAIL)
                : savedInstanceState.getString(EMAIL);
        if (email == null) email = "";
        binding.resetEmail.setText(email);
        if (AuthRepository.isValidEmail(email)) {
            lastCheckedEmail = email;
            viewModel.resumeResetCountdown(email);
        }
        binding.resetRequest.setOnClickListener(ignored -> requestLink());
        binding.resetBack.setOnClickListener(ignored -> {
            Intent intent = new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(VerifyOtpActivity.EXTRA_EMAIL, email);
            startActivity(intent);
            finish();
        });
        binding.resetEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start,
                                                    int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start,
                                                int before, int count) {
                binding.resetEmailLayout.setError(null);
                binding.resetMessage.setVisibility(View.GONE);
                email = text.toString().trim();
                if (AuthRepository.isValidEmail(email) && !email.equals(lastCheckedEmail)) {
                    lastCheckedEmail = email;
                    viewModel.resumeResetCountdown(email);
                }
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        viewModel.getCountdownTimeRemaining().observe(this, value -> {
            secondsLeft = value == null ? 0L : value;
            renderControls();
        });
        viewModel.getIsLoading().observe(this, value -> {
            loading = Boolean.TRUE.equals(value);
            binding.resetProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
            renderControls();
        });
        viewModel.getResetRequestState().observe(this, this::renderRequest);
    }

    private void requestLink() {
        email = value(binding.resetEmail).trim();
        binding.resetEmailLayout.setError(null);
        binding.resetMessage.setVisibility(View.GONE);
        if (!AuthRepository.isValidEmail(email)) {
            binding.resetEmailLayout.setError(getString(R.string.auth_error_email));
            return;
        }
        viewModel.requestPasswordReset(email);
    }

    private void renderRequest(AuthResult<OtpChallenge> result) {
        if (result.getStatus() == AuthResult.Status.SUCCESS) {
            binding.resetMessage.setText(R.string.auth_reset_link_sent);
            binding.resetMessage.setTextColor(MaterialColors.getColor(binding.resetMessage,
                    com.google.android.material.R.attr.colorPrimary));
            binding.resetMessage.setVisibility(View.VISIBLE);
        } else if (result.getStatus() == AuthResult.Status.ERROR) {
            binding.resetMessage.setText(result.getError() == AuthResult.Error.OTP_LOCKED
                    ? R.string.auth_error_otp_locked : R.string.auth_error_storage);
            binding.resetMessage.setTextColor(getColor(R.color.workspace_error));
            binding.resetMessage.setVisibility(View.VISIBLE);
        }
    }

    private void renderControls() {
        if (binding == null) return;
        binding.resetRequest.setEnabled(!loading && secondsLeft == 0);
        binding.resetBack.setEnabled(!loading);
        binding.resetRequest.setText(secondsLeft > 0
                ? getString(R.string.auth_reset_link_wait, secondsLeft)
                : getString(R.string.auth_reset_request));
    }

    @Override protected void onStart() {
        super.onStart();
        if (viewModel != null) viewModel.resumeResetCountdown(email);
    }

    @Override protected void onStop() {
        if (viewModel != null) viewModel.stopCountdown();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString(EMAIL, email);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}

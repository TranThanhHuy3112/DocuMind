package com.example.appdt.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.appdt.MainActivity;
import com.example.appdt.R;
import com.example.appdt.databinding.ActivityLoginBinding;
import com.example.appdt.model.AuthResult;
import com.example.appdt.model.UserModel;
import com.example.appdt.repository.AuthRepository;

public final class LoginActivity extends BaseAuthActivity {
    private static final String NAVIGATED = "navigated";
    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;
    private boolean navigated;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (savedInstanceState != null) {
            navigated = savedInstanceState.getBoolean(NAVIGATED);
        }
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        String initialEmail = getIntent().getStringExtra(VerifyOtpActivity.EXTRA_EMAIL);
        if (initialEmail != null && savedInstanceState == null) binding.loginEmail.setText(initialEmail);
        binding.loginSubmit.setOnClickListener(ignored -> submit());
        binding.loginRegisterLink.setOnClickListener(ignored -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });
        binding.loginForgot.setOnClickListener(ignored -> {
            Intent intent = new Intent(this, ResetPasswordActivity.class);
            intent.putExtra(VerifyOtpActivity.EXTRA_EMAIL, value(binding.loginEmail).trim());
            startActivity(intent);
        });
        watch(binding.loginEmail, () -> binding.loginEmailLayout.setError(null));
        watch(binding.loginPassword, () -> {
            binding.loginPasswordLayout.setError(null);
            binding.loginMessage.setVisibility(View.GONE);
        });
        viewModel.getIsLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            binding.loginProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.loginSubmit.setEnabled(!busy);
            binding.loginForgot.setEnabled(!busy);
            binding.loginRegisterLink.setEnabled(!busy);
        });
        viewModel.getLoginState().observe(this, this::renderLogin);
    }

    private void submit() {
        String email = value(binding.loginEmail).trim();
        String password = value(binding.loginPassword);
        binding.loginEmailLayout.setError(null);
        binding.loginPasswordLayout.setError(null);
        binding.loginMessage.setVisibility(View.GONE);
        if (!AuthRepository.isValidEmail(email)) {
            binding.loginEmailLayout.setError(getString(R.string.auth_error_email));
            binding.loginEmail.requestFocus();
            return;
        }
        if (password.isEmpty() || password.length() > AuthRepository.MAX_PASSWORD_LENGTH) {
            binding.loginPasswordLayout.setError(getString(R.string.auth_error_credentials));
            binding.loginPassword.requestFocus();
            return;
        }
        viewModel.login(email, password, binding.loginRemember.isChecked());
    }

    private void renderLogin(AuthResult<UserModel> result) {
        if (result.getStatus() == AuthResult.Status.SUCCESS && !navigated) {
            navigated = true;
            Intent intent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else if (result.getStatus() == AuthResult.Status.ERROR) {
            if (result.getError() == AuthResult.Error.EMAIL_UNVERIFIED) {
                Intent intent = new Intent(this, VerifyOtpActivity.class);
                intent.putExtra(VerifyOtpActivity.EXTRA_EMAIL, value(binding.loginEmail).trim());
                startActivity(intent);
                finish();
            } else if (result.getError() == AuthResult.Error.INVALID_EMAIL) {
                binding.loginEmailLayout.setError(getString(R.string.auth_error_email));
            } else {
                showMessage(binding.loginMessage,
                        result.getError() == AuthResult.Error.INVALID_CREDENTIALS
                        ? R.string.auth_error_credentials : R.string.auth_error_storage);
            }
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (binding == null || intent == null) return;
        String emailExtra = intent.getStringExtra(VerifyOtpActivity.EXTRA_EMAIL);
        if (emailExtra != null) {
            binding.loginEmail.setText(emailExtra);
            binding.loginEmailLayout.setError(null);
            binding.loginMessage.setVisibility(View.GONE);
        }
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

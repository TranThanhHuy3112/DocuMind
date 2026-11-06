package com.example.appdt.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import com.example.appdt.MainActivity;
import com.example.appdt.R;
import com.example.appdt.databinding.ActivityLoginBinding;
import com.example.appdt.model.AuthResult;
import com.example.appdt.model.UserModel;
import com.example.appdt.repository.AuthRepository;
import com.example.appdt.repository.SettingsRepository;

public final class LoginActivity extends AppCompatActivity {
    private static final String NAVIGATED = "navigated";
    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;
    private boolean navigated;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        SettingsRepository settings = new SettingsRepository(this);
        AppCompatDelegate.setDefaultNightMode(settings.getNightMode());
        setTheme(R.style.Theme_DocuMind);
        getTheme().applyStyle(settings.getAccent().getThemeOverlay(), true);
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
        binding.loginEmail.addTextChangedListener(clearError(() ->
                binding.loginEmailLayout.setError(null)));
        binding.loginPassword.addTextChangedListener(clearError(() -> {
            binding.loginPasswordLayout.setError(null);
            binding.loginMessage.setVisibility(View.GONE);
        }));
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
        if (password.isEmpty()) {
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
                showMessage(result.getError() == AuthResult.Error.INVALID_CREDENTIALS
                        ? R.string.auth_error_credentials : R.string.auth_error_storage);
            }
        }
    }

    private void showMessage(int message) {
        binding.loginMessage.setText(message);
        binding.loginMessage.setVisibility(View.VISIBLE);
    }

    private static String value(com.google.android.material.textfield.TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    private static TextWatcher clearError(Runnable action) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start,
                                                    int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start,
                                                int before, int count) { action.run(); }
            @Override public void afterTextChanged(Editable text) { }
        };
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(NAVIGATED, navigated);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onStop() {
        viewModel.stopCountdown();
        super.onStop();
    }

    @Override protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}

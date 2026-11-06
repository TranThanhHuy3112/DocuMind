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

import com.example.appdt.R;
import com.example.appdt.databinding.ActivityRegisterBinding;
import com.example.appdt.model.AuthResult;
import com.example.appdt.model.OtpChallenge;
import com.example.appdt.repository.AuthRepository;
import com.example.appdt.repository.SettingsRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class RegisterActivity extends AppCompatActivity {
    private static final String NAVIGATED = "navigated_to_verify";
    private ActivityRegisterBinding binding;
    private AuthViewModel viewModel;
    private boolean navigated;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        SettingsRepository settings = new SettingsRepository(this);
        AppCompatDelegate.setDefaultNightMode(settings.getNightMode());
        setTheme(R.style.Theme_DocuMind);
        getTheme().applyStyle(settings.getAccent().getThemeOverlay(), true);
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        navigated = savedInstanceState != null && savedInstanceState.getBoolean(NAVIGATED);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.registerContinue.setOnClickListener(ignored -> submit());
        binding.registerLoginLink.setOnClickListener(ignored -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
        installLiveValidation();
        viewModel.getRegisterState().observe(this, this::renderResult);
    }

    private void installLiveValidation() {
        watch(binding.registerName, () -> binding.registerNameLayout.setError(null));
        watch(binding.registerEmail, () -> binding.registerEmailLayout.setError(null));
        watch(binding.registerPassword, () -> {
            binding.registerPasswordLayout.setError(null);
            updateConfirmationError();
        });
        watch(binding.registerConfirmation, this::updateConfirmationError);
    }

    private void updateConfirmationError() {
        String confirmation = value(binding.registerConfirmation);
        binding.registerConfirmationLayout.setError(!confirmation.isEmpty()
                && !confirmation.equals(value(binding.registerPassword))
                ? getString(R.string.auth_error_confirm) : null);
    }

    private void submit() {
        String name = value(binding.registerName).trim();
        String email = value(binding.registerEmail).trim();
        String password = value(binding.registerPassword);
        String confirmation = value(binding.registerConfirmation);
        binding.registerMessage.setVisibility(View.GONE);
        binding.registerNameLayout.setError(null);
        binding.registerEmailLayout.setError(null);
        binding.registerPasswordLayout.setError(null);
        binding.registerConfirmationLayout.setError(null);

        TextInputLayout firstError = null;
        if (name.isEmpty()) {
            binding.registerNameLayout.setError(getString(R.string.auth_error_name));
            firstError = binding.registerNameLayout;
        }
        if (!AuthRepository.isValidEmail(email)) {
            binding.registerEmailLayout.setError(getString(R.string.auth_error_email));
            if (firstError == null) firstError = binding.registerEmailLayout;
        }
        if (!AuthRepository.isStrongPassword(password)) {
            binding.registerPasswordLayout.setError(getString(R.string.auth_error_password));
            if (firstError == null) firstError = binding.registerPasswordLayout;
        }
        if (!password.equals(confirmation)) {
            binding.registerConfirmationLayout.setError(getString(R.string.auth_error_confirm));
            if (firstError == null) firstError = binding.registerConfirmationLayout;
        }
        if (firstError != null) {
            if (firstError.getEditText() != null) firstError.getEditText().requestFocus();
            return;
        }
        viewModel.register(name, email, password, confirmation);
    }

    private void renderResult(AuthResult<OtpChallenge> result) {
        boolean loading = result.getStatus() == AuthResult.Status.LOADING;
        binding.registerProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.registerContinue.setEnabled(!loading);
        binding.registerLoginLink.setEnabled(!loading);
        if (result.getStatus() == AuthResult.Status.ERROR) {
            renderError(result.getError());
        } else if (result.getStatus() == AuthResult.Status.SUCCESS && !navigated) {
            navigated = true;
            Intent intent = new Intent(this, VerifyOtpActivity.class);
            intent.putExtra(VerifyOtpActivity.EXTRA_EMAIL, result.getValue().getEmail());
            intent.putExtra(VerifyOtpActivity.EXTRA_SEND_FAILED,
                    result.getValue().getExpiresAt() == 0L);
            startActivity(intent);
            finish();
        }
    }

    private void renderError(AuthResult.Error error) {
        switch (error) {
            case NAME_REQUIRED:
                binding.registerNameLayout.setError(getString(R.string.auth_error_name));
                break;
            case INVALID_EMAIL:
                binding.registerEmailLayout.setError(getString(R.string.auth_error_email));
                break;
            case WEAK_PASSWORD:
                binding.registerPasswordLayout.setError(getString(R.string.auth_error_password));
                break;
            case PASSWORD_MISMATCH:
                binding.registerConfirmationLayout.setError(getString(R.string.auth_error_confirm));
                break;
            case EMAIL_EXISTS:
                binding.registerEmailLayout.setError(getString(R.string.auth_error_email_exists));
                break;
            default:
                binding.registerMessage.setText(error == AuthResult.Error.OTP_LOCKED
                        ? R.string.auth_error_otp_locked : error == AuthResult.Error.FIREBASE_SETUP
                        ? R.string.auth_error_firebase_setup : R.string.auth_error_storage);
                binding.registerMessage.setVisibility(View.VISIBLE);
                break;
        }
    }

    private static String value(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    private static void watch(TextInputEditText field, Runnable changed) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start,
                                                    int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start,
                                                int before, int count) { changed.run(); }
            @Override public void afterTextChanged(Editable text) { }
        });
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

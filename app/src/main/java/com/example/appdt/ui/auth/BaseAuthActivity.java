package com.example.appdt.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.appdt.repository.SettingsRepository;
import com.google.android.material.textfield.TextInputEditText;

abstract class BaseAuthActivity extends AppCompatActivity {
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        SettingsRepository.applyThemeAndSecureWindow(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    @Override public void setContentView(View view) {
        super.setContentView(view);
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            target.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(view);
    }

    protected static String value(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    protected static void watch(TextInputEditText field, Runnable changed) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start,
                                                    int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start,
                                                int before, int count) { changed.run(); }
            @Override public void afterTextChanged(Editable text) { }
        });
    }

    protected static void showMessage(TextView target, int message) {
        target.setText(message);
        target.setVisibility(View.VISIBLE);
    }
}

package com.example.appdt.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.appdt.R;
import com.example.appdt.model.AccentColor;
import com.example.appdt.model.DisplayDensity;

/** Local appearance storage. Retains no Activity or View references. */
public final class SettingsRepository {
    private final SharedPreferences preferences;

    public SettingsRepository(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences("documind_settings", Context.MODE_PRIVATE);
    }

    public static SettingsRepository applyThemeAndSecureWindow(AppCompatActivity activity) {
        SettingsRepository settings = new SettingsRepository(activity);
        AppCompatDelegate.setDefaultNightMode(settings.getNightMode());
        activity.setTheme(R.style.Theme_DocuMind);
        activity.getTheme().applyStyle(settings.getAccent().getThemeOverlay(), true);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        return settings;
    }

    public static boolean isDarkMode(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public AccentColor getAccent() {
        try {
            return AccentColor.valueOf(preferences.getString("accent", AccentColor.PURPLE.name()));
        } catch (IllegalArgumentException exception) {
            return AccentColor.PURPLE;
        }
    }

    public void setAccent(AccentColor accent) {
        preferences.edit().putString("accent", accent.name()).apply();
    }

    public int getNightMode() {
        int mode = preferences.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (mode == AppCompatDelegate.MODE_NIGHT_YES || mode == AppCompatDelegate.MODE_NIGHT_NO) {
            return mode;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    public void setNightMode(int mode) {
        preferences.edit().putInt("night_mode", mode).apply();
    }

    public DisplayDensity getDisplayDensity() {
        try {
            return DisplayDensity.valueOf(preferences.getString("display_density",
                    DisplayDensity.COMFORTABLE.name()));
        } catch (IllegalArgumentException exception) {
            return DisplayDensity.COMFORTABLE;
        }
    }

    public void setDisplayDensity(DisplayDensity density) {
        preferences.edit().putString("display_density", density.name()).apply();
    }
}

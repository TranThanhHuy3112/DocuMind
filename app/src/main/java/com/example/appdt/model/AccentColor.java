package com.example.appdt.model;

import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import com.example.appdt.R;

public enum AccentColor {
    PURPLE(R.string.accent_purple, R.style.ThemeOverlay_DocuMind_Purple),
    BLUE(R.string.accent_blue, R.style.ThemeOverlay_DocuMind_Blue),
    GREEN(R.string.accent_green, R.style.ThemeOverlay_DocuMind_Green),
    PINK(R.string.accent_pink, R.style.ThemeOverlay_DocuMind_Pink),
    ORANGE(R.string.accent_orange, R.style.ThemeOverlay_DocuMind_Orange);

    private final int label;
    private final int themeOverlay;

    AccentColor(@StringRes int label, @StyleRes int themeOverlay) {
        this.label = label;
        this.themeOverlay = themeOverlay;
    }

    @StringRes public int getLabel() { return label; }
    @StyleRes public int getThemeOverlay() { return themeOverlay; }
}

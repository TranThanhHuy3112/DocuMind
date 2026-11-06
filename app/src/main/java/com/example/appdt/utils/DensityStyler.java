package com.example.appdt.utils;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.example.appdt.model.DisplayDensity;
import com.example.appdt.repository.SettingsRepository;
import com.google.android.material.card.MaterialCardView;

/** Adjusts list spacing without shrinking text or interactive touch targets. */
public final class DensityStyler {
    private DensityStyler() { }

    public static void applyCard(MaterialCardView card, int compactPaddingDp,
                                 int compactBottomGapDp) {
        Context context = card.getContext();
        if (new SettingsRepository(context).getDisplayDensity() != DisplayDensity.COMPACT) return;
        ViewGroup.LayoutParams raw = card.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) raw;
            margins.bottomMargin = dp(context, compactBottomGapDp);
            card.setLayoutParams(margins);
        }
        if (card.getChildCount() > 0) {
            View content = card.getChildAt(0);
            int padding = dp(context, compactPaddingDp);
            content.setPadding(padding, padding, padding, padding);
        }
    }

    public static void applyActivityRow(View row) {
        Context context = row.getContext();
        if (new SettingsRepository(context).getDisplayDensity() != DisplayDensity.COMPACT) return;
        row.setMinimumHeight(dp(context, 56));
        row.setPadding(row.getPaddingLeft(), dp(context, 6), row.getPaddingRight(),
                dp(context, 6));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

package com.example.appdt.ui.tools;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public final class RevenueBarChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<RevenueCsvParser.Entry> entries = new ArrayList<>();
    private final int accent;
    private final int labelColor;

    public RevenueBarChartView(Context context) {
        super(context);
        accent = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary);
        labelColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        setMinimumHeight((int) (210 * getResources().getDisplayMetrics().density));
    }

    public void setEntries(List<RevenueCsvParser.Entry> values) {
        entries = new ArrayList<>(values);
        StringBuilder description = new StringBuilder();
        for (RevenueCsvParser.Entry entry : entries) {
            if (description.length() > 0) description.append(", ");
            description.append(entry.label).append(": ").append(entry.value);
        }
        setContentDescription(description.toString());
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) (220 * getResources().getDisplayMetrics().density + 0.5f);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (entries.isEmpty()) return;
        float density = getResources().getDisplayMetrics().density;
        float top = 26 * density;
        float baseline = getHeight() - 38 * density;
        float chartHeight = baseline - top;
        float slot = getWidth() / (float) entries.size();
        double max = 1;
        for (RevenueCsvParser.Entry entry : entries) max = Math.max(max, entry.value);
        paint.setColor(accent);
        for (int i = 0; i < entries.size(); i++) {
            RevenueCsvParser.Entry entry = entries.get(i);
            float center = slot * (i + 0.5f);
            float half = Math.min(slot * 0.30f, 22 * density);
            float height = (float) (entry.value / max * chartHeight);
            canvas.drawRoundRect(center - half, baseline - height, center + half, baseline,
                    5 * density, 5 * density, paint);
            paint.setColor(labelColor);
            if (entries.size() <= 6) {
                paint.setTextSize(10 * density);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(String.format(java.util.Locale.getDefault(), "%.0f", entry.value),
                        center, baseline - height - 7 * density, paint);
            }
            paint.setTextSize(11 * density);
            paint.setTextAlign(Paint.Align.CENTER);
            String label = entry.label.length() > 8
                    ? entry.label.substring(0, 7) + "…" : entry.label;
            canvas.drawText(label, center, baseline + 20 * density, paint);
            paint.setColor(accent);
        }
    }
}

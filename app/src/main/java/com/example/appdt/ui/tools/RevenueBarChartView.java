package com.example.appdt.ui.tools;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RevenueBarChartView extends View {
    private static final Locale VI_LOCALE = Locale.forLanguageTag("vi-VN");
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<TabularData.ChartEntry> entries = new ArrayList<>();
    private String[] valueLabels = new String[0];
    private String[] axisLabels = new String[0];
    private final float density;
    private final int accent;
    private final int labelColor;

    public RevenueBarChartView(Context context) {
        this(context, null);
    }

    public RevenueBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        accent = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary);
        labelColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        paint.setTextAlign(Paint.Align.CENTER);
        setMinimumHeight((int) (210 * density));
    }

    public void setEntries(List<TabularData.ChartEntry> values) {
        entries = new ArrayList<>(values);
        valueLabels = new String[entries.size()];
        axisLabels = new String[entries.size()];
        NumberFormat numberFormat = NumberFormat.getNumberInstance(VI_LOCALE);
        numberFormat.setMaximumFractionDigits(0);
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            TabularData.ChartEntry entry = entries.get(i);
            valueLabels[i] = formatCompact(entry.value, numberFormat);
            axisLabels[i] = entry.label.length() > 8
                    ? entry.label.substring(0, 7) + "…" : entry.label;
            if (description.length() > 0) description.append(", ");
            description.append(entry.label).append(": ").append(entry.value);
        }
        setContentDescription(description.toString());
        invalidate();
    }

    private static String formatCompact(double value, NumberFormat fullFormat) {
        if (value >= 1_000_000_000d) return String.format(VI_LOCALE, "%.1fB", value / 1_000_000_000d);
        if (value >= 1_000_000d) return String.format(VI_LOCALE, "%.1fM", value / 1_000_000d);
        if (value >= 100_000d) return String.format(VI_LOCALE, "%.0fK", value / 1_000d);
        return fullFormat.format(value);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) (220 * density + 0.5f);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (entries.isEmpty()) return;
        float top = 26 * density;
        float baseline = getHeight() - 38 * density;
        float chartHeight = Math.max(0f, baseline - top);
        float slot = getWidth() / (float) entries.size();
        double max = 0, min = 0;
        for (TabularData.ChartEntry entry : entries) {
            max = Math.max(max, entry.value);
            min = Math.min(min, entry.value);
        }
        double range = max - min;
        if (range == 0) range = 1;
        float zero = baseline - (float) ((-min) / range * chartHeight);
        paint.setColor(labelColor);
        canvas.drawLine(0, zero, getWidth(), zero, paint);
        paint.setColor(accent);
        for (int i = 0; i < entries.size(); i++) {
            TabularData.ChartEntry entry = entries.get(i);
            float center = slot * (i + 0.5f);
            float half = Math.min(slot * 0.30f, 22 * density);
            float valueY = baseline - (float) ((entry.value - min) / range * chartHeight);
            canvas.drawRoundRect(center - half, Math.min(zero, valueY), center + half, Math.max(zero, valueY),
                    5 * density, 5 * density, paint);
            paint.setColor(labelColor);
            if (entries.size() <= 6) {
                paint.setTextSize(10 * density);
                canvas.drawText(valueLabels[i], center, Math.max(top, Math.min(zero, valueY) - 7 * density), paint);
            }
            paint.setTextSize(11 * density);
            canvas.drawText(axisLabels[i], center, baseline + 20 * density, paint);
            paint.setColor(accent);
        }
    }
}

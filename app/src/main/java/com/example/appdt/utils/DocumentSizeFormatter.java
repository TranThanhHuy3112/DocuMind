package com.example.appdt.utils;

import android.content.Context;

import com.example.appdt.R;

public final class DocumentSizeFormatter {
    private DocumentSizeFormatter() { }

    public static String format(Context context, long bytes) {
        if (bytes < 0) return context.getString(R.string.unknown_size);
        if (bytes < 1024) return context.getString(R.string.size_bytes, bytes);
        if (bytes < 1024 * 1024) return context.getString(R.string.size_kb,
                bytes / 1024.0);
        return context.getString(R.string.size_mb, bytes / (1024.0 * 1024));
    }
}

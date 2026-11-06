package com.example.appdt.utils;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.DocumentCategory;

public final class DocumentPicker {
    private DocumentPicker() { }

    public static String[] mimeTypes() {
        // Providers disagree on MIME types for CSV and office formats.
        // Validate the selected file ourselves after DocumentsUI returns it.
        return new String[]{"*/*"};
    }

    public static boolean isSupported(DocumentModel document) {
        return DocumentCategory.from(document) != DocumentCategory.OTHER;
    }

    public static boolean keepReadPermission(ContentResolver resolver, Uri uri) {
        if (!"content".equals(uri.getScheme())) return false;
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return true;
        } catch (SecurityException | IllegalArgumentException ignored) {
            return false;
        }
    }

    public static DocumentModel readMetadata(ContentResolver resolver, Uri uri,
                                             String unnamedDocument) {
        String name = uri.getLastPathSegment() == null
                ? unnamedDocument : uri.getLastPathSegment();
        long size = -1L;
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn);
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn);
            }
        } catch (RuntimeException ignored) {
            // Fallback to URI metadata.
        }
        String mimeType;
        try {
            mimeType = resolver.getType(uri);
        } catch (RuntimeException ignored) {
            mimeType = null;
        }
        return new DocumentModel(uri.toString(), name, size,
                mimeType == null ? "application/octet-stream" : mimeType, false);
    }
}

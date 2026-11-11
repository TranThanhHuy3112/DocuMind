package com.example.appdt.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.DocumentCategory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

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

    public static boolean isAllowedPickedUri(Uri uri) {
        return uri != null && "content".equalsIgnoreCase(uri.getScheme());
    }

    public static boolean isReadableUri(Context context, Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if ("content".equals(scheme)) return true;
        if ("file".equals(scheme) && context != null && uri.getPath() != null) {
            try {
                File cacheDir = context.getApplicationContext().getCacheDir();
                String cacheRoot = cacheDir.getCanonicalPath() + File.separator;
                String target = new File(uri.getPath()).getCanonicalPath();
                return target.startsWith(cacheRoot);
            } catch (IOException ignored) {
                return false;
            }
        }
        return false;
    }

    public static boolean isBlockedPath(String rawPath) {
        if (rawPath == null) return true;
        String trimmed = rawPath.trim();
        if (trimmed.isEmpty()) return true;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file:")) {
            int cut = lower.length();
            int q = lower.indexOf('?');
            if (q >= 0 && q < cut) cut = q;
            int h = lower.indexOf('#');
            if (h >= 0 && h < cut) cut = h;
            String pathPart = lower.substring(0, cut);
            if (pathPart.contains("..") || pathPart.contains("%2e") || pathPart.contains("\\")) {
                return true;
            }
            return !pathPart.contains("/cache/");
        }
        int colon = lower.indexOf(':');
        if (colon <= 0) return true;
        String scheme = lower.substring(0, colon);
        return !"content".equals(scheme) && !"test".equals(scheme);
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

    public static byte[] readBoundedBytes(InputStream stream, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > maxBytes) {
                throw new IOException("Stream exceeds size limit");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}

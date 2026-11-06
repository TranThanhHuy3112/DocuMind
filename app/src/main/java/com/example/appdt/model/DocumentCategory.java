package com.example.appdt.model;

import java.util.Locale;

public enum DocumentCategory {
    PDF, TEXT, SPREADSHEET, MEDIA, OTHER;

    public static DocumentCategory from(DocumentModel document) {
        String ext = document.getExtension().toLowerCase(Locale.ROOT);
        String mime = document.getMimeType().toLowerCase(Locale.ROOT);
        if (ext.equals("pdf") || mime.equals("application/pdf")) return PDF;
        if (ext.equals("doc") || ext.equals("docx") || ext.equals("txt")
                || mime.startsWith("text/plain") || mime.contains("word")) return TEXT;
        if (ext.equals("xls") || ext.equals("xlsx") || ext.equals("csv")
                || mime.contains("spreadsheet") || mime.contains("excel")
                || mime.equals("text/csv")) return SPREADSHEET;
        if (ext.equals("mp4") || ext.equals("mov") || ext.equals("mp3")
                || ext.equals("wav") || ext.equals("m4a")
                || mime.startsWith("video/") || mime.startsWith("audio/")) return MEDIA;
        return OTHER;
    }
}

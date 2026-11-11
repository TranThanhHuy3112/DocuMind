package com.example.appdt.model;

import java.util.Locale;

public enum DocumentCategory {
    PDF, TEXT, SPREADSHEET, MEDIA, OTHER;

    public static DocumentCategory from(DocumentModel document) {
        String ext = canonicalExtension(document);
        if (ext.equals("pdf")) return PDF;
        if (ext.equals("docx") || ext.equals("txt")) return TEXT;
        if (ext.equals("xlsx") || ext.equals("csv")) return SPREADSHEET;
        if (ext.equals("mp4") || ext.equals("mov") || ext.equals("mp3")
                || ext.equals("wav") || ext.equals("m4a")) return MEDIA;
        return OTHER;
    }

    public static String canonicalExtension(DocumentModel document) {
        if (document == null) return "";
        String ext = document.getExtension().toLowerCase(Locale.ROOT);
        if (ext.equals("pdf") || ext.equals("docx") || ext.equals("txt")
                || ext.equals("xlsx") || ext.equals("csv")
                || ext.equals("mp4") || ext.equals("mov") || ext.equals("mp3")
                || ext.equals("wav") || ext.equals("m4a")) {
            return ext;
        }
        String mime = document.getMimeType().toLowerCase(Locale.ROOT);
        int semi = mime.indexOf(';');
        if (semi >= 0) mime = mime.substring(0, semi).trim();
        if (mime.equals("application/pdf")) return "pdf";
        if (mime.equals("text/plain")) return "txt";
        if (mime.equals("text/csv") || mime.equals("text/comma-separated-values")
                || mime.equals("application/csv")) return "csv";
        if (mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) return "docx";
        if (mime.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) return "xlsx";
        if (mime.equals("video/mp4")) return "mp4";
        if (mime.equals("video/quicktime")) return "mov";
        if (mime.equals("audio/mpeg") || mime.equals("audio/mp3")) return "mp3";
        if (mime.equals("audio/wav") || mime.equals("audio/x-wav")) return "wav";
        if (mime.equals("audio/mp4") || mime.equals("audio/x-m4a")) return "m4a";
        return "";
    }
}

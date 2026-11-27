package com.example.appdt.ui.tools;

import com.example.appdt.model.DocumentCategory;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.example.appdt.utils.DocumentPicker;

import java.util.ArrayList;
import java.util.List;

public final class ToolSources {
    private ToolSources() { }

    public static List<DocumentModel> readyFor(List<DocumentModel> documents, ToolKind tool) {
        List<DocumentModel> result = new ArrayList<>();
        if (documents == null) return result;
        for (DocumentModel document : documents) {
            if (!document.isReady() || DocumentPicker.isBlockedPath(document.getPath())) continue;
            String extension = DocumentCategory.canonicalExtension(document);
            boolean spreadsheet = extension.equals("csv") || extension.equals("xlsx")
                    || extension.equals("pdf") || extension.equals("docx");
            boolean media = extension.equals("mp4") || extension.equals("mov")
                    || extension.equals("mp3") || extension.equals("wav")
                    || extension.equals("m4a");
            boolean readable = extension.equals("pdf") || extension.equals("txt")
                    || extension.equals("csv") || extension.equals("docx")
                    || extension.equals("xlsx");
            if (tool == ToolKind.ANALYZE && spreadsheet
                    || tool == ToolKind.TRANSCRIBE && media
                    || (tool == ToolKind.CHAT || tool == ToolKind.COMPARE || tool == ToolKind.DRAFT)
                    && readable) {
                result.add(document);
            }
        }
        return result;
    }
}

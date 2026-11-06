package com.example.appdt.ui.tools;

import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ToolSources {
    private ToolSources() { }

    public static List<DocumentModel> readyFor(List<DocumentModel> documents, ToolKind tool) {
        List<DocumentModel> result = new ArrayList<>();
        if (documents == null) return result;
        for (DocumentModel document : documents) {
            if (!document.isReady()) continue;
            String extension = document.getExtension().toLowerCase(Locale.ROOT);
            boolean spreadsheet = extension.equals("csv") || extension.equals("xlsx");
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

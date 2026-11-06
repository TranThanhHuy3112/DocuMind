package com.example.appdt.ui.tools;

import androidx.fragment.app.FragmentManager;

import com.example.appdt.model.ToolKind;

public final class DocumentTools {
    private DocumentTools() { }

    public static void show(FragmentManager manager, ToolKind tool) {
        if (manager.isStateSaved() || manager.findFragmentByTag("document_tool") != null) return;
        ToolSheet sheet;
        switch (tool) {
            case CHAT: sheet = new ChatBottomSheetDialog(); break;
            case ANALYZE: sheet = new AnalyzeBottomSheetDialog(); break;
            case TRANSCRIBE: sheet = new VideoTranscriptBottomSheetDialog(); break;
            case COMPARE: sheet = new CompareBottomSheetDialog(); break;
            case DRAFT: sheet = new GenerateDraftDialog(); break;
            default: return;
        }
        sheet.showNow(manager, "document_tool");
    }
}

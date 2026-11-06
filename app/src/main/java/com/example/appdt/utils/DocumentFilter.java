package com.example.appdt.utils;

import com.example.appdt.model.DocumentCategory;
import com.example.appdt.model.DocumentModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DocumentFilter {
    private DocumentFilter() { }

    public static List<DocumentModel> apply(List<DocumentModel> documents, String query,
                                            DocumentCategory category, boolean ascending) {
        if (documents == null) return Collections.emptyList();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<DocumentModel> result = new ArrayList<>();
        for (DocumentModel document : documents) {
            if (!document.getName().toLowerCase(Locale.ROOT).contains(normalized)) continue;
            if (category != null && DocumentCategory.from(document) != category) continue;
            result.add(document);
        }
        Comparator<DocumentModel> compare = (first, second) ->
                String.CASE_INSENSITIVE_ORDER.compare(first.getName(), second.getName());
        result.sort(ascending ? compare : compare.reversed());
        return result;
    }
}

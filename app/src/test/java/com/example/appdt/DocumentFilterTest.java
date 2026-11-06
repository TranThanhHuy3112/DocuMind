package com.example.appdt;

import com.example.appdt.model.DocumentCategory;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.utils.DocumentFilter;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DocumentFilterTest {
    private final List<DocumentModel> documents = Arrays.asList(
            new DocumentModel("1", "Zeta.pdf", 100, "application/pdf", false),
            new DocumentModel("2", "Alpha.docx", 100, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false),
            new DocumentModel("3", "Beta.csv", 100, "text/csv", false),
            new DocumentModel("4", "Meeting.mp4", 100, "video/mp4", false));

    @Test
    public void searchCategoryAndSortCanBeCombined() {
        assertEquals("Alpha.docx", DocumentFilter.apply(documents, " ALPHA ",
                DocumentCategory.TEXT, true).get(0).getName());
        assertEquals(0, DocumentFilter.apply(documents, "alpha",
                DocumentCategory.PDF, true).size());
        assertEquals("Zeta.pdf", DocumentFilter.apply(documents, "",
                null, false).get(0).getName());
        assertEquals("Alpha.docx", DocumentFilter.apply(documents, "",
                null, true).get(0).getName());
    }

    @Test
    public void categorizesSpreadsheetAndMediaFiles() {
        assertEquals(DocumentCategory.SPREADSHEET, DocumentCategory.from(documents.get(2)));
        assertEquals(DocumentCategory.MEDIA, DocumentCategory.from(documents.get(3)));
        assertEquals(1, DocumentFilter.apply(documents, "",
                DocumentCategory.MEDIA, true).size());
    }
}

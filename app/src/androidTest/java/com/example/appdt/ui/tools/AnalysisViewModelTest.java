package com.example.appdt.ui.tools;

import android.app.Application;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.appdt.model.DocumentModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AnalysisViewModelTest {
    private static void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }

    @Test public void pdfEditsInvalidateAcceptedNumbersAndLatestColumnWins() throws Exception {
        AnalysisViewModel[] holder = new AnalysisViewModel[1];
        main(() -> {
            Application app = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
            holder[0] = new AnalysisViewModel(app);
            holder[0].select(new DocumentModel("content://test/one", "table.pdf", 100, "application/pdf", true));
            holder[0].importPdfTable("Tên,Chi phí,Số lượng\nA,100,2\nB,200,3\n");
        });
        AnalysisViewModel model = holder[0];
        try {
            for (int i = 0; i < 60 && model.state().getValue().table == null; i++) Thread.sleep(25);
            main(() -> {
                assertNotNull(model.state().getValue().table);
                TabularData table = model.state().getValue().table;
                model.valueColumn = 1; model.summarize(table);
                model.valueColumn = 2; model.summarize(table);
            });
            for (int i = 0; i < 60 && model.statistics().getValue() == null; i++) Thread.sleep(25);
            main(() -> {
                assertNotNull(model.statistics().getValue());
                assertEquals(5.0, model.statistics().getValue().total.doubleValue(), 0.0001);
                TabularData.Statistics existing = model.statistics().getValue();
                model.summarize(model.state().getValue().table);
                assertSame("Same selection keeps the already-computed statistics", existing, model.statistics().getValue());
                model.editPdfPreview("Tên,Chi phí\nA,999\n");
                assertNull(model.state().getValue().table);
                assertNull(model.statistics().getValue());
                assertTrue(model.pdfPreview.contains("999"));
                model.select(new DocumentModel("content://test/two", "other.pdf", 100, "application/pdf", true));
                assertEquals("", model.pdfPreview);
                assertNull(model.state().getValue().table);
            });
        } finally { main(model::onCleared); }
    }
}

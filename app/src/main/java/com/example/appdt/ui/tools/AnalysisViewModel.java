package com.example.appdt.ui.tools;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.appdt.model.DocumentCategory;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.utils.DocumentPicker;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Imported data survives view recreation. No raw document data is written to saved state. */
public final class AnalysisViewModel extends AndroidViewModel {
    static final class State {
        final String sourceId;
        final TabularData table;
        final String error;
        State(String id, TabularData data, String error) { sourceId = id; table = data; this.error = error; }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<TabularData.Statistics> statistics = new MutableLiveData<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Future<?> pending;
    private Future<?> summaryTask;
    private long summaryGeneration;
    private long generation;
    private String sourceId;
    private TabularData summaryTable;
    private int summaryValueColumn, summaryLabelColumn, summaryNumberStyle;
    int valueColumn = -1, labelColumn, numberStyle;
    String pdfPreview = "";
    public AnalysisViewModel(@NonNull Application app) { super(app); }
    LiveData<State> state() { return state; }
    LiveData<TabularData.Statistics> statistics() { return statistics; }

    void summarize(TabularData table) {
        if (table == summaryTable && valueColumn == summaryValueColumn
                && labelColumn == summaryLabelColumn && numberStyle == summaryNumberStyle) return;
        summaryTable = table; summaryValueColumn = valueColumn;
        summaryLabelColumn = labelColumn; summaryNumberStyle = numberStyle;
        long request = ++summaryGeneration;
        if (summaryTask != null) summaryTask.cancel(true);
        statistics.setValue(null);
        int value = valueColumn, label = labelColumn;
        TabularData.NumberStyle style = TabularData.NumberStyle.values()[numberStyle];
        summaryTask = io.submit(() -> {
            TabularData.Statistics result = table.summarize(value, label, style);
            main.post(() -> { if (request == summaryGeneration) statistics.setValue(result); });
        });
    }

    void select(DocumentModel source) {
        if (source.getId().equals(sourceId)) return;
        cancel(); sourceId = source.getId();
        valueColumn = -1; labelColumn = 0; numberStyle = 0; pdfPreview = "";
        state.setValue(new State(sourceId, null, null));
        String ext = DocumentCategory.canonicalExtension(source);
        if (ext.equals("pdf")) return;
        long request = generation;
        pending = io.submit(() -> {
            try {
                Uri uri = Uri.parse(source.getPath());
                if (!DocumentPicker.isReadableUri(getApplication(), uri)) throw new IOException("Không có quyền đọc tệp; hãy thêm lại tài liệu.");
                String csv;
                try (InputStream stream = getApplication().getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IOException("Không thể mở tệp.");
                    byte[] bytes = DocumentPicker.readBoundedBytes(stream, 10 * 1024 * 1024);
                    if (ext.equals("xlsx")) csv = OfficeTextExtractor.xlsxAnalysisCsv(bytes);
                    else if (ext.equals("docx")) csv = OfficeTextExtractor.docxTableCsv(bytes);
                    else csv = new String(bytes, StandardCharsets.UTF_8);
                }
                deliver(request, new State(source.getId(), TabularData.parse(new StringReader(csv)), null));
            } catch (IOException | SecurityException | IllegalArgumentException error) {
                deliver(request, new State(source.getId(), null, error.getMessage()));
            }
        });
    }

    void importPdfTable(String csv) {
        if (csv.length() > 512 * 1024) { state.setValue(new State(sourceId, null, "Bảng trích xuất quá lớn.")); return; }
        pdfPreview = csv; cancel();
        long request = generation;
        String id = sourceId;
        state.setValue(new State(id, null, null));
        pending = io.submit(() -> {
            try { deliver(request, new State(id, TabularData.parse(new StringReader(csv)), null)); }
            catch (IOException error) { deliver(request, new State(id, null, error.getMessage())); }
        });
    }
    void editPdfPreview(String csv) {
        pdfPreview = csv;
        cancel();
        state.setValue(new State(sourceId, null, null));
    }
    private void deliver(long request, State result) { main.post(() -> { if (request == generation) state.setValue(result); }); }
    private void cancel() {
        generation++; summaryGeneration++;
        summaryTable = null;
        if (pending != null) pending.cancel(true);
        if (summaryTask != null) summaryTask.cancel(true);
        statistics.setValue(null);
    }
    @Override protected void onCleared() { cancel(); main.removeCallbacksAndMessages(null); io.shutdownNow(); }
}

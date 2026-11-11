package com.example.appdt.ui;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.appdt.R;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.RecentActivityModel;
import com.example.appdt.model.ToolKind;
import com.example.appdt.repository.WorkspaceRepository;
import com.example.appdt.utils.DocumentPicker;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WorkspaceViewModel extends ViewModel {
    private static final String SELECTED_TAB = "selected_tab";
    private static final long MAX_READ_BYTES = 100L * 1024 * 1024;
    private final SavedStateHandle state;
    private final WorkspaceRepository repository;
    private final ExecutorService documentExecutor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Future<?>> documentJobs = new ConcurrentHashMap<>();
    private final MutableLiveData<ImportFeedback> importFeedback = new MutableLiveData<>();

    public static final class ImportFeedback {
        public final int messageRes;
        @Nullable public final String documentName;
        public final boolean shortDuration;

        ImportFeedback(int messageRes, @Nullable String documentName, boolean shortDuration) {
            this.messageRes = messageRes;
            this.documentName = documentName;
            this.shortDuration = shortDuration;
        }
    }

    public WorkspaceViewModel(SavedStateHandle state) {
        this.state = state;
        this.repository = WorkspaceRepository.getInstance();
        ContentResolver resolver = repository.getContentResolver();
        List<DocumentModel> saved = repository.getDocuments().getValue();
        if (resolver != null && saved != null) {
            for (DocumentModel document : saved) {
                if (!document.isReady() && !document.isFailed()) {
                    Future<?> job = documentExecutor.submit(() -> readDocument(document, resolver));
                    documentJobs.put(document.getId(), job);
                }
            }
        }
    }

    public LiveData<Integer> getSelectedTab() {
        return state.getLiveData(SELECTED_TAB, R.id.nav_home);
    }

    public void selectTab(int tabId) {
        if (tabId == R.id.nav_home || tabId == R.id.nav_files
                || tabId == R.id.nav_community || tabId == R.id.nav_account) {
            state.set(SELECTED_TAB, tabId);
        }
    }

    public LiveData<List<DocumentModel>> getDocuments() {
        return repository.getDocuments();
    }

    public LiveData<List<RecentActivityModel>> getRecentActivities() {
        return repository.getRecentActivities();
    }

    public WorkspaceRepository.AddResult addDocument(DocumentModel document,
                                                    ContentResolver resolver) {
        WorkspaceRepository.AddResult result = repository.addDocument(document);
        if (result == WorkspaceRepository.AddResult.ADDED) {
            Future<?> job = documentExecutor.submit(() -> readDocument(document, resolver));
            documentJobs.put(document.getId(), job);
        }
        return result;
    }

    public boolean hasReadyDocumentFor(ToolKind tool) {
        return repository.hasReadyDocumentFor(tool);
    }

    public LiveData<ImportFeedback> getImportFeedback() {
        return importFeedback;
    }

    public void clearImportFeedback() {
        importFeedback.setValue(null);
    }

    public void importDocuments(List<Uri> uris, ContentResolver resolver, String unnamedDocument) {
        if (uris == null || uris.isEmpty() || documentExecutor.isShutdown()) return;
        documentExecutor.execute(() -> {
            for (Uri uri : uris) {
                if (Thread.currentThread().isInterrupted()) return;
                if (!DocumentPicker.isAllowedPickedUri(uri)) {
                    mainHandler.post(() -> {
                        if (documentExecutor.isShutdown()) return;
                        importFeedback.setValue(
                                new ImportFeedback(R.string.unsupported_document, null, false));
                    });
                    continue;
                }
                DocumentModel document = DocumentPicker.readMetadata(resolver, uri, unnamedDocument);
                if (Thread.currentThread().isInterrupted()) return;
                mainHandler.post(() -> {
                    if (documentExecutor.isShutdown()) return;
                    if (!DocumentPicker.isSupported(document)) {
                        importFeedback.setValue(
                                new ImportFeedback(R.string.unsupported_document, null, false));
                        return;
                    }
                    WorkspaceRepository.AddResult result = addDocument(document, resolver);
                    if (result == WorkspaceRepository.AddResult.ADDED) {
                        DocumentPicker.keepReadPermission(resolver, Uri.parse(document.getPath()));
                        importFeedback.setValue(new ImportFeedback(
                                R.string.document_added, document.getName(), true));
                    } else {
                        importFeedback.setValue(new ImportFeedback(
                                result == WorkspaceRepository.AddResult.DUPLICATE
                                        ? R.string.duplicate_document : R.string.workspace_full,
                                null, false));
                    }
                });
            }
        });
    }

    public void removeDocument(String id) {
        Future<?> job = documentJobs.remove(id);
        if (job != null) job.cancel(true);
        repository.removeDocument(id);
    }

    private void readDocument(DocumentModel document, ContentResolver resolver) {
        String id = document.getId();
        Uri uri = Uri.parse(document.getPath());
        if (!DocumentPicker.isReadableUri(repository.getAppContext(), uri)) {
            mainHandler.post(() -> repository.markFailed(id));
            return;
        }
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Document provider returned no stream");
            byte[] buffer = new byte[64 * 1024];
            long read = 0L;
            int lastProgress = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) return;
                read += count;
                if (read > MAX_READ_BYTES) {
                    throw new IOException("Document stream exceeds maximum size");
                }
                if (document.getSize() > 0L) {
                    int progress = Math.min(90,
                            (int) Math.round((double) read * 90 / document.getSize()));
                    if (progress > lastProgress) {
                        lastProgress = progress;
                        postProgress(id, progress);
                    }
                }
            }
            if (Thread.currentThread().isInterrupted()) return;
            if (lastProgress < 90) postProgress(id, 90);
            Thread.sleep(250);
            if (!Thread.currentThread().isInterrupted()) postProgress(id, 100);
        } catch (IOException | SecurityException error) {
            mainHandler.post(() -> repository.markFailed(id));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            documentJobs.remove(id);
        }
    }

    private void postProgress(String id, int progress) {
        mainHandler.post(() -> repository.updateProgress(id, progress));
    }

    @Override
    protected void onCleared() {
        for (Future<?> job : documentJobs.values()) {
            job.cancel(true);
        }
        documentJobs.clear();
        documentExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onCleared();
    }
}

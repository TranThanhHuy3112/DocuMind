package com.example.appdt.repository;

import android.content.ContentResolver;
import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.RecentActivityModel;
import com.example.appdt.model.ToolKind;
import com.example.appdt.ui.tools.ToolSources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WorkspaceRepository {
    public static final int MAX_DOCUMENTS = 5;
    private static final int MAX_ACTIVITIES = 6;
    private static final WorkspaceRepository INSTANCE = new WorkspaceRepository();

    public enum AddResult { ADDED, DUPLICATE, LIMIT_REACHED }

    private final MutableLiveData<List<DocumentModel>> documents =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<RecentActivityModel>> recentActivities =
            new MutableLiveData<>(Collections.emptyList());
    private final Set<String> restoredIds = new HashSet<>();
    private LocalDataStore localData;
    private Context appContext;
    private String activeUid;

    private WorkspaceRepository() { }

    public static WorkspaceRepository getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(Context context) {
        if (localData != null) return;
        appContext = context.getApplicationContext();
        localData = new LocalDataStore(appContext);
        List<DocumentModel> savedDocuments = localData.loadDocuments();
        for (DocumentModel document : savedDocuments) restoredIds.add(document.getId());
        documents.setValue(savedDocuments);
        recentActivities.setValue(localData.loadActivities());
    }

    public synchronized void initialize(Context context, String uid) {
        if (uid.equals(activeUid)) return;
        appContext = context.getApplicationContext();
        activeUid = uid;
        restoredIds.clear();
        localData = new LocalDataStore(appContext, "documind_local_data_" + uid);
        List<DocumentModel> saved = localData.loadDocuments();
        for (DocumentModel document : saved) restoredIds.add(document.getId());
        documents.setValue(saved);
        recentActivities.setValue(localData.loadActivities());
    }

    private synchronized void restoreRemote(DocumentModel document) {
        List<DocumentModel> existing = documents.getValue();
        if (existing == null) return;
        for (int index = 0; index < existing.size(); index++) {
            if (existing.get(index).getId().equals(document.getId())) {
                List<DocumentModel> updated = new ArrayList<>(existing);
                updated.set(index, document);
                documents.setValue(Collections.unmodifiableList(updated));
                saveDocuments(updated);
                return;
            }
        }
        if (existing.size() >= MAX_DOCUMENTS) return;
        List<DocumentModel> updated = new ArrayList<>(existing);
        updated.add(document);
        documents.setValue(Collections.unmodifiableList(updated));
        saveDocuments(updated);
    }

    public synchronized ContentResolver getContentResolver() {
        return appContext == null ? null : appContext.getContentResolver();
    }

    public LiveData<List<DocumentModel>> getDocuments() {
        return documents;
    }

    public LiveData<List<RecentActivityModel>> getRecentActivities() {
        return recentActivities;
    }

    public synchronized AddResult addDocument(DocumentModel document) {
        List<DocumentModel> current = documents.getValue();
        List<DocumentModel> snapshot = current == null ? Collections.emptyList() : current;
        for (DocumentModel existing : snapshot) {
            if (existing.getId().equals(document.getId())
                    || (existing.getSize() >= 0 && document.getSize() >= 0
                    && existing.getName().equalsIgnoreCase(document.getName())
                    && existing.getSize() == document.getSize())) {
                return AddResult.DUPLICATE;
            }
        }
        if (snapshot.size() >= MAX_DOCUMENTS) {
            return AddResult.LIMIT_REACHED;
        }
        List<DocumentModel> updated = new ArrayList<>(snapshot);
        updated.add(document);
        documents.setValue(Collections.unmodifiableList(updated));
        saveDocuments(updated);
        addActivity(document);
        return AddResult.ADDED;
    }

    public synchronized boolean updateProgress(String id, int progress) {
        List<DocumentModel> current = documents.getValue();
        if (current == null) return false;
        for (int index = 0; index < current.size(); index++) {
            DocumentModel existing = current.get(index);
            if (!existing.getId().equals(id) || existing.isFailed() || existing.isReady()) continue;
            int next = Math.max(0, Math.min(100, progress));
            if (next <= existing.getProgress()) return true;
            List<DocumentModel> updated = new ArrayList<>(current);
            DocumentModel replacement = existing.withProgress(next);
            updated.set(index, replacement);
            documents.setValue(Collections.unmodifiableList(updated));
            if (replacement.isReady()) {
                if (!restoredIds.remove(id)) {
                    addActivity("Đã nạp xong", existing.getName() + " · Sẵn sàng");
                }
            }
            return true;
        }
        return false;
    }

    public synchronized boolean markFailed(String id) {
        List<DocumentModel> current = documents.getValue();
        if (current == null) return false;
        for (int index = 0; index < current.size(); index++) {
            DocumentModel existing = current.get(index);
            if (!existing.getId().equals(id) || existing.isReady() || existing.isFailed()) continue;
            List<DocumentModel> updated = new ArrayList<>(current);
            updated.set(index, existing.withFailure());
            documents.setValue(Collections.unmodifiableList(updated));
            restoredIds.remove(id);
            addActivity("Không thể nạp tài liệu", existing.getName());
            return true;
        }
        return false;
    }

    public synchronized boolean removeDocument(String id) {
        List<DocumentModel> current = documents.getValue();
        if (current == null) return false;
        List<DocumentModel> updated = new ArrayList<>(current);
        for (DocumentModel document : current) {
            if (document.getId().equals(id)) {
                updated.remove(document);
                documents.setValue(Collections.unmodifiableList(updated));
                restoredIds.remove(id);
                saveDocuments(updated);
                addActivity("Đã xóa tài liệu", document.getName());
                return true;
            }
        }
        return false;
    }

    public synchronized boolean hasReadyDocument() {
        List<DocumentModel> current = documents.getValue();
        if (current == null) {
            return false;
        }
        for (DocumentModel document : current) {
            if (document.isReady()) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean hasReadyDocumentFor(ToolKind tool) {
        List<DocumentModel> current = documents.getValue();
        if (current == null) return false;
        int count = ToolSources.readyFor(current, tool).size();
        return tool == ToolKind.COMPARE ? count >= 2 : count >= 1;
    }

    private void addActivity(DocumentModel document) {
        addActivity("Đã thêm tài liệu", document.getName() + " · Đang nạp");
    }

    private void addActivity(String title, String detail) {
        List<RecentActivityModel> current = recentActivities.getValue();
        List<RecentActivityModel> updated = new ArrayList<>();
        updated.add(new RecentActivityModel(System.nanoTime(), title, detail, "Vừa xong"));
        if (current != null) {
            updated.addAll(current.subList(0, Math.min(current.size(), MAX_ACTIVITIES - 1)));
        }
        recentActivities.setValue(Collections.unmodifiableList(updated));
        if (localData != null) localData.saveActivities(updated);
    }

    private void saveDocuments(List<DocumentModel> current) {
        if (localData != null) localData.saveDocuments(current);
    }
}

package com.example.appdt.ui;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.appdt.R;
import com.example.appdt.adapter.RecentActivityAdapter;
import com.example.appdt.databinding.FragmentHomeBinding;
import com.example.appdt.databinding.ItemHomeTaskBinding;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.example.appdt.repository.WorkspaceRepository;
import com.example.appdt.ui.tools.DocumentTools;
import com.example.appdt.utils.DocumentPicker;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private WorkspaceViewModel viewModel;
    private RecentActivityAdapter activityAdapter;
    private ExecutorService fileExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> documentPicker = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(), this::onDocumentsSelected);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        fileExecutor = Executors.newSingleThreadExecutor();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(WorkspaceViewModel.class);
        setupRecentActivities();
        setupTaskGrid();
        binding.uploadCard.setOnClickListener(ignored -> openDocumentPicker());
        binding.chooseFilesButton.setOnClickListener(ignored -> openDocumentPicker());
        viewModel.getDocuments().observe(getViewLifecycleOwner(), documents -> {
            binding.documentCount.setText(String.valueOf(documents.size()));
            int ready = 0;
            for (DocumentModel document : documents) if (document.isReady()) ready++;
            binding.readyDocumentCount.setText(String.valueOf(ready));
            binding.workspaceUsage.setText(getString(R.string.workspace_usage,
                    documents.size(), WorkspaceRepository.MAX_DOCUMENTS));
        });
        viewModel.getRecentActivities().observe(getViewLifecycleOwner(), activities -> {
            activityAdapter.submitList(activities);
            binding.emptyActivities.setVisibility(activities.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void setupRecentActivities() {
        activityAdapter = new RecentActivityAdapter();
        binding.recentActivities.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recentActivities.setAdapter(activityAdapter);
        binding.recentActivities.setNestedScrollingEnabled(false);
    }

    private void setupTaskGrid() {
        addTask(R.drawable.ic_chat, R.string.task_chat, R.string.task_chat_description,
                () -> showSourceRequired(ToolKind.CHAT));
        addTask(R.drawable.ic_search, R.string.task_search, R.string.task_search_description,
                () -> viewModel.selectTab(R.id.nav_files));
        addTask(R.drawable.ic_compare, R.string.task_compare, R.string.task_compare_description,
                () -> showSourceRequired(ToolKind.COMPARE));
        addTask(R.drawable.ic_analyze, R.string.task_analyze, R.string.task_analyze_description,
                () -> showSourceRequired(ToolKind.ANALYZE));
        addTask(R.drawable.ic_draft, R.string.task_draft, R.string.task_draft_description,
                () -> showSourceRequired(ToolKind.DRAFT));
        addTask(R.drawable.ic_video, R.string.task_video, R.string.task_video_description,
                () -> showSourceRequired(ToolKind.TRANSCRIBE));
    }

    private void addTask(int icon, int title, int description, Runnable action) {
        ItemHomeTaskBinding task = ItemHomeTaskBinding.inflate(getLayoutInflater(),
                binding.taskGrid, false);
        task.taskIcon.setImageResource(icon);
        task.taskTitle.setText(title);
        task.taskDescription.setText(description);
        task.getRoot().setContentDescription(getString(title));
        task.getRoot().setOnClickListener(ignored -> action.run());
        binding.taskGrid.addView(task.getRoot());
    }

    private void showSourceRequired(ToolKind tool) {
        if (!viewModel.hasReadyDocumentFor(tool)) {
            Snackbar.make(binding.getRoot(), R.string.source_not_ready, Snackbar.LENGTH_LONG)
                    .setAction(R.string.choose_document, ignored -> openDocumentPicker())
                    .show();
            return;
        }
        DocumentTools.show(getParentFragmentManager(), tool);
    }

    private void openDocumentPicker() {
        List<DocumentModel> current = viewModel.getDocuments().getValue();
        if (current != null && current.size() >= WorkspaceRepository.MAX_DOCUMENTS) {
            showMessage(R.string.workspace_full);
            return;
        }
        documentPicker.launch(DocumentPicker.mimeTypes());
    }

    private void onDocumentsSelected(List<Uri> uris) {
        if (uris == null || uris.isEmpty() || fileExecutor == null
                || fileExecutor.isShutdown()) {
            return;
        }
        ContentResolver resolver = requireContext().getApplicationContext().getContentResolver();
        String unnamedDocument = getString(R.string.unnamed_document);
        fileExecutor.execute(() -> {
            for (Uri uri : uris) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                DocumentModel document = DocumentPicker.readMetadata(resolver, uri,
                        unnamedDocument);
                mainHandler.post(() -> addDocumentOnMainThread(document, resolver));
            }
        });
    }

    private void addDocumentOnMainThread(DocumentModel document, ContentResolver resolver) {
        if (!isAdded()) {
            return;
        }
        if (!DocumentPicker.isSupported(document)) {
            showMessage(R.string.unsupported_document);
            return;
        }
        WorkspaceRepository.AddResult result = viewModel.addDocument(document, resolver);
        if (binding == null) {
            return;
        }
        if (result == WorkspaceRepository.AddResult.ADDED) {
            DocumentPicker.keepReadPermission(resolver, Uri.parse(document.getPath()));
            Snackbar.make(binding.getRoot(),
                    getString(R.string.document_added, document.getName()),
                    Snackbar.LENGTH_SHORT).show();
        } else if (result == WorkspaceRepository.AddResult.DUPLICATE) {
            showMessage(R.string.duplicate_document);
        } else {
            showMessage(R.string.workspace_full);
        }
    }

    private void showMessage(int message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        if (fileExecutor != null) {
            fileExecutor.shutdownNow();
            fileExecutor = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        binding.recentActivities.setAdapter(null);
        binding = null;
        super.onDestroyView();
    }
}

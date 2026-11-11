package com.example.appdt.ui;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.appdt.R;
import com.example.appdt.adapter.DocumentAdapter;
import com.example.appdt.databinding.FragmentFilesBinding;
import com.example.appdt.model.DocumentCategory;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.example.appdt.repository.WorkspaceRepository;
import com.example.appdt.ui.tools.DocumentTools;
import com.example.appdt.utils.DocumentFilter;
import com.example.appdt.utils.DocumentPicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class FilesFragment extends Fragment {
    private static final String STATE_QUERY = "files_query";
    private static final String STATE_CATEGORY = "files_category";
    private static final String STATE_ASCENDING = "files_ascending";

    private FragmentFilesBinding binding;
    private WorkspaceViewModel viewModel;
    private DocumentAdapter adapter;
    private AlertDialog deleteDialog;
    private String query = "";
    private DocumentCategory selectedCategory;
    private boolean ascending = true;

    private final ActivityResultLauncher<String[]> documentPicker = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(), this::onDocumentsSelected);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFilesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            query = savedInstanceState.getString(STATE_QUERY, "");
            String savedCategory = savedInstanceState.getString(STATE_CATEGORY);
            if (savedCategory != null) {
                try {
                    selectedCategory = DocumentCategory.valueOf(savedCategory);
                } catch (IllegalArgumentException ignored) {
                    selectedCategory = null;
                }
            }
            ascending = savedInstanceState.getBoolean(STATE_ASCENDING, true);
        }
        viewModel = new ViewModelProvider(requireActivity()).get(WorkspaceViewModel.class);
        adapter = new DocumentAdapter(this::confirmDelete);
        binding.documentsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.documentsList.setAdapter(adapter);
        binding.addFiles.setOnClickListener(ignored -> openDocumentPicker());
        setupSearchAndFilters();
        setupQuickActions();
        viewModel.getDocuments().observe(getViewLifecycleOwner(), this::renderDocuments);
        viewModel.getImportFeedback().observe(getViewLifecycleOwner(), this::showImportFeedback);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, query);
        if (selectedCategory != null) outState.putString(STATE_CATEGORY, selectedCategory.name());
        outState.putBoolean(STATE_ASCENDING, ascending);
    }

    private void setupSearchAndFilters() {
        binding.documentSearch.setQueryHint(getString(R.string.search_files));
        View searchPlate = binding.documentSearch.findViewById(
                androidx.appcompat.R.id.search_plate);
        if (searchPlate != null) searchPlate.setBackgroundColor(Color.TRANSPARENT);
        if (!query.isEmpty()) binding.documentSearch.setQuery(query, false);
        binding.documentSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String text) {
                binding.documentSearch.clearFocus();
                return true;
            }
            @Override public boolean onQueryTextChange(String text) {
                query = text;
                refreshDocuments();
                return true;
            }
        });
        binding.documentSearch.clearFocus();
        binding.fileFilters.check(chipForCategory(selectedCategory));
        binding.fileFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int id = checkedIds.isEmpty() ? R.id.filter_all : checkedIds.get(0);
            if (id == R.id.filter_pdf) selectedCategory = DocumentCategory.PDF;
            else if (id == R.id.filter_text) selectedCategory = DocumentCategory.TEXT;
            else if (id == R.id.filter_sheet) selectedCategory = DocumentCategory.SPREADSHEET;
            else if (id == R.id.filter_media) selectedCategory = DocumentCategory.MEDIA;
            else selectedCategory = null;
            refreshDocuments();
        });
        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.file_sort_options, android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.fileSort.setAdapter(sortAdapter);
        binding.fileSort.setSelection(ascending ? 0 : 1, false);
        binding.fileSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View item,
                                                 int position, long id) {
                ascending = position == 0;
                refreshDocuments();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private static int chipForCategory(@Nullable DocumentCategory category) {
        if (category == DocumentCategory.PDF) return R.id.filter_pdf;
        if (category == DocumentCategory.TEXT) return R.id.filter_text;
        if (category == DocumentCategory.SPREADSHEET) return R.id.filter_sheet;
        if (category == DocumentCategory.MEDIA) return R.id.filter_media;
        return R.id.filter_all;
    }

    private void setupQuickActions() {
        binding.quickChat.setOnClickListener(view -> openTool(ToolKind.CHAT));
        binding.quickAnalyze.setOnClickListener(view -> openTool(ToolKind.ANALYZE));
        binding.quickVideo.setOnClickListener(view -> openTool(ToolKind.TRANSCRIBE));
    }

    private void openTool(ToolKind tool) {
        if (binding == null) return;
        if (!viewModel.hasReadyDocumentFor(tool)) {
            Snackbar.make(binding.getRoot(), R.string.source_not_ready,
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        DocumentTools.show(getParentFragmentManager(), tool);
    }

    private void renderDocuments(List<DocumentModel> documents) {
        binding.filesCount.setText(getString(R.string.files_count,
                documents.size(), WorkspaceRepository.MAX_DOCUMENTS));
        binding.quickChat.setEnabled(viewModel.hasReadyDocumentFor(ToolKind.CHAT));
        binding.quickAnalyze.setEnabled(viewModel.hasReadyDocumentFor(ToolKind.ANALYZE));
        binding.quickVideo.setEnabled(viewModel.hasReadyDocumentFor(ToolKind.TRANSCRIBE));
        List<DocumentModel> visible = DocumentFilter.apply(documents, query,
                selectedCategory, ascending);
        adapter.submitList(visible);
        binding.emptyDocuments.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        binding.emptyDocuments.setText(documents.isEmpty()
                ? R.string.no_documents : R.string.no_matching_documents);
    }

    private void refreshDocuments() {
        if (binding == null || viewModel == null) return;
        List<DocumentModel> documents = viewModel.getDocuments().getValue();
        if (documents != null) renderDocuments(documents);
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
        if (uris == null || uris.isEmpty() || !isAdded()) return;
        viewModel.importDocuments(uris,
                requireContext().getApplicationContext().getContentResolver(),
                getString(R.string.unnamed_document));
    }

    private void showImportFeedback(@Nullable WorkspaceViewModel.ImportFeedback feedback) {
        if (feedback == null || binding == null) return;
        String text = feedback.documentName != null
                ? getString(feedback.messageRes, feedback.documentName)
                : getString(feedback.messageRes);
        Snackbar.make(binding.getRoot(), text,
                feedback.shortDuration ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG).show();
        viewModel.clearImportFeedback();
    }

    private void confirmDelete(DocumentModel document) {
        if (deleteDialog != null && deleteDialog.isShowing()) deleteDialog.dismiss();
        deleteDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_confirm_title)
                .setMessage(getString(R.string.delete_confirm_message, document.getName()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete,
                        (dialog, which) -> viewModel.removeDocument(document.getId()))
                .create();
        deleteDialog.setOnShowListener(ignored -> {
            Button positive = deleteDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) positive.setFilterTouchesWhenObscured(true);
        });
        deleteDialog.show();
    }

    private void showMessage(int message) {
        if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        if (deleteDialog != null && deleteDialog.isShowing()) deleteDialog.dismiss();
        deleteDialog = null;
        binding.documentsList.setAdapter(null);
        binding = null;
        super.onDestroyView();
    }
}

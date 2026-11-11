package com.example.appdt.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.appdt.R;
import com.example.appdt.databinding.DialogCreatePostBinding;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.PostAttachment;
import com.example.appdt.model.PostCategory;
import com.example.appdt.model.PostModel;
import com.example.appdt.repository.CommunityRepository;
import com.example.appdt.utils.DocumentPicker;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CreatePostDialog extends BottomSheetDialogFragment {
    private static final String STATE_URI = "attachment_uri";
    private static final String STATE_NAME = "attachment_name";
    private static final String STATE_MIME = "attachment_mime";
    private static final String STATE_CATEGORY = "post_category";

    private DialogCreatePostBinding binding;
    private PostAttachment attachment;
    private int categoryIndex;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onAttachmentChosen);

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogCreatePostBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        if (state != null) {
            categoryIndex = state.getInt(STATE_CATEGORY, 0);
            String uri = state.getString(STATE_URI);
            if (uri != null) attachment = new PostAttachment(Uri.parse(uri),
                    state.getString(STATE_NAME, ""),
                    state.getString(STATE_MIME, "application/octet-stream"));
        }
        PostCategory[] categories = PostCategory.values();
        String[] labels = new String[categories.length];
        for (int index = 0; index < categories.length; index++) {
            labels[index] = getString(categories[index].getLabel());
        }
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, labels);
        binding.createPostCategory.setAdapter(categoryAdapter);
        categoryIndex = Math.max(0, Math.min(categoryIndex, categories.length - 1));
        binding.createPostCategory.setText(labels[categoryIndex], false);
        binding.createPostCategory.setOnItemClickListener((parent, item, index, id) ->
                categoryIndex = index);
        showAttachmentName();
        boolean canAttach = CommunityRepository.getInstance().supportsSharedAttachments();
        binding.createPostAttachment.setVisibility(canAttach ? View.VISIBLE : View.GONE);
        if (!canAttach) binding.createPostAttachmentName.setText(R.string.community_attachment_unavailable);
        binding.createPostAttachment.setOnClickListener(ignored -> picker.launch(DocumentPicker.mimeTypes()));
        binding.createPostSubmit.setOnClickListener(ignored -> publish());
    }

    private void onAttachmentChosen(Uri uri) {
        if (uri == null || !isAdded() || metadataExecutor.isShutdown()) return;
        if (!DocumentPicker.isAllowedPickedUri(uri)) {
            if (binding != null) {
                Snackbar.make(binding.getRoot(), R.string.unsupported_document,
                        Snackbar.LENGTH_LONG).show();
            }
            return;
        }
        Context app = requireContext().getApplicationContext();
        ContentResolver resolver = app.getContentResolver();
        String fallbackName = getString(R.string.unnamed_document);
        metadataExecutor.execute(() -> {
            DocumentModel metadata = DocumentPicker.readMetadata(resolver, uri, fallbackName);
            main.post(() -> {
                if (binding == null) return;
                attachment = new PostAttachment(uri, metadata.getName(), metadata.getMimeType());
                showAttachmentName();
            });
        });
    }

    private void showAttachmentName() {
        if (binding == null) return;
        binding.createPostAttachmentName.setText(attachment == null
                ? getString(R.string.community_no_attachment)
                : getString(R.string.community_attachment_name, attachment.getName()));
    }

    private void publish() {
        String title = binding.createPostTitle.getText() == null ? ""
                : binding.createPostTitle.getText().toString().trim();
        String content = binding.createPostContent.getText() == null ? ""
                : binding.createPostContent.getText().toString().trim();
        binding.postTitleLayout.setError(title.isEmpty()
                ? getString(R.string.community_post_required) : null);
        binding.postContentLayout.setError(content.isEmpty()
                ? getString(R.string.community_post_required) : null);
        if (title.isEmpty() || content.isEmpty()) return;
        if (attachment != null && !CommunityRepository.getInstance().supportsSharedAttachments()) {
            Snackbar.make(binding.getRoot(), R.string.community_attachment_unavailable,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        if (attachment != null) DocumentPicker.keepReadPermission(
                requireContext().getContentResolver(), attachment.getUri());
        PostModel post = CommunityRepository.getInstance().addPost(title,
                PostCategory.values()[categoryIndex], content, attachment);
        if (post != null) {
            dismiss();
            View root = requireActivity().findViewById(android.R.id.content);
            Snackbar.make(root, R.string.community_post_created, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(STATE_CATEGORY, categoryIndex);
        if (attachment != null) {
            state.putString(STATE_URI, attachment.getUri().toString());
            state.putString(STATE_NAME, attachment.getName());
            state.putString(STATE_MIME, attachment.getMimeType());
        }
    }

    @Override public void onStart() {
        super.onStart();
        if (!(getDialog() instanceof BottomSheetDialog)) return;
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.getLayoutParams().height = (int) (
                getResources().getDisplayMetrics().heightPixels * 0.84f);
        sheet.requestLayout();
        BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override public void onDestroyView() {
        main.removeCallbacksAndMessages(null);
        binding = null;
        super.onDestroyView();
    }

    @Override public void onDestroy() {
        metadataExecutor.shutdownNow();
        super.onDestroy();
    }
}

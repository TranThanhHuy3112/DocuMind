package com.example.appdt.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appdt.R;
import com.example.appdt.databinding.ItemDocumentBinding;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.utils.DensityStyler;

import java.util.Locale;

public final class DocumentAdapter extends ListAdapter<DocumentModel, DocumentAdapter.Holder> {
    public interface OnDeleteListener { void onDelete(DocumentModel document); }

    private static final DiffUtil.ItemCallback<DocumentModel> DIFF =
            new DiffUtil.ItemCallback<DocumentModel>() {
                @Override public boolean areItemsTheSame(@NonNull DocumentModel oldItem,
                                                          @NonNull DocumentModel newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }
                @Override public boolean areContentsTheSame(@NonNull DocumentModel oldItem,
                                                             @NonNull DocumentModel newItem) {
                    return oldItem.getName().equals(newItem.getName())
                            && oldItem.getSize() == newItem.getSize()
                            && oldItem.getProgress() == newItem.getProgress()
                            && oldItem.isReady() == newItem.isReady()
                            && oldItem.isFailed() == newItem.isFailed();
                }
            };

    private final OnDeleteListener deleteListener;

    public DocumentAdapter(OnDeleteListener deleteListener) {
        super(DIFF);
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDocumentBinding binding = ItemDocumentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        DensityStyler.applyCard(binding.getRoot(), 10, 6);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DocumentModel document = getItem(position);
        Context context = holder.itemView.getContext();
        ItemDocumentBinding binding = holder.binding;
        binding.documentName.setText(document.getName());
        binding.documentType.setText(document.getExtension().toUpperCase(Locale.ROOT));
        binding.documentSize.setText(formatSize(context, document.getSize()));
        binding.documentProgress.setProgressCompat(document.getProgress(), true);
        if (document.isFailed()) {
            binding.documentStatus.setText(R.string.document_failed);
            binding.documentStatus.setTextColor(context.getColor(R.color.workspace_error));
        } else if (document.isReady()) {
            binding.documentStatus.setText(R.string.document_ready);
            binding.documentStatus.setTextColor(context.getColor(R.color.workspace_success));
        } else {
            binding.documentStatus.setText(context.getString(R.string.document_loading,
                    document.getProgress()));
            binding.documentStatus.setTextColor(context.getColor(R.color.workspace_muted));
        }
        binding.deleteDocument.setContentDescription(context.getString(
                R.string.delete_named_document, document.getName()));
        binding.deleteDocument.setOnClickListener(view -> deleteListener.onDelete(document));
    }

    private static String formatSize(Context context, long bytes) {
        if (bytes < 0) return context.getString(R.string.unknown_size);
        if (bytes < 1024) return context.getString(R.string.size_bytes, bytes);
        if (bytes < 1024 * 1024) return context.getString(R.string.size_kb,
                bytes / 1024.0);
        return context.getString(R.string.size_mb, bytes / (1024.0 * 1024));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemDocumentBinding binding;
        Holder(ItemDocumentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

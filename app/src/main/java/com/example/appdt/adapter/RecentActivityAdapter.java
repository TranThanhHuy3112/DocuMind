package com.example.appdt.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appdt.databinding.ItemRecentActivityBinding;
import com.example.appdt.model.RecentActivityModel;
import com.example.appdt.utils.DensityStyler;

public final class RecentActivityAdapter extends
        ListAdapter<RecentActivityModel, RecentActivityAdapter.ActivityViewHolder> {

    private static final DiffUtil.ItemCallback<RecentActivityModel> DIFF =
            new DiffUtil.ItemCallback<RecentActivityModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull RecentActivityModel oldItem,
                                               @NonNull RecentActivityModel newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull RecentActivityModel oldItem,
                                                  @NonNull RecentActivityModel newItem) {
                    return oldItem.getTitle().equals(newItem.getTitle())
                            && oldItem.getDetail().equals(newItem.getDetail())
                            && oldItem.getTimeLabel().equals(newItem.getTimeLabel());
                }
            };

    public RecentActivityAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRecentActivityBinding binding = ItemRecentActivityBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        DensityStyler.applyActivityRow(binding.getRoot());
        return new ActivityViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static final class ActivityViewHolder extends RecyclerView.ViewHolder {
        private final ItemRecentActivityBinding binding;

        ActivityViewHolder(ItemRecentActivityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(RecentActivityModel item) {
            binding.activityTitle.setText(item.getTitle());
            binding.activityDetail.setText(item.getDetail());
            binding.activityTime.setText(item.getTimeLabel());
        }
    }
}

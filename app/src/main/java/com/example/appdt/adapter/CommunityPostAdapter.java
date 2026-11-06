package com.example.appdt.adapter;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appdt.R;
import com.example.appdt.databinding.ItemCommunityCommentBinding;
import com.example.appdt.databinding.ItemCommunityPostBinding;
import com.example.appdt.model.CommentModel;
import com.example.appdt.model.PostModel;
import com.example.appdt.repository.CommunityRepository;
import com.example.appdt.utils.DensityStyler;

import java.util.HashMap;
import java.util.Map;

public final class CommunityPostAdapter extends
        ListAdapter<PostModel, CommunityPostAdapter.PostHolder> {
    public interface Actions {
        boolean onComment(PostModel post, String text);
        void onHelpful(PostModel post);
        void onShare(PostModel post);
        void onAttachment(PostModel post);
        void onDelete(PostModel post);
    }

    private static final DiffUtil.ItemCallback<PostModel> DIFF =
            new DiffUtil.ItemCallback<PostModel>() {
                @Override public boolean areItemsTheSame(@NonNull PostModel first,
                                                         @NonNull PostModel second) {
                    return first.getId().equals(second.getId());
                }
                @Override public boolean areContentsTheSame(@NonNull PostModel first,
                                                            @NonNull PostModel second) {
                    return first.equals(second);
                }
            };

    private final Actions actions;
    private final Map<String, String> commentDrafts = new HashMap<>();

    public CommunityPostAdapter(Actions actions) {
        super(DIFF);
        this.actions = actions;
    }

    @NonNull @Override
    public PostHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCommunityPostBinding binding = ItemCommunityPostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        DensityStyler.applyCard(binding.getRoot(), 10, 7);
        return new PostHolder(binding);
    }

    @Override public void onBindViewHolder(@NonNull PostHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public final class PostHolder extends RecyclerView.ViewHolder {
        private final ItemCommunityPostBinding binding;
        private TextWatcher watcher;

        PostHolder(ItemCommunityPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PostModel post) {
            Context context = binding.getRoot().getContext();
            binding.postCategory.setText(post.getCategory().getLabel());
            binding.postTime.setText(relativeTime(context, post.getCreatedAt()));
            binding.postTitle.setText(post.getTitle());
            binding.postAuthor.setText(context.getString(R.string.community_post_author,
                    post.getAuthor()));
            binding.postContent.setText(post.getContent());
            boolean hasAttachment = post.getAttachment() != null;
            binding.postAttachment.setVisibility(hasAttachment ? View.VISIBLE : View.GONE);
            if (hasAttachment) {
                binding.postAttachment.setText(context.getString(
                        R.string.community_attachment_name, post.getAttachment().getName()));
                binding.postAttachment.setOnClickListener(view -> actions.onAttachment(post));
            } else {
                binding.postAttachment.setOnClickListener(null);
            }
            binding.postCommentsAction.setText(context.getString(
                    R.string.community_comment_count, post.getComments().size()));
            binding.postCommentsAction.setOnClickListener(view -> {
                binding.postCommentInput.requestFocus();
                InputMethodManager keyboard = (InputMethodManager) context
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                keyboard.showSoftInput(binding.postCommentInput, InputMethodManager.SHOW_IMPLICIT);
            });
            binding.postHelpful.setText(context.getString(R.string.community_helpful_count,
                    post.getHelpfulCount()));
            boolean voted = post.hasVoted(CommunityRepository.getInstance().voterId());
            binding.postHelpful.setEnabled(!voted);
            binding.postHelpful.setContentDescription(voted
                    ? context.getString(R.string.community_already_helpful)
                    : binding.postHelpful.getText());
            binding.postHelpful.setOnClickListener(view -> actions.onHelpful(post));
            binding.postShare.setOnClickListener(view -> actions.onShare(post));
            boolean own = post.getAuthor().equals("Bạn");
            binding.postDelete.setVisibility(own ? View.VISIBLE : View.GONE);
            binding.postDelete.setOnClickListener(own ? view -> actions.onDelete(post) : null);

            binding.postCommentsList.removeAllViews();
            binding.postCommentsEmpty.setVisibility(post.getComments().isEmpty()
                    ? View.VISIBLE : View.GONE);
            for (CommentModel comment : post.getComments()) {
                ItemCommunityCommentBinding item = ItemCommunityCommentBinding.inflate(
                        LayoutInflater.from(context), binding.postCommentsList, false);
                item.commentMeta.setText(context.getString(R.string.community_comment_meta,
                        comment.getAuthor(), relativeTime(context, comment.getCreatedAt())));
                item.commentContent.setText(comment.getContent());
                binding.postCommentsList.addView(item.getRoot());
            }
            if (watcher != null) binding.postCommentInput.removeTextChangedListener(watcher);
            binding.postCommentInput.setText(commentDrafts.getOrDefault(post.getId(), ""));
            watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start,
                                                        int count, int after) { }
                @Override public void onTextChanged(CharSequence text, int start,
                                                    int before, int count) {
                    commentDrafts.put(post.getId(), text.toString());
                }
                @Override public void afterTextChanged(Editable text) { }
            };
            binding.postCommentInput.addTextChangedListener(watcher);
            binding.postCommentSend.setOnClickListener(view -> {
                String text = binding.postCommentInput.getText() == null ? ""
                        : binding.postCommentInput.getText().toString().trim();
                if (text.isEmpty()) {
                    binding.postCommentInput.setError(
                            context.getString(R.string.community_comment_required));
                    return;
                }
                if (actions.onComment(post, text)) {
                    binding.postCommentInput.setText("");
                    commentDrafts.remove(post.getId());
                    binding.postCommentInput.clearFocus();
                }
            });
        }
    }

    private static String relativeTime(Context context, long createdAt) {
        long minutes = Math.max(0L, (System.currentTimeMillis() - createdAt) / 60_000L);
        if (minutes < 1) return context.getString(R.string.community_time_now);
        if (minutes < 60) return context.getString(R.string.community_time_minutes, minutes);
        if (minutes < 1440) return context.getString(R.string.community_time_hours, minutes / 60);
        return context.getString(R.string.community_time_days, minutes / 1440);
    }
}

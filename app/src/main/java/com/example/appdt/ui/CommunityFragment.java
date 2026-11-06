package com.example.appdt.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.appdt.R;
import com.example.appdt.adapter.CommunityPostAdapter;
import com.example.appdt.databinding.FragmentCommunityBinding;
import com.example.appdt.model.PostAttachment;
import com.example.appdt.model.PostModel;
import com.example.appdt.repository.CommunityRepository;
import com.example.appdt.utils.CommunityShare;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public final class CommunityFragment extends Fragment {
    private FragmentCommunityBinding binding;
    private CommunityPostAdapter adapter;
    private CommunityRepository repository;
    private String firstPostId;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCommunityBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        repository = CommunityRepository.getInstance();
        repository.startListening();
        adapter = new CommunityPostAdapter(new CommunityPostAdapter.Actions() {
            @Override public boolean onComment(PostModel post, String text) {
                boolean added = repository.addComment(post.getId(), text);
                if (added) showMessage(R.string.community_comment_sent);
                return added;
            }
            @Override public void onHelpful(PostModel post) {
                if (repository.markHelpful(post.getId(), repository.voterId())) {
                    showMessage(R.string.community_helpful_added);
                }
            }
            @Override public void onShare(PostModel post) { sharePost(post); }
            @Override public void onAttachment(PostModel post) { openAttachment(post); }
            @Override public void onDelete(PostModel post) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.community_delete_confirm)
                        .setMessage(post.getTitle())
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (dialog, which) ->
                                repository.removePost(post.getId()))
                        .show();
            }
        });
        binding.communityPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.communityPosts.setAdapter(adapter);
        binding.communityNewPost.setOnClickListener(ignored -> openCreatePost());
        repository.getPosts().observe(getViewLifecycleOwner(), this::renderPosts);
        repository.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || binding == null) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            repository.clearError();
        });
    }

    private void renderPosts(List<PostModel> posts) {
        if (binding == null) return;
        binding.communityCount.setText(getString(R.string.community_post_count, posts.size()));
        binding.communityEmpty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        binding.communityEmpty.setText(repository.isLoading() ? R.string.community_loading
                : R.string.community_empty);
        String newestId = posts.isEmpty() ? null : posts.get(0).getId();
        boolean newPost = firstPostId != null && newestId != null
                && !firstPostId.equals(newestId);
        firstPostId = newestId;
        adapter.submitList(posts, () -> {
            if (newPost && binding != null) binding.communityPosts.scrollToPosition(0);
        });
    }

    private void openCreatePost() {
        if (getParentFragmentManager().isStateSaved()) return;
        if (getParentFragmentManager().findFragmentByTag("create_community_post") == null) {
            new CreatePostDialog().show(getParentFragmentManager(), "create_community_post");
        }
    }

    private void sharePost(PostModel post) {
        try {
            Intent send = CommunityShare.createSendIntent(requireContext(), post);
            startActivity(Intent.createChooser(send, getString(R.string.community_share_with)));
        } catch (ActivityNotFoundException | SecurityException error) {
            showMessage(R.string.community_no_share_app);
        }
    }

    private void openAttachment(PostModel post) {
        PostAttachment attachment = post.getAttachment();
        if (attachment == null) return;
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(attachment.getUri(), attachment.getMimeType())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(view);
        } catch (ActivityNotFoundException | SecurityException error) {
            showMessage(R.string.community_cannot_open_attachment);
        }
    }

    private void showMessage(int message) {
        if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override public void onDestroyView() {
        repository.stopListening();
        binding.communityPosts.setAdapter(null);
        adapter = null;
        binding = null;
        firstPostId = null;
        super.onDestroyView();
    }
}

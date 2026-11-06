package com.example.appdt.ui;

import android.content.res.Configuration;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.appdt.R;
import com.example.appdt.databinding.FragmentAccountBinding;
import com.example.appdt.model.AccentColor;
import com.example.appdt.model.DisplayDensity;
import com.example.appdt.model.PostModel;
import com.example.appdt.repository.CommunityRepository;
import com.example.appdt.repository.SettingsRepository;
import com.example.appdt.repository.WorkspaceRepository;
import com.example.appdt.repository.AuthRepository;
import com.example.appdt.model.UserModel;
import com.example.appdt.ui.auth.RegisterActivity;
import com.example.appdt.ui.auth.LoginActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

public final class AccountFragment extends Fragment {
    private FragmentAccountBinding binding;
    private SettingsRepository settings;
    private AuthRepository authRepository;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        settings = new SettingsRepository(requireContext());
        authRepository = new AuthRepository(requireContext());
        binding.accountLogin.setOnClickListener(ignored ->
                startActivity(new Intent(requireContext(), LoginActivity.class)));
        binding.accountCreateAccount.setOnClickListener(ignored ->
                startActivity(new Intent(requireContext(), RegisterActivity.class)));
        binding.accountLogout.setOnClickListener(ignored -> {
            CommunityRepository.getInstance().clearSession();
            authRepository.logout();
            Intent intent = new Intent(requireContext(), LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
        binding.accountEditName.setOnClickListener(ignored -> editName());
        renderUser();
        binding.accountDarkMode.setChecked(isDarkMode());
        binding.accountDarkMode.setOnCheckedChangeListener((button, checked) -> {
            int mode = checked ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO;
            settings.setNightMode(mode);
            AppCompatDelegate.setDefaultNightMode(mode);
        });
        binding.accountAccents.check(chipId(settings.getAccent()));
        binding.accountAccents.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            AccentColor accent = accentFor(checkedIds.get(0));
            if (accent != settings.getAccent()) {
                settings.setAccent(accent);
                requireActivity().recreate();
            }
        });
        binding.accountDensity.check(settings.getDisplayDensity() == DisplayDensity.COMPACT
                ? R.id.account_density_compact : R.id.account_density_comfortable);
        binding.accountDensity.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            DisplayDensity density = checkedIds.get(0) == R.id.account_density_compact
                    ? DisplayDensity.COMPACT : DisplayDensity.COMFORTABLE;
            if (density != settings.getDisplayDensity()) {
                settings.setDisplayDensity(density);
                requireActivity().recreate();
            }
        });
        WorkspaceRepository.getInstance().getDocuments().observe(getViewLifecycleOwner(), docs ->
                binding.accountDocumentCount.setText(
                        getString(R.string.account_document_count, docs.size())));
        CommunityRepository.getInstance().getPosts().observe(getViewLifecycleOwner(), posts -> {
            int ownPosts = 0;
            for (PostModel post : posts) if (post.getAuthor().equals("Bạn")) ownPosts++;
            binding.accountPostCount.setText(getString(R.string.account_post_count, ownPosts));
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (binding != null && authRepository != null) renderUser();
    }

    private void renderUser() {
        UserModel user = authRepository.getCurrentUser();
        boolean signedIn = user != null;
        binding.accountLogin.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        binding.accountCreateAccount.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        binding.accountLogout.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        binding.accountEditName.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        if (!signedIn) {
            binding.accountName.setText(R.string.account_profile_name);
            binding.accountEmail.setText(R.string.account_profile_note);
            binding.accountAvatar.setText(R.string.brand_mark);
            binding.accountVerificationNote.setVisibility(View.GONE);
            return;
        }
        String name = user.getFullName().trim();
        binding.accountName.setText(name);
        binding.accountEmail.setText(user.getEmail());
        binding.accountVerificationNote.setVisibility(View.VISIBLE);
        if (name.isEmpty()) {
            binding.accountAvatar.setText(R.string.brand_mark);
        } else {
            int end = name.offsetByCodePoints(0, 1);
            binding.accountAvatar.setText(name.substring(0, end).toUpperCase(Locale.ROOT));
        }
        binding.accountAvatar.setContentDescription(getString(
                R.string.account_avatar_description, name));
    }

    private void editName() {
        UserModel current = authRepository.getCurrentUser();
        if (current == null) return;
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(R.string.account_name_hint);
        layout.setPadding(24, 8, 24, 0);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setText(current.getFullName());
        layout.addView(input);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_edit_name)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.account_save_name, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(-1).setOnClickListener(view -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            if (name.isEmpty() || name.length() > 80) {
                layout.setError(getString(R.string.account_name_invalid));
                return;
            }
            dialog.getButton(-1).setEnabled(false);
            authRepository.updateDisplayName(name, saved -> {
                if (binding == null) return;
                dialog.getButton(-1).setEnabled(true);
                if (saved) { dialog.dismiss(); renderUser(); }
                else Snackbar.make(binding.getRoot(), R.string.account_name_save_error,
                        Snackbar.LENGTH_LONG).show();
            });
        }));
        dialog.show();
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private int chipId(AccentColor accent) {
        switch (accent) {
            case BLUE: return R.id.account_accent_blue;
            case GREEN: return R.id.account_accent_green;
            case PINK: return R.id.account_accent_pink;
            case ORANGE: return R.id.account_accent_orange;
            default: return R.id.account_accent_purple;
        }
    }

    private AccentColor accentFor(int id) {
        if (id == R.id.account_accent_blue) return AccentColor.BLUE;
        if (id == R.id.account_accent_green) return AccentColor.GREEN;
        if (id == R.id.account_accent_pink) return AccentColor.PINK;
        if (id == R.id.account_accent_orange) return AccentColor.ORANGE;
        return AccentColor.PURPLE;
    }

    @Override public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}

package com.example.appdt.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.appdt.MainActivity;
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
    private AlertDialog editNameDialog;

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
        binding.accountLogout.setOnClickListener(ignored -> logoutAndExit());
        binding.accountEditName.setOnClickListener(ignored -> editName());
        renderUser();
        binding.accountDarkMode.setChecked(SettingsRepository.isDarkMode(requireContext()));
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
        CommunityRepository community = CommunityRepository.getInstance();
        community.startListening();
        community.getPosts().observe(getViewLifecycleOwner(), posts -> {
            int ownPosts = 0;
            for (PostModel post : posts) if (post.isOwn()) ownPosts++;
            binding.accountPostCount.setText(getString(R.string.account_post_count, ownPosts));
        });
    }

    private void logoutAndExit() {
        WorkspaceRepository.getInstance().clearSession();
        CommunityRepository.getInstance().clearSession();
        authRepository.logout();
        Intent intent = new Intent(requireContext(), LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override public void onResume() {
        super.onResume();
        if (binding != null && authRepository != null) renderUser();
    }

    private void renderUser() {
        UserModel user = authRepository.getCurrentUser();
        if (user == null) {
            logoutAndExit();
            return;
        }
        binding.accountAvatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
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
        if (editNameDialog != null && editNameDialog.isShowing()) editNameDialog.dismiss();
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(R.string.account_name_hint);
        layout.setPadding(24, 8, 24, 0);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            input.setAutofillHints(View.AUTOFILL_HINT_NAME);
        }
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(80) });
        input.setText(current.getFullName());
        layout.addView(input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_edit_name)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.account_save_name, null)
                .create();
        editNameDialog = dialog;
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive == null) return;
            positive.setFilterTouchesWhenObscured(true);
            positive.setOnClickListener(view -> {
                String name = input.getText() == null ? "" : input.getText().toString().trim();
                if (name.isEmpty() || name.length() > 80) {
                    layout.setError(getString(R.string.account_name_invalid));
                    return;
                }
                positive.setEnabled(false);
                authRepository.updateDisplayName(name, saved -> {
                    if (binding == null || !isAdded() || !dialog.isShowing()) return;
                    positive.setEnabled(true);
                    if (saved) {
                        dialog.dismiss();
                        renderUser();
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).refreshUserSubtitle();
                        }
                    } else Snackbar.make(binding.getRoot(), R.string.account_name_save_error,
                            Snackbar.LENGTH_LONG).show();
                });
            });
        });
        dialog.show();
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
        CommunityRepository.getInstance().stopListening();
        if (editNameDialog != null && editNameDialog.isShowing()) editNameDialog.dismiss();
        editNameDialog = null;
        binding = null;
        super.onDestroyView();
    }
}

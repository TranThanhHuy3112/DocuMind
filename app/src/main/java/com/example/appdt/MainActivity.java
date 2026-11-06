package com.example.appdt;

import android.content.res.Configuration;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.appdt.databinding.ActivityMainBinding;
import com.example.appdt.model.AccentColor;
import com.example.appdt.repository.SettingsRepository;
import com.example.appdt.repository.WorkspaceRepository;
import com.example.appdt.repository.CommunityRepository;
import com.example.appdt.repository.AuthRepository;
import com.example.appdt.model.UserModel;
import com.example.appdt.ui.auth.LoginActivity;
import com.example.appdt.ui.AccountFragment;
import com.example.appdt.ui.CommunityFragment;
import com.example.appdt.ui.FilesFragment;
import com.example.appdt.ui.HomeFragment;
import com.example.appdt.ui.WorkspaceViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private WorkspaceViewModel viewModel;
    private SettingsRepository settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new SettingsRepository(getApplicationContext());
        AppCompatDelegate.setDefaultNightMode(settings.getNightMode());
        setTheme(R.style.Theme_DocuMind);
        getTheme().applyStyle(settings.getAccent().getThemeOverlay(), true);
        super.onCreate(savedInstanceState);
        UserModel currentUser = new AuthRepository(getApplicationContext()).getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }
        WorkspaceRepository.getInstance().initialize(getApplicationContext(), currentUser.getId());
        CommunityRepository.getInstance().initialize(getApplicationContext(), currentUser.getId());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.brandSubtitle.setText(getString(R.string.main_signed_in_as,
                currentUser.getFullName()));
        applyInsets();

        viewModel = new ViewModelProvider(this).get(WorkspaceViewModel.class);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            viewModel.selectTab(item.getItemId());
            return true;
        });
        viewModel.getSelectedTab().observe(this, this::showTab);
        binding.themeToggle.setOnClickListener(view -> toggleTheme());
        binding.accentPicker.setOnClickListener(view -> showAccentPicker());
        boolean dark = isDarkMode();
        binding.themeToggle.setIconResource(dark ? R.drawable.ic_sun : R.drawable.ic_moon);
        binding.themeToggle.setContentDescription(getString(dark
                ? R.string.switch_to_light : R.string.switch_to_dark));
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
        WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                .setAppearanceLightStatusBars(!isDarkMode());
        WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                .setAppearanceLightNavigationBars(!isDarkMode());
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void toggleTheme() {
        int mode = settings.getNightMode() == AppCompatDelegate.MODE_NIGHT_YES
                ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        settings.setNightMode(mode);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void showAccentPicker() {
        AccentColor[] accents = AccentColor.values();
        String[] labels = new String[accents.length];
        for (int i = 0; i < accents.length; i++) {
            labels[i] = getString(accents[i].getLabel());
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.choose_accent)
                .setSingleChoiceItems(labels, settings.getAccent().ordinal(), (dialog, which) -> {
                    if (accents[which] != settings.getAccent()) {
                        settings.setAccent(accents[which]);
                        recreate();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showTab(int tabId) {
        if (binding.bottomNavigation.getSelectedItemId() != tabId) {
            binding.bottomNavigation.getMenu().findItem(tabId).setChecked(true);
        }
        String tag = "workspace:" + tabId;
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (current != null && tag.equals(current.getTag())) {
            return;
        }
        Fragment next;
        if (tabId == R.id.nav_files) {
            next = new FilesFragment();
        } else if (tabId == R.id.nav_community) {
            next = new CommunityFragment();
        } else if (tabId == R.id.nav_account) {
            next = new AccountFragment();
        } else {
            next = new HomeFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, next, tag)
                .commitNow();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}

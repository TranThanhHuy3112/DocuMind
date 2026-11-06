package com.example.appdt.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import com.example.appdt.databinding.FragmentWorkspaceSectionBinding;

/** Stage 1 shell, replaced by each feature screen in subsequent stages. */
public abstract class WorkspaceSectionFragment extends Fragment {
    private FragmentWorkspaceSectionBinding binding;

    @StringRes protected abstract int title();
    @StringRes protected abstract int description();
    @DrawableRes protected abstract int icon();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWorkspaceSectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.sectionTitle.setText(title());
        binding.sectionDescription.setText(description());
        binding.sectionIcon.setImageResource(icon());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

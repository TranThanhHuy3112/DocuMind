package com.example.appdt.ui.tools;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.appdt.R;
import com.example.appdt.databinding.SheetDocumentToolBinding;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.example.appdt.ui.WorkspaceViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

abstract class ToolSheet extends BottomSheetDialogFragment {
    private static final String STATE_SOURCE_INDEX = "tool_source_index";
    private static final String STATE_SECOND_INDEX = "tool_second_index";

    protected SheetDocumentToolBinding binding;
    protected WorkspaceViewModel workspace;
    protected AiDocumentService ai;
    private boolean aiBusy;
    private String lastSources;
    private int sourceIndex;
    private int secondIndex = 1;

    protected abstract ToolKind tool();
    protected abstract int title();
    protected abstract int subtitle();
    protected abstract void render(List<DocumentModel> sources);

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetDocumentToolBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            sourceIndex = Math.max(0, savedInstanceState.getInt(STATE_SOURCE_INDEX, 0));
            secondIndex = Math.max(0, savedInstanceState.getInt(STATE_SECOND_INDEX, 1));
        }
        workspace = new ViewModelProvider(requireActivity()).get(WorkspaceViewModel.class);
        ai = new AiDocumentService(requireContext());
        binding.toolTitle.setText(title());
        binding.toolSubtitle.setText(subtitle());
        workspace.getDocuments().observe(getViewLifecycleOwner(), documents -> {
            List<DocumentModel> sources = ToolSources.readyFor(documents, tool());
            StringBuilder signature = new StringBuilder();
            for (DocumentModel source : sources) signature.append(source.getId()).append('\n');
            if (signature.toString().equals(lastSources)) return;
            if (lastSources != null) {
                sourceIndex = 0;
                secondIndex = 1;
            }
            lastSources = signature.toString();
            renderCurrent(sources);
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SOURCE_INDEX, sourceIndex);
        outState.putInt(STATE_SECOND_INDEX, secondIndex);
    }

    private void renderCurrent(List<DocumentModel> sources) {
        if (ai != null) ai.cancelPending();
        aiBusy = false;
        binding.toolContent.removeAllViews();
        binding.toolActions.removeAllViews();
        if (sources.isEmpty() || tool() == ToolKind.COMPARE && sources.size() < 2) {
            addText(binding.toolContent, getString(R.string.tool_empty), 15, false);
        } else {
            if (sources.size() > 1) {
                addSourceCycle(sources, false);
                if (tool() == ToolKind.COMPARE) addSourceCycle(sources, true);
            }
            render(sources);
        }
    }

    private void addSourceCycle(List<DocumentModel> sources, boolean second) {
        int rawIndex = second ? secondIndex : sourceIndex;
        int index = Math.min(rawIndex, sources.size() - 1);
        MaterialButton button = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(getString(second ? R.string.tool_second_source : R.string.tool_first_source,
                index + 1, sources.size(), sources.get(index).getName()));
        binding.toolContent.addView(button);
        button.setOnClickListener(ignored -> {
            int next = (index + 1) % sources.size();
            if (tool() == ToolKind.COMPARE && sources.size() > 1) {
                int other = second ? Math.min(sourceIndex, sources.size() - 1)
                        : Math.min(secondIndex, sources.size() - 1);
                if (next == other) next = (next + 1) % sources.size();
            }
            if (second) secondIndex = next;
            else sourceIndex = next;
            renderCurrent(sources);
        });
    }

    protected DocumentModel selectedSource(List<DocumentModel> sources) {
        return sources.get(Math.min(sourceIndex, sources.size() - 1));
    }

    protected DocumentModel selectedSecondSource(List<DocumentModel> sources) {
        return sources.get(Math.min(secondIndex, sources.size() - 1));
    }

    @Override public void onStart() {
        super.onStart();
        if (!(getDialog() instanceof BottomSheetDialog)) return;
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        float fraction;
        if (tool() == ToolKind.CHAT) fraction = 0.82f;
        else if (tool() == ToolKind.ANALYZE || tool() == ToolKind.DRAFT) fraction = 0.58f;
        else fraction = 0.50f;
        int height = (int) (getResources().getDisplayMetrics().heightPixels * fraction);
        sheet.getLayoutParams().height = height;
        sheet.requestLayout();
        BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    protected TextView addText(LinearLayout parent, String content, int sp, boolean bold) {
        TextView text = new TextView(requireContext());
        text.setText(content);
        text.setTextSize(sp);
        text.setTextColor(MaterialColors.getColor(binding.getRoot(),
                com.google.android.material.R.attr.colorOnSurface));
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        text.setPadding(0, dp(6), 0, dp(6));
        parent.addView(text);
        return text;
    }

    protected int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    protected void askAi(List<DocumentModel> sources, String instruction, TextView output,
                         View action) {
        askAi(sources, instruction, output, action, null, null);
    }

    protected void askAi(List<DocumentModel> sources, String instruction, TextView output,
                         View action, Runnable afterSuccess) {
        askAi(sources, instruction, output, action, afterSuccess, null);
    }

    protected void askAi(List<DocumentModel> sources, String instruction, TextView output,
                         View action, @Nullable Runnable afterSuccess,
                         @Nullable Runnable afterFinish) {
        if (ai == null || aiBusy) return;
        aiBusy = true;
        action.setEnabled(false);
        output.setText(R.string.ai_working);
        ai.ask(sources, instruction, new AiDocumentService.Callback() {
            @Override public void onSuccess(String answer) {
                if (binding == null) return;
                output.setText(answer);
                action.setEnabled(true);
                aiBusy = false;
                if (afterSuccess != null) afterSuccess.run();
                if (afterFinish != null) afterFinish.run();
            }
            @Override public void onError(String message) {
                if (binding == null) return;
                output.setText(message);
                action.setEnabled(true);
                aiBusy = false;
                if (afterFinish != null) afterFinish.run();
            }
        });
    }

    protected void copySensitiveText(int labelRes, CharSequence text, int snackbarRes) {
        if (binding == null) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        ClipData clip = ClipData.newPlainText(getString(labelRes), text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        Snackbar.make(binding.getRoot(), snackbarRes, Snackbar.LENGTH_SHORT).show();
    }

    @Override public void onDestroyView() {
        if (ai != null) ai.close();
        ai = null;
        lastSources = null;
        binding = null;
        super.onDestroyView();
    }
}

package com.example.appdt.ui.tools;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.List;

abstract class ToolSheet extends BottomSheetDialogFragment {
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
        workspace = new ViewModelProvider(requireActivity()).get(WorkspaceViewModel.class);
        ai = new AiDocumentService(requireContext());
        binding.toolTitle.setText(title());
        binding.toolSubtitle.setText(subtitle());
        workspace.getDocuments().observe(getViewLifecycleOwner(), documents -> {
            List<DocumentModel> sources = ToolSources.readyFor(documents, tool());
            StringBuilder signature = new StringBuilder();
            for (DocumentModel source : sources) signature.append(source.getId()).append('\n');
            if (signature.toString().equals(lastSources)) return;
            lastSources = signature.toString();
            sourceIndex = 0;
            secondIndex = 1;
            renderCurrent(sources);
        });
    }

    private void renderCurrent(List<DocumentModel> sources) {
        if (ai != null) ai.close();
        ai = new AiDocumentService(requireContext());
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
        int index = second ? secondIndex : sourceIndex;
        MaterialButton button = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(getString(second ? R.string.tool_second_source : R.string.tool_first_source,
                index + 1, sources.size(), sources.get(index).getName()));
        binding.toolContent.addView(button);
        button.setOnClickListener(ignored -> {
            int next = (index + 1) % sources.size();
            if (tool() == ToolKind.COMPARE && sources.size() > 1) {
                int other = second ? sourceIndex : secondIndex;
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
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        float fraction;
        if (tool() == ToolKind.CHAT) fraction = 0.82f;
        else if (tool() == ToolKind.ANALYZE || tool() == ToolKind.DRAFT) fraction = 0.58f;
        else fraction = 0.50f;
        int height = (int) (Resources.getSystem().getDisplayMetrics().heightPixels * fraction);
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
        askAi(sources, instruction, output, action, null);
    }

    protected void askAi(List<DocumentModel> sources, String instruction, TextView output,
                         View action, Runnable afterSuccess) {
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
            }
            @Override public void onError(String message) {
                if (binding == null) return;
                output.setText(message);
                action.setEnabled(true);
                aiBusy = false;
            }
        });
    }

    @Override public void onDestroyView() {
        if (ai != null) ai.close();
        ai = null;
        lastSources = null;
        binding = null;
        super.onDestroyView();
    }
}

package com.example.appdt.ui.tools;

import android.widget.TextView;

import com.example.appdt.R;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Collections;

public final class VideoTranscriptBottomSheetDialog extends ToolSheet {
    @Override protected ToolKind tool() { return ToolKind.TRANSCRIBE; }
    @Override protected int title() { return R.string.transcript_title; }
    @Override protected int subtitle() { return R.string.transcript_subtitle; }

    @Override protected void render(List<DocumentModel> sources) {
        DocumentModel source = selectedSource(sources);
        addText(binding.toolContent, getString(R.string.tool_source, source.getName()), 13, true);
        addText(binding.toolContent, getString(R.string.ai_send_notice), 13, false);
        TextView body = addText(binding.toolContent, getString(R.string.transcript_ready), 15, false);
        body.setId(R.id.transcript_text);
        body.setLineSpacing(dp(7), 1f);
        MaterialButton copy = new MaterialButton(requireContext());
        copy.setId(R.id.transcript_copy);
        copy.setText(R.string.copy_transcript);
        copy.setEnabled(false);
        copy.setFilterTouchesWhenObscured(true);
        MaterialButton transcribe = new MaterialButton(requireContext());
        transcribe.setText(R.string.transcript_start);
        transcribe.setFilterTouchesWhenObscured(true);
        binding.toolActions.addView(transcribe);
        transcribe.setOnClickListener(view -> {
            copy.setEnabled(false);
            askAi(Collections.singletonList(source),
                    "Chép lại chính xác lời nói trong tệp. Nếu có thể xác định thời gian, ghi mốc thời gian. "
                            + "Không mô tả cảnh không liên quan đến lời nói.", body, transcribe,
                    () -> copy.setEnabled(true));
        });
        binding.toolActions.addView(copy);
        copy.setOnClickListener(view -> copySensitiveText(
                R.string.transcript_title, body.getText(), R.string.transcript_copied));
    }
}

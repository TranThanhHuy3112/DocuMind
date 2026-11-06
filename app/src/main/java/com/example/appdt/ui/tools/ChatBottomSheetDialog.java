package com.example.appdt.ui.tools;

import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.appdt.R;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Collections;

public final class ChatBottomSheetDialog extends ToolSheet {
    @Override protected ToolKind tool() { return ToolKind.CHAT; }
    @Override protected int title() { return R.string.chat_title; }
    @Override protected int subtitle() { return R.string.chat_subtitle; }

    @Override protected void render(List<DocumentModel> sources) {
        DocumentModel source = selectedSource(sources);
        addText(binding.toolContent, getString(R.string.tool_source, source.getName()), 13, true);
        addText(binding.toolContent, getString(R.string.ai_send_notice), 13, false);
        LinearLayout messages = new LinearLayout(requireContext());
        messages.setId(R.id.chat_messages);
        messages.setOrientation(LinearLayout.VERTICAL);
        binding.toolContent.addView(messages);
        TextView guidance = addText(messages, getString(R.string.chat_start_hint), 14, false);
        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setHint(getString(R.string.chat_question_hint));
        TextInputEditText question = new TextInputEditText(inputLayout.getContext());
        question.setId(R.id.chat_question);
        question.setSingleLine(false);
        question.setMaxLines(3);
        question.setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputLayout.addView(question);
        binding.toolActions.addView(inputLayout);
        LinearLayout suggestions = new LinearLayout(requireContext());
        suggestions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton summary = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        summary.setText(R.string.chat_suggest_summary);
        MaterialButton numbers = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        numbers.setText(R.string.chat_suggest_numbers);
        suggestions.addView(summary, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        suggestions.addView(numbers, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        binding.toolContent.addView(suggestions);
        summary.setOnClickListener(view -> question.setText(R.string.chat_suggest_summary_question));
        numbers.setOnClickListener(view -> question.setText(R.string.chat_suggest_numbers_question));
        MaterialButton send = new MaterialButton(requireContext());
        send.setId(R.id.chat_send);
        send.setText(R.string.chat_send);
        binding.toolActions.addView(send);
        send.setOnClickListener(view -> {
            String message = question.getText() == null ? "" : question.getText().toString().trim();
            if (message.isEmpty()) {
                inputLayout.setError(getString(R.string.chat_question_required));
                return;
            }
            inputLayout.setError(null);
            messages.removeView(guidance);
            TextView user = addText(messages, message, 15, true);
            user.setContentDescription(getString(R.string.chat_your_question, message));
            TextView answer = addText(messages, "", 14, false);
            askAi(Collections.singletonList(source), "Trả lời câu hỏi sau dựa trên tài liệu: "
                    + message, answer, send);
            question.setText("");
            binding.toolScroll.post(() -> binding.toolScroll.fullScroll(android.view.View.FOCUS_DOWN));
        });
    }
}

package com.example.appdt.ui.tools;

import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

import com.example.appdt.R;
import com.example.appdt.model.DocumentModel;
import com.example.appdt.model.ToolKind;
import com.example.appdt.utils.DocumentSizeFormatter;

import java.util.List;
import java.util.Locale;
import java.util.Arrays;

public final class CompareBottomSheetDialog extends ToolSheet {
    @Override protected ToolKind tool() { return ToolKind.COMPARE; }
    @Override protected int title() { return R.string.compare_title; }
    @Override protected int subtitle() { return R.string.compare_subtitle; }

    @Override protected void render(List<DocumentModel> sources) {
        DocumentModel first = selectedSource(sources);
        DocumentModel second = selectedSecondSource(sources);
        addText(binding.toolContent, getString(R.string.ai_send_notice), 13, false);
        String result = getString(R.string.compare_source_line, first.getName(),
                first.getExtension().toUpperCase(Locale.ROOT),
                DocumentSizeFormatter.format(requireContext(), first.getSize()))
                + "\n\n" + getString(R.string.compare_source_line, second.getName(),
                second.getExtension().toUpperCase(Locale.ROOT),
                DocumentSizeFormatter.format(requireContext(), second.getSize()));
        TextView comparison = addText(binding.toolContent, result, 16, false);
        comparison.setId(R.id.compare_result);
        MaterialButton compare = new MaterialButton(requireContext());
        compare.setText(R.string.compare_start);
        binding.toolActions.addView(compare);
        compare.setOnClickListener(view -> askAi(Arrays.asList(first, second),
                "So sánh nội dung hai nguồn: điểm giống, điểm khác, số liệu mâu thuẫn "
                        + "và kết luận ngắn. Chỉ nêu điều kiểm chứng được từ tệp.",
                comparison, compare));
    }

}

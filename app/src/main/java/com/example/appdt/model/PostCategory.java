package com.example.appdt.model;

import androidx.annotation.StringRes;

import com.example.appdt.R;

public enum PostCategory {
    STUDY(R.string.community_category_study),
    DATA(R.string.community_category_data),
    WORK(R.string.community_category_work),
    OTHER(R.string.community_category_other);

    private final int label;

    PostCategory(@StringRes int label) { this.label = label; }

    @StringRes public int getLabel() { return label; }
}

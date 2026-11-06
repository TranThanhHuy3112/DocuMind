package com.example.appdt.model;

import java.util.Objects;

public final class RecentActivityModel {
    private final long id;
    private final String title;
    private final String detail;
    private final String timeLabel;

    public RecentActivityModel(long id, String title, String detail, String timeLabel) {
        this.id = id;
        this.title = Objects.requireNonNull(title);
        this.detail = Objects.requireNonNull(detail);
        this.timeLabel = Objects.requireNonNull(timeLabel);
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public String getTimeLabel() { return timeLabel; }
}

package com.example.google.google_hackathon.dto;

import java.util.List;

public class RoadmapRequestDto {
    private String category;
    private List<ReflectionSummaryDtoByFuji> summaries;

    // ゲッター
    public String getCategory() {
        return category;
    }

    public List<ReflectionSummaryDtoByFuji> getSummaries() {
        return summaries;
    }

    // セッター
    public void setCategory(String category) {
        this.category = category;
    }

    public void setSummaries(List<ReflectionSummaryDtoByFuji> summaries) {
        this.summaries = summaries;
    }
}

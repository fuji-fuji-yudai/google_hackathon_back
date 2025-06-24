package com.example.google.google_hackathon.dto;

import java.util.List;

public class RoadmapRequestDto {
    private String category;
    private List<ReflectionSummaryDtoByFuji> summaries;

    // ゲッター・セッター
    public String getCategory() {
        return category;
    }

    public List<ReflectionSummaryDtoByFuji> getSummaries() {
        return summaries;
    }

    public void setCategory(String category2) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setCategory'");
    }

    public void setSummaries(List<ReflectionSummaryDtoByFuji> result) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setSummaries'");
    }

}

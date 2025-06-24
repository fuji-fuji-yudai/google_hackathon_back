package com.example.google.google_hackathon.service;

import java.time.YearMonth;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.google.google_hackathon.dto.ReflectionSummaryDtoByFuji;
import com.example.google.google_hackathon.repository.ReflectionSummaryRepository;

@Service
public class ReflectionSummaryService {

    @Autowired
    private ReflectionSummaryRepository repository;

    public List<ReflectionSummaryDtoByFuji> getSummariesByPeriod(Long userId, String periodKey) {
    YearMonth now = YearMonth.now();
    YearMonth startYm;

    switch (periodKey) {
        case "lastMonth" -> startYm = now.minusMonths(1);
        case "last3Months" -> startYm = now.minusMonths(3);
        case "last6Months" -> startYm = now.minusMonths(6);
        default -> throw new IllegalArgumentException("無効な期間指定: " + periodKey);
    }

    String start = startYm.toString();
    String end = now.minusMonths(1).toString();

    return repository.findByUserIdAndYearMonthRange(userId, start, end).stream()
        .map(e -> new ReflectionSummaryDtoByFuji(
            e.getYearMonth(),
            e.getActivitySummary(),
            e.getAchievementSummary(),
            e.getImprovementSummary()
        ))
        .toList();
}

}


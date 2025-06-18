package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.RoadmapEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoadmapEntryRepository extends JpaRepository<RoadmapEntry, Long> {
    // カスタムクエリの場合、この行のみ、または有効なプロパティを使用する他の行のみである必要があります。
    List<RoadmapEntry> findByCategoryName(String categoryName);
    // 以下の行が存在する場合は削除してください:
    // List<RoadmapEntry> findByQuarterAndMonth(String quarter, String month);
}
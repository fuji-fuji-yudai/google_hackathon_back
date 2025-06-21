package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.RoadmapEntry; // パッケージはこれでOK
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoadmapEntryRepository extends JpaRepository<RoadmapEntry, Long> {
    List<RoadmapEntry> findByCategoryName(String categoryName);

    // ユーザーID (AppUserのid) に紐づくロードマップエントリを検索
    // user_Id は RoadmapEntryの 'user' フィールドにある AppUser オブジェクトの 'id' フィールドを参照します
    List<RoadmapEntry> findByUser_Id(Long userId);
}
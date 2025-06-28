package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.RoadmapEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional; // Optionalを使用しているためインポートが必要

/**
 * RoadmapEntryRepository は RoadmapEntry エンティティに対応する
 * データベース操作を行うためのインターフェースです。
 *
 * JpaRepository を継承しているため、標準的な操作が自動で使えます。
 */
public interface RoadmapEntryRepository extends JpaRepository<RoadmapEntry, Long> {

    /**
     * 特定のAppUserに紐づく全てのRoadmapEntryを取得します。
     * RoadmapEntryエンティティ内のフィールド名「user」に合わせます。
     * 
     * @param userId 検索対象のAppUserのID
     * @return 指定されたAppUserに紐づくRoadmapEntryのリスト
     */
    List<RoadmapEntry> findByUser_Id(Long userId);

    /**
     * カテゴリ名でRoadmapEntryを検索します。
     * 
     * @param categoryName 検索対象のカテゴリ名
     * @return 指定されたカテゴリ名に紐づくRoadmapEntryのリスト
     */
    List<RoadmapEntry> findByCategoryName(String categoryName);

    // 必要に応じてfindByUser_Usernameなどの複合クエリメソッドもここに追加できます
    // List<RoadmapEntry> findByUser_Username(String username);
    // Optional<RoadmapEntry> findByIdAndUser_Username(Long id, String username);
}
package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.RoadmapCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapCategoryRepository extends JpaRepository<RoadmapCategory, Long> {
    // user_id と name でRoadmapCategoryを検索するメソッド
    Optional<RoadmapCategory> findByUser_IdAndName(Long userId, String name);

    @Query("SELECT DISTINCT SUBSTRING(rc.name, 1, LOCATE(' - ', rc.name) - 1) FROM RoadmapCategory rc WHERE rc.user.id = :userId")
    List<String> findDistinctCategoryNamesByUserId(@Param("userId") Long userId);

}



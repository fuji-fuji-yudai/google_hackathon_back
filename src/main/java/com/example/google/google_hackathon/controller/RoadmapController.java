package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.entity.RoadmapEntry;
import com.example.google.google_hackathon.service.RoadmapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // 必要であれば認証・認可
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roadmap")
public class RoadmapController {

    @Autowired
    private RoadmapService roadmapService;

    // ロードマップエントリを新規作成
    @PostMapping
    @PreAuthorize("hasRole('USER')") // 例: 認証済みユーザーのみ許可
    public ResponseEntity<RoadmapEntry> createRoadmapEntry(@RequestBody RoadmapEntry roadmapEntry) {
        RoadmapEntry createdEntry = roadmapService.createRoadmapEntry(roadmapEntry);
        return new ResponseEntity<>(createdEntry, HttpStatus.CREATED);
    }

    // 全ロードマップエントリを取得
    @GetMapping
    public ResponseEntity<List<RoadmapEntry>> getAllRoadmapEntries() {
        List<RoadmapEntry> entries = roadmapService.getAllRoadmapEntries();
        return new ResponseEntity<>(entries, HttpStatus.OK);
    }

    // 特定のIDのロードマップエントリを取得
    @GetMapping("/{id}")
    public ResponseEntity<RoadmapEntry> getRoadmapEntryById(@PathVariable Long id) {
        return roadmapService.getRoadmapEntryById(id)
                .map(entry -> new ResponseEntity<>(entry, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // ロードマップエントリを更新
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')") // 例: 認証済みユーザーのみ許可
    public ResponseEntity<RoadmapEntry> updateRoadmapEntry(@PathVariable Long id,
            @RequestBody RoadmapEntry roadmapEntry) {
        try {
            RoadmapEntry updatedEntry = roadmapService.updateRoadmapEntry(id, roadmapEntry);
            return new ResponseEntity<>(updatedEntry, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // ロードマップエントリを削除
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')") // 例: 認証済みユーザーのみ許可
    public ResponseEntity<Void> deleteRoadmapEntry(@PathVariable Long id) {
        try {
            roadmapService.deleteRoadmapEntry(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}

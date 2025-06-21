package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.RoadmapEntry; // パッケージ名を確認
import com.example.google.google_hackathon.repository.RoadmapEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RoadmapService {

    @Autowired
    private RoadmapEntryRepository roadmapEntryRepository;

    @Transactional
    public RoadmapEntry createRoadmapEntry(RoadmapEntry roadmapEntry) {
        return roadmapEntryRepository.save(roadmapEntry);
    }

    @Transactional(readOnly = true)
    public List<RoadmapEntry> getAllRoadmapEntries() {
        return roadmapEntryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<RoadmapEntry> getRoadmapEntryById(Long id) {
        return roadmapEntryRepository.findById(id);
    }

    @Transactional
    public RoadmapEntry updateRoadmapEntry(Long id, RoadmapEntry updatedEntry) {
        return roadmapEntryRepository.findById(id).map(entry -> {
            entry.setCategoryName(updatedEntry.getCategoryName());
            entry.setTaskName(updatedEntry.getTaskName()); // タスク名を設定
            entry.setStartMonth(updatedEntry.getStartMonth()); // 開始月を設定
            entry.setEndMonth(updatedEntry.getEndMonth()); // 終了月を設定
            return roadmapEntryRepository.save(entry);
        }).orElseThrow(() -> new RuntimeException("Roadmap Entry not found with id " + id));
    }

    @Transactional
    public void deleteRoadmapEntry(Long id) {
        roadmapEntryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<RoadmapEntry> getRoadmapEntriesByCategory(String categoryName) {
        return roadmapEntryRepository.findByCategoryName(categoryName);
    }
}
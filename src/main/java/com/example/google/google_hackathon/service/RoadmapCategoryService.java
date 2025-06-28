package com.example.google.google_hackathon.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.google.google_hackathon.repository.RoadmapCategoryRepository;

@Service
public class RoadmapCategoryService {
    @Autowired
    private RoadmapCategoryRepository repository;

    public List<String> getUniqueCategoryNames(Long userId) {
        return repository.findDistinctCategoryNamesByUserId(userId);
    }
}


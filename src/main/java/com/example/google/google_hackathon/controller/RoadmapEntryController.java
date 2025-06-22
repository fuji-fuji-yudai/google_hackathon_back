package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.RoadmapEntry;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.RoadmapEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/roadmap-entries")
public class RoadmapEntryController {

    @Autowired
    private RoadmapEntryRepository roadmapEntryRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    // ロードマップエントリの作成
    @PostMapping
    public ResponseEntity<RoadmapEntry> createRoadmapEntry(@RequestBody RoadmapEntry roadmapEntry) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);

        if (currentUserOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // ★ここを修正: roadmapEntry.setUser() は元々正しかったので戻す★
        roadmapEntry.setUser(currentUserOptional.get());
        RoadmapEntry savedEntry = roadmapEntryRepository.save(roadmapEntry);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEntry);
    }

    // ロードマップエントリの取得 (ログインユーザーに紐づくもののみ)
    @GetMapping
    public List<RoadmapEntry> getAllRoadmapEntries() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Collections.emptyList();
        }

        String username = authentication.getName();
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);

        if (currentUserOptional.isEmpty()) {
            return Collections.emptyList();
        }

        // ★ここを修正: findByUser_Id() (userフィールドのIDで検索)★
        return roadmapEntryRepository.findByUser_Id(currentUserOptional.get().getId());
    }

    // ロードマップエントリの更新
    @PutMapping("/{id}")
    public ResponseEntity<RoadmapEntry> updateRoadmapEntry(@PathVariable Long id,
            @RequestBody RoadmapEntry roadmapEntryDetails) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = authentication.getName();
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);
        if (currentUserOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Long currentUserId = currentUserOptional.get().getId();

        Optional<RoadmapEntry> existingEntryOptional = roadmapEntryRepository.findById(id);

        if (existingEntryOptional.isEmpty()) {
            return ResponseEntity.notFound().build(); // 404 Not Found
        }

        RoadmapEntry existingEntry = existingEntryOptional.get();

        // ★ここを修正: existingEntry.getUser() は元々正しかったので戻す★
        if (!existingEntry.getUser().getId().equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403 Forbidden
        }

        existingEntry.setCategoryName(roadmapEntryDetails.getCategoryName());
        existingEntry.setTaskName(roadmapEntryDetails.getTaskName());
        existingEntry.setStartMonth(roadmapEntryDetails.getStartMonth());
        existingEntry.setEndMonth(roadmapEntryDetails.getEndMonth());
        RoadmapEntry updatedEntry = roadmapEntryRepository.save(existingEntry);
        return new ResponseEntity<>(updatedEntry, HttpStatus.OK); // 200 OK
    }

    // ロードマップエントリの削除
    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> deleteRoadmapEntry(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = authentication.getName();
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);
        if (currentUserOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Long currentUserId = currentUserOptional.get().getId();

        Optional<RoadmapEntry> entryToDeleteOptional = roadmapEntryRepository.findById(id);

        if (entryToDeleteOptional.isEmpty()) {
            return ResponseEntity.notFound().build(); // 404 Not Found
        }

        RoadmapEntry entryToDelete = entryToDeleteOptional.get();

        // ★ここを修正: entryToDelete.getUser() は元々正しかったので戻す★
        if (!entryToDelete.getUser().getId().equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403 Forbidden
        }

        roadmapEntryRepository.delete(entryToDelete);
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
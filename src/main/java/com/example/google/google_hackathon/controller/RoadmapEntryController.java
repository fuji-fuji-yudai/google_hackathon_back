package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.RoadmapEntry;
import com.example.google.google_hackathon.entity.RoadmapCategory;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.RoadmapEntryRepository;
import com.example.google.google_hackathon.repository.RoadmapCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
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

    @Autowired
    private RoadmapCategoryRepository roadmapCategoryRepository; // ★注入

    // ロードマップエントリの作成
    @PostMapping
    public ResponseEntity<RoadmapEntry> createRoadmapEntry(@RequestBody RoadmapEntry roadmapEntry) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("DEBUG (Controller - POST): Authentication object: " + authentication);

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            System.out.println(
                    "DEBUG (Controller - POST): User not authenticated or is anonymous. Returning UNAUTHORIZED.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();
        System.out.println("DEBUG (Controller - POST): Authenticated username: " + username);
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);

        if (currentUserOptional.isEmpty()) {
            System.out.println("DEBUG (Controller - POST): AppUser not found for username: " + username
                    + ". Returning FORBIDDEN.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        AppUser currentUser = currentUserOptional.get(); // ユーザー情報を取得
        Long currentUserId = currentUser.getId(); // ユーザーIDを取得

        System.out.println(
                "DEBUG (Controller - POST): Received data for new entry - Category: " + roadmapEntry.getCategoryName() +
                        ", Task: " + roadmapEntry.getTaskName() +
                        ", StartMonth: " + roadmapEntry.getStartMonth() +
                        ", EndMonth: " + roadmapEntry.getEndMonth() +
                        ", StartYear: " + roadmapEntry.getStartYear() +
                        ", EndYear: " + roadmapEntry.getEndYear());

        // ★追加: RoadmapCategory の保存/更新ロジック
        // カテゴリ名とタスク名を結合した文字列を生成
        String combinedName = roadmapEntry.getCategoryName() + " - " + roadmapEntry.getTaskName();

        // 同じユーザー、同じ結合名の組み合わせが存在するかチェック
        Optional<RoadmapCategory> existingCategory = roadmapCategoryRepository.findByUser_IdAndName(
                currentUserId, combinedName);

        if (existingCategory.isEmpty()) {
            // 存在しない場合は新しく作成して保存
            RoadmapCategory newRoadmapCategory = new RoadmapCategory(currentUser, combinedName);
            roadmapCategoryRepository.save(newRoadmapCategory);
            System.out.println("DEBUG (Controller - POST): New RoadmapCategory saved: Name='"
                    + newRoadmapCategory.getName() + "' for User ID=" + currentUser.getId());
        } else {
            System.out.println("DEBUG (Controller - POST): RoadmapCategory already exists for Name='" + combinedName
                    + "' for User ID=" + currentUserId);
            // 既存のカテゴリ情報を更新する必要がある場合（例: updated_atを更新したい）は、ここで処理を追加
            RoadmapCategory existing = existingCategory.get();
            existing.setUpdatedAt(LocalDateTime.now()); // updated_atを更新
            roadmapCategoryRepository.save(existing);
            System.out.println("DEBUG (Controller - POST): Existing RoadmapCategory updated (updated_at): Name='"
                    + combinedName + "' for User ID=" + currentUserId);
        }

        roadmapEntry.setUser(currentUser); // ユーザーをセット
        RoadmapEntry savedEntry = roadmapEntryRepository.save(roadmapEntry);
        System.out.println("DEBUG (Controller - POST): RoadmapEntry saved with ID: " + savedEntry.getId() +
                ", StartMonth: " + savedEntry.getStartMonth() + ", EndMonth: " + savedEntry.getEndMonth() +
                ", StartYear: " + savedEntry.getStartYear() + ", EndYear: " + savedEntry.getEndYear());
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEntry);
    }

    // ロードマップエントリの取得 (ログインユーザーに紐づくもののみ)
    @GetMapping
    public List<RoadmapEntry> getAllRoadmapEntries() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("DEBUG (Controller - GET): Authentication object: " + authentication);

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            System.out
                    .println("DEBUG (Controller - GET): User not authenticated or is anonymous. Returning empty list.");
            return Collections.emptyList();
        }

        String username = authentication.getName();
        System.out.println("DEBUG (Controller - GET): Authenticated username: " + username);
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);

        if (currentUserOptional.isEmpty()) {
            System.out.println("DEBUG (Controller - GET): AppUser not found for username: " + username
                    + ". Returning empty list.");
            return Collections.emptyList();
        }

        Long currentUserId = currentUserOptional.get().getId();
        System.out.println("DEBUG (Controller - GET): AppUser ID found: " + currentUserId);

        List<RoadmapEntry> entries = roadmapEntryRepository.findByUser_Id(currentUserId);
        System.out.println(
                "DEBUG (Controller - GET): Found " + entries.size() + " roadmap entries for user ID: " + currentUserId);
        return entries;
    }

    // ロードマップエントリの更新
    @PutMapping("/{id}")
    public ResponseEntity<RoadmapEntry> updateRoadmapEntry(@PathVariable Long id,
            @RequestBody RoadmapEntry roadmapEntryDetails) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("DEBUG (Controller - PUT): Authentication object: " + authentication);

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            System.out.println(
                    "DEBUG (Controller - PUT): User not authenticated or is anonymous. Returning UNAUTHORIZED.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = authentication.getName();
        System.out.println("DEBUG (Controller - PUT): Authenticated username: " + username);
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);
        if (currentUserOptional.isEmpty()) {
            System.out.println(
                    "DEBUG (Controller - PUT): AppUser not found for username: " + username + ". Returning FORBIDDEN.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        AppUser currentUser = currentUserOptional.get(); // ユーザー情報を取得
        Long currentUserId = currentUser.getId(); // ユーザーIDを取得

        System.out.println("DEBUG (Controller - PUT): AppUser ID found: " + currentUserId);

        Optional<RoadmapEntry> existingEntryOptional = roadmapEntryRepository.findById(id);

        if (existingEntryOptional.isEmpty()) {
            System.out.println(
                    "DEBUG (Controller - PUT): RoadmapEntry with ID " + id + " not found. Returning NOT_FOUND.");
            return ResponseEntity.notFound().build();
        }

        RoadmapEntry existingEntry = existingEntryOptional.get();

        if (!existingEntry.getUser().getId().equals(currentUserId)) {
            System.out.println("DEBUG (Controller - PUT): User " + username + " (ID: " + currentUserId
                    + ") is not owner of entry ID " + id + ". Returning FORBIDDEN.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // ★追加: RoadmapCategory の保存/更新ロジック（更新時も同様に）
        String combinedName = roadmapEntryDetails.getCategoryName() + " - " + roadmapEntryDetails.getTaskName();

        Optional<RoadmapCategory> existingCategory = roadmapCategoryRepository.findByUser_IdAndName(
                currentUserId, combinedName);

        if (existingCategory.isEmpty()) {
            // 存在しない場合は新しく作成して保存
            RoadmapCategory newRoadmapCategory = new RoadmapCategory(currentUser, combinedName);
            roadmapCategoryRepository.save(newRoadmapCategory);
            System.out.println("DEBUG (Controller - PUT): New RoadmapCategory saved: Name='"
                    + newRoadmapCategory.getName() + "' for User ID=" + currentUser.getId());
        } else {
            // 存在する場合はupdated_atを更新
            RoadmapCategory existing = existingCategory.get();
            existing.setUpdatedAt(LocalDateTime.now());
            roadmapCategoryRepository.save(existing);
            System.out.println("DEBUG (Controller - PUT): Existing RoadmapCategory updated (updated_at): Name='"
                    + combinedName + "' for User ID=" + currentUserId);
        }

        existingEntry.setCategoryName(roadmapEntryDetails.getCategoryName());
        existingEntry.setTaskName(roadmapEntryDetails.getTaskName());
        existingEntry.setStartMonth(roadmapEntryDetails.getStartMonth());
        existingEntry.setEndMonth(roadmapEntryDetails.getEndMonth());
        existingEntry.setStartYear(roadmapEntryDetails.getStartYear());
        existingEntry.setEndYear(roadmapEntryDetails.getEndYear());

        RoadmapEntry updatedEntry = roadmapEntryRepository.save(existingEntry);
        System.out.println("DEBUG (Controller - PUT): RoadmapEntry ID " + updatedEntry.getId() + " updated.");
        return new ResponseEntity<>(updatedEntry, HttpStatus.OK);
    }

    // ロードマップエントリの削除
    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> deleteRoadmapEntry(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("DEBUG (Controller - DELETE): Authentication object: " + authentication);

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            System.out.println(
                    "DEBUG (Controller - DELETE): User not authenticated or is anonymous. Returning UNAUTHORIZED.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = authentication.getName();
        System.out.println("DEBUG (Controller - DELETE): Authenticated username: " + username);
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);
        if (currentUserOptional.isEmpty()) {
            System.out.println("DEBUG (Controller - DELETE): AppUser not found for username: " + username
                    + ". Returning FORBIDDEN.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Long currentUserId = currentUserOptional.get().getId();
        System.out.println("DEBUG (Controller - DELETE): AppUser ID found: " + currentUserId);

        Optional<RoadmapEntry> entryToDeleteOptional = roadmapEntryRepository.findById(id);

        if (entryToDeleteOptional.isEmpty()) {
            System.out.println(
                    "DEBUG (Controller - DELETE): RoadmapEntry with ID " + id + " not found. Returning NOT_FOUND.");
            return ResponseEntity.notFound().build();
        }

        RoadmapEntry entryToDelete = entryToDeleteOptional.get();

        if (!entryToDelete.getUser().getId().equals(currentUserId)) {
            System.out.println("DEBUG (Controller - DELETE): User " + username + " (ID: " + currentUserId
                    + ") is not owner of entry ID " + id + ". Returning FORBIDDEN.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        roadmapEntryRepository.delete(entryToDelete);
        System.out.println("DEBUG (Controller - DELETE): RoadmapEntry ID " + id + " deleted.");
        return ResponseEntity.noContent().build();
    }
}
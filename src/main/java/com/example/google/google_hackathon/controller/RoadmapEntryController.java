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
        System.out.println("DEBUG (Controller - POST): Authentication object: " + authentication); // デバッグログ

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            System.out.println(
                    "DEBUG (Controller - POST): User not authenticated or is anonymous. Returning UNAUTHORIZED."); // デバッグログ
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();
        System.out.println("DEBUG (Controller - POST): Authenticated username: " + username); // デバッグログ
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);

        if (currentUserOptional.isEmpty()) {
            System.out.println("DEBUG (Controller - POST): AppUser not found for username: " + username
                    + ". Returning FORBIDDEN."); // デバッグログ
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        roadmapEntry.setUser(currentUserOptional.get());
        RoadmapEntry savedEntry = roadmapEntryRepository.save(roadmapEntry);
        System.out.println("DEBUG (Controller - POST): RoadmapEntry saved with ID: " + savedEntry.getId()); // デバッグログ
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEntry);
    }

    // ロードマップエントリの取得 (ログインユーザーに紐づくもののみ)
    @GetMapping
    public List<RoadmapEntry> getAllRoadmapEntries() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("DEBUG (Controller - GET): Authentication object: " + authentication); // デバッグログ

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            System.out
                    .println("DEBUG (Controller - GET): User not authenticated or is anonymous. Returning empty list."); // デバッグログ
            return Collections.emptyList();
        }

        String username = authentication.getName();
        System.out.println("DEBUG (Controller - GET): Authenticated username: " + username); // デバッグログ
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);

        if (currentUserOptional.isEmpty()) {
            System.out.println("DEBUG (Controller - GET): AppUser not found for username: " + username
                    + ". Returning empty list."); // デバッグログ
            return Collections.emptyList();
        }

        Long currentUserId = currentUserOptional.get().getId();
        System.out.println("DEBUG (Controller - GET): AppUser ID found: " + currentUserId); // デバッグログ

        List<RoadmapEntry> entries = roadmapEntryRepository.findByUser_Id(currentUserId);
        System.out.println(
                "DEBUG (Controller - GET): Found " + entries.size() + " roadmap entries for user ID: " + currentUserId); // デバッグログ
        return entries;
    }

    // ロードマップエントリの更新
    @PutMapping("/{id}")
    public ResponseEntity<RoadmapEntry> updateRoadmapEntry(@PathVariable Long id,
            @RequestBody RoadmapEntry roadmapEntryDetails) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("DEBUG (Controller - PUT): Authentication object: " + authentication); // デバッグログ

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            System.out.println(
                    "DEBUG (Controller - PUT): User not authenticated or is anonymous. Returning UNAUTHORIZED."); // デバッグログ
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = authentication.getName();
        System.out.println("DEBUG (Controller - PUT): Authenticated username: " + username); // デバッグログ
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);
        if (currentUserOptional.isEmpty()) {
            System.out.println(
                    "DEBUG (Controller - PUT): AppUser not found for username: " + username + ". Returning FORBIDDEN."); // デバッグログ
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Long currentUserId = currentUserOptional.get().getId();
        System.out.println("DEBUG (Controller - PUT): AppUser ID found: " + currentUserId); // デバッグログ

        Optional<RoadmapEntry> existingEntryOptional = roadmapEntryRepository.findById(id);

        if (existingEntryOptional.isEmpty()) {
            System.out.println(
                    "DEBUG (Controller - PUT): RoadmapEntry with ID " + id + " not found. Returning NOT_FOUND."); // デバッグログ
            return ResponseEntity.notFound().build(); // 404 Not Found
        }

        RoadmapEntry existingEntry = existingEntryOptional.get();

        if (!existingEntry.getUser().getId().equals(currentUserId)) {
            System.out.println("DEBUG (Controller - PUT): User " + username + " (ID: " + currentUserId
                    + ") is not owner of entry ID " + id + ". Returning FORBIDDEN."); // デバッグログ
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403 Forbidden
        }

        existingEntry.setCategoryName(roadmapEntryDetails.getCategoryName());
        existingEntry.setTaskName(roadmapEntryDetails.getTaskName());
        existingEntry.setStartMonth(roadmapEntryDetails.getStartMonth());
        existingEntry.setEndMonth(roadmapEntryDetails.getEndMonth());
        RoadmapEntry updatedEntry = roadmapEntryRepository.save(existingEntry);
        System.out.println("DEBUG (Controller - PUT): RoadmapEntry ID " + updatedEntry.getId() + " updated."); // デバッグログ
        return new ResponseEntity<>(updatedEntry, HttpStatus.OK); // 200 OK
    }

    // ロードマップエントリの削除
    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> deleteRoadmapEntry(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("DEBUG (Controller - DELETE): Authentication object: " + authentication); // デバッグログ

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            System.out.println(
                    "DEBUG (Controller - DELETE): User not authenticated or is anonymous. Returning UNAUTHORIZED."); // デバッグログ
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = authentication.getName();
        System.out.println("DEBUG (Controller - DELETE): Authenticated username: " + username); // デバッグログ
        Optional<AppUser> currentUserOptional = appUserRepository.findByUsername(username);
        if (currentUserOptional.isEmpty()) {
            System.out.println("DEBUG (Controller - DELETE): AppUser not found for username: " + username
                    + ". Returning FORBIDDEN."); // デバッグログ
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Long currentUserId = currentUserOptional.get().getId();
        System.out.println("DEBUG (Controller - DELETE): AppUser ID found: " + currentUserId); // デバッグログ

        Optional<RoadmapEntry> entryToDeleteOptional = roadmapEntryRepository.findById(id);

        if (entryToDeleteOptional.isEmpty()) {
            System.out.println(
                    "DEBUG (Controller - DELETE): RoadmapEntry with ID " + id + " not found. Returning NOT_FOUND."); // デバッグログ
            return ResponseEntity.notFound().build(); // 404 Not Found
        }

        RoadmapEntry entryToDelete = entryToDeleteOptional.get();

        if (!entryToDelete.getUser().getId().equals(currentUserId)) {
            System.out.println("DEBUG (Controller - DELETE): User " + username + " (ID: " + currentUserId
                    + ") is not owner of entry ID " + id + ". Returning FORBIDDEN."); // デバッグログ
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403 Forbidden
        }

        roadmapEntryRepository.delete(entryToDelete);
        System.out.println("DEBUG (Controller - DELETE): RoadmapEntry ID " + id + " deleted."); // デバッグログ
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
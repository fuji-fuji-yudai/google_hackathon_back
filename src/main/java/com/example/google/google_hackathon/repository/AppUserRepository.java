package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * AppUserRepository は AppUser エンティティに対応する
 * データベース操作（CRUD）を行うためのインターフェースです。
 * 
 * JpaRepository を継承しているため、標準的な操作（findAll, save, delete など）が自動で使えます。
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * ユーザー名でユーザーを検索するためのカスタムクエリメソッド。
     * メソッド名から自動で SQL が生成されます。
     * 
     * 例：
     * Optional<AppUser> user = appUserRepository.findByUsername("admin");
     */
    Optional<AppUser> findByUsername(String username);
}

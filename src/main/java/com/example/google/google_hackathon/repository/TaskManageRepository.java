package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository // Repository層としてDI可能にする
public interface TaskManageRepository extends JpaRepository<Task, Long> {
    // JpaRepositoryを使うとfindAll(), saveAll(), findById() などが自動で使える
}
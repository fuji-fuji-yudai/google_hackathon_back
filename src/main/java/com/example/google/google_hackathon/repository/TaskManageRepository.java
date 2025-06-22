package com.example.google.google_hackathon.repository;

import com.example.google.google_hackathon.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskManageRepository extends JpaRepository<Task, Integer> {
    // 修正: フィールド名を正確に使用
    List<Task> findByParentId(Integer parentId);
    
    // 修正: 正確なフィールド名を使用
    List<Task> findByParentIdIsNull();
}
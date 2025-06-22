package com.example.google.google_hackathon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tasks")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    private String title;
    private String assignee;
    private String plan_start;
    private String plan_end;
    private String actual_start;
    private String actual_end;
    private String status;
    
    // フィールド名を JPA 命名規則に合わせて修正
    // データベースのカラム名は親クラスとの関係を明確にするため snake_case のままにする
    @Column(name = "parent_id")
    private Integer parentId;
    
    // ゲッター・セッター
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getAssignee() {
        return assignee;
    }
    
    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }
    
    public String getPlan_start() {
        return plan_start;
    }
    
    public void setPlan_start(String plan_start) {
        this.plan_start = plan_start;
    }
    
    public String getPlan_end() {
        return plan_end;
    }
    
    public void setPlan_end(String plan_end) {
        this.plan_end = plan_end;
    }
    
    public String getActual_start() {
        return actual_start;
    }
    
    public void setActual_start(String actual_start) {
        this.actual_start = actual_start;
    }
    
    public String getActual_end() {
        return actual_end;
    }
    
    public void setActual_end(String actual_end) {
        this.actual_end = actual_end;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Integer getParentId() {
        return parentId;
    }
    
    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }
}
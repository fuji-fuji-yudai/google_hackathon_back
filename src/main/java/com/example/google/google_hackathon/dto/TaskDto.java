package com.example.google.google_hackathon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskDto {
    public Integer id;
    public String title;
    public String assignee;
    public String plan_start;
    public String plan_end;
    public String actual_start;
    public String actual_end;
    public String status;
    
    // 親タスクのID（親子関係のサポート）
    @JsonProperty("parent_id")
    public Integer parent_id;
    
    // フロントエンド側のcamelCase互換性のため
    @JsonProperty("parentId")
    public Integer getParentId() {
        return parent_id;
    }
    
    @JsonProperty("parentId")
    public void setParentId(Integer parentId) {
        this.parent_id = parentId;
    }
}
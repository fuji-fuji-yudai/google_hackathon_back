package com.example.google.google_hackathon.dto;

import com.example.google.google_hackathon.entity.Room;

public class RoomDTO {
    private Long id;
    private String index;
    private String title;
    private String parentIndex;

    public RoomDTO(Room room) {
        this.id = room.getId();
        this.index = room.getIndex();
        this.title = room.getTitle();
        this.parentIndex = room.getParent() != null ? room.getParent().getIndex() : null;
    }

    public Long getId() {
        return id;
    }

    public String getIndex() {
        return index;
    }

    public String getTitle() {
        return title;
    }

    public String getParentIndex() {
        return parentIndex;
    }
}

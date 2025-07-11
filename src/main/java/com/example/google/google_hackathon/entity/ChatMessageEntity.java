package com.example.google.google_hackathon.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.example.google.google_hackathon.listener.ChatMessageListener;
import com.fasterxml.jackson.annotation.JsonFormat;

@EntityListeners(ChatMessageListener.class)
@Entity
@Table(name = "chat_messages",schema = "public")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sender;
    private String text;
    private String roomId;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;


    // --- Getters ---
    public Long getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public String getRoomId() {
        return roomId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    // --- Setters ---
    public void setId(Long id) {
        this.id = id;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

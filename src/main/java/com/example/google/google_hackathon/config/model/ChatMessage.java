package com.example.google.google_hackathon.config.model;



public class ChatMessage {
    private String sender;
    private String text;
    private String roomId;

    // コンストラクタ（必要に応じて）
    public ChatMessage() {}

    public ChatMessage(String sender, String text, String roomId) {
    this.sender = sender;
    this.text = text;
    this.roomId = roomId;
    }

    // ゲッター
    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public String getRoomId() {
        return roomId;
    }

    // セッター
    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
}


package com.example.google.google_hackathon.dto;

//ログイン時にログイン情報をリクエスト
public class LoginRequest {
    private String username;
    private String password;

    // デフォルトコンストラクタ（必須）
    public LoginRequest() {}

    // ゲッターとセッター
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}


package com.example.google.google_hackathon.dto;

//認証成功時にjwtを返す（ユーザー情報が入っている。）
public class JwtResponse {
    private String token;

    public JwtResponse(String token) {
    this.token = token;
    }

    public String getToken() {
    return token;
    }
}

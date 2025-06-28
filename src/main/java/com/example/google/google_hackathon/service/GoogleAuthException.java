package com.example.google.google_hackathon.service; // service パッケージに配置します

/**
 * Google OAuth2 認証時に、追加の権限要求や再認証が必要になった場合にスローされるカスタム例外です。
 * フロントエンドへのリダイレクトURLを保持します。
 */
public class GoogleAuthException extends RuntimeException {
    private final String authUrl; // Google認証画面へのリダイレクトURL

    public GoogleAuthException(String message, String authUrl) {
        super(message);
        this.authUrl = authUrl;
    }

    public String getAuthUrl() {
        return authUrl;
    }
}
package com.example.google.google_hackathon.security;

import java.util.Date;
import java.nio.charset.StandardCharsets;
import java.security.Key;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Component
public class JwtTokenProvider {

    private final String jwtSecret = "a-very-long-and-secure-secret-key-that-is-at-least-64-bytes-long-1234567890!@#$%^&*()"; // 適切な長さの秘密鍵を使用してください
    private final long jwtExpirationMs = 86400000; // 1日（ミリ秒）

    // 🔐 署名用のキーを生成
    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, SignatureAlgorithm.HS512.getJcaName());
    }

    // ✅ トークン生成
    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        return Jwts.builder()
            .setSubject(userPrincipal.getUsername())
            .setIssuedAt(new Date())
            .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
            .signWith(getSigningKey(), SignatureAlgorithm.HS512)
            .compact();
    }

    // ✅ トークンからユーザー名を取得
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody()
            .getSubject();
    }

    // ✅ トークンの有効期限をチェック
    private boolean isTokenExpired(String token) {
        Date expiration = Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody()
            .getExpiration();

            return expiration.before(new Date()); // ← これを追加！

        }

    // ✅ トークンの検証
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}




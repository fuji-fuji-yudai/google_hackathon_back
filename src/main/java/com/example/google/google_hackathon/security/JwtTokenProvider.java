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
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
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
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            logger.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty: {}", ex.getMessage());
        } catch (Exception ex) {
            logger.error("An unexpected error occurred while getting username from token", ex);
        }
        return null; // エラーが発生した場合はnullを返す
    }

    // ✅ トークンの有効期限をチェック
    private boolean isTokenExpired(String token) {
        try {
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();

            return expiration.before(new Date());
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature during expiration check: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token during expiration check: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            logger.error("Expired JWT token during expiration check, but it's still an expired token: {}",
                    ex.getMessage());
            return true; // 期限切れの場合はtrueを返す
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token during expiration check: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty during expiration check: {}", ex.getMessage());
        } catch (Exception ex) {
            logger.error("An unexpected error occurred during token expiration check", ex);
        }
        return true; // 例外が発生した場合は、安全のため有効期限切れ（無効）と見なす
    }

    // ✅ トークンの検証
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token); // ここで例外はキャッチされる
        if (username == null) {
            return false; // ユーザー名が取得できない（検証失敗）
        }

        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}

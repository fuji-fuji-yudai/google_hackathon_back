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

    // ✅ JWTトークンからユーザー名（サブジェクト）を取得します
    public String getUsernameFromToken(String token) {
        System.out.println("DEBUG (JwtTokenProvider): Attempting to get username from token.");
        // ★新規追加: トークンの一部を出力します。トークン全体は機密情報のため、一部のみ表示。
        System.out.println("DEBUG (JwtTokenProvider): Token (first 50 chars): "
                + token.substring(0, Math.min(token.length(), 50)) + "...");
        try {
            String username = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey()) // 署名キーを設定
                    .build()
                    .parseClaimsJws(token) // JWTをパースしてJWS（署名付きJWT）として検証
                    .getBody() // クレーム（ペイロード）を取得
                    .getSubject(); // サブジェクト（ユーザー名）を取得
            System.out.println("DEBUG (JwtTokenProvider): Successfully extracted username: " + username);
            return username;
        } catch (SignatureException ex) {
            // ★新規追加: 無効なJWT署名の場合のログとスタックトレース出力
            System.err.println("ERROR (JwtTokenProvider): Invalid JWT signature: " + ex.getMessage());
            ex.printStackTrace();
        } catch (MalformedJwtException ex) {
            // ★新規追加: JWTの形式が不正な場合のログとスタックトレース出力
            System.err.println("ERROR (JwtTokenProvider): Invalid JWT token: " + ex.getMessage());
            ex.printStackTrace();
        } catch (ExpiredJwtException ex) {
            // ★新規追加: JWTの有効期限が切れている場合のログとスタックトレース出力
            System.err.println("ERROR (JwtTokenProvider): Expired JWT token: " + ex.getMessage());
            ex.printStackTrace();
        } catch (UnsupportedJwtException ex) {
            // ★新規追加: 未サポートのJWTトークンの場合のログとスタックトレース出力
            System.err.println("ERROR (JwtTokenProvider): Unsupported JWT token: " + ex.getMessage());
            ex.printStackTrace();
        } catch (IllegalArgumentException ex) {
            // ★新規追加: JWTクレーム文字列が空の場合などのログとスタックトレース出力
            System.err.println("ERROR (JwtTokenProvider): JWT claims string is empty: " + ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            System.err.println(
                    "ERROR (JwtTokenProvider): An unexpected error occurred while getting username from token: "
                            + ex.getMessage());
            ex.printStackTrace();
        }
        System.out.println("DEBUG (JwtTokenProvider): Failed to get username from token. Returning null.");
        return null;
    }

    // ✅ トークンの有効期限をチェック
    private boolean isTokenExpired(String token) {
        System.out.println("DEBUG (JwtTokenProvider): Checking if token is expired."); // ★ここ
        try {
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();

            boolean expired = expiration.before(new Date());
            System.out.println(
                    "DEBUG (JwtTokenProvider): Token expiration status: " + (expired ? "EXPIRED" : "NOT EXPIRED")); // ★ここ
            return expired;
        } catch (SignatureException ex) {
            System.err.println(
                    "ERROR (JwtTokenProvider): Invalid JWT signature during expiration check: " + ex.getMessage()); // ★ここ
        } catch (MalformedJwtException ex) {
            System.err
                    .println("ERROR (JwtTokenProvider): Invalid JWT token during expiration check: " + ex.getMessage()); // ★ここ
        } catch (ExpiredJwtException ex) {
            System.err.println(
                    "ERROR (JwtTokenProvider): Expired JWT token during expiration check, but it's still an expired token: "
                            + ex.getMessage());
            return true; // 期限切れの場合はtrueを返す
        } catch (UnsupportedJwtException ex) {
            System.err.println(
                    "ERROR (JwtTokenProvider): Unsupported JWT token during expiration check: " + ex.getMessage()); // ★ここ
        } catch (IllegalArgumentException ex) {
            System.err.println(
                    "ERROR (JwtTokenProvider): JWT claims string is empty during expiration check: " + ex.getMessage()); // ★ここ
        } catch (Exception ex) {
            System.err.println("ERROR (JwtTokenProvider): An unexpected error occurred during token expiration check: "
                    + ex.getMessage());
            ex.printStackTrace(); // スタックトレースも出力
        }
        System.out.println(
                "DEBUG (JwtTokenProvider): Failed to check token expiration. Returning true (expired/invalid)."); // ★ここ
        return true; // 例外が発生した場合は、安全のため有効期限切れ（無効）と見なす
    }

    // ✅ トークンの検証
    public boolean validateToken(String token, UserDetails userDetails) {
        System.out.println("DEBUG (JwtTokenProvider): Validating token for user: "
                + (userDetails != null ? userDetails.getUsername() : "null")); // ★ここ
        final String username = getUsernameFromToken(token);
        if (username == null) {
            System.out.println("DEBUG (JwtTokenProvider): Username could not be extracted. Token validation failed."); // ★ここ
            return false;
        }

        boolean tokenValid = username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        System.out.println("DEBUG (JwtTokenProvider): Final token validation result: " + tokenValid); // ★ここ
        return tokenValid;
    }
}

package com.example.google.google_hackathon.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;

import javax.crypto.spec.SecretKeySpec;

public class JwtUtil {
    private static final String SECRET_KEY = "a-very-long-and-secure-secret-key-that-is-at-least-64-bytes-long-1234567890!@#$%^&*()"; // 32文字以上
    

    
    public static boolean validateToken(String token) {
    try {
        Key key = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        Jwts.parserBuilder().setSigningKey(key).build()
.parseClaimsJws(token);
    return true;
    } catch (Exception e) {
        return false;
    }
    }


public static String getUsernameFromToken(String token) {
 Key key = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA512");

 return Jwts.parserBuilder()
 .setSigningKey(key)
 .build()
 .parseClaimsJws(token)
 .getBody()
 .getSubject();
}

}


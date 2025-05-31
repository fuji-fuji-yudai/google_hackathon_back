package com.example.google.google_hackathon.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.security.Key;

public class JwtUtil {
    private static final String SECRET_KEY = "a-very-long-and-secure-secret-key-that-is-at-least-64-bytes-long-1234567890!@#$%^&*()"; // 32文字以上

    
    public static boolean validateToken(String token) {
    try {
    Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token);
    return true;
    } catch (Exception e) {
        return false;
    }
    }

public static String getUsernameFromToken(String token) {
return Jwts.parser()
            .setSigningKey(SECRET_KEY)
            .parseClaimsJws(token)
            .getBody()
            .getSubject();
 }
}


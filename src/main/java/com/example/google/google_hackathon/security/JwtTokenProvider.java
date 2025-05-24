package com.example.google.google_hackathon.security;

import java.util.Date;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;


@Component
public class JwtTokenProvider {

    private final String jwtSecret = "your-secret-key";
    private final long jwtExpirationMs = 86400000; // 1日

    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        return Jwts.builder()
            .setSubject(userPrincipal.getUsername())
            .setIssuedAt(new Date())
            .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
            .signWith(SignatureAlgorithm.HS512, jwtSecret)
            .compact();
    }
    public String getUsernameFromToken(String token) {
    return Jwts.parser()
        .setSigningKey(jwtSecret)
        .parseClaimsJws(token)
        .getBody()
        .getSubject();
    }

    private boolean isTokenExpired(String token) {
    Date expiration = Jwts.parser()
        .setSigningKey(jwtSecret)
        .parseClaimsJws(token)
        .getBody()
        .getExpiration();

    return expiration.before(new Date());
    }


    public boolean validateToken(String token, UserDetails userDetails) {
    final String username = getUsernameFromToken(token);
    return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    // private boolean isToken Jwts.parser()
    //         .setSigningKey(jwtSecret)
    //         .parseClaimsJws(token)
    //         .getBody()
    //         .getExpiration();
    //     return expiration.before(new Date());
}



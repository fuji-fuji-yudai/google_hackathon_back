package com.example.google.google_hackathon.interceptor;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.security.Principal;

import com.example.google.google_hackathon.security.JwtUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Override
public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Map<String, Object> attributes) {
    List<String> authHeaders = request.getHeaders().get("Authorization");
    if (authHeaders != null && !authHeaders.isEmpty()) {
        String token = authHeaders.get(0).replace("Bearer ", "");
        if (JwtUtil.validateToken(token)) {
            String username = JwtUtil.getUsernameFromToken(token);
            Principal principal = new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
            attributes.put("principal", principal); // 👈 ここが重要！
            return true;
        }
    }
    return false; // トークンが無効な場合は接続拒否
}


    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 何もしない
    }
}

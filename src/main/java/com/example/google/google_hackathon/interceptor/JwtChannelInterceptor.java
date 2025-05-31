package com.example.google.google_hackathon.interceptor;

import java.util.Collections;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.example.google.google_hackathon.security.JwtUtil;

public class JwtChannelInterceptor implements ChannelInterceptor {

    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            System.out.println("🔐 Authorization ヘッダー: " + token);

            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                if (JwtUtil.validateToken(token)) {
                    String username = JwtUtil.getUsernameFromToken(token);

                    // Spring Security が認識できる Principal をセット
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());

                    accessor.setUser(authentication);
                    System.out.println("✅ Principal セット: " + username);
                } else {
                    System.out.println("❌ トークン検証失敗");
                }
            } else {
                System.out.println("⚠️ Authorization ヘッダーが無効または存在しない");
            }
        }

        return message;
    }
}


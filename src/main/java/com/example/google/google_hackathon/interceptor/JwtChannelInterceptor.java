package com.example.google.google_hackathon.interceptor;

import java.security.Principal;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.example.google.google_hackathon.security.JwtUtil;

public class JwtChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            System.out.println("🔐 Authorization ヘッダー: " + token);
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                if (JwtUtil.validateToken(token)) {
                    String username = JwtUtil.getUsernameFromToken(token);
                    Principal userPrincipal = new StompPrincipal(username);
                    accessor.setUser(userPrincipal);
                }
            }
        }
        
        return message;
        
    }   


        // Principal 実装クラス
        private static class StompPrincipal implements Principal {
            private final String name;

            public StompPrincipal(String name) {
            this.name = name;
            }

            @Override
            public String getName() {
            return name;
            }
        }
    
}

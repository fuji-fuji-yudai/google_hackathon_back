package com.example.google.google_hackathon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

import com.example.google.google_hackathon.interceptor.JwtChannelInterceptor;
import com.example.google.google_hackathon.interceptor.JwtHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic"); // クライアントが購読する宛先
    config.setApplicationDestinationPrefixes("/app"); // クライアントが送信する宛先
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws") // Vue 側と一致させる
            .addInterceptors(new JwtHandshakeInterceptor())
            .setAllowedOriginPatterns("*") // CORS 対応
            .withSockJS(); // SockJS を有効化
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new JwtChannelInterceptor());
  }

}

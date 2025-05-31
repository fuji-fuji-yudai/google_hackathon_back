package com.example.google.google_hackathon.config;

import java.security.Principal;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

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
    registry.addEndpoint("/ws")
            .setHandshakeHandler(new DefaultHandshakeHandler() {
                @Override
                protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                                  Map<String, Object> attributes) {
                    return (Principal) attributes.get("principal"); // 👈 beforeHandshakeで設定したPrincipalを返す
                }
            })
            //.addInterceptors(new JwtHandshakeInterceptor())
            .setAllowedOriginPatterns("*")
            .withSockJS();
}


  // @Override
  // public void configureClientInboundChannel(ChannelRegistration registration) {
  //   registration.interceptors(new JwtChannelInterceptor());
  // }

}

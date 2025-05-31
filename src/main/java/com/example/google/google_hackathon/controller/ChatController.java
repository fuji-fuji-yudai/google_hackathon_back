package com.example.google.google_hackathon.controller;
import java.security.Principal;

import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.google.google_hackathon.config.model.ChatMessage;

@Controller
public class ChatController {

  private final SimpMessagingTemplate messagingTemplate;

  public ChatController(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @MessageMapping("/chat/{roomId}")
public void sendMessage(@Payload ChatMessage message, Message<?> rawMessage) {
    SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(rawMessage);
    Principal principal = accessor.getUser();

    System.out.println("📨 メッセージ受信: " + message.getText());
    System.out.println("Principal from header: " + (principal != null ? principal.getName() : "null"));

    if (principal != null) {
        message.setSender(principal.getName());
    } else {
        message.setSender("anonymous");
    }

    messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId(), message);
}
}
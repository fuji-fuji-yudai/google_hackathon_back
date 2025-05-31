package com.example.google.google_hackathon.controller;
import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.google.google_hackathon.config.model.ChatMessage;

@Controller
public class ChatController {

  private final SimpMessagingTemplate messagingTemplate;

  public ChatController(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @MessageMapping("/chat/{roomId}") // /app/chat/{roomId} に対応
  public void sendMessage(@Payload ChatMessage message,Principal principal) {
    System.out.println("📨 メッセージ受信: " + message.getText());
    System.out.println("Principal: " + (principal != null ? principal.getName() : "null"));
    message.setSender(principal.getName());
    // DB保存処理をここに追加してもOK
    messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId(), message);
  }

}
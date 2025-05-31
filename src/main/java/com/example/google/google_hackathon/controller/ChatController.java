package com.example.google.google_hackathon.controller;
import java.security.Principal;

import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.google.google_hackathon.config.model.ChatMessage;
import com.example.google.google_hackathon.security.JwtUtil;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/{roomId}")
    public void sendMessage(@Header("Authorization") String authHeader,
                            @Payload ChatMessage message,
                            @DestinationVariable String roomId) {
        String token = authHeader.replace("Bearer ", "");
        System.out.println("トークンはこちら！！"+token);
        if (JwtUtil.validateToken(token)) {
            String username = JwtUtil.getUsernameFromToken(token);
            message.setSender(username);
        } else {
            message.setSender("anonymous");
        }

        messagingTemplate.convertAndSend("/topic/chat/" + roomId, message);
    }
}

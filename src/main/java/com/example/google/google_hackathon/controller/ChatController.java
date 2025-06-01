package com.example.google.google_hackathon.controller;
import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.example.google.google_hackathon.config.model.ChatMessage;
import com.example.google.google_hackathon.entity.ChatMessageEntity;
import com.example.google.google_hackathon.security.JwtUtil;
import com.example.google.google_hackathon.service.ChatMessageService;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageService ChatMessageService;

    public ChatController(SimpMessagingTemplate messagingTemplate,ChatMessageService chatMessageService) {
        this.messagingTemplate = messagingTemplate;
        this.ChatMessageService = chatMessageService;
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
        message.setRoomId(roomId);
        ChatMessageService.saveMessage(message);
        //クライアントに送信
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, message);
    }

    @GetMapping("/chat/history/{roomId}")
public ResponseEntity<List<ChatMessageEntity>> getChatHistory(@PathVariable String roomId) {
    List<ChatMessageEntity> messages = ChatMessageService.getMessages(roomId);
    return ResponseEntity.ok(messages); // 常に200 OKで返す

}

}

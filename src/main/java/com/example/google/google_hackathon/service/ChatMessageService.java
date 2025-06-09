package com.example.google.google_hackathon.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import com.example.google.google_hackathon.config.model.ChatMessage;
import com.example.google.google_hackathon.entity.ChatMessageEntity;
import com.example.google.google_hackathon.repository.ChatMessageRepository;

@Service
public class ChatMessageService {

    private final ChatMessageRepository repository;

    public ChatMessageService(ChatMessageRepository repository) {
        this.repository = repository;
    }

    public void saveMessage(ChatMessage message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSender(message.getSender());
        entity.setText(message.getText());
        entity.setRoomId(message.getRoomId());
        entity.setTimestamp(LocalDateTime.now());
        repository.save(entity);
    }

    public List<ChatMessageEntity> getMessages(String roomId) {
        return repository.findByRoomIdOrderByTimestampAsc(roomId);
    }
}


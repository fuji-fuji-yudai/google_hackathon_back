package com.example.google.google_hackathon.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.google.google_hackathon.entity.ChatMessageEntity;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByRoomIdOrderByTimestampAsc(String roomId);
}

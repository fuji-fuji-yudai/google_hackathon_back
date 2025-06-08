package com.example.google.google_hackathon.listener;

import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.google.google_hackathon.entity.ChatMessageEntity;
import com.example.google.google_hackathon.service.FirestoreService;
import com.example.google.google_hackathon.service.VertexAIService;

import jakarta.persistence.PostPersist;

@Component
public class ChatMessageListener {
    private static final Logger logger = LoggerFactory.getLogger(ChatMessageListener.class);

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    private VertexAIService vertexAIService;

    @PostPersist
    public void onPostPersist(ChatMessageEntity message) {
        try {
            // Vertex AI でベクトル生成（後述）
            List<Double> embedding = vertexAIService.generateEmbedding(message.getText());

            // Firestore に保存
            firestoreService.saveChatMessageWithEmbedding(message, embedding);
            
            logger.info("Firestore 保存成功");
        } catch (Exception e) {
            System.err.println("Firestore保存失敗: " + e.getMessage());
            logger.error("Firestore保存失敗", e);

        }
    }
}


package com.example.google.google_hackathon.listener;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.google.google_hackathon.entity.ChatMessageEntity;
import com.example.google.google_hackathon.service.FirestoreService;
import com.example.google.google_hackathon.service.VertexAIService;

import jakarta.persistence.PostPersist;

@Component
public class ChatMessageListener {

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
        } catch (Exception e) {
            System.err.println("Firestore保存失敗: " + e.getMessage());
        }
    }
}


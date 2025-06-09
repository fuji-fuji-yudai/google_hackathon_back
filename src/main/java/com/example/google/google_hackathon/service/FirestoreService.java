package com.example.google.google_hackathon.service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import com.example.google.google_hackathon.entity.ChatMessageEntity;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.Timestamp;

@Service
public class FirestoreService {

    private final Firestore db;

    public FirestoreService() throws IOException {
        FirestoreOptions firestoreOptions =
            FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId("nomadic-bison-459812-a8")
                .build();
        this.db = firestoreOptions.getService();
    }

    public void saveChatMessageWithEmbedding(ChatMessageEntity message, List<Double> embedding) throws Exception {
        
        
    Instant instant = message.getTimestamp().toInstant(ZoneOffset.UTC);
    
    Timestamp firestoreTimestamp = Timestamp.ofTimeSecondsAndNanos(
    instant.getEpochSecond(),
    instant.getNano()
    );


        Map<String, Object> docData = new HashMap<>();
        docData.put("room_id", message.getRoomId());
        docData.put("message", message.getText());
        docData.put("sender", message.getSender());
        docData.put("embedding", embedding);
        docData.put("timestamp", firestoreTimestamp
        );

        db.collection("chat_embeddings").add(docData).get();
        System.out.println("保存完了: " + message.getText());
    }
}

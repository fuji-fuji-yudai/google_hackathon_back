package com.example.google.google_hackathon.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
//import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class SimilarityService {

    @Autowired
    private Firestore db;

    @Autowired
    private VertexAIService vertexAIService;

    public List<SimilarMessage> findSimilarMessages(String userQuestion, int topK) throws Exception {
        // 1. ユーザーの質問をベクトル化
        List<Double> userEmbedding = vertexAIService.generateEmbedding(userQuestion);

        // 2. Firestore から履歴を取得
        ApiFuture<QuerySnapshot> future = db.collection("chat_embeddings").get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        // 3. 類似度を計算
        List<SimilarMessage> similarMessages = new ArrayList<>();
        for (QueryDocumentSnapshot doc : documents) {
            List<Double> embedding = (List<Double>) doc.get("embedding");
            String message = (String) doc.get("message");

            double similarity = cosineSimilarity(userEmbedding, embedding);
            similarMessages.add(new SimilarMessage(message, similarity));
        }

        // 4. 類似度順にソートして上位を返す
        return similarMessages.stream()
                .sorted(Comparator.comparingDouble(SimilarMessage::getSimilarity).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(List<Double> vec1, List<Double> vec2) {
        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
        for (int i = 0; i < vec1.size(); i++) {
            dot += vec1.get(i) * vec2.get(i);
            norm1 += Math.pow(vec1.get(i), 2);
            norm2 += Math.pow(vec2.get(i), 2);
        }
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    public static class SimilarMessage {
        private final String message;
        private final double similarity;

        public SimilarMessage(String message, double similarity) {
            this.message = message;
            this.similarity = similarity;
        }

        public String getMessage() {
            return message;
        }

        public double getSimilarity() {
            return similarity;
        }
    }
}
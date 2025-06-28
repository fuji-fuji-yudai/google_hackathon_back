package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.dto.ReflectionSummaryDtoByFuji;
import com.example.google.google_hackathon.dto.SimilarMessageDTO;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.*;
import org.springframework.stereotype.Service;
//import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.text.SimpleDateFormat;
import java.util.List;
//import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    public String generateAnswer(String userQuestion, List<SimilarMessageDTO> similarMessages) {
        try {
            // アクセストークン取得
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
            credentials.refreshIfExpired();
            String accessToken = credentials.getAccessToken().getTokenValue();

            // プロンプト構築
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("以下の履歴を参考にして、質問に答えてください。\n\n");
            for (SimilarMessageDTO msg : similarMessages) {
                
                String sender = msg.sender();
                String timestamp = msg.timestamp() != null
                                   ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(msg.timestamp())
                                    : "不明な時刻";
                promptBuilder.append(String.format("- [%s %s] %s\n", timestamp, sender, msg.message()));
            }
            promptBuilder.append("\n質問: ").append(userQuestion);

            // リクエストを構築
            JsonObject requestBody = new JsonObject();
            JsonArray contents = new JsonArray();

            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", promptBuilder.toString());
            parts.add(part);
            userMessage.add("parts", parts);
            contents.add(userMessage);

            requestBody.add("contents", contents);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://us-central1-aiplatform.googleapis.com/v1/projects/nomadic-bison-459812-a8/locations/us-central1/publishers/google/models/gemini-2.0-flash-001:generateContent"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Vertex AI API error: {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Vertex AI API error: " + response.statusCode());
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray candidates = json.getAsJsonArray("candidates");

            if (candidates != null && candidates.size() > 0) {
                JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                JsonArray partsArray = content.getAsJsonArray("parts");
                return partsArray.get(0).getAsJsonObject().get("text").getAsString();
            } else {
                logger.warn("Vertex AI API returned no candidates: {}", response.body());
                return "回答を生成できませんでした。";
            }

        } catch (Exception e) {
            logger.error("Vertex AI 呼び出し中にエラーが発生しました", e);
            return "エラーが発生しました: " + e.getMessage();
        }
    }

    public String generateRoadmapProposal(String category, List<ReflectionSummaryDtoByFuji> summaries) {
    StringBuilder promptBuilder = new StringBuilder();
    promptBuilder.append("以下のカテゴリ、業務の振り返り履歴をもとに、カテゴリに合わせた自己成長・キャリアアップのための長期的な目標設計につながる今後のロードマップ作製案を、「#」や「*」といった記号を使わない、マークダウンではないが、記号を使って読みやすくした自然な日本語文章形式で簡潔に提示してください。\n\n");
    promptBuilder.append("カテゴリ: ").append(category).append("\n\n");

    for (ReflectionSummaryDtoByFuji summary : summaries) {
        promptBuilder.append("【").append(summary.getYearMonth()).append("】\n");
        promptBuilder.append("活動内容: ").append(summary.getActivitySummary()).append("\n");
        promptBuilder.append("達成事項: ").append(summary.getAchievementSummary()).append("\n");
        promptBuilder.append("改善点: ").append(summary.getImprovementSummary()).append("\n\n");
    }

    return generateAnswer(promptBuilder.toString(), List.of()); // SimilarMessageDTO は使わない
}

}

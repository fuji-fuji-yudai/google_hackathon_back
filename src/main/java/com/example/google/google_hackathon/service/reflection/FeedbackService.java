package com.example.google.google_hackathon.service.reflection;

import com.example.google.google_hackathon.dto.SimilarMessageDTO;
import com.example.google.google_hackathon.entity.FeedbackEntity;
import com.example.google.google_hackathon.entity.ReflectionEntity;
import com.example.google.google_hackathon.repository.FeedbackRepository;
import com.google.cloud.secretmanager.v1.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.auth.oauth2.GoogleCredentials;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FeedbackService {

  // private static final String GEMINI_API_URL = "https://gemini-ai.googleapis.com/v1/models/text-bison:predict";
  // private static final String SECRET_NAME = "projects/14467698004/secrets/tanaka-gemini-api/versions/latest";
  private static final Logger logger = LoggerFactory.getLogger(FeedbackService.class);
  private final FeedbackRepository feedbackRepository;
  public FeedbackService(FeedbackRepository feedbackRepository) {
    this.feedbackRepository = feedbackRepository;
  }

  /**
   * Reflection ID に基づいて FeedbackEntity を検索する
    *
    * @param reflectionId ReflectionデータのID
    * @return feedbackEntity
    * @throws Exception API呼び出し失敗時の例外
    */
  public FeedbackEntity getFeedbackByReflectionId(Long reflectionId) throws Exception {
    Optional<FeedbackEntity> feedbackEntity = feedbackRepository.findByReflectionId(reflectionId);
    return feedbackEntity.orElse(null);
  }

  /**
   * Reflectionデータを基にGoogle Cloud Geminiを呼び出してフィードバックを生成する
    *
    * @param reflectionEntity Reflectionデータ（活動内容、達成事項、改善点など）
    * @return feedbackEntity
    * @throws Exception API呼び出し失敗時の例外
    */
  public FeedbackEntity createFeedback(ReflectionEntity reflectionEntity) throws Exception {
    try {
      // アクセストークン取得
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
          .createScoped("https://www.googleapis.com/auth/cloud-platform");
      credentials.refreshIfExpired();
      String accessToken = credentials.getAccessToken().getTokenValue();

      // Reflectionデータをプロンプトとして構築
      String prompt = String.format(
        "以下のReflectionデータに基づいて、次回の活動に役立つ具体的なフィードバックを生成してください。\n\n" +
        "活動内容: %s\n達成事項: %s\n改善点: %s",
        reflectionEntity.getActivity(),
        reflectionEntity.getAchievement(),
        reflectionEntity.getImprovementPoints()
      );

      // リクエストを構築
      JsonObject requestBody = new JsonObject();
      JsonArray contents = new JsonArray();

      JsonObject userMessage = new JsonObject();
      userMessage.addProperty("role", "user");
      JsonArray parts = new JsonArray();
      JsonObject part = new JsonObject();
      part.addProperty("text", prompt);
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
        String feedbackText = partsArray.get(0).getAsJsonObject().get("text").getAsString();
        // DBにフィードバックを保存
        FeedbackEntity feedbackEntity = new FeedbackEntity(
          reflectionEntity.getId(),
          feedbackText,
          LocalDateTime.now()
        );
        feedbackRepository.save(feedbackEntity);
        return feedbackEntity;
      } else {
        logger.warn("Gemini API returned no candidates: {}", response.body());
        throw new RuntimeException("Gemini APIからフィードバックを取得できませんでした");
      }
    } catch (Exception e) {
      logger.error("Gemini API呼び出し中にエラーが発生しました", e);
      throw new RuntimeException("Gemini API呼び出し中にエラーが発生しました: " + e.getMessage());
    }
    // System.out.println("フィードバック生成処理開始");
    // // Reflectionデータをプロンプトとして構築
    // String reflectionData = String.format(
    //   "活動内容: %s\n達成事項: %s\n改善点: %s",
    //   reflectionEntity.getActivity(),
    //   reflectionEntity.getAchievement(),
    //   reflectionEntity.getImprovementPoints()
    // );
    // System.out.println("フィードバック対象データ："+reflectionData);
    // // リクエストの内容
    // Map<String, Object> requestBody = Map.of(
    //   "instances", List.of(Map.of(
    //     "content", reflectionData,
    //     "prompt", "以下のReflectionデータに基づいて、次回の活動に役立つ具体的なアドバイスを提供してください: \n" + reflectionData
    //   ))
    // );

    // // Secret Managerからサービスアカウントキーを取得
    // SecretManagerServiceClient client = SecretManagerServiceClient.create();
    // System.out.println("client : "+client);
    // AccessSecretVersionResponse secretResponse = client.accessSecretVersion(SECRET_NAME);
    // System.out.println("secretResponse : "+secretResponse);
    // String credentialsJson = secretResponse.getPayload().getData().toStringUtf8();
    // System.out.println("credentialsJson : "+credentialsJson);
    // // サービスアカウントキーを読み込んで認証情報を生成
    // GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(credentialsJson.getBytes()));
    // credentials.refreshIfExpired();
    // // HTTPヘッダーを設定
    // HttpHeaders headers = new HttpHeaders();
    // headers.setContentType(MediaType.APPLICATION_JSON);
    // headers.setBearerAuth(credentials.getAccessToken().getTokenValue());

    // // HTTPリクエストを送信
    // RestTemplate restTemplate = new RestTemplate();
    // HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
    // ResponseEntity<Map> response = restTemplate.postForEntity(GEMINI_API_URL, entity, Map.class);

    // // フィードバックを取得して返却
    // List<Map<String, String>> predictions = (List<Map<String, String>>) response.getBody().get("predictions");
    // String generatedFeedback = predictions.get(0).get("content");
    // System.out.println("生成したフィードバック : "+generatedFeedback);
    
    // // DBにフィードバックを保存
    // FeedbackEntity feedbackEntity = new FeedbackEntity(reflectionEntity.getId(), generatedFeedback, LocalDateTime.now());
    // feedbackRepository.save(feedbackEntity);
    // return feedbackEntity;
  }
}

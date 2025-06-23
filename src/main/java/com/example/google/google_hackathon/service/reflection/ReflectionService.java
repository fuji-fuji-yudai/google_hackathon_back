package com.example.google.google_hackathon.service.reflection;

import java.net.URI;
import java.net.http.*;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.ReflectionEntity;
import com.example.google.google_hackathon.entity.ReflectionSummaryEntity;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.ReflectionRepository;
import com.example.google.google_hackathon.repository.ReflectionSummaryRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.*;

@Service
public class ReflectionService {
  private final ReflectionRepository reflectionRepository;
  private final AppUserRepository appUserRepository;
  private final ReflectionSummaryRepository reflectionSummaryRepository;
  
  private static final Logger logger = LoggerFactory.getLogger(ReflectionService.class);
  @Autowired
  public ReflectionService(ReflectionRepository reflectionRepository, AppUserRepository appUserRepository, ReflectionSummaryRepository reflectionSummaryRepository) {
    this.reflectionRepository = reflectionRepository;
    this.appUserRepository = appUserRepository;
    this.reflectionSummaryRepository = reflectionSummaryRepository;
  }

  // 指定した日付のreflectionデータを取得するメソッド
  public ReflectionEntity getReflectionsByDate(Date date, String userName) throws SQLException {
    System.out.println("振り返りデータ取得処理開始");
    AppUser user = appUserRepository.findByUsername(userName)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    // データベースから取得
    return reflectionRepository.findByUserIdAndDate(user.getId(), date);
  }

  // 月のreflectionデータを取得するメソッド
  public List<ReflectionEntity> getReflectionsByMonth(int year, int month, String userName) throws SQLException {
    System.out.println("振り返りデータ取得処理開始");
    AppUser user = appUserRepository.findByUsername(userName)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    // 月の開始日と終了日を計算
    LocalDate startDate = LocalDate.of(year, month, 1);
    LocalDate endDate = startDate.with(TemporalAdjusters.lastDayOfMonth());
    // データベースから取得
    return reflectionRepository.findByUserIdAndDateBetween(user.getId(), Date.valueOf(startDate), Date.valueOf(endDate));
  }

  // reflectionデータを登録するメソッド
  public ReflectionEntity createReflection(ReflectionEntity reflectionEntity, String userName) throws SQLException {
    System.out.println("登録処理開始");
    AppUser user = appUserRepository.findByUsername(userName)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    reflectionEntity.setUserId(user.getId());
    System.out.println("取得したユーザーID : " + reflectionEntity.getUserId());
    return reflectionRepository.save(reflectionEntity);
  }

  // reflectionデータを更新するメソッド
  public ReflectionEntity updateReflection(Long id, ReflectionEntity reflectionEntity) {
    ReflectionEntity existingReflection = reflectionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Reflection not found"));
    // 更新する内容を設定
    existingReflection.setDate(reflectionEntity.getDate());
    existingReflection.setActivity(reflectionEntity.getActivity());
    existingReflection.setAchievement(reflectionEntity.getAchievement());
    existingReflection.setImprovementPoints(reflectionEntity.getImprovementPoints());
    return reflectionRepository.save(existingReflection);
  }

  // reflectionのサマリーデータを取得するメソッド
  public ReflectionSummaryEntity getReflectionSummaryByUserIdAndYearMonth(String yearMonth, String userName) {
    // ユーザー情報の取得
    AppUser user = appUserRepository.findByUsername(userName)
                      .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    // サマリーデータを取得
    ReflectionSummaryEntity reflectionSummaryEntity 
      = reflectionSummaryRepository.findByUserIdAndYearMonth(user.getId(), yearMonth);
    return reflectionSummaryEntity;
  }

  // reflectionデータを要約するメソッド
  public ReflectionSummaryEntity summarizeReflection(List<ReflectionEntity> reflections, String userName, String yearMonth) throws SQLException {
    try {
      System.out.println("サマリー作成処理開始");
      // ユーザー情報の取得
      AppUser user = appUserRepository.findByUsername(userName)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
      // アクセストークン取得
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
          .createScoped("https://www.googleapis.com/auth/cloud-platform");
      credentials.refreshIfExpired();
      String accessToken = credentials.getAccessToken().getTokenValue();

      // Reflectionデータをプロンプトとして構築
      StringBuilder promptBuilder = new StringBuilder();
      promptBuilder.append("以下は1ヶ月分のReflectionデータです。活動内容、達成事項、改善点をそれぞれ要約してください。\n")
             .append("出力は以下のJSON形式で返してください：\n")
             .append("{\n")
             .append("  \"activity_summary\": \"要約された活動内容\",\n")
             .append("  \"achievement_summary\": \"要約された達成事項\",\n")
             .append("  \"improvement_summary\": \"要約された改善点\"\n")
             .append("}\n\n")
             .append("1ヶ月分の振り返りデータ：\n");
      for (ReflectionEntity reflection : reflections) {
          promptBuilder.append("日付: ").append(reflection.getDate()).append("\n")
                      .append("活動内容: ").append(reflection.getActivity()).append("\n")
                      .append("達成事項: ").append(reflection.getAchievement()).append("\n")
                      .append("改善点: ").append(reflection.getImprovementPoints()).append("\n\n");
      }
      System.out.println("プロンプト: "+promptBuilder);

      String prompt = promptBuilder.toString();

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

      // レスポンスを解析
      System.out.println("response: "+response);
      JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
      System.out.println("jsonResponse: "+jsonResponse);
      JsonArray candidates = jsonResponse.getAsJsonArray("candidates");

      if (candidates != null && candidates.size() > 0) {
        JsonObject content = candidates.get(0).getAsJsonObject()
        .getAsJsonObject("content");
        JsonArray responseParts  = content.getAsJsonArray("parts");

        if (responseParts  != null && responseParts .size() > 0) {
          String text = responseParts .get(0).getAsJsonObject().get("text").getAsString();

          // text は JSON 文字列なので、さらにパース
          JsonObject summaryJson = JsonParser.parseString(text).getAsJsonObject();

          String activitySummary = summaryJson.get("activity_summary").getAsString();
          String achievementSummary = summaryJson.get("achievement_summary").getAsString();
          String improvementSummary = summaryJson.get("improvement_summary").getAsString();
          // ReflectionSummaryEntityを作成
          ReflectionSummaryEntity summaryEntity = new ReflectionSummaryEntity();
          summaryEntity.setUserId(user.getId());
          summaryEntity.setYearMonth(yearMonth); // 例: "2023-10"
          summaryEntity.setActivitySummary(activitySummary);
          summaryEntity.setAchievementSummary(achievementSummary);
          summaryEntity.setImprovementSummary(improvementSummary);
          summaryEntity.setCreatedAt(LocalDateTime.now());

          // データベースに保存
          reflectionSummaryRepository.save(summaryEntity);
          return summaryEntity;
        } else {
          throw new IllegalStateException("parts が空です");
        }      
      } else {
        throw new IllegalStateException("candidates が空です");
      }
    } catch (Exception e) {
      logger.error("Gemini API呼び出し中にエラーが発生しました", e);
      return null;
    }
  }
}

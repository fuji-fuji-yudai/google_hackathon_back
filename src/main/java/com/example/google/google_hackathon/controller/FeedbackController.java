package com.example.google.google_hackathon.controller;

import com.example.google.google_hackathon.entity.FeedbackEntity;
import com.example.google.google_hackathon.entity.ReflectionEntity;
import com.example.google.google_hackathon.service.reflection.FeedbackService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
  @Autowired
  private final FeedbackService feedbackService;

  public FeedbackController(FeedbackService feedbackService) {
      this.feedbackService = feedbackService;
  }
  /**
   * Reflection ID に基づいて FeedbackEntity を検索する
    *
    * @param reflectionId ReflectionデータのID
    * @return フィードバックを含むレスポンス
    */
  @GetMapping
  public ResponseEntity<FeedbackEntity> getFeedback(@RequestParam Long reflectionId) {
    try {
      // Reflectionデータのidに紐づくフィードバックを取得
      FeedbackEntity feedbackEntity = feedbackService.getFeedbackByReflectionId(reflectionId);
      return ResponseEntity.ok(feedbackEntity);
    } catch (Exception e) {
      // エラーハンドリング（例: Gemini API呼び出し失敗）
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }
  
  /**
   * Reflectionデータを受け取り、Google Cloud Geminiを利用してフィードバックを生成する。
   *
   * @param reflectionEntity Reflectionデータ（活動内容、達成事項、改善点など）
   * @return フィードバックを含むレスポンス
   */
  @PostMapping("/create")
  public ResponseEntity<FeedbackEntity> createFeedback(@RequestBody ReflectionEntity reflectionEntity) {
    try {
      System.out.println("API呼び出し完了");
      // フィードバック生成処理
      FeedbackEntity feedbackEntity = feedbackService.createFeedback(reflectionEntity);

      // フィードバックをレスポンスとして返却
      return ResponseEntity.ok(feedbackEntity);
    } catch (Exception e) {
      // エラーハンドリング（例: Gemini API呼び出し失敗）
      System.err.println("エラー発生 : " + e.getMessage());
      e.printStackTrace();
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }
}

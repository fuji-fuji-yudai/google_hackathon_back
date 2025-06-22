package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.dto.TaskDto;
import com.example.google.google_hackathon.entity.Task;
import com.example.google.google_hackathon.repository.TaskManageRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelAnalyzerService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private TaskManageRepository taskManageRepository;

    // プロジェクトIDを設定
    @Value("${google.cloud.project.id:nomadic-bison-459812-a8}")
    private String projectId;

    /**
     * Excel解析メイン処理
     */
    public List<TaskDto> analyzeExcel(MultipartFile file) throws Exception {
        try {
            // 1. Excelファイルを解析
            Map<String, Object> excelData = readExcelFile(file.getInputStream());
            logger.info("Excel解析完了: {} sheets", ((List<?>) excelData.get("sheets")).size());

            // 2. Vertex AI Gemini APIでタスク分割
            String taskJson = processWithVertexAI(excelData);

            // 3. JSONをTaskDtoのリストに変換
            List<TaskDto> tasks = parseTaskJson(taskJson);
            logger.info("生成されたタスク数: {}", tasks.size());

            return tasks;
        } catch (Exception e) {
            logger.error("Excel解析中にエラーが発生しました", e);
            // エラー時はモックデータを返す
            return parseTaskJson(generateMockTaskData());
        }
    }

    /**
     * Excelファイルを解析してMap形式で返す
     */
    private Map<String, Object> readExcelFile(InputStream inputStream) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> sheets = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Map<String, Object> sheetData = extractSheetData(sheet);
                sheets.add(sheetData);
            }
        }

        result.put("sheets", sheets);
        logger.debug("Excel読み込み完了: {} sheets", sheets.size());
        return result;
    }

    /**
     * シートからデータを抽出
     */
    private Map<String, Object> extractSheetData(Sheet sheet) {
        Map<String, Object> sheetData = new HashMap<>();
        List<Map<String, String>> rows = new ArrayList<>();

        sheetData.put("name", sheet.getSheetName());

        // ヘッダー行の取得
        Row headerRow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                headers.add(getCellValueAsString(cell));
            }
        }

        // データ行の取得（空行をスキップ）
        for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row != null && !isRowEmpty(row)) {
                Map<String, String> rowData = extractRowData(row, headers);
                if (!rowData.isEmpty()) {
                    rows.add(rowData);
                }
            }
        }

        sheetData.put("headers", headers);
        sheetData.put("rows", rows);
        return sheetData;
    }

    /**
     * 行からデータを抽出
     */
    private Map<String, String> extractRowData(Row row, List<String> headers) {
        Map<String, String> rowData = new HashMap<>();
        for (int cellNum = 0; cellNum < headers.size(); cellNum++) {
            Cell cell = row.getCell(cellNum);
            String value = getCellValueAsString(cell);
            if (!value.trim().isEmpty()) {
                rowData.put(headers.get(cellNum), value);
            }
        }
        return rowData;
    }

    /**
     * 行が空かどうかをチェック
     */
    private boolean isRowEmpty(Row row) {
        for (int cellNum = row.getFirstCellNum(); cellNum < row.getLastCellNum(); cellNum++) {
            Cell cell = row.getCell(cellNum);
            if (cell != null && !getCellValueAsString(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * セルの値を文字列として取得
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().format(DATE_FORMATTER);
                }
                double numValue = cell.getNumericCellValue();
                if (numValue == (long) numValue) {
                    return String.valueOf((long) numValue);
                } else {
                    return String.valueOf(numValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * Vertex AI Gemini APIを使用してExcelデータを処理
     */
    private String processWithVertexAI(Map<String, Object> excelData) throws Exception {
        try {
            // Google Cloud認証
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
            credentials.refreshIfExpired();
            String accessToken = credentials.getAccessToken().getTokenValue();

            // プロンプト構築（改善された汎用的プロンプト）
            String prompt = buildAdvancedWBSPrompt(excelData);

            // リクエストボディ構築
            JsonObject requestBody = createVertexAIRequest(prompt);

            // API呼び出し
            String endpoint = String.format(
                    "https://us-central1-aiplatform.googleapis.com/v1/projects/%s/locations/us-central1/publishers/google/models/gemini-2.0-flash-001:generateContent",
                    projectId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Vertex AI API error: {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Vertex AI API error: " + response.statusCode());
            }

            return extractTaskJsonFromResponse(response.body());

        } catch (Exception e) {
            logger.error("Vertex AI API呼び出し中にエラーが発生しました", e);
            throw e;
        }
    }

    /**
     * 高度なWBS生成プロンプトを構築（汎用的・柔軟性重視）
     */
    private String buildAdvancedWBSPrompt(Map<String, Object> excelData) {
        Gson gson = new Gson();
        String excelDataJson = gson.toJson(excelData);
        LocalDate startDate = LocalDate.now();

        // Excelデータの特性を事前分析
        String functionalAnalysis = analyzeFunctionalCharacteristics(excelData);

        return String.format("""
                あなたはプロジェクト管理とソフトウェア開発のエキスパートです。
                提供されたExcelファイルの機能一覧を分析し、各機能の特性に基づいた最適なWBSを生成してください。

                【分析されたExcelの特性】
                %s

                【WBS生成の戦略的原則】
                1. **機能の依存関係分析**: 機能間の論理的依存関係を特定
                2. **リスク評価**: 難易度と複雑さからリスクレベルを評価
                3. **リソース最適化**: 担当者のスキルと負荷を考慮した配置
                4. **品質保証**: 各フェーズでの品質チェックポイント設定

                【標準開発フェーズの適用】
                以下のフェーズ構造に沿ってタスクを生成してください：
                - 要件定義フェーズ（各機能の要件を明確化）
                - 基本設計フェーズ（システム全体のアーキテクチャ設計）
                - 詳細設計フェーズ（個別機能の詳細仕様）
                - 実装フェーズ（コーディングと単体テスト）
                - 結合テストフェーズ（機能間の連携テスト）
                - システムテストフェーズ（全体統合テスト）
                - リリース準備フェーズ（本番環境への展開準備）

                【動的期間設定ルール】
                - 難易度「小」: 1-3日間
                - 難易度「中」: 3-7日間
                - 難易度「大」: 7-14日間
                - 画面系機能: UI設計に+1-2日
                - データ処理系: データ設計に+2-3日
                - フェーズ間バッファ: 各フェーズ終了後1-2日の調整期間

                【担当者割り当て戦略】
                - 複雑な機能（難易度「大」）→ 経験豊富な担当者
                - 関連性の高い機能 → 同一担当者にグルーピング
                - 並行実装可能な機能 → 異なる担当者に分散
                - 担当者: PM、担当者A、担当者B、担当者C、テスト担当

                【出力要件】
                以下のJSON配列形式で出力してください（前後の説明文は不要）：

                ```json
                [
                  {
                    "id": 1,
                    "title": "要件定義フェーズ",
                    "assignee": "PM",
                    "parentId": null,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "id": 2,
                    "title": "[Excelで見つかった機能名]の要件定義",
                    "assignee": "担当者A",
                    "parentId": 1,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  }
                ]
                ```

                【重要な制約】
                - IDは1から連番で設定
                - 親タスクのIDは子タスクより小さくする
                - 日付はyyyy-MM-dd形式（%s から開始）
                - statusは"ToDo"固定
                - parentIdは親タスクのid、最上位はnull
                - 機能名は元データから動的に取得して使用
                - JSONのみ出力（説明文なし）

                【解析対象データ】
                %s
                """,
                functionalAnalysis,
                startDate.format(DATE_FORMATTER),
                startDate.plusWeeks(1).format(DATE_FORMATTER),
                startDate.format(DATE_FORMATTER),
                startDate.plusDays(3).format(DATE_FORMATTER),
                startDate.format(DATE_FORMATTER),
                excelDataJson);
    }

    /**
     * Excelデータから機能特性を分析
     */
    private String analyzeFunctionalCharacteristics(Map<String, Object> excelData) {
        StringBuilder analysis = new StringBuilder();

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sheets = (List<Map<String, Object>>) excelData.get("sheets");

            if (sheets != null && !sheets.isEmpty()) {
                Map<String, Object> sheet = sheets.get(0);
                @SuppressWarnings("unchecked")
                List<Map<String, String>> rows = (List<Map<String, String>>) sheet.get("rows");

                if (rows != null && !rows.isEmpty()) {
                    analysis.append("機能数: ").append(rows.size()).append("個\n");

                    // 難易度分析
                    Map<String, Long> difficultyCount = rows.stream()
                            .filter(row -> row.containsKey("難易度"))
                            .collect(Collectors.groupingBy(
                                    row -> row.getOrDefault("難易度", "不明"),
                                    Collectors.counting()));

                    if (!difficultyCount.isEmpty()) {
                        analysis.append("難易度分布: ");
                        difficultyCount
                                .forEach((key, value) -> analysis.append(key).append("(").append(value).append("個) "));
                        analysis.append("\n");
                    }

                    // 機能タイプ分析
                    long screenCount = rows.stream()
                            .mapToLong(row -> {
                                String name = row.values().stream()
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.joining(" "));
                                return (name.contains("画面") || name.contains("UI") || name.contains("表示")
                                        || name.contains("画") || name.contains("入力")) ? 1 : 0;
                            })
                            .sum();

                    long processCount = rows.stream()
                            .mapToLong(row -> {
                                String name = row.values().stream()
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.joining(" "));
                                return (name.contains("処理") || name.contains("計算") || name.contains("登録")
                                        || name.contains("更新") || name.contains("削除")) ? 1 : 0;
                            })
                            .sum();

                    analysis.append("画面系機能: ").append(screenCount).append("個\n");
                    analysis.append("処理系機能: ").append(processCount).append("個\n");
                    analysis.append("その他機能: ").append(rows.size() - screenCount - processCount).append("個\n");

                    // プロジェクト複雑度の推定
                    double complexityScore = rows.size() * 0.3;
                    if (difficultyCount.getOrDefault("大", 0L) > 0) {
                        complexityScore += difficultyCount.get("大") * 2.0;
                    }
                    if (difficultyCount.getOrDefault("中", 0L) > 0) {
                        complexityScore += difficultyCount.get("中") * 1.0;
                    }

                    String complexityLevel = complexityScore > 10 ? "高" : complexityScore > 5 ? "中" : "低";
                    analysis.append("プロジェクト複雑度: ").append(complexityLevel).append("\n");
                }
            }
        } catch (Exception e) {
            logger.warn("機能特性の分析中に軽微なエラーが発生しました: {}", e.getMessage());
            analysis.append("基本的な機能一覧として分析します\n");
        }

        return analysis.toString();
    }

    /**
     * Vertex AI用リクエストボディ作成
     */
    private JsonObject createVertexAIRequest(String prompt) {
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

        // 生成設定（JSON出力を促進、より確実性を高める）
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.1); // 一貫性重視
        generationConfig.addProperty("topK", 1); // 最も確率の高い選択
        generationConfig.addProperty("topP", 0.8); // 高品質な出力
        generationConfig.addProperty("maxOutputTokens", 8192);
        requestBody.add("generationConfig", generationConfig);

        return requestBody;
    }

    /**
     * Vertex AIレスポンスからJSONを抽出
     */
    private String extractTaskJsonFromResponse(String responseBody) throws Exception {
        try {
            logger.info("=== Vertex AI API レスポンス分析開始 ===");
            logger.info("レスポンス全体（最初の500文字）: {}", responseBody.substring(0, Math.min(500, responseBody.length())));

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray candidates = json.getAsJsonArray("candidates");

            if (candidates != null && candidates.size() > 0) {
                JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                JsonArray partsArray = content.getAsJsonArray("parts");
                String text = partsArray.get(0).getAsJsonObject().get("text").getAsString();

                logger.info("抽出されたテキスト全体: {}", text);

                // JSON部分のみを抽出（より堅牢な抽出ロジック）
                String jsonText = extractJsonFromText(text);
                if (jsonText != null) {
                    logger.info("抽出されたJSON: {}", jsonText.substring(0, Math.min(500, jsonText.length())) + "...");

                    // JSONの妥当性をチェック
                    try {
                        JsonParser.parseString(jsonText);
                        logger.info("JSON妥当性チェック: OK");
                        return jsonText;
                    } catch (Exception e) {
                        logger.error("JSONの妥当性チェック: NG - {}", e.getMessage());
                    }
                }

                logger.warn("JSONが見つかりませんでした。レスポンス全体を返します");
                logger.warn("レスポンステキスト: {}", text);
                return text;
            } else {
                logger.error("Vertex AI API returned no candidates: {}", responseBody);
                throw new Exception("回答を生成できませんでした");
            }
        } catch (Exception e) {
            logger.error("Vertex AIレスポンスの解析に失敗しました", e);
            logger.error("問題のあるレスポンス: {}", responseBody);
            throw new Exception("レスポンス解析エラー: " + e.getMessage());
        }
    }

    /**
     * テキストからJSON配列を抽出する改善されたメソッド
     */
    private String extractJsonFromText(String text) {
        logger.info("=== JSON抽出開始 ===");
        logger.info("抽出対象テキスト: {}", text);

        // 複数のパターンでJSON抽出を試行
        String[] patterns = {
                "\\[.*?\\]", // 基本的な配列パターン
                "```json\\s*\\[.*?\\]\\s*```", // マークダウンコードブロック
                "```\\s*\\[.*?\\]\\s*```" // 一般的なコードブロック
        };

        for (int i = 0; i < patterns.length; i++) {
            String pattern = patterns[i];
            logger.debug("パターン{}を試行: {}", i + 1, pattern);

            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                String found = m.group();
                logger.info("パターン{}でマッチしました: {}", i + 1, found.substring(0, Math.min(100, found.length())) + "...");

                // マークダウンマーカーを除去
                found = found.replaceAll("```json|```", "").trim();

                // 簡単な妥当性チェック
                if (found.startsWith("[") && found.endsWith("]")) {
                    logger.info("JSON形式チェック: OK");
                    return found;
                } else {
                    logger.warn("JSON形式チェック: NG - '[' または ']' が見つからない");
                }
            } else {
                logger.debug("パターン{}はマッチしませんでした", i + 1);
            }
        }

        // フォールバック: 手動で最初の [から最後の ] まで抽出
        logger.info("フォールバック抽出を試行");
        int startIdx = text.indexOf('[');
        int endIdx = text.lastIndexOf(']');
        if (startIdx >= 0 && endIdx > startIdx) {
            String extracted = text.substring(startIdx, endIdx + 1);
            logger.info("フォールバック抽出成功: {}", extracted.substring(0, Math.min(100, extracted.length())) + "...");
            return extracted;
        }

        logger.error("全てのJSON抽出方法が失敗しました");
        return null;
    }

    /**
     * モックデータ生成（完全汎用版 - 具体的な機能名を含まない）
     */
    private String generateMockTaskData() {
        LocalDate now = LocalDate.now();
        return String.format("""
                [
                  {
                    "id": 1,
                    "title": "要件定義フェーズ",
                    "assignee": "PM",
                    "parentId": null,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "id": 2,
                    "title": "基本設計フェーズ",
                    "assignee": "PM",
                    "parentId": null,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "id": 3,
                    "title": "実装フェーズ",
                    "assignee": "開発チーム",
                    "parentId": null,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "id": 4,
                    "title": "テストフェーズ",
                    "assignee": "テスト担当",
                    "parentId": null,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  }
                ]""",
                now.format(DATE_FORMATTER),
                now.plusWeeks(1).format(DATE_FORMATTER),
                now.plusWeeks(1).format(DATE_FORMATTER),
                now.plusWeeks(2).format(DATE_FORMATTER),
                now.plusWeeks(2).format(DATE_FORMATTER),
                now.plusWeeks(4).format(DATE_FORMATTER),
                now.plusWeeks(4).format(DATE_FORMATTER),
                now.plusWeeks(5).format(DATE_FORMATTER));
    }

    /**
     * JSON文字列をTaskDtoのリストに変換
     */
    private List<TaskDto> parseTaskJson(String json) {
        try {
            logger.info("=== JSON解析開始 ===");
            logger.info("解析対象JSON: {}", json);

            Gson gson = new Gson();
            TaskDto[] taskArray = gson.fromJson(json, TaskDto[].class);

            if (taskArray == null) {
                logger.error("Gsonがnullを返しました");
                return Collections.emptyList();
            }

            logger.info("Gsonで解析されたタスク数: {}", taskArray.length);

            List<TaskDto> tasks = new ArrayList<>();

            for (int i = 0; i < taskArray.length; i++) {
                TaskDto task = taskArray[i];
                logger.debug("タスク{}: {}", i + 1, task.title);

                // 必須フィールドの初期化
                if (task.status == null || task.status.isEmpty()) {
                    task.status = "ToDo";
                }
                if (task.assignee == null) {
                    task.assignee = "";
                }
                if (task.actual_start == null) {
                    task.actual_start = "";
                }
                if (task.actual_end == null) {
                    task.actual_end = "";
                }

                tasks.add(task);
            }

            logger.info("最終的に作成されたタスク数: {}", tasks.size());
            return tasks;
        } catch (Exception e) {
            logger.error("JSONの解析に失敗しました: {}", e.getMessage());
            logger.error("問題のあるJSON: {}", json);
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // 以下、既存のDB操作メソッドをそのまま維持
    public List<TaskDto> saveExcelTasks(List<TaskDto> taskDtos) {
        List<Task> tasks = taskDtos.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());

        List<Task> savedTasks = taskManageRepository.saveAll(tasks);

        return savedTasks.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private Task convertToEntity(TaskDto dto) {
        Task task = new Task();
        if (dto.id != null && dto.id > 0) {
            task.setId(dto.id);
        }
        task.setTitle(dto.title);
        task.setAssignee(dto.assignee);
        task.setPlan_start(parseDate(dto.plan_start));
        task.setPlan_end(parseDate(dto.plan_end));
        task.setActual_start(parseDate(dto.actual_start));
        task.setActual_end(parseDate(dto.actual_end));
        task.setStatus(dto.status);
        task.setParentId(dto.parent_id);
        return task;
    }

    private TaskDto convertToDto(Task entity) {
        TaskDto dto = new TaskDto();
        dto.id = entity.getId();
        dto.title = entity.getTitle();
        dto.assignee = entity.getAssignee();
        dto.plan_start = entity.getPlan_start() != null ? entity.getPlan_start().format(DATE_FORMATTER) : "";
        dto.plan_end = entity.getPlan_end() != null ? entity.getPlan_end().format(DATE_FORMATTER) : "";
        dto.actual_start = entity.getActual_start() != null ? entity.getActual_start().format(DATE_FORMATTER) : "";
        dto.actual_end = entity.getActual_end() != null ? entity.getActual_end().format(DATE_FORMATTER) : "";
        dto.status = entity.getStatus();
        dto.parent_id = entity.getParentId();
        return dto;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
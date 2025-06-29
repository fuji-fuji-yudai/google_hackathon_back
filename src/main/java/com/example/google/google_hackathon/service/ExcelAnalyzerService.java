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
            throw new RuntimeException("Excel解析に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * ExcelからのタスクをDBに保存する（tmp_id方式）
     */
    public List<TaskDto> saveExcelTasks(List<TaskDto> taskDtos) {
        logger.info("=== tmp_id方式でExcelタスク保存開始 ===");
        logger.info("保存対象タスク数: {}", taskDtos.size());

        try {
            // Step 1: tmp_id -> 実際のIDのマッピングを構築するため、全タスクを保存
            Map<Integer, Integer> tmpIdToRealIdMap = new HashMap<>();
            List<Task> savedTasks = new ArrayList<>();
            
            for (TaskDto dto : taskDtos) {
                Task entity = convertToEntity(dto);
                entity.setId(null); // 自動採番させる
                entity.setParentId(null); // 後で設定
                entity.setTmpId(dto.tmp_id); // AI生成の論理IDを保持
                
                Task saved = taskManageRepository.save(entity);
                savedTasks.add(saved);
                
                // マッピングを記録
                if (dto.tmp_id != null) {
                    tmpIdToRealIdMap.put(dto.tmp_id, saved.getId());
                    logger.info("IDマッピング: tmp_id:{} -> real_id:{} ({})", 
                        dto.tmp_id, saved.getId(), dto.title);
                }
            }
            
            // Step 2: tmp_parent_idを使って実際の親子関係を設定
            List<Task> tasksToUpdate = new ArrayList<>();
            
            for (int i = 0; i < taskDtos.size(); i++) {
                TaskDto dto = taskDtos.get(i);
                Task saved = savedTasks.get(i);
                
                if (dto.tmp_parent_id != null) {
                    Integer realParentId = tmpIdToRealIdMap.get(dto.tmp_parent_id);
                    if (realParentId != null) {
                        saved.setParentId(realParentId);
                        tasksToUpdate.add(saved);
                        
                        logger.info("親子関係設定: {} -> 親tmp_id:{} -> 親real_id:{}", 
                            dto.title, dto.tmp_parent_id, realParentId);
                    } else {
                        logger.warn("親タスクが見つからない: {} -> tmp_parent_id:{}", 
                            dto.title, dto.tmp_parent_id);
                    }
                }
            }
            
            // Step 3: 親子関係を設定したタスクを一括更新
            if (!tasksToUpdate.isEmpty()) {
                taskManageRepository.saveAll(tasksToUpdate);
                logger.info("親子関係更新完了: {}件", tasksToUpdate.size());
            }
            
            // Step 4: 最終結果を返す
            List<TaskDto> result = taskManageRepository.findAll().stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
                    
            logger.info("=== 保存完了: 総タスク数:{} ===", result.size());
            return result;
            
        } catch (Exception e) {
            logger.error("tmp_id方式でのタスク保存中にエラー", e);
            throw new RuntimeException("タスクの保存に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * Excelファイルを解析してMap形式で返す
     */
    private Map<String, Object> readExcelFile(InputStream inputStream) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> sheets = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            logger.info("=== Excel読み込み開始 ===");
            logger.info("シート数: {}", workbook.getNumberOfSheets());

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                logger.info("シート{}名: '{}'", i + 1, sheet.getSheetName());
                logger.info("シート{}の行数: {}", i + 1, sheet.getLastRowNum() + 1);

                Map<String, Object> sheetData = extractSheetData(sheet);
                sheets.add(sheetData);

                // シートデータの内容を詳細ログ
                @SuppressWarnings("unchecked")
                List<String> headers = (List<String>) sheetData.get("headers");
                @SuppressWarnings("unchecked")
                List<Map<String, String>> rows = (List<Map<String, String>>) sheetData.get("rows");

                logger.info("シート{}のヘッダー: {}", i + 1, headers);
                logger.info("シート{}のデータ行数: {}", i + 1, rows.size());

                if (rows.size() > 0) {
                    logger.info("シート{}の最初の行データ: {}", i + 1, rows.get(0));
                }
            }
        }

        result.put("sheets", sheets);
        logger.info("Excel読み込み完了: {} sheets, 総データ: {}", sheets.size(), result);
        return result;
    }

    /**
     * シートからデータを抽出
     */
    private Map<String, Object> extractSheetData(Sheet sheet) {
        Map<String, Object> sheetData = new HashMap<>();
        List<Map<String, String>> rows = new ArrayList<>();

        sheetData.put("name", sheet.getSheetName());
        logger.info("=== シート '{}' のデータ抽出開始 ===", sheet.getSheetName());

        // シートの物理的な範囲を確認
        int firstRowNum = sheet.getFirstRowNum();
        int lastRowNum = sheet.getLastRowNum();
        logger.info("シートの行範囲: {} ～ {}", firstRowNum, lastRowNum);

        // 全行を強制スキャン（空行も含む）
        logger.info("=== 全行スキャン開始 ===");
        for (int rowIndex = 0; rowIndex <= Math.max(lastRowNum, 10); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                int firstCellNum = row.getFirstCellNum();
                int lastCellNum = row.getLastCellNum();
                logger.info("行{}: セル範囲 {} ～ {}, 物理セル数: {}",
                        rowIndex, firstCellNum, lastCellNum, row.getPhysicalNumberOfCells());

                // 各セルの内容を詳細チェック
                for (int cellIndex = 0; cellIndex < Math.max(lastCellNum, 10); cellIndex++) {
                    Cell cell = row.getCell(cellIndex);
                    if (cell != null) {
                        String cellValue = getCellValueAsString(cell);
                        logger.info("セル[{},{}]: 型={}, 値='{}'",
                                rowIndex, cellIndex, cell.getCellType(), cellValue);
                    } else {
                        logger.debug("セル[{},{}]: null", rowIndex, cellIndex);
                    }
                }
            } else {
                logger.debug("行{}: null", rowIndex);
            }
        }

        // ヘッダー行の取得（通常は0行目）
        Row headerRow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        if (headerRow != null) {
            logger.info("=== ヘッダー行処理 ===");
            logger.info("ヘッダー行のセル数: {}", headerRow.getPhysicalNumberOfCells());

            // 最大20列まで強制チェック
            for (int i = 0; i < Math.max(headerRow.getLastCellNum(), 20); i++) {
                Cell cell = headerRow.getCell(i);
                String headerValue = getCellValueAsString(cell);
                logger.info("ヘッダー[{}]: '{}'", i, headerValue);
                headers.add(headerValue);
            }
        } else {
            logger.warn("ヘッダー行（行0）がnullです");
        }

        // データ行の取得（1行目以降）
        logger.info("=== データ行処理開始 ===");
        for (int rowNum = 1; rowNum <= lastRowNum; rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row != null) {
                logger.info("データ行{}を処理中...", rowNum);

                Map<String, String> rowData = new HashMap<>();
                boolean hasData = false;

                // 最大20列まで強制チェック
                for (int i = 0; i < Math.max(row.getLastCellNum(), 20); i++) {
                    Cell cell = row.getCell(i);
                    String value = getCellValueAsString(cell);
                    String header = i < headers.size() ? headers.get(i) : "Column" + i;

                    if (value != null && !value.trim().isEmpty()) {
                        hasData = true;
                        logger.info("データ発見 - 行{}列{}: ヘッダー='{}', 値='{}'", rowNum, i, header, value);
                    }

                    if (header != null && !header.trim().isEmpty()) {
                        rowData.put(header, value);
                    }
                }

                if (hasData) {
                    rows.add(rowData);
                    logger.info("行{}をデータリストに追加: {}", rowNum, rowData);
                } else {
                    logger.warn("行{}にはデータが見つかりませんでした", rowNum);
                }
            }
        }

        sheetData.put("headers", headers);
        sheetData.put("rows", rows);

        logger.info("シート '{}' 抽出完了: ヘッダー{}個, データ行{}個",
                sheet.getSheetName(), headers.size(), rows.size());

        return sheetData;
    }

    /**
     * セルの値を文字列として取得
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        try {
            switch (cell.getCellType()) {
                case STRING:
                    String stringValue = cell.getStringCellValue().trim();
                    logger.trace("セル[{},{}] STRING: '{}'", cell.getRowIndex(), cell.getColumnIndex(), stringValue);
                    return stringValue;
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        String dateValue = cell.getLocalDateTimeCellValue().format(DATE_FORMATTER);
                        logger.trace("セル[{},{}] DATE: '{}'", cell.getRowIndex(), cell.getColumnIndex(), dateValue);
                        return dateValue;
                    }
                    double numValue = cell.getNumericCellValue();
                    String numString;
                    if (numValue == (long) numValue) {
                        numString = String.valueOf((long) numValue);
                    } else {
                        numString = String.valueOf(numValue);
                    }
                    logger.trace("セル[{},{}] NUMERIC: '{}'", cell.getRowIndex(), cell.getColumnIndex(), numString);
                    return numString;
                case BOOLEAN:
                    String boolValue = String.valueOf(cell.getBooleanCellValue());
                    logger.trace("セル[{},{}] BOOLEAN: '{}'", cell.getRowIndex(), cell.getColumnIndex(), boolValue);
                    return boolValue;
                case FORMULA:
                    String formulaValue = cell.getCellFormula();
                    logger.trace("セル[{},{}] FORMULA: '{}'", cell.getRowIndex(), cell.getColumnIndex(), formulaValue);
                    return formulaValue;
                case BLANK:
                    logger.trace("セル[{},{}] BLANK", cell.getRowIndex(), cell.getColumnIndex());
                    return "";
                default:
                    logger.trace("セル[{},{}] OTHER: ''", cell.getRowIndex(), cell.getColumnIndex());
                    return "";
            }
        } catch (Exception e) {
            logger.warn("セル[{},{}]の読み込みでエラー: {}", cell.getRowIndex(), cell.getColumnIndex(), e.getMessage());
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

            // プロンプト構築（修正版）
            String prompt = buildPhaseBasedWBSPrompt(excelData);

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
     * フェーズ別WBS生成プロンプトを構築（修正版）
     */
    private String buildPhaseBasedWBSPrompt(Map<String, Object> excelData) {
        Gson gson = new Gson();
        String excelDataJson = gson.toJson(excelData);
        LocalDate startDate = LocalDate.now();

        // Excelデータが有効かチェック
        if (!isValidExcelData(excelData)) {
            logger.error("Excelファイルからデータを取得できませんでした");
            throw new RuntimeException("Excelファイルにデータが含まれていません。機能一覧、難易度、種別などのデータを含むExcelファイルをアップロードしてください。");
        }

        // Excelから機能一覧を抽出
        List<String> functionList = extractFunctionList(excelData);
        logger.info("抽出された機能一覧: {}", functionList);

        return String.format("""
                あなたはプロジェクト管理のエキスパートです。
                Excelファイルから抽出した機能一覧を基に、各開発フェーズごとのWBSを生成してください。

                【抽出された機能一覧】
                %s

                【生成ルール】
                以下の6つのフェーズ × 機能数のタスクを生成：
                1. 要件定義フェーズ (tmp_id: 1) → 各機能の要件定義 (tmp_id: 11, 12, 13...)
                2. 基本設計フェーズ (tmp_id: 20) → 各機能の基本設計 (tmp_id: 21, 22, 23...)
                3. 詳細設計フェーズ (tmp_id: 40) → 各機能の詳細設計 (tmp_id: 41, 42, 43...)
                4. 実装フェーズ (tmp_id: 60) → 各機能の実装 (tmp_id: 61, 62, 63...)
                5. 結合テストフェーズ (tmp_id: 80) → 各機能の結合テスト (tmp_id: 81, 82, 83...)
                6. システムテストフェーズ (tmp_id: 100) → 各機能のシステムテスト (tmp_id: 101, 102, 103...)

                【必須】以下のJSON配列のみを出力してください。説明文やコードブロックマーカーは不要です：

                [{"tmp_id":1,"title":"要件定義フェーズ","assignee":"PM","tmp_parent_id":null,"plan_start":"%s","plan_end":"%s","actual_start":"","actual_end":"","status":"ToDo"},{"tmp_id":11,"title":"[機能1]の要件定義","assignee":"担当者A","tmp_parent_id":1,"plan_start":"%s","plan_end":"%s","actual_start":"","actual_end":"","status":"ToDo"},{"tmp_id":20,"title":"基本設計フェーズ","assignee":"PM","tmp_parent_id":null,"plan_start":"%s","plan_end":"%s","actual_start":"","actual_end":"","status":"ToDo"}]

                上記の形式で、抽出した全機能について全フェーズのタスクを生成してください。
                """,
                String.join("\n", functionList),
                startDate.format(DATE_FORMATTER),
                startDate.plusWeeks(2).format(DATE_FORMATTER),
                startDate.format(DATE_FORMATTER),
                startDate.plusWeeks(2).format(DATE_FORMATTER),
                startDate.plusWeeks(2).plusDays(1).format(DATE_FORMATTER),
                startDate.plusWeeks(4).format(DATE_FORMATTER));
    }

    /**
     * Excelデータから機能一覧を抽出
     */
    private List<String> extractFunctionList(Map<String, Object> excelData) {
        List<String> functionList = new ArrayList<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sheets = (List<Map<String, Object>>) excelData.get("sheets");

            if (sheets != null && !sheets.isEmpty()) {
                Map<String, Object> sheet = sheets.get(0);
                @SuppressWarnings("unchecked")
                List<Map<String, String>> rows = (List<Map<String, String>>) sheet.get("rows");

                if (rows != null && !rows.isEmpty()) {
                    for (Map<String, String> row : rows) {
                        // 機能名を抽出（機能名、機能ID、などのキーから）
                        String functionName = null;
                        
                        // 様々なキー名パターンに対応
                        for (String key : Arrays.asList("機能名", "機能", "function", "Function", "FUNCTION")) {
                            if (row.containsKey(key) && row.get(key) != null && !row.get(key).trim().isEmpty()) {
                                functionName = row.get(key).trim();
                                break;
                            }
                        }
                        
                        if (functionName != null) {
                            functionList.add(functionName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("機能一覧の抽出中にエラー: {}", e.getMessage());
        }
        
        return functionList;
    }

    /**
     * Excelデータが有効かチェック
     */
    private boolean isValidExcelData(Map<String, Object> excelData) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sheets = (List<Map<String, Object>>) excelData.get("sheets");

            if (sheets == null || sheets.isEmpty()) {
                logger.warn("シートが見つかりません");
                return false;
            }

            for (Map<String, Object> sheet : sheets) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> rows = (List<Map<String, String>>) sheet.get("rows");

                if (rows != null && !rows.isEmpty()) {
                    logger.info("有効なデータ行が{}個見つかりました", rows.size());
                    return true; // 少なくとも1つのシートにデータがあれば有効
                }
            }

            logger.warn("すべてのシートでデータ行が見つかりません");
            return false;
        } catch (Exception e) {
            logger.error("Excelデータの妥当性チェック中にエラー: {}", e.getMessage());
            return false;
        }
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
        generationConfig.addProperty("maxOutputTokens", 32768); // 大幅に増加
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
        // まず、不完全なレスポンスかチェック
        if (text.trim().equals("```json") || text.trim().equals("```") || text.trim().startsWith("```json\n[") && !text.trim().endsWith("]")) {
            logger.error("AI応答が不完全です: {}", text.substring(0, Math.min(100, text.length())));
            throw new RuntimeException("AI応答が不完全です。レスポンスが途中で切れています。");
        }

        // 複数のパターンでJSON抽出を試行
        String[] patterns = {
                "\\[.*?\\]", // 基本的な配列パターン
                "```json\\s*\\[.*?\\]\\s*```", // マークダウンコードブロック
                "```\\s*\\[.*?\\]\\s*```" // 一般的なコードブロック
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                String found = m.group();
                // マークダウンマーカーを除去
                found = found.replaceAll("```json|```", "").trim();
                // 簡単な妥当性チェック
                if (found.startsWith("[") && found.endsWith("]")) {
                    // 追加チェック: 基本的なJSON構造確認
                    if (found.contains("tmp_id") && found.contains("title")) {
                        return found;
                    }
                }
            }
        }

        // フォールバック: 手動で最初の [から最後の ] まで抽出
        int startIdx = text.indexOf('[');
        int endIdx = text.lastIndexOf(']');
        if (startIdx >= 0 && endIdx > startIdx) {
            String extracted = text.substring(startIdx, endIdx + 1);
            // 基本的な妥当性チェック
            if (extracted.contains("tmp_id") && extracted.contains("title")) {
                return extracted;
            }
        }

        // どうしても見つからない場合
        logger.error("JSON抽出失敗。AI応答内容: {}", text.substring(0, Math.min(200, text.length())));
        throw new RuntimeException("AI応答からJSONを抽出できませんでした。レスポンスが不完全な可能性があります。");
    }

    /**
     * フェーズ別WBS生成プロンプトを構築（修正版）
     */
    private List<TaskDto> parseTaskJson(String json) {
        try {
            logger.info("=== JSON解析開始 ===");
            logger.info("解析対象JSON: {}", json);

            // 空文字列やnullのチェック
            if (json == null || json.trim().isEmpty()) {
                logger.error("JSONが空です");
                throw new RuntimeException("AI応答からタスクデータを取得できませんでした");
            }

            // JSON配列の形式かチェック
            String trimmedJson = json.trim();
            if (!trimmedJson.startsWith("[") || !trimmedJson.endsWith("]")) {
                logger.error("JSONが配列形式ではありません。内容: {}", trimmedJson);
                throw new RuntimeException("AI応答が期待された形式ではありません: " + trimmedJson);
            }

            Gson gson = new Gson();
            TaskDto[] taskArray = gson.fromJson(trimmedJson, TaskDto[].class);

            if (taskArray == null) {
                logger.error("Gsonがnullを返しました");
                throw new RuntimeException("タスクデータの解析に失敗しました");
            }

            logger.info("Gsonで解析されたタスク数: {}", taskArray.length);

            List<TaskDto> tasks = new ArrayList<>();

            for (int i = 0; i < taskArray.length; i++) {
                TaskDto task = taskArray[i];
                logger.debug("タスク{}: {} (tmp_id: {}, tmp_parent_id: {})", 
                    i + 1, task.title, task.tmp_id, task.tmp_parent_id);

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
            throw new RuntimeException("タスクデータの解析に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * DTO → Entity の変換（tmp_id対応版）
     */
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
        
        // 追加: tmp_id の設定
        task.setTmpId(dto.tmp_id);
        
        return task;
    }

    /**
     * Entity → DTO の変換（tmp_id対応版）
     */
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
        
        // 追加: tmp_id の設定
        dto.tmp_id = entity.getTmpId();
        
        return dto;
    }

    /**
     * 文字列の日付をLocalDate型に変換するユーティリティメソッド
     */
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
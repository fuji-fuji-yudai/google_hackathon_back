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

            // プロンプト構築
            String prompt = buildWBSPrompt(excelData);

            // リクエストボディ構築
            JsonObject requestBody = createVertexAIRequest(prompt);

            // API呼び出し
            String endpoint = String.format(
                "https://us-central1-aiplatform.googleapis.com/v1/projects/%s/locations/us-central1/publishers/google/models/gemini-2.0-flash-001:generateContent",
                projectId
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

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
     * WBS生成用プロンプトを構築
     */
    private String buildWBSPrompt(Map<String, Object> excelData) {
        Gson gson = new Gson();
        String excelDataJson = gson.toJson(excelData);

        return "あなたはプロジェクト管理のエキスパートです。" +
                "与えられたExcelの機能一覧から、実用的なWBS（Work Breakdown Structure）タスクを自動作成してください。\n\n" +
                
                "【作成ルール】\n" +
                "1. 標準的な開発フェーズを含める：要件定義、設計、実装、テスト、リリース\n" +
                "2. 各機能を適切なフェーズに振り分ける\n" +
                "3. 大きな機能は複数のサブタスクに分割する\n" +
                "4. 親子関係を正しく設定する（parentIdは親タスクのid）\n" +
                "5. 現実的な作業期間を設定する（今日から開始）\n\n" +
                
                "【重要】以下のJSON配列形式のみを出力してください（前後の説明は不要）：\n" +
                "[\n" +
                "  {\n" +
                "    \"id\": 1,\n" +
                "    \"title\": \"要件定義フェーズ\",\n" +
                "    \"assignee\": \"PM\",\n" +
                "    \"parentId\": null,\n" +
                "    \"plan_start\": \"" + LocalDate.now().format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + LocalDate.now().plusWeeks(1).format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  }\n" +
                "]\n\n" +
                
                "【制約】\n" +
                "- IDは1から連番\n" +
                "- 親タスクのIDは子タスクより小さくする\n" +
                "- 日付はyyyy-MM-dd形式\n" +
                "- statusは\"ToDo\"固定\n" +
                "- JSONのみ出力（説明文なし）\n\n" +
                
                "解析対象のExcelデータ：\n" + excelDataJson;
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

        // 生成設定（JSON出力を促進）
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.1);
        generationConfig.addProperty("topK", 1);
        generationConfig.addProperty("topP", 0.8);
        generationConfig.addProperty("maxOutputTokens", 8192);
        requestBody.add("generationConfig", generationConfig);

        return requestBody;
    }

    /**
     * Vertex AIレスポンスからJSONを抽出
     */
    private String extractTaskJsonFromResponse(String responseBody) throws Exception {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray candidates = json.getAsJsonArray("candidates");

            if (candidates != null && candidates.size() > 0) {
                JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                JsonArray partsArray = content.getAsJsonArray("parts");
                String text = partsArray.get(0).getAsJsonObject().get("text").getAsString();
                
                // JSON部分のみを抽出
                int startIdx = text.indexOf('[');
                int endIdx = text.lastIndexOf(']') + 1;
                if (startIdx >= 0 && endIdx > startIdx) {
                    String jsonText = text.substring(startIdx, endIdx);
                    logger.debug("抽出されたJSON: {}", jsonText);
                    return jsonText;
                }
                
                logger.warn("JSONが見つかりませんでした。レスポンス全体: {}", text);
                return text;
            } else {
                logger.warn("Vertex AI API returned no candidates: {}", responseBody);
                throw new Exception("回答を生成できませんでした");
            }
        } catch (Exception e) {
            logger.error("Vertex AIレスポンスの解析に失敗しました", e);
            throw new Exception("レスポンス解析エラー: " + e.getMessage());
        }
    }

    /**
     * モックデータ生成
     */
    private String generateMockTaskData() {
        LocalDate now = LocalDate.now();
        return "[\n" +
                "  {\n" +
                "    \"id\": 1,\n" +
                "    \"title\": \"要件定義フェーズ\",\n" +
                "    \"assignee\": \"PM\",\n" +
                "    \"parentId\": null,\n" +
                "    \"plan_start\": \"" + now.format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + now.plusWeeks(1).format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 2,\n" +
                "    \"title\": \"基本設計フェーズ\",\n" +
                "    \"assignee\": \"設計担当\",\n" +
                "    \"parentId\": null,\n" +
                "    \"plan_start\": \"" + now.plusWeeks(1).format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + now.plusWeeks(2).format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 3,\n" +
                "    \"title\": \"ユーザー管理機能の要件定義\",\n" +
                "    \"assignee\": \"担当者A\",\n" +
                "    \"parentId\": 1,\n" +
                "    \"plan_start\": \"" + now.format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + now.plusDays(3).format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 4,\n" +
                "    \"title\": \"ログイン機能の要件定義\",\n" +
                "    \"assignee\": \"担当者B\",\n" +
                "    \"parentId\": 1,\n" +
                "    \"plan_start\": \"" + now.plusDays(3).format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + now.plusWeeks(1).format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  }\n" +
                "]";
    }

    /**
     * JSON文字列をTaskDtoのリストに変換
     */
    private List<TaskDto> parseTaskJson(String json) {
        try {
            Gson gson = new Gson();
            TaskDto[] taskArray = gson.fromJson(json, TaskDto[].class);
            List<TaskDto> tasks = new ArrayList<>();

            for (TaskDto task : taskArray) {
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

            return tasks;
        } catch (Exception e) {
            logger.error("JSONの解析に失敗しました: {}", e.getMessage());
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
        dto.plan_start = entity.getPlan_start() != null ? 
            entity.getPlan_start().format(DATE_FORMATTER) : "";
        dto.plan_end = entity.getPlan_end() != null ? 
            entity.getPlan_end().format(DATE_FORMATTER) : "";
        dto.actual_start = entity.getActual_start() != null ? 
            entity.getActual_start().format(DATE_FORMATTER) : "";
        dto.actual_end = entity.getActual_end() != null ? 
            entity.getActual_end().format(DATE_FORMATTER) : "";
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
package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.dto.TaskDto;
import com.example.google.google_hackathon.entity.Task;
import com.example.google.google_hackathon.repository.TaskManageRepository;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelAnalyzerService {

    // 日付フォーマット定義
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private TaskManageRepository taskManageRepository;

    // Gemini API キー
    @Value("${google.cloud.gemini.api-key:your-default-key}")
    private String geminiApiKey;

    // Excel解析メイン処理
    public List<TaskDto> analyzeExcel(MultipartFile file) throws Exception {
        // 1. Excelファイルを解析して構造化データを取得
        Map<String, Object> excelData = readExcelFile(file.getInputStream());
        // 2. Gemini APIにExcelデータを送信してタスク分割
        String taskJson = processWithGeminiAPI(excelData);
        // 3. JSONをTaskDtoのリストに変換
        return parseTaskJson(taskJson);
    }

    // Excelファイルを解析してMap形式で返す
    private Map<String, Object> readExcelFile(InputStream inputStream) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> sheets = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Map<String, Object> sheetData = new HashMap<>();
                List<Map<String, String>> rows = new ArrayList<>();

                // シート名を保存
                sheetData.put("name", sheet.getSheetName());

                // ヘッダー行の取得
                Row headerRow = sheet.getRow(0);
                List<String> headers = new ArrayList<>();
                if (headerRow != null) {
                    for (Cell cell : headerRow) {
                        headers.add(getCellValueAsString(cell));
                    }
                }

                // データ行の取得
                for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (row != null) {
                        Map<String, String> rowData = new HashMap<>();
                        for (int cellNum = 0; cellNum < headers.size(); cellNum++) {
                            Cell cell = row.getCell(cellNum);
                            if (cell != null) {
                                rowData.put(headers.get(cellNum), getCellValueAsString(cell));
                            }
                        }
                        rows.add(rowData);
                    }
                }

                sheetData.put("headers", headers);
                sheetData.put("rows", rows);
                sheets.add(sheetData);
            }
        }

        result.put("sheets", sheets);
        return result;
    }

    // セルの値を文字列として取得
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().format(DATE_FORMATTER);
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    // Gemini APIを使用してExcelデータを処理
    private String processWithGeminiAPI(Map<String, Object> excelData) throws IOException {
        // APIキーが設定されていない場合はローカルでモックデータを返す
        if (geminiApiKey == null || geminiApiKey.equals("your-default-key")) {
            System.out.println("APIキーが設定されていないため、モックデータを使用します");
            return generateMockTaskData();
        }

        // Gemini APIのエンドポイント
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key="
                + geminiApiKey);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // リクエストボディの作成
        Gson gson = new Gson();
        JsonObject requestBody = new JsonObject();

        // コンテンツ部分の作成
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();

        // テキスト部分（プロンプト）
        JsonObject textPart = new JsonObject();

        // Excel データを JSON 文字列に変換
        String excelDataJson = gson.toJson(excelData);

        String prompt = "あなたはシステム開発のWBS(Work Breakdown Structure)作成を支援するAIです。" +
                "与えられたExcelの機能一覧から、以下のようなWBSタスクを自動作成してください：\n\n" +
                "1. フェーズ/カテゴリ別に親タスクを作成（要件定義、基本設計、詳細設計、実装、テストなど）\n" +
                "2. 各機能を適切な親タスクの下に子タスクとして配置\n" +
                "3. 必要に応じて、機能をさらに小さなタスクに分割\n\n" +
                "以下の形式のJSONオブジェクトの配列を返してください：\n" +
                "[\n" +
                "  {\n" +
                "    \"id\": \"一意の識別子（整数）\",\n" +
                "    \"title\": \"タスク名\",\n" +
                "    \"assignee\": \"担当者（空白でOK）\",\n" +
                "    \"parentId\": \"親タスクのID（最上位タスクの場合はnull）\",\n" +
                "    \"plan_start\": \"yyyy-MM-dd形式の計画開始日（空でOK）\",\n" +
                "    \"plan_end\": \"yyyy-MM-dd形式の計画終了日（空でOK）\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  }\n" +
                "]\n\n" +
                "注意点：\n" +
                "- 機能がない場合は、標準的なWBS階層を作成してください\n" +
                "- 親子関係は正しくリンクしてください\n" +
                "- JSONのみを返してください（説明文は不要）\n\n" +
                "以下のExcelデータを解析してください：\n" + excelDataJson;

        textPart.addProperty("text", prompt);
        parts.add(textPart);

        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);

        // リクエストの送信
        try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
            writer.write(requestBody.toString());
            writer.flush();
        }

        // レスポンスの取得
        StringBuilder response = new StringBuilder();
        try (Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8.name())) {
            while (scanner.hasNextLine()) {
                response.append(scanner.nextLine());
            }
        }

        // レスポンスからJSONを抽出
        JsonObject responseJson = gson.fromJson(response.toString(), JsonObject.class);
        JsonArray candidates = responseJson.getAsJsonArray("candidates");

        if (candidates != null && candidates.size() > 0) {
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            JsonObject candidateContent = candidate.getAsJsonObject("content");
            JsonArray responseParts = candidateContent.getAsJsonArray("parts");

            if (responseParts != null && responseParts.size() > 0) {
                JsonObject responsePart = responseParts.get(0).getAsJsonObject();
                return responsePart.get("text").getAsString();
            }
        }

        throw new IOException("Gemini APIからの応答を解析できませんでした");
    }

    // APIキーがない場合のモックデータ生成
    private String generateMockTaskData() {
        // 現在日付を取得
        LocalDate now = LocalDate.now();

        // 今日から1週間後
        LocalDate oneWeekLater = now.plusWeeks(1);
        // 今日から2週間後
        LocalDate twoWeeksLater = now.plusWeeks(2);
        // 今日から3週間後
        LocalDate threeWeeksLater = now.plusWeeks(3);

        // サンプルのWBSタスクデータ
        return "[\n" +
                "  {\n" +
                "    \"id\": 1,\n" +
                "    \"title\": \"要件定義\",\n" +
                "    \"assignee\": \"PM\",\n" +
                "    \"parentId\": null,\n" +
                "    \"plan_start\": \"" + now.format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + oneWeekLater.format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 2,\n" +
                "    \"title\": \"基本設計\",\n" +
                "    \"assignee\": \"設計担当\",\n" +
                "    \"parentId\": null,\n" +
                "    \"plan_start\": \"" + oneWeekLater.format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + twoWeeksLater.format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 3,\n" +
                "    \"title\": \"詳細設計\",\n" +
                "    \"assignee\": \"設計担当\",\n" +
                "    \"parentId\": null,\n" +
                "    \"plan_start\": \"" + twoWeeksLater.format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + threeWeeksLater.format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 4,\n" +
                "    \"title\": \"ログイン機能の要件定義\",\n" +
                "    \"assignee\": \"担当者A\",\n" +
                "    \"parentId\": 1,\n" +
                "    \"plan_start\": \"" + now.format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + now.plusDays(3).format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 5,\n" +
                "    \"title\": \"検索機能の要件定義\",\n" +
                "    \"assignee\": \"担当者B\",\n" +
                "    \"parentId\": 1,\n" +
                "    \"plan_start\": \"" + now.plusDays(3).format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + oneWeekLater.format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 6,\n" +
                "    \"title\": \"ログイン機能の基本設計\",\n" +
                "    \"assignee\": \"担当者A\",\n" +
                "    \"parentId\": 2,\n" +
                "    \"plan_start\": \"" + oneWeekLater.format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + oneWeekLater.plusDays(3).format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 7,\n" +
                "    \"title\": \"検索機能の基本設計\",\n" +
                "    \"assignee\": \"担当者B\",\n" +
                "    \"parentId\": 2,\n" +
                "    \"plan_start\": \"" + oneWeekLater.plusDays(3).format(DATE_FORMATTER) + "\",\n" +
                "    \"plan_end\": \"" + twoWeeksLater.format(DATE_FORMATTER) + "\",\n" +
                "    \"actual_start\": \"\",\n" +
                "    \"actual_end\": \"\",\n" +
                "    \"status\": \"ToDo\"\n" +
                "  }\n" +
                "]";
    }

    // JSON文字列をTaskDtoのリストに変換
    private List<TaskDto> parseTaskJson(String json) {
        List<TaskDto> tasks = new ArrayList<>();
        Gson gson = new Gson();

        try {
            // [ と ] で囲まれたJSON配列だけを抽出
            String cleanedJson = json.trim();
            int startIdx = cleanedJson.indexOf('[');
            int endIdx = cleanedJson.lastIndexOf(']') + 1;

            if (startIdx >= 0 && endIdx > startIdx) {
                cleanedJson = cleanedJson.substring(startIdx, endIdx);
            }

            TaskDto[] taskArray = gson.fromJson(cleanedJson, TaskDto[].class);

            // 一時的なIDを割り当て（DB保存時に実際のIDが割り当てられる）
            LocalDate now = LocalDate.now();
            String defaultDate = now.format(DATE_FORMATTER);

            for (int i = 0; i < taskArray.length; i++) {
                TaskDto task = taskArray[i];

                // 生成されたIDが数値でない場合は一時的なIDを割り当て
                if (task.id == null) {
                    task.id = -1 * (i + 1); // 負の値を一時IDとして使用
                }

                // 日付が指定されていない場合はデフォルト値を設定
                if (task.plan_start == null || task.plan_start.isEmpty()) {
                    task.plan_start = defaultDate;
                }

                if (task.plan_end == null || task.plan_end.isEmpty()) {
                    task.plan_end = defaultDate;
                }

                // 必須フィールドの初期化
                if (task.status == null || task.status.isEmpty()) {
                    task.status = "ToDo";
                }

                if (task.assignee == null) {
                    task.assignee = "";
                }

                // 空の値を初期化
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
            System.err.println("JSONの解析に失敗しました: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * 分析したExcelデータをタスクとしてデータベースに保存
     */
    public List<TaskDto> saveExcelTasks(List<TaskDto> taskDtos) {
        // DTOをエンティティに変換
        List<Task> tasks = taskDtos.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());

        // リポジトリを使用して保存
        List<Task> savedTasks = taskManageRepository.saveAll(tasks);

        // 保存後のエンティティをDTOに変換して返す
        return savedTasks.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * DTOをエンティティに変換
     */
    private Task convertToEntity(TaskDto dto) {
        Task task = new Task();

        if (dto.id != null && dto.id > 0) {
            task.setId(dto.id);
        }

        task.setTitle(dto.title);
        task.setAssignee(dto.assignee);

        // 文字列の日付をLocalDate型に変換
        if (dto.plan_start != null && !dto.plan_start.isEmpty()) {
            task.setPlan_start(parseDate(dto.plan_start));
        }

        if (dto.plan_end != null && !dto.plan_end.isEmpty()) {
            task.setPlan_end(parseDate(dto.plan_end));
        }

        if (dto.actual_start != null && !dto.actual_start.isEmpty()) {
            task.setActual_start(parseDate(dto.actual_start));
        }

        if (dto.actual_end != null && !dto.actual_end.isEmpty()) {
            task.setActual_end(parseDate(dto.actual_end));
        }

        task.setStatus(dto.status);
        task.setParentId(dto.parent_id);

        return task;
    }

    /**
     * エンティティをDTOに変換
     */
    private TaskDto convertToDto(Task entity) {
        TaskDto dto = new TaskDto();
        dto.id = entity.getId();
        dto.title = entity.getTitle();
        dto.assignee = entity.getAssignee();

        // LocalDate型を文字列に変換
        if (entity.getPlan_start() != null) {
            dto.plan_start = entity.getPlan_start().format(DATE_FORMATTER);
        }

        if (entity.getPlan_end() != null) {
            dto.plan_end = entity.getPlan_end().format(DATE_FORMATTER);
        }

        if (entity.getActual_start() != null) {
            dto.actual_start = entity.getActual_start().format(DATE_FORMATTER);
        } else {
            dto.actual_start = "";
        }

        if (entity.getActual_end() != null) {
            dto.actual_end = entity.getActual_end().format(DATE_FORMATTER);
        } else {
            dto.actual_end = "";
        }

        dto.status = entity.getStatus();
        dto.parent_id = entity.getParentId();

        return dto;
    }

    // 文字列の日付をLocalDate型に変換するユーティリティメソッド
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            // 日付形式が異なる場合はnullを返す
            return null;
        }
    }
}
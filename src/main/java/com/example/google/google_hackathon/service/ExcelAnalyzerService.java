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
     * ExcelからのタスクをDBに保存する（tmp_id方式・デバッグ強化版）
     */
    public List<TaskDto> saveExcelTasks(List<TaskDto> taskDtos) {
        logger.info("=== tmp_id方式でExcelタスク保存開始 ===");
        logger.info("保存対象タスク数: {}", taskDtos.size());

        try {
            // 受信データの詳細確認
            logger.info("=== 受信データ詳細確認 ===");
            for (int i = 0; i < taskDtos.size(); i++) {
                TaskDto dto = taskDtos.get(i);
                logger.info("タスク{}: title='{}', tmp_id={}, tmp_parent_id={}",
                        i + 1, dto.title, dto.tmp_id, dto.tmp_parent_id);
            }

            // Step 1: 親タスクを先に保存（tmp_parent_id が null のもの）
            Map<Integer, Integer> tmpIdToRealIdMap = new HashMap<>();
            List<Task> allSavedTasks = new ArrayList<>();

            // 親タスクの保存
            logger.info("=== Step 1: 親タスク保存 ===");
            List<TaskDto> parentTasks = taskDtos.stream()
                    .filter(dto -> dto.tmp_parent_id == null)
                    .collect(Collectors.toList());

            logger.info("親タスク数: {}", parentTasks.size());

            for (TaskDto dto : parentTasks) {
                Task entity = convertToEntity(dto);
                entity.setId(null); // 自動採番
                entity.setParentId(null); // 親タスクなのでnull
                entity.setTmpId(dto.tmp_id);

                Task saved = taskManageRepository.save(entity);
                allSavedTasks.add(saved);

                if (dto.tmp_id != null) {
                    tmpIdToRealIdMap.put(dto.tmp_id, saved.getId());
                    logger.info("親タスクIDマッピング: tmp_id:{} -> real_id:{} ({})",
                            dto.tmp_id, saved.getId(), dto.title);
                }
            }

            // Step 2: 子タスクの保存（tmp_parent_id が設定されているもの）
            logger.info("=== Step 2: 子タスク保存 ===");
            List<TaskDto> childTasks = taskDtos.stream()
                    .filter(dto -> dto.tmp_parent_id != null)
                    .collect(Collectors.toList());

            logger.info("子タスク数: {}", childTasks.size());

            for (TaskDto dto : childTasks) {
                Task entity = convertToEntity(dto);
                entity.setId(null); // 自動採番
                entity.setTmpId(dto.tmp_id);

                // 親IDの解決
                Integer realParentId = tmpIdToRealIdMap.get(dto.tmp_parent_id);
                if (realParentId != null) {
                    entity.setParentId(realParentId);
                    logger.info("子タスク親ID設定: {} -> tmp_parent_id:{} -> real_parent_id:{}",
                            dto.title, dto.tmp_parent_id, realParentId);
                } else {
                    logger.error("親タスクが見つからない: {} -> tmp_parent_id:{}",
                            dto.title, dto.tmp_parent_id);
                    logger.error("利用可能なtmp_id: {}", tmpIdToRealIdMap.keySet());
                    // 親が見つからない場合はnullにして続行
                    entity.setParentId(null);
                }

                Task saved = taskManageRepository.save(entity);
                allSavedTasks.add(saved);

                if (dto.tmp_id != null) {
                    tmpIdToRealIdMap.put(dto.tmp_id, saved.getId());
                    logger.info("子タスクIDマッピング: tmp_id:{} -> real_id:{} ({})",
                            dto.tmp_id, saved.getId(), dto.title);
                }
            }

            // Step 3: 保存結果の確認
            logger.info("=== Step 3: 保存結果確認 ===");
            logger.info("総保存タスク数: {}", allSavedTasks.size());

            for (Task task : allSavedTasks) {
                logger.info("保存済みタスク: id={}, title='{}', parent_id={}, tmp_id={}",
                        task.getId(), task.getTitle(), task.getParentId(), task.getTmpId());
            }

            // Step 4: 最終結果を返す
            List<TaskDto> result = allSavedTasks.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            logger.info("=== 保存完了: 総タスク数:{} ===", result.size());

            // 結果の階層構造確認
            long parentCount = result.stream().filter(dto -> dto.parent_id == null).count();
            long childCount = result.stream().filter(dto -> dto.parent_id != null).count();
            logger.info("結果: 親タスク{}個, 子タスク{}個", parentCount, childCount);

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

            // プロンプト構築（tmp_id対応版）
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
     * 高度なWBS生成プロンプトを構築（tmp_id対応版）
     */
    private String buildAdvancedWBSPrompt(Map<String, Object> excelData) {
        Gson gson = new Gson();
        String excelDataJson = gson.toJson(excelData);
        LocalDate startDate = LocalDate.now();

        // Excelデータが有効かチェック
        if (!isValidExcelData(excelData)) {
            logger.error("Excelファイルからデータを取得できませんでした");
            throw new RuntimeException("Excelファイルにデータが含まれていません。機能一覧、難易度、種別などのデータを含むExcelファイルをアップロードしてください。");
        }

        // Excelデータの特性を事前分析
        String functionalAnalysis = analyzeFunctionalCharacteristics(excelData);

        return String.format("""
                あなたはプロジェクト管理とソフトウェア開発のエキスパートです。
                提供されたExcelファイルの機能一覧を分析し、各機能の特性に基づいた最適なWBS（階層構造付き）を生成してください。

                【分析されたExcelの特性】
                %s

                【WBS生成の戦略的原則】
                1. **明確な階層構造**: 各フェーズを親タスクとし、個別機能のタスクを子タスクとする
                2. **機能の依存関係分析**: 機能間の論理的依存関係を特定
                3. **リスク評価**: 難易度と複雑さからリスクレベルを評価
                4. **リソース最適化**: 担当者のスキルと負荷を考慮した配置

                【必須の階層構造ルール】
                以下のフェーズ構造に沿って親子関係を明確に設定してください：

                **親タスク（フェーズ）**:
                - tmp_id 1: 要件定義フェーズ（tmp_parent_id: null）
                - tmp_id 12: 基本設計フェーズ（tmp_parent_id: null）
                - tmp_id 23: 詳細設計フェーズ（tmp_parent_id: null）
                - tmp_id 34: 実装フェーズ（tmp_parent_id: null）
                - tmp_id 45: 結合テストフェーズ（tmp_parent_id: null）
                - tmp_id 51: システムテストフェーズ（tmp_parent_id: null）
                - tmp_id 54: リリース準備フェーズ（tmp_parent_id: null）

                **子タスク（個別機能）**:
                - 要件定義関連タスク → tmp_parent_id: 1 を設定
                - 基本設計関連タスク → tmp_parent_id: 12 を設定
                - 詳細設計関連タスク → tmp_parent_id: 23 を設定
                - 実装関連タスク → tmp_parent_id: 34 を設定
                - 結合テスト関連タスク → tmp_parent_id: 45 を設定
                - システムテスト関連タスク → tmp_parent_id: 51 を設定
                - リリース準備関連タスク → tmp_parent_id: 54 を設定

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

                【重要】以下のJSON配列形式のみを出力してください（前後の説明文は不要）：

                [
                  {
                    "tmp_id": 1,
                    "title": "要件定義フェーズ",
                    "assignee": "PM",
                    "tmp_parent_id": null,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "tmp_id": 2,
                    "title": "[機能名]の要件定義",
                    "assignee": "担当者A",
                    "tmp_parent_id": 1,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "tmp_id": 12,
                    "title": "基本設計フェーズ",
                    "assignee": "PM",
                    "tmp_parent_id": null,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "tmp_id": 13,
                    "title": "[機能名]の基本設計",
                    "assignee": "担当者A",
                    "tmp_parent_id": 12,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  }
                ]

                【重要な制約】
                - tmp_idは1から連番で設定
                - **tmp_parent_idを必ず正しく設定する**（フェーズタスクはnull、個別機能タスクは対応するフェーズのtmp_idを指定）
                - 日付はyyyy-MM-dd形式（%s から開始）
                - statusは"ToDo"固定
                - 機能名は元データから動的に取得して使用
                - JSONのみ出力（説明文なし）
                - id、parent_idフィールドは出力しないでください（tmp_id、tmp_parent_idのみ使用）

                【解析対象データ】
                %s
                """,
                functionalAnalysis,
                startDate.format(DATE_FORMATTER),
                startDate.plusWeeks(1).format(DATE_FORMATTER),
                startDate.format(DATE_FORMATTER),
                startDate.plusDays(3).format(DATE_FORMATTER),
                startDate.plusWeeks(1).format(DATE_FORMATTER),
                startDate.plusWeeks(2).format(DATE_FORMATTER),
                startDate.plusWeeks(1).plusDays(1).format(DATE_FORMATTER),
                startDate.plusWeeks(1).plusDays(3).format(DATE_FORMATTER),
                startDate.format(DATE_FORMATTER),
                excelDataJson);
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
     * モックデータ生成（tmp_id対応版）
     */
    private String generateMockTaskData() {
        LocalDate now = LocalDate.now();
        return String.format("""
                [
                  {
                    "tmp_id": 1,
                    "title": "要件定義フェーズ",
                    "assignee": "PM",
                    "tmp_parent_id": null,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "tmp_id": 2,
                    "title": "機能Aの要件定義",
                    "assignee": "担当者A",
                    "tmp_parent_id": 1,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "tmp_id": 3,
                    "title": "機能Bの要件定義",
                    "assignee": "担当者B",
                    "tmp_parent_id": 1,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "tmp_id": 12,
                    "title": "基本設計フェーズ",
                    "assignee": "PM",
                    "tmp_parent_id": null,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "tmp_id": 13,
                    "title": "機能Aの基本設計",
                    "assignee": "担当者A",
                    "tmp_parent_id": 12,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  },
                  {
                    "tmp_id": 14,
                    "title": "機能Bの基本設計",
                    "assignee": "担当者B",
                    "tmp_parent_id": 12,
                    "plan_start": "%s",
                    "plan_end": "%s",
                    "actual_start": "",
                    "actual_end": "",
                    "status": "ToDo"
                  }
                ]""",
                now.format(DATE_FORMATTER),
                now.plusWeeks(1).format(DATE_FORMATTER),
                now.format(DATE_FORMATTER),
                now.plusDays(2).format(DATE_FORMATTER),
                now.plusDays(2).format(DATE_FORMATTER),
                now.plusDays(4).format(DATE_FORMATTER),
                now.plusWeeks(1).format(DATE_FORMATTER),
                now.plusWeeks(2).format(DATE_FORMATTER),
                now.plusWeeks(1).format(DATE_FORMATTER),
                now.plusWeeks(1).plusDays(3).format(DATE_FORMATTER),
                now.plusWeeks(1).plusDays(3).format(DATE_FORMATTER),
                now.plusWeeks(2).format(DATE_FORMATTER));
    }

    /**
     * JSON文字列をTaskDtoのリストに変換（tmp_id対応版）
     */
    private List<TaskDto> parseTaskJson(String json) {
        try {
            logger.info("=== JSON解析開始 ===");
            logger.info("解析対象JSON: {}", json);

            // 空文字列やnullのチェック
            if (json == null || json.trim().isEmpty()) {
                logger.warn("JSONが空です。モックデータを返します。");
                return parseTaskJson(generateMockTaskData());
            }

            // JSON配列の形式かチェック
            String trimmedJson = json.trim();
            if (!trimmedJson.startsWith("[") || !trimmedJson.endsWith("]")) {
                logger.warn("JSONが配列形式ではありません。内容: {}", trimmedJson);
                logger.warn("Vertex AIがJSON以外の応答を返しました。モックデータを返します。");
                return parseTaskJson(generateMockTaskData());
            }

            Gson gson = new Gson();
            TaskDto[] taskArray = gson.fromJson(trimmedJson, TaskDto[].class);

            if (taskArray == null) {
                logger.error("Gsonがnullを返しました");
                return parseTaskJson(generateMockTaskData());
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
            logger.warn("解析に失敗したため、モックデータを返します");

            // 解析に失敗した場合はモックデータを返す
            try {
                return parseTaskJson(generateMockTaskData());
            } catch (Exception mockError) {
                logger.error("モックデータの生成にも失敗しました: {}", mockError.getMessage());
                return Collections.emptyList();
            }
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
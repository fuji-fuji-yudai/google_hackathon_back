package com.example.google.google_hackathon.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/monthly-data") // ★このパスをVue.js側でも使用する
@CrossOrigin(origins = "http://localhost:8080")
public class MonthlyGraphDataController { // ★ファイル名・クラス名

    @GetMapping("/graph") // ★このパスをVue.js側でも使用する
    public List<Map<String, Object>> getMonthlyGraphData() {
        List<Map<String, Object>> data = new ArrayList<>();
        String[] months = {
                "1月", "2月", "3月", "4月", "5月", "6月",
                "7月", "8月", "9月", "10月", "11月", "12月"
        };

        for (String month : months) {
            data.add(createDataEntry(month, null)); // nullを返すことでグラフに棒が表示されないようにする
        }

        return data;
    }

    private Map<String, Object> createDataEntry(String label, Integer value) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("label", label);
        entry.put("value", value);
        return entry;
    }
}
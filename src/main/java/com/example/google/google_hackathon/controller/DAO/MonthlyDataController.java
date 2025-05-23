package com.example.google.google_hackathon.controller.DAO;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/monthly-data")
@CrossOrigin(origins = "http://localhost:8080")
public class MonthlyDataController {

    @GetMapping
    public List<MonthlyData> getMonthlyData() {
        List<MonthlyData> data = new ArrayList<>();
        data.add(new MonthlyData("1月", 10.0));
        data.add(new MonthlyData("2月", 12.0));
        data.add(new MonthlyData("3月", 8.0));
        data.add(new MonthlyData("4月", 15.0));
        data.add(new MonthlyData("5月", 11.0));
        data.add(new MonthlyData("6月", 9.0));
        data.add(new MonthlyData("7月", 13.0));
        data.add(new MonthlyData("8月", 16.0));
        data.add(new MonthlyData("9月", 14.0));
        data.add(new MonthlyData("10月", 17.0));
        data.add(new MonthlyData("11月", 10.0));
        data.add(new MonthlyData("12月", 18.0));
        return data;
    }
}
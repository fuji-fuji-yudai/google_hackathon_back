package com.example.google.google_hackathon.controller;

public class MonthlyData {
    private String month;
    private double value; // 現時点では仮の値

    public MonthlyData(String month, double value) {
        this.month = month;
        this.value = value;
    }

    public String getMonth() {
        return month;
    }

    public double getValue() {
        return value;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public void setValue(double value) {
        this.value = value;
    }
}
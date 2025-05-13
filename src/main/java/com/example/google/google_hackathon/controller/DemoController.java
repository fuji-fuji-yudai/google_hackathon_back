package com.example.google.google_hackathon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DemoController {
    
    @GetMapping("/demo")
    private String display(){
        return "/index.html";
    }
}
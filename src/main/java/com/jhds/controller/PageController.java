package com.jhds.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/patrol")
    public String patrol() {
        return "patrol";
    }

    @GetMapping("/weather")
    public String weather() {
        return "weather";
    }

    @GetMapping("/nutrient")
    public String nutrient() {
        return "nutrient";
    }

    @GetMapping("/insect")
    public String insect() {
        return "insect";
    }

    @GetMapping("/alarm")
    public String alarm() {
        return "alarm";
    }

    @GetMapping("/iot")
    public String iot() {
        return "iot";
    }

    @GetMapping("/ai")
    public String ai() {
        return "ai";
    }

    @GetMapping("/ai-learn")
    public String aiLearn() {
        return "ai-learn";
    }

}

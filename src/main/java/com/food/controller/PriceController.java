package com.food.controller;

import com.food.service.PriceScraperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.regex.Pattern;

@Controller
public class PriceController {

    // Only allow alphanumeric, spaces, and common food characters
    private static final Pattern SAFE_INPUT = Pattern.compile("^[a-zA-Z0-9 \\-']{1,50}$");

    @Autowired
    private PriceScraperService priceScraperService;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/compare")
    public String compareView(@RequestParam String item, Model model) {
        String sanitized = sanitizeInput(item);
        if (sanitized == null) {
            model.addAttribute("error", "Invalid search input. Only letters, numbers, spaces, and hyphens are allowed (max 50 characters).");
            return "index";
        }
        Map<String, Object> results = priceScraperService.getPrices(sanitized);
        model.addAttribute("results", results);
        model.addAttribute("item", sanitized);
        return "compare";
    }

    @GetMapping("/api/compare")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> compareApi(@RequestParam String item) {
        String sanitized = sanitizeInput(item);
        if (sanitized == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid input. Only letters, numbers, spaces, and hyphens are allowed (max 50 characters)."));
        }
        Map<String, Object> results = priceScraperService.getPrices(sanitized);
        return ResponseEntity.ok(results);
    }

    /**
     * Validates and sanitizes user input to prevent injection attacks.
     * Returns null if the input is invalid.
     */
    private String sanitizeInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        if (!SAFE_INPUT.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }
}

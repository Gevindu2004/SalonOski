package com.saloonx.saloonx.controller;

import com.saloonx.saloonx.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeFace(@RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select an image.");
        }

        try {
            // 1. Detect Face Shape (Simulated)
            String faceShape = aiService.detectFaceShape(image);

            // 2. Get Recommendation from Gemini
            String recommendation = aiService.getHairstyleRecommendation(faceShape);

            // Return JSON response
            return ResponseEntity.ok(Map.of(
                    "faceShape", faceShape,
                    "recommendation", recommendation));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing image: " + e.getMessage());
        }
    }
}

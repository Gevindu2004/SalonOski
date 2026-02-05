package com.saloonx.saloonx.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.List;
import java.util.Base64;

@Service
public class AiService {

    private static final String GEMINI_API_KEY = "AIzaSyDsYqbpxIWxPoThO5kSAb5CjjxtOqreZbo";

    private static final String GEMINI_MODEL = "gemini-3-flash-preview";

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
            + GEMINI_MODEL + ":generateContent?key=" + GEMINI_API_KEY;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

 
    public String detectFaceShape(MultipartFile image) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());
            String prompt = "Analyze this face and identify the face shape. Return ONLY one word from this list: Oval, Round, Square, Diamond, Heart, Oblong.";
            String mimeType = image.getContentType() != null ? image.getContentType() : "image/jpeg";
            Map<String, Object> payload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt),
                                    Map.of("inline_data", Map.of(
                                            "mime_type", mimeType,
                                            "data", base64Image))))));

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_BASE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseGeminiResponse(response.body());
            } else {
                return "Error: " + response.statusCode();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to analyze face.";
        }
    }

    /**
     * Calls Gemini API to get hairstyle recommendations based on face shape.
     */
    public String getHairstyleRecommendation(String faceShape) {
        String prompt = "Suggest 3 trendy hairstyles for a man with a " + faceShape + " face shape. " +
                "Format the output as a simple list. Keep it concise.";

        try {
            // Create JSON Payload
            Map<String, Object> payload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)))));

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_BASE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseGeminiResponse(response.body());
            } else {
                return "Error from AI: " + response.body();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to connect to AI Service.";
        }
    }

    private String parseGeminiResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }
            return "No recommendation found.";
        } catch (Exception e) {
            return "Error parsing AI response.";
        }
    }
}
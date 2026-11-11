package com.smartparking.ai.infrastructure.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@Slf4j
public class GeminiApiClient {

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-1.5-flash}")
    private String model;

    @Value("${ai.gemini.temperature:0.3}")
    private Double temperature;

    @Value("${ai.gemini.max-tokens:2048}")
    private Integer maxTokens;

    @Value("${ai.gemini.vision-read-timeout-ms:25000}")
    private Integer visionReadTimeoutMs;

    private final RestTemplate restTemplate;
    private final RestTemplate visionRestTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiApiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); // 3 giây kết nối
        factory.setReadTimeout(5000); // 5 giây đọc phản hồi tối đa
        this.restTemplate = new RestTemplate(factory);

        SimpleClientHttpRequestFactory visionFactory = new SimpleClientHttpRequestFactory();
        visionFactory.setConnectTimeout(5000);
        visionFactory.setReadTimeout(25000); // 25 giây tối đa cho Gemini Vision
        this.visionRestTemplate = new RestTemplate(visionFactory);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("demo-key");
    }

    private String getEffectiveModel() {
        if (model == null || model.isBlank() || model.contains("3.8")) {
            // gemini-1.5-flash là model chuẩn có độ trễ cực thấp (~800ms) của Google
            return "gemini-1.5-flash";
        }
        return model;
    }

    public Optional<String> generateContent(String systemInstruction, String userPrompt) {
        if (!isConfigured()) {
            log.info("Gemini API key not configured, using smart fallback engine");
            return Optional.empty();
        }

        String effectiveModel = getEffectiveModel();

        // 1. Thử gọi model chính thức
        Optional<String> result = callGeminiApi(effectiveModel, systemInstruction, userPrompt);
        if (result.isPresent()) {
            return result;
        }

        // 2. Nếu model chính thức bị 503 (Overloaded), thử gọi model dự phòng
        // gemini-2.0-flash hoặc gemini-1.5-flash
        if (!"gemini-2.0-flash".equalsIgnoreCase(effectiveModel)) {
            log.info("Model '{}' is unavailable, attempting fast fallback to 'gemini-2.0-flash'...", effectiveModel);
            Optional<String> backupResult = callGeminiApi("gemini-2.0-flash", systemInstruction, userPrompt);
            if (backupResult.isPresent()) {
                return backupResult;
            }
        }

        log.warn("Gemini API unavailable. Seamlessly activating Smart Internal Fallback Engine.");
        return Optional.empty();
    }

    public Optional<String> generateMultimodalContent(
            String systemInstruction,
            String userPrompt,
            String mimeType,
            byte[] imageBytes) {
        if (!isConfigured()) {
            log.warn("Gemini API key not configured for multimodal layout extraction");
            return Optional.empty();
        }
        if (imageBytes == null || imageBytes.length == 0) {
            return Optional.empty();
        }

        int totalTimeoutMs = (visionReadTimeoutMs != null && visionReadTimeoutMs > 0)
                ? visionReadTimeoutMs
                : 25000;
        long deadline = System.currentTimeMillis() + totalTimeoutMs;

        String effectiveModel = getEffectiveModel();
        long remainingMs = deadline - System.currentTimeMillis();
        if (remainingMs <= 500) {
            log.warn("Vision extraction deadline budget exhausted before primary call");
            return Optional.empty();
        }

        int primaryReadTimeout = (int) Math.min(totalTimeoutMs, remainingMs);
        int primaryConnectTimeout = (int) Math.min(5000, remainingMs);

        Optional<String> result = callGeminiVisionApi(
                effectiveModel, systemInstruction, userPrompt, mimeType, imageBytes,
                primaryConnectTimeout, primaryReadTimeout);
        if (result.isPresent()) {
            return result;
        }

        remainingMs = deadline - System.currentTimeMillis();
        if (remainingMs <= 1000) {
            log.warn("Vision extraction deadline budget exhausted (remaining={}ms); skipping fallback", remainingMs);
            return Optional.empty();
        }

        if (!"gemini-2.0-flash".equalsIgnoreCase(effectiveModel)) {
            log.info(
                    "Vision model '{}' is unavailable, attempting fallback to 'gemini-2.0-flash' with remaining budget {}ms",
                    effectiveModel, remainingMs);
            int fallbackReadTimeout = (int) remainingMs;
            int fallbackConnectTimeout = (int) Math.min(3000, remainingMs);
            return callGeminiVisionApi(
                    "gemini-2.0-flash", systemInstruction, userPrompt, mimeType, imageBytes,
                    fallbackConnectTimeout, fallbackReadTimeout);
        }

        return Optional.empty();
    }

    protected RestTemplate createVisionRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(500, connectTimeoutMs));
        factory.setReadTimeout(Math.max(500, readTimeoutMs));
        return new RestTemplate(factory);
    }

    private Optional<String> callGeminiApi(String targetModel, String systemInstruction, String userPrompt) {
        try {
            String url = String.format(
                    "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                    targetModel, apiKey);

            Map<String, Object> requestBody = new HashMap<>();

            // System instruction
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                requestBody.put("system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))));
            }

            // User content
            requestBody.put("contents", List.of(
                    Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", userPrompt)))));

            // Generation config
            requestBody.put("generationConfig", Map.of(
                    "temperature", temperature != null ? temperature : 0.3,
                    "maxOutputTokens", maxTokens != null ? maxTokens : 2048));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        return Optional.of(textNode.asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Gemini API call failed for model '{}': {}", targetModel, e.getMessage());
        }

        return Optional.empty();
    }

    private Optional<String> callGeminiVisionApi(
            String targetModel,
            String systemInstruction,
            String userPrompt,
            String mimeType,
            byte[] imageBytes,
            int connectTimeoutMs,
            int readTimeoutMs) {
        try {
            String url = String.format(
                    "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                    targetModel, apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                requestBody.put("system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))));
            }

            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> inlineDataPart = Map.of(
                    "inline_data", Map.of(
                            "mime_type", mimeType,
                            "data", base64Image));
            Map<String, Object> textPart = Map.of("text",
                    userPrompt != null ? userPrompt : "Extract parking layout JSON.");

            requestBody.put("contents", List.of(
                    Map.of(
                            "role", "user",
                            "parts", List.of(textPart, inlineDataPart))));

            requestBody.put("generationConfig", Map.of(
                    "temperature", 0.1,
                    "maxOutputTokens", 4096,
                    "responseMimeType", "application/json"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            RestTemplate customTemplate = createVisionRestTemplate(connectTimeoutMs, readTimeoutMs);
            ResponseEntity<String> response = customTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        return Optional.of(textNode.asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Gemini Vision API call failed for model '{}' (mime={}, bytes={})",
                    targetModel, mimeType, imageBytes.length);
        }

        return Optional.empty();
    }
}

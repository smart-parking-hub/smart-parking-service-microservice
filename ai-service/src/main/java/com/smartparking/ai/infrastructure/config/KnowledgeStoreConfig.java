package com.smartparking.ai.infrastructure.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
@Slf4j
@Getter
public class KnowledgeStoreConfig {

    private final List<PolicyChunk> policyChunks = new ArrayList<>();

    public record PolicyChunk(String title, String content) {}

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("docs/parking-policies-faq.md");
            if (!resource.exists()) {
                log.warn("parking-policies-faq.md not found, knowledge store empty");
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String fullText = reader.lines().collect(Collectors.joining("\n"));
                String[] sections = fullText.split("\n## ");
                for (String section : sections) {
                    if (section.isBlank()) continue;
                    String[] lines = section.split("\n", 2);
                    String title = lines[0].replace("#", "").trim();
                    String content = lines.length > 1 ? lines[1].trim() : "";
                    policyChunks.add(new PolicyChunk(title, content));
                }
            }
            log.info("Initialized In-Memory Policy Knowledge Store with {} sections", policyChunks.size());
        } catch (Exception e) {
            log.error("Failed to load policy knowledge base: {}", e.getMessage());
        }
    }

    /**
     * Search relevant policy sections based on keywords in query
     */
    public Optional<PolicyChunk> searchRelevantPolicy(String query) {
        if (query == null || policyChunks.isEmpty()) return Optional.empty();
        String lowerQuery = query.toLowerCase();

        return policyChunks.stream()
                .filter(chunk -> {
                    String combined = (chunk.title() + " " + chunk.content()).toLowerCase();
                    // Match keywords: hủy, hold, 10 phút, phạt, quá giờ, check in, check out, pccc, bồi thường, khiếu nại, refund, hoàn tiền
                    return (lowerQuery.contains("hủy") && combined.contains("hủy"))
                            || (lowerQuery.contains("giữ chỗ") && combined.contains("giữ chỗ"))
                            || (lowerQuery.contains("10 phút") && combined.contains("10 phút"))
                            || (lowerQuery.contains("quá giờ") && combined.contains("quá giờ"))
                            || (lowerQuery.contains("muộn") && combined.contains("quá giờ"))
                            || (lowerQuery.contains("check-in") && combined.contains("check-in"))
                            || (lowerQuery.contains("qr") && combined.contains("qr"))
                            || (lowerQuery.contains("bồi thường") && combined.contains("bồi thường"))
                            || (lowerQuery.contains("khiếu nại") && combined.contains("khiếu nại"))
                            || (lowerQuery.contains("hoàn tiền") && combined.contains("hoàn tiền"))
                            || (lowerQuery.contains("pccc") && combined.contains("pccc"));
                })
                .findFirst();
    }
}

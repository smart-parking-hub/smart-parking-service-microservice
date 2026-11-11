package com.smartparking.payment.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Converter
public class EvidenceUrlsConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            List<String> sanitized = attribute.stream()
                    .filter(Objects::nonNull)
                    .toList();
            if (sanitized.isEmpty()) {
                return null;
            }
            return OBJECT_MAPPER.writeValueAsString(sanitized);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize evidenceUrls to JSON", ex);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(dbData);
            if (node == null || node.isNull()) {
                return Collections.emptyList();
            }
            // H2 JSON columns bound as VARCHAR return a JSON-encoded string literal when read back.
            if (node.isTextual()) {
                String nested = node.asText();
                if (nested == null || nested.isBlank()) {
                    return Collections.emptyList();
                }
                node = OBJECT_MAPPER.readTree(nested);
            }
            if (node == null || !node.isArray()) {
                throw new IllegalStateException("Expected JSON array for evidenceUrls");
            }
            if (node.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>(node.size());
            for (JsonNode element : node) {
                if (element != null && !element.isNull()) {
                    result.add(element.asText());
                }
            }
            return Collections.unmodifiableList(result);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to deserialize evidenceUrls from JSON", ex);
        }
    }
}

package com.smartparking.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.ai.dto.MockDataDto;
import com.smartparking.ai.dto.ParkingDescriptionDto;
import com.smartparking.ai.infrastructure.gemini.GeminiApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiContentGenerationService {

    private final GeminiApiClient geminiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParkingDescriptionDto.Response generateParkingContent(ParkingDescriptionDto.Request request) {
        String systemInstruction = """
            Bạn là Chuyên gia Vận hành và Truyền thông Bãi đỗ xe Đô thị tại Việt Nam.
            Hãy sinh nội dung bãi xe theo đúng format JSON:
            {
              "marketingDescription": "Đoạn giới thiệu hấp dẫn 2-3 câu làm nổi bật tiện ích",
              "operatingRegulations": "Nội quy ngắn gọn về PCCC, giữ vé QR, biểu phí phạt đỗ quá giờ",
              "wayfindingGuide": "Hướng dẫn di chuyển rõ ràng từ các trục đường chính xung quanh bãi"
            }
            Chỉ trả về JSON thuần túy, không dùng markdown ```json.
            """;

        String userPrompt = String.format("""
            Tên bãi: %s
            Địa chỉ: %s
            Tổng chỗ: %s
            Loại xe hỗ trợ: %s
            Tiện ích: %s
            Mốc địa danh gần đó: %s
            """,
                request.getLotName(),
                request.getAddress(),
                request.getTotalCapacity() != null ? request.getTotalCapacity() : 50,
                request.getSupportedVehicleTypes() != null ? String.join(", ", request.getSupportedVehicleTypes()) : "SEDAN_SUV",
                request.getAmenities() != null ? String.join(", ", request.getAmenities()) : "Camera 24/7, có mái che",
                request.getNearbyLandmarks() != null ? request.getNearbyLandmarks() : "Trung tâm"
        );

        Optional<String> aiOutput = geminiClient.generateContent(systemInstruction, userPrompt);
        if (aiOutput.isPresent()) {
            try {
                String cleaned = cleanJsonString(aiOutput.get());
                JsonNode node = objectMapper.readTree(cleaned);
                return ParkingDescriptionDto.Response.builder()
                        .marketingDescription(node.path("marketingDescription").asText())
                        .operatingRegulations(node.path("operatingRegulations").asText())
                        .wayfindingGuide(node.path("wayfindingGuide").asText())
                        .build();
            } catch (Exception e) {
                log.warn("Failed to parse Gemini JSON, falling back to smart template: {}", e.getMessage());
            }
        }

        // Smart Fallback Provider (chạy offline hoặc khi chưa cấp API key)
        return generateFallbackDescription(request);
    }

    public List<MockDataDto.MockParkingLot> generateMockParkingLots(MockDataDto.Request request) {
        String systemInstruction = """
            Bạn là chuyên gia QA sinh dữ liệu mô phỏng cho hệ thống Smart Parking tại Việt Nam.
            Hãy sinh mảng JSON chứa các bãi xe có tọa độ và địa chỉ thực tế tại khu vực yêu cầu.
            Cấu trúc JSON:
            [
              {
                "name": "Bãi xe...",
                "address": "Số nhà, đường...",
                "latitude": 10.7769,
                "longitude": 106.7009,
                "totalCapacity": 80,
                "basePricePerHour": 25000.0,
                "slotDistribution": ["SEDAN_SUV: 60", "LIGHT_TRUCK: 20"],
                "sampleReviews": ["Bãi xe rộng rãi, nhân viên thân thiện", "Lối vào thuận tiện"]
              }
            ]
            Chỉ trả về JSON thuần túy.
            """;

        String userPrompt = String.format("Hãy sinh %d bãi đỗ xe chân thực tại khu vực: %s",
                request.getCount(), request.getTargetArea());

        Optional<String> aiOutput = geminiClient.generateContent(systemInstruction, userPrompt);
        if (aiOutput.isPresent()) {
            try {
                String cleaned = cleanJsonString(aiOutput.get());
                JsonNode node = objectMapper.readTree(cleaned);
                if (node.isArray()) {
                    List<MockDataDto.MockParkingLot> results = new ArrayList<>();
                    for (JsonNode item : node) {
                        List<String> slots = new ArrayList<>();
                        item.path("slotDistribution").forEach(s -> slots.add(s.asText()));
                        List<String> reviews = new ArrayList<>();
                        item.path("sampleReviews").forEach(r -> reviews.add(r.asText()));

                        results.add(MockDataDto.MockParkingLot.builder()
                                .name(item.path("name").asText())
                                .address(item.path("address").asText())
                                .latitude(item.path("latitude").asDouble())
                                .longitude(item.path("longitude").asDouble())
                                .totalCapacity(item.path("totalCapacity").asInt(50))
                                .basePricePerHour(item.path("basePricePerHour").asDouble(20000.0))
                                .slotDistribution(slots)
                                .sampleReviews(reviews)
                                .build());
                    }
                    if (!results.isEmpty()) return results;
                }
            } catch (Exception e) {
                log.warn("Failed to parse Gemini Mock Data JSON: {}", e.getMessage());
            }
        }

        // Smart Fallback Mock Data
        return generateFallbackMockLots(request);
    }

    private ParkingDescriptionDto.Response generateFallbackDescription(ParkingDescriptionDto.Request request) {
        String amenitiesStr = request.getAmenities() != null && !request.getAmenities().isEmpty()
                ? String.join(", ", request.getAmenities())
                : "hệ thống giám sát camera 24/7, phòng cháy chữa cháy đạt chuẩn";

        String marketing = String.format(
                "%s tọa lạc tại vị trí đắc địa trên trục đường %s. Bãi xe sở hữu không gian rộng rãi với sức chứa %d vị trí đỗ, trang bị %s, đảm bảo an toàn tuyệt đối cho phương tiện của quý khách.",
                request.getLotName(), request.getAddress(),
                request.getTotalCapacity() != null ? request.getTotalCapacity() : 50,
                amenitiesStr
        );

        String regulations = "1. Quý khách vui lòng xuất trình mã QR trên ứng dụng khi check-in/check-out.\n" +
                "2. Nghiêm cấm hút thuốc, mang chất dễ cháy nổ vào bãi đỗ xe.\n" +
                "3. Thời gian giữ chỗ hợp lệ tối đa 10 phút. Quá giờ dự kiến 15 phút sẽ áp dụng phụ phí 1.5x đơn giá niêm yết.";

        String wayfinding = String.format(
                "Từ trục đường chính, quý khách di chuyển theo hướng %s, quan sát biển chỉ dẫn SmartParkingHub bên tay phải. Cổng vào có barie tự động và camera nhận diện biển số.",
                request.getNearbyLandmarks() != null && !request.getNearbyLandmarks().isBlank() ? request.getNearbyLandmarks() : request.getAddress()
        );

        return ParkingDescriptionDto.Response.builder()
                .marketingDescription(marketing)
                .operatingRegulations(regulations)
                .wayfindingGuide(wayfinding)
                .build();
    }

    private List<MockDataDto.MockParkingLot> generateFallbackMockLots(MockDataDto.Request request) {
        List<MockDataDto.MockParkingLot> list = new ArrayList<>();
        double baseLat = 10.776889;
        double baseLon = 106.700806;

        for (int i = 1; i <= request.getCount(); i++) {
            list.add(MockDataDto.MockParkingLot.builder()
                    .name(String.format("Bãi Đỗ Xe Thông Minh %s #%d", request.getTargetArea(), i))
                    .address(String.format("Số %d Đường Trung Tâm, %s", i * 12, request.getTargetArea()))
                    .latitude(baseLat + (i * 0.005))
                    .longitude(baseLon + (i * 0.005))
                    .totalCapacity(50 + i * 10)
                    .basePricePerHour(20000.0 + i * 5000.0)
                    .slotDistribution(List.of("SEDAN_SUV: 40", "LIGHT_TRUCK: 15"))
                    .sampleReviews(List.of("Bãi đỗ sạch sẽ, an ninh tốt", "Nhân viên hỗ trợ chu đáo"))
                    .build());
        }
        return list;
    }

    private String cleanJsonString(String raw) {
        String result = raw.trim();
        if (result.startsWith("```json")) {
            result = result.substring(7);
        } else if (result.startsWith("```")) {
            result = result.substring(3);
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }
        return result.trim();
    }
}

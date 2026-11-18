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
        String sanitizedHighlights = sanitizeCustomHighlights(request.getCustomHighlights());

        String systemInstruction = """
            Bạn là Chuyên gia Vận hành và Truyền thông Bãi đỗ xe Đô thị & Logistics tại Việt Nam.
            Hãy sinh nội dung bãi xe theo đúng format JSON:
            {
              "marketingDescription": "Đoạn giới thiệu 2-3 câu tự nhiên, mạch lạc, nêu bật tiện ích thực tế và lợi ích cho tài xế",
              "operatingRegulations": "Nội quy an toàn PCCC, hướng dẫn kiểm soát vé QR và quy định đậu xe văn minh",
              "wayfindingGuide": "Hướng dẫn di chuyển rõ ràng, trực quan từ các trục đường chính xung quanh bãi"
            }
            LƯU Ý QUAN TRỌNG:
            - Tuyệt đối KHÔNG dùng các từ sáo rỗng như 'vị trí đắc địa', 'an toàn tuyệt đối'.
            - Tuyệt đối KHÔNG để lộ các mã code kỹ thuật viết hoa (ví dụ: ALPR_GATE, REEFER_380V, SECURITY_24_7). Hãy dịch sang tiếng Việt mượt mà.
            - Nếu chủ bãi có cung cấp từ khóa/điểm nhấn đặc thù, hãy lồng ghép khéo léo vào nội dung.
            Chỉ trả về JSON thuần túy, không dùng markdown ```json.
            """;

        String userPrompt = String.format("""
            Tên bãi: %s
            Địa chỉ: %s
            Tổng chỗ: %s
            Loại xe hỗ trợ: %s
            Tiện ích: %s
            Mốc địa danh gần đó: %s
            Điểm nhấn / Từ khóa đặc biệt từ chủ bãi: %s
            """,
                request.getLotName(),
                request.getAddress(),
                request.getTotalCapacity() != null ? request.getTotalCapacity() : 50,
                request.getSupportedVehicleTypes() != null ? String.join(", ", request.getSupportedVehicleTypes()) : "SEDAN_SUV",
                request.getAmenities() != null ? String.join(", ", request.getAmenities()) : "Camera 24/7, có mái che",
                request.getNearbyLandmarks() != null ? request.getNearbyLandmarks() : "Trung tâm",
                !sanitizedHighlights.isBlank() ? sanitizedHighlights : "Tiêu chuẩn an toàn hiện đại"
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
                        if (item.has("slotDistribution") && item.get("slotDistribution").isArray()) {
                            item.get("slotDistribution").forEach(s -> slots.add(s.asText()));
                        }
                        List<String> reviews = new ArrayList<>();
                        if (item.has("sampleReviews") && item.get("sampleReviews").isArray()) {
                            item.get("sampleReviews").forEach(r -> reviews.add(r.asText()));
                        }

                        results.add(MockDataDto.MockParkingLot.builder()
                                .name(item.path("name").asText("Bãi đỗ xe thông minh"))
                                .address(item.path("address").asText("Khu vực " + request.getTargetArea()))
                                .latitude(item.path("latitude").asDouble(10.776889))
                                .longitude(item.path("longitude").asDouble(106.700806))
                                .totalCapacity(item.path("totalCapacity").asInt(50))
                                .basePricePerHour(item.path("basePricePerHour").asDouble(20000.0))
                                .slotDistribution(slots)
                                .sampleReviews(reviews)
                                .build());
                    }
                    return results;
                }
            } catch (Exception e) {
                log.warn("Failed to parse Mock Lots JSON, falling back to smart mock template: {}", e.getMessage());
            }
        }

        // Smart Fallback Mock Data
        return generateFallbackMockLots(request);
    }

    private String formatAmenities(List<String> rawAmenities) {
        if (rawAmenities == null || rawAmenities.isEmpty()) {
            return "hệ thống camera giám sát an ninh 24/7, phòng cháy chữa cháy đạt chuẩn";
        }
        Map<String, String> translation = Map.of(
            "ALPR_GATE", "cổng barie nhận diện biển số tự động ALPR",
            "REEFER_380V", "hạ tầng ổ cắm điện lạnh Reefer 380V",
            "SECURITY_24_7", "bảo vệ giám sát 24/7",
            "EV_CHARGER", "trụ sạc nhanh xe điện",
            "COVERED_ROOF", "mái che mát toàn bãi",
            "WEIGH_STATION", "trạm cân xe tải",
            "CASHLESS_PAYMENT", "thanh toán tự động không tiền mặt"
        );
        List<String> translated = new ArrayList<>();
        for (String item : rawAmenities) {
            translated.add(translation.getOrDefault(item, item));
        }
        return String.join(", ", translated);
    }

    private ParkingDescriptionDto.Response generateFallbackDescription(ParkingDescriptionDto.Request request) {
        String amenitiesStr = formatAmenities(request.getAmenities());

        String highlightsSuffix = "";
        if (request.getCustomHighlights() != null && !request.getCustomHighlights().isBlank()) {
            highlightsSuffix = String.format(" Điểm nhấn nổi bật: %s.", sanitizeCustomHighlights(request.getCustomHighlights()));
        }

        String marketing = String.format(
                "Bãi đỗ xe \"%s\" tại %s được quy hoạch đồng bộ với quy mô %d vị trí đỗ, trang bị %s.%s Không gian an toàn, thuận tiện cho mọi phương tiện ra vào.",
                request.getLotName(), request.getAddress(),
                request.getTotalCapacity() != null ? request.getTotalCapacity() : 50,
                amenitiesStr,
                highlightsSuffix
        );

        String regulations = "1. Quý khách vui lòng xuất trình vé điện tử hoặc biển số đã đăng ký khi ra vào cổng.\n" +
                "2. Nghiêm cấm hút thuốc, mang chất dễ cháy nổ hoặc sang chiết nhiên liệu trong bãi.\n" +
                "3. Thời gian giữ chỗ hợp lệ tối đa 10 phút. Duy trì tốc độ an toàn < 10km/h, tuân thủ vạch kẻ ô và khóa xe cẩn thận trước khi rời đi.";

        String landmark = (request.getNearbyLandmarks() != null && !request.getNearbyLandmarks().isBlank())
                ? request.getNearbyLandmarks()
                : request.getAddress();

        String wayfinding = String.format(
                "Từ trục giao thông chính, di chuyển theo hướng %s, quan sát biển chỉ dẫn lối vào bãi đỗ. Cổng vào có barie tự động và camera quét biển số nhanh chóng.",
                landmark
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

    private String sanitizeCustomHighlights(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.length() > 500) {
            trimmed = trimmed.substring(0, 500);
        }
        // Lọc bỏ các từ khóa prompt injection tiềm ẩn
        return trimmed.replaceAll("(?i)(ignore previous instructions|system instruction|bypass)", "").trim();
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

package com.smartparking.ai.application.service;

import com.smartparking.ai.dto.MockDataDto;
import com.smartparking.ai.dto.ParkingDescriptionDto;
import com.smartparking.ai.infrastructure.gemini.GeminiApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiContentGenerationServiceTest {

    @Mock
    private GeminiApiClient geminiClient;

    @InjectMocks
    private AiContentGenerationService service;

    @Test
    @DisplayName("AAA - generateParkingContent: Should parse JSON from Gemini successfully")
    void generateParkingContent_withGeminiResponse_shouldParseCorrectly() {
        // Arrange
        ParkingDescriptionDto.Request request = ParkingDescriptionDto.Request.builder()
                .lotName("Bãi Đỗ Xe An Khánh")
                .address("120 Song Hành, Thủ Đức")
                .totalCapacity(60)
                .supportedVehicleTypes(List.of("SEDAN_SUV"))
                .amenities(List.of("Có mái che", "Camera 24/7"))
                .customHighlights("Miễn phí 1 giờ đầu, có trạm sạc điện")
                .build();

        String mockAiJson = """
            {
              "marketingDescription": "Bãi xe hiện đại bậc nhất khu vực An Khánh, miễn phí 1 giờ đầu.",
              "operatingRegulations": "Giữ vé cẩn thận, không hút thuốc.",
              "wayfindingGuide": "Đi thẳng từ cầu Sài Gòn, rẽ phải tại ngã tư."
            }
            """;
        when(geminiClient.generateContent(anyString(), anyString())).thenReturn(Optional.of(mockAiJson));

        // Act
        ParkingDescriptionDto.Response response = service.generateParkingContent(request);

        // Assert
        assertNotNull(response);
        assertEquals("Bãi xe hiện đại bậc nhất khu vực An Khánh, miễn phí 1 giờ đầu.", response.getMarketingDescription());
        assertEquals("Giữ vé cẩn thận, không hút thuốc.", response.getOperatingRegulations());
        assertEquals("Đi thẳng từ cầu Sài Gòn, rẽ phải tại ngã tư.", response.getWayfindingGuide());
    }

    @Test
    @DisplayName("AAA - generateParkingContent: Should fallback gracefully and embed customHighlights when Gemini returns empty")
    void generateParkingContent_withEmptyGemini_shouldFallbackGracefullyWithHighlights() {
        // Arrange
        ParkingDescriptionDto.Request request = ParkingDescriptionDto.Request.builder()
                .lotName("Bãi Đỗ Xe Thủ Thiêm")
                .address("Khu Đô Thị Mới Thủ Thiêm, Quận 2")
                .totalCapacity(100)
                .customHighlights("Ưu đãi xe điện 20%, có bảo vệ 24/7")
                .build();

        when(geminiClient.generateContent(anyString(), anyString())).thenReturn(Optional.empty());

        // Act
        ParkingDescriptionDto.Response response = service.generateParkingContent(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.getMarketingDescription().contains("Bãi Đỗ Xe Thủ Thiêm"));
        assertTrue(response.getMarketingDescription().contains("Ưu đãi xe điện 20%"));
        assertTrue(response.getOperatingRegulations().contains("10 phút"));
        assertTrue(response.getWayfindingGuide().contains("Khu Đô Thị Mới Thủ Thiêm"));
    }

    @Test
    @DisplayName("AAA - generateMockParkingLots: Should generate correct number of mock lots in fallback mode")
    void generateMockParkingLots_withEmptyGemini_shouldGenerateMockLots() {
        // Arrange
        MockDataDto.Request request = MockDataDto.Request.builder()
                .targetArea("Quận 1, TP.HCM")
                .count(3)
                .build();

        when(geminiClient.generateContent(anyString(), anyString())).thenReturn(Optional.empty());

        // Act
        List<MockDataDto.MockParkingLot> lots = service.generateMockParkingLots(request);

        // Assert
        assertNotNull(lots);
        assertEquals(3, lots.size());
        assertTrue(lots.get(0).getName().contains("Quận 1, TP.HCM"));
        assertTrue(lots.get(0).getBasePricePerHour() > 0);
    }
}

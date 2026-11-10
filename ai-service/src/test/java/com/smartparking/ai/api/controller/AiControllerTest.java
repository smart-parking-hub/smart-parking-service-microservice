package com.smartparking.ai.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.ai.application.service.AiContentGenerationService;
import com.smartparking.ai.application.service.AiLayoutExtractionService;
import com.smartparking.ai.application.service.AiParkingAssistantService;
import com.smartparking.ai.dto.AiChatDto;
import com.smartparking.ai.dto.LayoutExtractionDto;
import com.smartparking.ai.dto.MockDataDto;
import com.smartparking.ai.dto.ParkingDescriptionDto;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiContentGenerationService contentGenerationService;

    @MockBean
    private AiParkingAssistantService assistantService;

    @MockBean
    private AiLayoutExtractionService layoutExtractionService;

    @Test
    @DisplayName("AAA - POST /api/v1/ai/chat: Should return 200 OK with AI response")
    void chatWithAssistant_validRequest_returns200() throws Exception {
        // Arrange
        AiChatDto.Request request = AiChatDto.Request.builder()
                .message("Tìm bãi xe gần chợ Bến Thành")
                .build();

        AiChatDto.Response mockResponse = AiChatDto.Response.builder()
                .reply("Đã tìm thấy bãi xe phù hợp.")
                .source("REALTIME_SLOTS")
                .suggestedLots(List.of())
                .build();

        when(assistantService.processDriverChat(any(), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply").value("Đã tìm thấy bãi xe phù hợp."))
                .andExpect(jsonPath("$.data.source").value("REALTIME_SLOTS"));
    }

    @Test
    @DisplayName("AAA - POST /api/v1/ai/generate-description: Should return 200 OK")
    void generateParkingDescription_validRequest_returns200() throws Exception {
        // Arrange
        ParkingDescriptionDto.Request request = ParkingDescriptionDto.Request.builder()
                .lotName("Bãi Xe Bình Thạnh")
                .address("Điện Biên Phủ, Bình Thạnh")
                .totalCapacity(50)
                .build();

        ParkingDescriptionDto.Response mockResponse = ParkingDescriptionDto.Response.builder()
                .marketingDescription("Mô tả hấp dẫn")
                .operatingRegulations("Nội quy chuẩn")
                .wayfindingGuide("Chỉ đường")
                .build();

        when(contentGenerationService.generateParkingContent(any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/generate-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.marketingDescription").value("Mô tả hấp dẫn"));
    }

    @Test
    @DisplayName("AAA - POST /api/v1/ai/generate-mock-data: Should return 200 OK")
    void generateMockData_validRequest_returns200() throws Exception {
        // Arrange
        MockDataDto.Request request = MockDataDto.Request.builder()
                .targetArea("Quận 7, TP.HCM")
                .count(2)
                .build();

        MockDataDto.MockParkingLot mockLot = MockDataDto.MockParkingLot.builder()
                .name("Bãi Xe Phú Mỹ Hưng")
                .address("Nguyễn Văn Linh, Quận 7")
                .totalCapacity(80)
                .basePricePerHour(20000.0)
                .build();

        when(contentGenerationService.generateMockParkingLots(any())).thenReturn(List.of(mockLot));

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/generate-mock-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Bãi Xe Phú Mỹ Hưng"));
    }

    @Test
    @DisplayName("AAA - POST /api/v1/ai/extract-layout: Should return 200 OK with 0-based grid and confidence")
    void extractParkingLayout_validMultipart_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "blueprint.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        );

        LayoutExtractionDto.Response mockResponse = LayoutExtractionDto.Response.builder()
                .grid(LayoutExtractionDto.GridMetadata.builder()
                        .rows(3)
                        .cols(4)
                        .indexBase(0)
                        .dimensionUnit("METER")
                        .imageWidthPx(800)
                        .imageHeightPx(600)
                        .imageMimeType("image/png")
                        .build())
                .cells(List.of(
                        LayoutExtractionDto.Cell.builder()
                                .row(0)
                                .col(0)
                                .cellType(LayoutExtractionDto.LayoutCellType.ENTRANCE)
                                .label("GATE-IN")
                                .confidence(0.95)
                                .build(),
                        LayoutExtractionDto.Cell.builder()
                                .row(1)
                                .col(1)
                                .cellType(LayoutExtractionDto.LayoutCellType.PARKING_SLOT)
                                .label("A-01")
                                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                                .dimensionsMeters(LayoutExtractionDto.DimensionsMeters.builder()
                                        .width(new BigDecimal("2.5"))
                                        .length(new BigDecimal("5.0"))
                                        .build())
                                .confidence(0.92)
                                .build()
                ))
                .overallConfidence(0.935)
                .needsHumanReview(false)
                .reviewWarnings(List.of())
                .build();

        when(layoutExtractionService.extractLayout(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/v1/ai/extract-layout")
                        .file(file)
                        .param("lotName", "Bãi Xe Quận 1")
                        .param("expectedRows", "3")
                        .param("expectedCols", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.grid.indexBase").value(0))
                .andExpect(jsonPath("$.data.cells[0].cellType").value("ENTRANCE"))
                .andExpect(jsonPath("$.data.cells[1].dimensionsMeters.width").value(2.5))
                .andExpect(jsonPath("$.data.needsHumanReview").value(false));
    }

    @Test
    @DisplayName("AAA - POST /api/v1/ai/extract-layout: Returns 400 on BAD_REQUEST and 500 on INTERNAL_SERVER_ERROR")
    void extractParkingLayout_errorCodesMapToCorrectHttpStatus() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "bad.txt", "text/plain", new byte[]{1, 2, 3, 4});

        when(layoutExtractionService.extractLayout(any(), any()))
                .thenThrow(new AppException(ErrorCode.BAD_REQUEST, "Định dạng ảnh không hợp lệ"))
                .thenThrow(new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Gemini Vision timeout"));

        mockMvc.perform(multipart("/api/v1/ai/extract-layout").file(invalidFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_400"));

        mockMvc.perform(multipart("/api/v1/ai/extract-layout").file(invalidFile))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_500"));
    }
}

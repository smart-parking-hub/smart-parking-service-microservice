package com.smartparking.ai.api.controller;

import com.smartparking.ai.application.service.AiContentGenerationService;
import com.smartparking.ai.application.service.AiLayoutExtractionService;
import com.smartparking.ai.application.service.AiParkingAssistantService;
import com.smartparking.ai.dto.AiChatDto;
import com.smartparking.ai.dto.LayoutExtractionDto;
import com.smartparking.ai.dto.MockDataDto;
import com.smartparking.ai.dto.ParkingDescriptionDto;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.security.SecurityConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Integration", description = "Các API phân hệ AI: Smart Chatbot, Sinh mô tả bãi xe, Trích xuất mặt bằng & Mock Data")
public class AiController {

    private final AiContentGenerationService contentGenerationService;
    private final AiParkingAssistantService assistantService;
    private final AiLayoutExtractionService layoutExtractionService;

    @Operation(summary = "Tự động sinh mô tả bãi xe, nội quy an toàn PCCC và hướng dẫn đường đi cho Merchant")
    @PostMapping("/generate-description")
    public ResponseEntity<ApiResponse<ParkingDescriptionDto.Response>> generateParkingDescription(
            @Valid @RequestBody ParkingDescriptionDto.Request request) {
        ParkingDescriptionDto.Response response = contentGenerationService.generateParkingContent(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Sinh nội dung bãi xe thành công"));
    }

    @Operation(summary = "Tự động sinh Synthetic Mock Data bãi xe phục vụ kiểm thử và demo")
    @PostMapping("/generate-mock-data")
    public ResponseEntity<ApiResponse<List<MockDataDto.MockParkingLot>>> generateMockData(
            @Valid @RequestBody MockDataDto.Request request) {
        List<MockDataDto.MockParkingLot> response = contentGenerationService.generateMockParkingLots(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Sinh dữ liệu mô phỏng thành công"));
    }

    @Operation(summary = "Chatbot tư vấn tìm kiếm bãi xe thời gian thực (Tool Calling) và tra cứu chính sách")
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatDto.Response>> chatWithAssistant(
            @RequestHeader(value = SecurityConstants.HEADER_USER_ID, required = false) String userId,
            @Valid @RequestBody AiChatDto.Request request) {
        AiChatDto.Response response = assistantService.processDriverChat(request, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Phản hồi từ AI Copilot"));
    }

    @Operation(summary = "Trích xuất sơ đồ mặt bằng bãi xe 2D (0-based grid) từ ảnh PNG/JPEG bằng Gemini Vision")
    @PostMapping(value = "/extract-layout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<LayoutExtractionDto.Response>> extractParkingLayout(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "lotName", required = false) String lotName,
            @RequestParam(value = "expectedRows", required = false) Integer expectedRows,
            @RequestParam(value = "expectedCols", required = false) Integer expectedCols,
            @RequestParam(value = "defaultVehicleType", required = false) VehicleTypeEnum defaultVehicleType,
            @RequestParam(value = "scaleHintMetersPerPixel", required = false) BigDecimal scaleHintMetersPerPixel) {
        LayoutExtractionDto.HintMetadata hints = LayoutExtractionDto.HintMetadata.builder()
                .lotName(lotName)
                .expectedRows(expectedRows)
                .expectedCols(expectedCols)
                .defaultVehicleType(defaultVehicleType)
                .scaleHintMetersPerPixel(scaleHintMetersPerPixel)
                .build();
        LayoutExtractionDto.Response response = layoutExtractionService.extractLayout(file, hints);
        return ResponseEntity.ok(ApiResponse.success(response, "Trích xuất sơ đồ mặt bằng thành công"));
    }
}

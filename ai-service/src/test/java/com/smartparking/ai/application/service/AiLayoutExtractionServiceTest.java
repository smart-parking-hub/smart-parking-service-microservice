package com.smartparking.ai.application.service;

import com.smartparking.ai.dto.LayoutExtractionDto.*;
import com.smartparking.ai.infrastructure.gemini.GeminiApiClient;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiLayoutExtractionServiceTest {

    @Mock
    private GeminiApiClient geminiApiClient;

    private AiLayoutExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new AiLayoutExtractionService(geminiApiClient);
    }

    private byte[] createSyntheticImageBytes(String format, int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawRect(10, 10, Math.max(1, width - 20), Math.max(1, height - 20));
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Valid PNG blueprint extracts 0-based grid with all cell types, dimensions, and needsHumanReview=false when confidence >= 0.75")
    void extractLayout_validPngHighConfidence_returnsValidatedGrid() throws Exception {
        byte[] pngBytes = createSyntheticImageBytes("png", 640, 480);
        MockMultipartFile file = new MockMultipartFile("file", "basement-b1.png", "image/png", pngBytes);

        when(geminiApiClient.isConfigured()).thenReturn(true);
        String geminiJson = """
                ```json
                {
                  "grid": {
                    "rows": 3,
                    "cols": 4,
                    "indexBase": 0,
                    "dimensionUnit": "METER"
                  },
                  "cells": [
                    {
                      "row": 0,
                      "col": 0,
                      "cellType": "ENTRANCE",
                      "label": "IN-1",
                      "vehicleType": null,
                      "dimensionsMeters": { "width": 3.5, "length": 6.0 },
                      "confidence": 0.96
                    },
                    {
                      "row": 0,
                      "col": 3,
                      "cellType": "EXIT",
                      "label": "OUT-1",
                      "vehicleType": null,
                      "dimensionsMeters": { "width": 3.5, "length": 6.0 },
                      "confidence": 0.94
                    },
                    {
                      "row": 1,
                      "col": 1,
                      "cellType": "PARKING_SLOT",
                      "label": "A-01",
                      "vehicleType": "SEDAN_SUV",
                      "dimensionsMeters": { "width": 2.5, "length": 5.0 },
                      "confidence": 0.91
                    },
                    {
                      "row": 1,
                      "col": 2,
                      "cellType": "CHARGING_PILLAR",
                      "label": "EV-01",
                      "vehicleType": null,
                      "dimensionsMeters": { "width": 1.2, "length": 1.2 },
                      "confidence": 0.89
                    },
                    {
                      "row": 2,
                      "col": 0,
                      "cellType": "EMERGENCY_EXIT",
                      "label": "EXIT-FIRE-1",
                      "vehicleType": null,
                      "dimensionsMeters": null,
                      "confidence": 0.88
                    }
                  ],
                  "overallConfidence": 0.916,
                  "needsHumanReview": false,
                  "reviewWarnings": []
                }
                ```
                """;
        when(geminiApiClient.generateMultimodalContent(anyString(), anyString(), eq("image/png"), any()))
                .thenReturn(Optional.of(geminiJson));

        HintMetadata hints = HintMetadata.builder()
                .lotName("Hầm B1 Vincom")
                .expectedRows(3)
                .expectedCols(4)
                .defaultVehicleType(VehicleTypeEnum.SEDAN_SUV)
                .build();

        Response response = extractionService.extractLayout(file, hints);

        assertNotNull(response);
        assertEquals(3, response.getGrid().getRows());
        assertEquals(4, response.getGrid().getCols());
        assertEquals(0, response.getGrid().getIndexBase());
        assertEquals(640, response.getGrid().getImageWidthPx());
        assertEquals(480, response.getGrid().getImageHeightPx());
        assertEquals(5, response.getCells().size());
        assertFalse(response.isNeedsHumanReview());
        assertTrue(response.getOverallConfidence() >= 0.75);
    }

    @Test
    @DisplayName("Valid JPEG with confidence < 0.75 and null dimensions sets needsHumanReview=true and populates warnings")
    void extractLayout_lowConfidenceAndNullDimensions_triggersNeedsHumanReview() throws Exception {
        byte[] jpegBytes = createSyntheticImageBytes("jpg", 500, 400);
        MockMultipartFile file = new MockMultipartFile("file", "blurry-lot.jpg", "image/jpeg", jpegBytes);

        when(geminiApiClient.isConfigured()).thenReturn(true);
        String geminiJson = """
                {
                  "grid": { "rows": 2, "cols": 2, "indexBase": 0, "dimensionUnit": "METER" },
                  "cells": [
                    {
                      "row": 0,
                      "col": 0,
                      "cellType": "PARKING_SLOT",
                      "label": "B-01",
                      "vehicleType": "LIGHT_TRUCK",
                      "dimensionsMeters": null,
                      "confidence": 0.68
                    },
                    {
                      "row": 0,
                      "col": 1,
                      "cellType": "DRIVEWAY",
                      "label": "LANE-1",
                      "vehicleType": null,
                      "dimensionsMeters": null,
                      "confidence": 0.72
                    }
                  ],
                  "overallConfidence": 0.70,
                  "needsHumanReview": false
                }
                """;
        when(geminiApiClient.generateMultimodalContent(anyString(), anyString(), eq("image/jpeg"), any()))
                .thenReturn(Optional.of(geminiJson));

        Response response = extractionService.extractLayout(file, null);

        assertTrue(response.isNeedsHumanReview(),
                "needsHumanReview must be forced to true when overallConfidence < 0.75 or any cell < 0.75");
        assertEquals(VehicleTypeEnum.LIGHT_TRUCK, response.getCells().get(0).getVehicleType());
        assertNull(response.getCells().get(0).getDimensionsMeters());
        assertFalse(response.getReviewWarnings().isEmpty());
    }

    @Test
    @DisplayName("Prompt and parser align strictly with VehicleTypeEnum (SEDAN_SUV, BUS_COACH, LIGHT_TRUCK, HEAVY_TRUCK, CONTAINER) and reject unsupported values")
    void extractLayout_vehicleTypeDomainAlignment_acceptsSupportedEnumsAndRejectsUnsupportedValues() throws Exception {
        byte[] pngBytes = createSyntheticImageBytes("png", 320, 240);
        MockMultipartFile file = new MockMultipartFile("file", "fleet-yard.png", "image/png", pngBytes);

        when(geminiApiClient.isConfigured()).thenReturn(true);
        String allDomainVehiclesJson = """
                {
                  "grid": { "rows": 2, "cols": 3, "indexBase": 0, "dimensionUnit": "METER" },
                  "cells": [
                    { "row": 0, "col": 0, "cellType": "PARKING_SLOT", "label": "S1", "vehicleType": "SEDAN_SUV", "confidence": 0.90 },
                    { "row": 0, "col": 1, "cellType": "PARKING_SLOT", "label": "B1", "vehicleType": "BUS_COACH", "confidence": 0.90 },
                    { "row": 0, "col": 2, "cellType": "PARKING_SLOT", "label": "L1", "vehicleType": "LIGHT_TRUCK", "confidence": 0.90 },
                    { "row": 1, "col": 0, "cellType": "PARKING_SLOT", "label": "H1", "vehicleType": "HEAVY_TRUCK", "confidence": 0.90 },
                    { "row": 1, "col": 1, "cellType": "PARKING_SLOT", "label": "C1", "vehicleType": "CONTAINER", "confidence": 0.90 },
                    { "row": 1, "col": 2, "cellType": "DRIVEWAY", "label": "D1", "vehicleType": null, "confidence": 0.90 }
                  ],
                  "overallConfidence": 0.90
                }
                """;
        when(geminiApiClient.generateMultimodalContent(anyString(), anyString(), eq("image/png"), any()))
                .thenReturn(Optional.of(allDomainVehiclesJson));

        Response response = extractionService.extractLayout(file, null);
        assertEquals(6, response.getCells().size());
        assertEquals(VehicleTypeEnum.SEDAN_SUV, response.getCells().get(0).getVehicleType());
        assertEquals(VehicleTypeEnum.BUS_COACH, response.getCells().get(1).getVehicleType());
        assertEquals(VehicleTypeEnum.LIGHT_TRUCK, response.getCells().get(2).getVehicleType());
        assertEquals(VehicleTypeEnum.HEAVY_TRUCK, response.getCells().get(3).getVehicleType());
        assertEquals(VehicleTypeEnum.CONTAINER, response.getCells().get(4).getVehicleType());
        assertNull(response.getCells().get(5).getVehicleType());

        ArgumentCaptor<String> systemInstructionCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiApiClient).generateMultimodalContent(systemInstructionCaptor.capture(), anyString(), eq("image/png"), any());
        String prompt = systemInstructionCaptor.getValue();
        for (VehicleTypeEnum type : VehicleTypeEnum.values()) {
            assertTrue(prompt.contains(type.name()), "SYSTEM_INSTRUCTION must include domain VehicleTypeEnum: " + type.name());
        }
        assertFalse(prompt.contains("MOTORBIKE"), "SYSTEM_INSTRUCTION must not instruct unsupported vehicleType MOTORBIKE");
        assertFalse(prompt.contains("BICYCLE"), "SYSTEM_INSTRUCTION must not instruct unsupported vehicleType BICYCLE");

        // Unsupported vehicleType in model response must be rejected with INTERNAL_SERVER_ERROR
        String unsupportedVehicleJson = """
                {
                  "grid": { "rows": 1, "cols": 1, "indexBase": 0 },
                  "cells": [
                    { "row": 0, "col": 0, "cellType": "PARKING_SLOT", "vehicleType": "MOTORBIKE", "confidence": 0.90 }
                  ]
                }
                """;
        AppException invalidVehicleEx = assertThrows(AppException.class,
                () -> extractionService.parseAndValidateLayoutJson(unsupportedVehicleJson, 320, 240, "image/png"));
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, invalidVehicleEx.getErrorCode());
    }

    @Test
    @DisplayName("Input validation rejects spoofed magic bytes, mismatched MIME, >10MB file, and >4096px dimensions with BAD_REQUEST")
    void extractLayout_invalidImageInputs_throwsValidationError() throws Exception {
        // 1. Spoofed non-image bytes claiming to be image/png
        MockMultipartFile spoofedPng = new MockMultipartFile(
                "file", "fake.png", "image/png", "%PDF-1.5 fake pdf content".getBytes(StandardCharsets.UTF_8));
        AppException spoofEx = assertThrows(AppException.class,
                () -> extractionService.extractLayout(spoofedPng, null));
        assertEquals(ErrorCode.BAD_REQUEST, spoofEx.getErrorCode());

        // 2. Real PNG bytes but declared as image/jpeg
        byte[] validPng = createSyntheticImageBytes("png", 100, 100);
        MockMultipartFile mismatchedMime = new MockMultipartFile(
                "file", "mismatch.jpg", "image/jpeg", validPng);
        AppException mimeEx = assertThrows(AppException.class,
                () -> extractionService.extractLayout(mismatchedMime, null));
        assertEquals(ErrorCode.BAD_REQUEST, mimeEx.getErrorCode());

        // 3. Oversized file (> 10MB)
        byte[] overTenMb = new byte[(int) (AiLayoutExtractionService.MAX_FILE_SIZE_BYTES + 1024)];
        MockMultipartFile hugeFile = new MockMultipartFile("file", "huge.png", "image/png", overTenMb);
        AppException sizeEx = assertThrows(AppException.class,
                () -> extractionService.extractLayout(hugeFile, null));
        assertEquals(ErrorCode.BAD_REQUEST, sizeEx.getErrorCode());

        // 4. Image dimensions > 4096 px (4100 x 64 px)
        byte[] tooWidePng = createSyntheticImageBytes("png", 4100, 64);
        MockMultipartFile wideFile = new MockMultipartFile("file", "wide.png", "image/png", tooWidePng);
        AppException dimEx = assertThrows(AppException.class,
                () -> extractionService.extractLayout(wideFile, null));
        assertEquals(ErrorCode.BAD_REQUEST, dimEx.getErrorCode());
    }

    @Test
    @DisplayName("Schema validation rejects out-of-bounds 0-based coordinates, duplicate cells, negative dimensions, and timeout/unconfigured Gemini")
    void extractLayout_malformedOrUnavailableGemini_throwsInternalErrorWithoutFakeFallback() throws Exception {
        byte[] validPng = createSyntheticImageBytes("png", 200, 200);
        MockMultipartFile file = new MockMultipartFile("file", "layout.png", "image/png", validPng);

        // 1. Unconfigured Gemini API key must fail explicitly rather than inventing fake layout
        when(geminiApiClient.isConfigured()).thenReturn(false);
        AppException unconfiguredEx = assertThrows(AppException.class,
                () -> extractionService.extractLayout(file, null));
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, unconfiguredEx.getErrorCode());

        // 2. Timeout / empty response from Gemini Vision
        when(geminiApiClient.isConfigured()).thenReturn(true);
        when(geminiApiClient.generateMultimodalContent(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        AppException timeoutEx = assertThrows(AppException.class,
                () -> extractionService.extractLayout(file, null));
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, timeoutEx.getErrorCode());

        // 3. Out-of-bounds 0-based coordinate (row == rows)
        String outOfBoundsJson = """
                {
                  "grid": { "rows": 2, "cols": 2, "indexBase": 0 },
                  "cells": [
                    { "row": 2, "col": 0, "cellType": "PARKING_SLOT", "confidence": 0.9 }
                  ]
                }
                """;
        AppException outOfBoundsEx = assertThrows(AppException.class,
                () -> extractionService.parseAndValidateLayoutJson(outOfBoundsJson, 200, 200, "image/png"));
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, outOfBoundsEx.getErrorCode());

        // 4. Duplicate (row, col) coordinate
        String duplicateCoordJson = """
                {
                  "grid": { "rows": 2, "cols": 2, "indexBase": 0 },
                  "cells": [
                    { "row": 0, "col": 0, "cellType": "PARKING_SLOT", "confidence": 0.9 },
                    { "row": 0, "col": 0, "cellType": "CHARGING_PILLAR", "confidence": 0.85 }
                  ]
                }
                """;
        AppException duplicateEx = assertThrows(AppException.class,
                () -> extractionService.parseAndValidateLayoutJson(duplicateCoordJson, 200, 200, "image/png"));
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, duplicateEx.getErrorCode());

        // 5. Negative dimension in dimensionsMeters
        String negativeDimJson = """
                {
                  "grid": { "rows": 2, "cols": 2, "indexBase": 0 },
                  "cells": [
                    {
                      "row": 0,
                      "col": 0,
                      "cellType": "PARKING_SLOT",
                      "dimensionsMeters": { "width": -2.5, "length": 5.0 },
                      "confidence": 0.9
                    }
                  ]
                }
                """;
        AppException negativeDimEx = assertThrows(AppException.class,
                () -> extractionService.parseAndValidateLayoutJson(negativeDimJson, 200, 200, "image/png"));
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, negativeDimEx.getErrorCode());
    }

    @Test
    @DisplayName("Synthetic benchmark evaluation verifies ground-truth coordinate & cellType metrics across fixtures")
    void evaluateSyntheticBenchmarkFixturesAgainstGroundTruth() {
        // Fixture 1: Standard 2x3 basement layout with entrance, exit, parking slots, charging pillar, emergency exit
        String fixture1Json = """
                {
                  "grid": { "rows": 2, "cols": 3, "indexBase": 0 },
                  "cells": [
                    { "row": 0, "col": 0, "cellType": "ENTRANCE", "label": "IN", "confidence": 0.95 },
                    { "row": 0, "col": 1, "cellType": "PARKING_SLOT", "label": "A1", "vehicleType": "SEDAN_SUV", "dimensionsMeters": { "width": 2.5, "length": 5.0 }, "confidence": 0.92 },
                    { "row": 0, "col": 2, "cellType": "CHARGING_PILLAR", "label": "EV1", "confidence": 0.90 },
                    { "row": 1, "col": 0, "cellType": "EMERGENCY_EXIT", "label": "FIRE1", "confidence": 0.89 },
                    { "row": 1, "col": 2, "cellType": "EXIT", "label": "OUT", "confidence": 0.94 }
                  ],
                  "overallConfidence": 0.92
                }
                """;
        Response pred1 = extractionService.parseAndValidateLayoutJson(fixture1Json, 800, 600, "image/png");
        BenchmarkEvaluationResult eval1 = extractionService.evaluateAgainstGroundTruth(
                "synthetic-basement-b1", pred1, pred1, false);
        assertTrue(eval1.isSchemaValid());
        assertTrue(eval1.isNeedsHumanReviewTriggeredAsExpected());
        assertEquals(1.0, eval1.getCoordinatePrecision());
        assertEquals(1.0, eval1.getCoordinateRecall());
        assertEquals(1.0, eval1.getCellTypeAccuracy());

        // Fixture 2: Low-contrast blueprint with partial cell type mismatch & confidence < 0.75
        String fixture2PredJson = """
                {
                  "grid": { "rows": 2, "cols": 2, "indexBase": 0 },
                  "cells": [
                    { "row": 0, "col": 0, "cellType": "PARKING_SLOT", "confidence": 0.71 },
                    { "row": 0, "col": 1, "cellType": "OBSTACLE", "confidence": 0.65 }
                  ],
                  "overallConfidence": 0.68
                }
                """;
        String fixture2TruthJson = """
                {
                  "grid": { "rows": 2, "cols": 2, "indexBase": 0 },
                  "cells": [
                    { "row": 0, "col": 0, "cellType": "PARKING_SLOT", "confidence": 1.0 },
                    { "row": 0, "col": 1, "cellType": "CHARGING_PILLAR", "confidence": 1.0 }
                  ],
                  "overallConfidence": 1.0
                }
                """;
        Response pred2 = extractionService.parseAndValidateLayoutJson(fixture2PredJson, 640, 480, "image/jpeg");
        Response truth2 = extractionService.parseAndValidateLayoutJson(fixture2TruthJson, 640, 480, "image/jpeg");
        BenchmarkEvaluationResult eval2 = extractionService.evaluateAgainstGroundTruth(
                "synthetic-low-contrast-ev-zone", pred2, truth2, true);
        assertTrue(eval2.isSchemaValid());
        assertTrue(eval2.isNeedsHumanReviewTriggeredAsExpected());
        assertEquals(1.0, eval2.getCoordinateRecall());
        assertEquals(0.5, eval2.getCellTypeAccuracy());
    }
}

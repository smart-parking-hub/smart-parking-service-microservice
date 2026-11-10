package com.smartparking.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.ai.dto.LayoutExtractionDto;
import com.smartparking.ai.dto.LayoutExtractionDto.*;
import com.smartparking.ai.infrastructure.gemini.GeminiApiClient;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiLayoutExtractionService {

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB
    public static final int MAX_IMAGE_DIMENSION_PX = 4096;
    public static final int MAX_GRID_DIMENSION = 200;
    public static final double HUMAN_REVIEW_CONFIDENCE_THRESHOLD = 0.75;

    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_MAGIC = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };

    static final String SYSTEM_INSTRUCTION = """
            Bạn là hệ thống Computer Vision chuyên trích xuất sơ đồ mặt bằng bãi đỗ xe (Parking Layout Blueprint Extractor).
            Nhiệm vụ: Phân tích ảnh mặt bằng bãi xe và trả về duy nhất 1 JSON object theo đúng schema sau (tọa độ 0-based):
            {
              "grid": {
                "rows": <số hàng nguyên dương>,
                "cols": <số cột nguyên dương>,
                "indexBase": 0,
                "dimensionUnit": "METER"
              },
              "cells": [
                {
                  "row": <0..rows-1>,
                  "col": <0..cols-1>,
                  "cellType": "PARKING_SLOT" | "CHARGING_PILLAR" | "EMERGENCY_EXIT" | "ENTRANCE" | "EXIT" | "DRIVEWAY" | "OBSTACLE",
                  "label": "<mã ô như A-01, EV-01, EXIT-1 hoặc null>",
                  "vehicleType": "SEDAN_SUV" | "BUS_COACH" | "LIGHT_TRUCK" | "HEAVY_TRUCK" | "CONTAINER" | null,
                  "dimensionsMeters": { "width": <số thực dương>, "length": <số thực dương> } hoặc null nếu không suy luận được tỷ lệ mét,
                  "confidence": <số thực từ 0.0 đến 1.0>
                }
              ],
              "overallConfidence": <số thực từ 0.0 đến 1.0>,
              "needsHumanReview": <true nếu overallConfidence < 0.75 hoặc ảnh mờ/thiếu tỷ lệ>,
              "reviewWarnings": ["<cảnh báo cần con người kiểm tra nếu có>"]
            }
            Tuyệt đối không bịa đặt kích thước mét nếu ảnh không có chú thích hoặc tỷ lệ tham chiếu (hãy trả về dimensionsMeters: null).
            """;

    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LayoutExtractionDto.Response extractLayout(MultipartFile file, HintMetadata hints) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File ảnh mặt bằng không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Dung lượng ảnh vượt quá giới hạn 10MB");
        }

        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể đọc dữ liệu file ảnh tải lên");
        }

        if (imageBytes.length == 0 || imageBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Dung lượng ảnh không hợp lệ (tối đa 10MB)");
        }

        String detectedMimeType = detectMimeTypeFromMagicBytes(imageBytes);
        String declaredContentType = file.getContentType();
        if (declaredContentType != null && !declaredContentType.isBlank()
                && !declaredContentType.equalsIgnoreCase("application/octet-stream")) {
            String normalizedDeclared = declaredContentType.toLowerCase(Locale.ROOT).trim();
            if (normalizedDeclared.equals("image/jpg")) {
                normalizedDeclared = "image/jpeg";
            }
            if (!normalizedDeclared.equals(detectedMimeType)) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Content-Type khai báo không khớp với định dạng thực tế của file ảnh (chỉ chấp nhận PNG/JPEG hợp lệ)");
            }
        }

        BufferedImage decodedImage = decodeAndValidateDimensions(imageBytes);
        int widthPx = decodedImage.getWidth();
        int heightPx = decodedImage.getHeight();

        if (!geminiApiClient.isConfigured()) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Gemini Vision API chưa được cấu hình; từ chối sinh sơ đồ mặt bằng giả lập");
        }

        String userPrompt = buildUserPrompt(hints, widthPx, heightPx);
        String rawJson = geminiApiClient.generateMultimodalContent(
                        SYSTEM_INSTRUCTION, userPrompt, detectedMimeType, imageBytes)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "Không thể trích xuất mặt bằng từ Gemini Vision (quá thời gian chờ 25s hoặc dịch vụ tạm ngưng)"));

        return parseAndValidateLayoutJson(rawJson, widthPx, heightPx, detectedMimeType);
    }

    public String detectMimeTypeFromMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File tải lên quá nhỏ hoặc không phải ảnh PNG/JPEG");
        }
        if (bytes.length >= PNG_MAGIC.length) {
            boolean isPng = true;
            for (int i = 0; i < PNG_MAGIC.length; i++) {
                if (bytes[i] != PNG_MAGIC[i]) {
                    isPng = false;
                    break;
                }
            }
            if (isPng) {
                return "image/png";
            }
        }
        if (bytes.length >= JPEG_MAGIC.length
                && bytes[0] == JPEG_MAGIC[0]
                && bytes[1] == JPEG_MAGIC[1]
                && bytes[2] == JPEG_MAGIC[2]) {
            return "image/jpeg";
        }
        throw new AppException(ErrorCode.BAD_REQUEST,
                "Định dạng file không được hỗ trợ. Chỉ chấp nhận ảnh PNG hoặc JPEG hợp lệ (kiểm tra magic bytes)");
    }

    private BufferedImage decodeAndValidateDimensions(byte[] imageBytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(bais);
            if (image == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Dữ liệu ảnh bị hỏng hoặc không thể giải mã");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0
                    || width > MAX_IMAGE_DIMENSION_PX
                    || height > MAX_IMAGE_DIMENSION_PX) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        String.format("Kích thước ảnh (%dx%d px) vượt quá giới hạn cho phép (tối đa %dx%d px)",
                                width, height, MAX_IMAGE_DIMENSION_PX, MAX_IMAGE_DIMENSION_PX));
            }
            return image;
        } catch (AppException e) {
            throw e;
        } catch (IOException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Lỗi giải mã dữ liệu hình ảnh mặt bằng");
        }
    }

    private String buildUserPrompt(HintMetadata hints, int widthPx, int heightPx) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Ảnh mặt bằng có độ phân giải %dx%d px. ", widthPx, heightPx));
        sb.append("Hãy trích xuất lưới 2D tọa độ 0-based (row, col) cho tất cả các ô đỗ xe, trụ sạc, lối thoát hiểm, cổng vào/ra, đường nội bộ và vật cản.");
        if (hints != null) {
            if (hints.getLotName() != null && !hints.getLotName().isBlank()) {
                sb.append(" Tên bãi xe: ").append(hints.getLotName().trim()).append(".");
            }
            if (hints.getExpectedRows() != null && hints.getExpectedRows() > 0) {
                sb.append(" Số hàng dự kiến: ").append(hints.getExpectedRows()).append(".");
            }
            if (hints.getExpectedCols() != null && hints.getExpectedCols() > 0) {
                sb.append(" Số cột dự kiến: ").append(hints.getExpectedCols()).append(".");
            }
            if (hints.getDefaultVehicleType() != null) {
                sb.append(" Loại phương tiện mặc định: ").append(hints.getDefaultVehicleType().name()).append(".");
            }
            if (hints.getScaleHintMetersPerPixel() != null
                    && hints.getScaleHintMetersPerPixel().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(" Gợi ý tỷ lệ mét/pixel: ").append(hints.getScaleHintMetersPerPixel().toPlainString()).append(".");
            }
        }
        return sb.toString();
    }

    public LayoutExtractionDto.Response parseAndValidateLayoutJson(
            String rawJson,
            int imageWidthPx,
            int imageHeightPx,
            String mimeType) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Phản hồi từ Gemini Vision rỗng");
        }

        String cleanedJson = cleanMarkdownCodeFence(rawJson);
        JsonNode root;
        try {
            root = objectMapper.readTree(cleanedJson);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Phản hồi từ Gemini Vision không phải JSON hợp lệ");
        }

        JsonNode gridNode = root.path("grid");
        if (gridNode.isMissingNode() || !gridNode.isObject()) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "JSON layout thiếu thông tin 'grid'");
        }

        int rows = gridNode.path("rows").asInt(-1);
        int cols = gridNode.path("cols").asInt(-1);
        if (rows <= 0 || rows > MAX_GRID_DIMENSION || cols <= 0 || cols > MAX_GRID_DIMENSION) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    String.format("Kích thước lưới grid không hợp lệ (rows=%d, cols=%d)", rows, cols));
        }

        int indexBase = gridNode.path("indexBase").asInt(0);
        if (indexBase != 0) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Hệ tọa độ grid phải là 0-based (indexBase = 0)");
        }

        JsonNode cellsNode = root.path("cells");
        if (!cellsNode.isArray() || cellsNode.isEmpty()) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Danh sách 'cells' trong JSON layout rỗng hoặc không hợp lệ");
        }

        List<Cell> validatedCells = new ArrayList<>();
        Set<String> seenCoordinates = new HashSet<>();
        List<String> reviewWarnings = new ArrayList<>();
        boolean hasLowConfidenceCell = false;
        boolean hasNullDimensionsOnSlot = false;
        double confidenceSum = 0.0;

        if (root.path("reviewWarnings").isArray()) {
            for (JsonNode warnNode : root.path("reviewWarnings")) {
                if (warnNode.isTextual() && !warnNode.asText().isBlank()) {
                    reviewWarnings.add(warnNode.asText().trim());
                }
            }
        }

        for (JsonNode cellNode : cellsNode) {
            if (!cellNode.hasNonNull("row") || !cellNode.get("row").isInt()
                    || !cellNode.hasNonNull("col") || !cellNode.get("col").isInt()) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Ô trong layout thiếu tọa độ nguyên (row, col)");
            }

            int row = cellNode.get("row").asInt();
            int col = cellNode.get("col").asInt();
            if (row < 0 || row >= rows || col < 0 || col >= cols) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                        String.format("Tọa độ ô (%d, %d) nằm ngoài phạm vi lưới 0-based (%dx%d)", row, col, rows, cols));
            }

            String coordKey = row + ":" + col;
            if (!seenCoordinates.add(coordKey)) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                        String.format("Phát hiện tọa độ ô trùng lặp tại (%d, %d)", row, col));
            }

            String cellTypeStr = cellNode.path("cellType").asText(null);
            if (cellTypeStr == null || cellTypeStr.isBlank()) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                        String.format("Ô tại (%d, %d) thiếu cellType", row, col));
            }

            LayoutCellType cellType;
            try {
                cellType = LayoutCellType.valueOf(cellTypeStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                        String.format("Giá trị cellType '%s' tại (%d, %d) không thuộc danh mục hỗ trợ", cellTypeStr, row, col));
            }

            if (!cellNode.hasNonNull("confidence") || !cellNode.get("confidence").isNumber()) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                        String.format("Ô tại (%d, %d) thiếu điểm tin cậy confidence", row, col));
            }
            double confidence = cellNode.get("confidence").asDouble();
            if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                        String.format("Điểm confidence %.4f tại (%d, %d) nằm ngoài khoảng [0.0, 1.0]", confidence, row, col));
            }
            confidenceSum += confidence;
            if (confidence < HUMAN_REVIEW_CONFIDENCE_THRESHOLD) {
                hasLowConfidenceCell = true;
            }

            String label = cellNode.hasNonNull("label") ? cellNode.get("label").asText().trim() : null;
            VehicleTypeEnum vehicleType = parseVehicleType(cellNode.path("vehicleType"));
            DimensionsMeters dimensions = parseDimensionsMeters(cellNode.path("dimensionsMeters"), row, col);

            if (dimensions == null && cellType == LayoutCellType.PARKING_SLOT) {
                hasNullDimensionsOnSlot = true;
            }

            validatedCells.add(Cell.builder()
                    .row(row)
                    .col(col)
                    .cellType(cellType)
                    .label(label)
                    .vehicleType(vehicleType)
                    .dimensionsMeters(dimensions)
                    .confidence(confidence)
                    .build());
        }

        double computedMeanConfidence = confidenceSum / validatedCells.size();
        double overallConfidence = computedMeanConfidence;
        if (root.hasNonNull("overallConfidence") && root.get("overallConfidence").isNumber()) {
            double reportedOverall = root.get("overallConfidence").asDouble();
            if (Double.isNaN(reportedOverall) || reportedOverall < 0.0 || reportedOverall > 1.0) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Điểm overallConfidence nằm ngoài khoảng [0.0, 1.0]");
            }
            overallConfidence = Math.min(reportedOverall, computedMeanConfidence);
        }

        boolean modelRequestedReview = root.path("needsHumanReview").asBoolean(false);
        boolean needsHumanReview = modelRequestedReview
                || overallConfidence < HUMAN_REVIEW_CONFIDENCE_THRESHOLD
                || hasLowConfidenceCell;

        if (overallConfidence < HUMAN_REVIEW_CONFIDENCE_THRESHOLD) {
            reviewWarnings.add(String.format(Locale.ROOT,
                    "Độ tin cậy tổng thể (%.2f) thấp hơn ngưỡng tự động duyệt (%.2f); yêu cầu Merchant/Admin kiểm tra lại.",
                    overallConfidence, HUMAN_REVIEW_CONFIDENCE_THRESHOLD));
        }
        if (hasLowConfidenceCell) {
            reviewWarnings.add("Có ít nhất 1 ô trên sơ đồ có độ tin cậy < 0.75 cần xác nhận thủ công.");
        }
        if (hasNullDimensionsOnSlot) {
            reviewWarnings.add("Một số ô đỗ xe chưa suy luận được kích thước thực tế (dimensionsMeters = null).");
        }

        GridMetadata grid = GridMetadata.builder()
                .rows(rows)
                .cols(cols)
                .indexBase(0)
                .dimensionUnit("METER")
                .imageWidthPx(imageWidthPx)
                .imageHeightPx(imageHeightPx)
                .imageMimeType(mimeType)
                .build();

        return Response.builder()
                .grid(grid)
                .cells(validatedCells)
                .overallConfidence(Math.round(overallConfidence * 10000.0) / 10000.0)
                .needsHumanReview(needsHumanReview)
                .reviewWarnings(reviewWarnings)
                .build();
    }

    public BenchmarkEvaluationResult evaluateAgainstGroundTruth(
            String fixtureName,
            Response predicted,
            Response groundTruth,
            boolean expectedNeedsHumanReview) {
        Map<String, Cell> truthByCoord = new HashMap<>();
        for (Cell cell : groundTruth.getCells()) {
            truthByCoord.put(cell.getRow() + ":" + cell.getCol(), cell);
        }

        int matchedCoord = 0;
        int matchedType = 0;
        for (Cell pred : predicted.getCells()) {
            String key = pred.getRow() + ":" + pred.getCol();
            Cell truth = truthByCoord.get(key);
            if (truth != null) {
                matchedCoord++;
                if (truth.getCellType() == pred.getCellType()) {
                    matchedType++;
                }
            }
        }

        int totalTruth = groundTruth.getCells().size();
        int totalPred = predicted.getCells().size();
        double precision = totalPred == 0 ? 0.0 : (double) matchedCoord / totalPred;
        double recall = totalTruth == 0 ? 0.0 : (double) matchedCoord / totalTruth;
        double typeAccuracy = totalTruth == 0 ? 0.0 : (double) matchedType / totalTruth;

        return BenchmarkEvaluationResult.builder()
                .fixtureName(fixtureName)
                .totalGroundTruthCells(totalTruth)
                .matchedCoordinateCells(matchedCoord)
                .matchedCellTypeCells(matchedType)
                .coordinatePrecision(Math.round(precision * 10000.0) / 10000.0)
                .coordinateRecall(Math.round(recall * 10000.0) / 10000.0)
                .cellTypeAccuracy(Math.round(typeAccuracy * 10000.0) / 10000.0)
                .schemaValid(predicted.getGrid() != null && predicted.getGrid().getIndexBase() == 0)
                .needsHumanReviewTriggeredAsExpected(predicted.isNeedsHumanReview() == expectedNeedsHumanReview)
                .build();
    }

    private VehicleTypeEnum parseVehicleType(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return VehicleTypeEnum.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    String.format("Loại phương tiện '%s' trong JSON layout không hợp lệ", text));
        }
    }

    private DimensionsMeters parseDimensionsMeters(JsonNode node, int row, int col) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    String.format("dimensionsMeters tại (%d, %d) phải là object hoặc null", row, col));
        }
        JsonNode widthNode = node.get("width");
        JsonNode lengthNode = node.get("length");
        if ((widthNode == null || widthNode.isNull()) && (lengthNode == null || lengthNode.isNull())) {
            return null;
        }
        if (widthNode == null || widthNode.isNull() || !widthNode.isNumber()
                || lengthNode == null || lengthNode.isNull() || !lengthNode.isNumber()) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    String.format("dimensionsMeters tại (%d, %d) phải có cả width và length là số thực dương", row, col));
        }
        BigDecimal width = widthNode.decimalValue();
        BigDecimal length = lengthNode.decimalValue();
        if (width.compareTo(BigDecimal.ZERO) <= 0 || length.compareTo(BigDecimal.ZERO) <= 0
                || width.compareTo(BigDecimal.valueOf(100)) > 0 || length.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    String.format("Kích thước ô (width=%s, length=%s) tại (%d, %d) phải nằm trong khoảng (0, 100] mét",
                            width, length, row, col));
        }
        return DimensionsMeters.builder()
                .width(width)
                .length(length)
                .build();
    }

    private String cleanMarkdownCodeFence(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3).trim();
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }
}

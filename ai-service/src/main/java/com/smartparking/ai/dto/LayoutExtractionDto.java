package com.smartparking.ai.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class LayoutExtractionDto {

    public enum LayoutCellType {
        PARKING_SLOT,
        CHARGING_PILLAR,
        EMERGENCY_EXIT,
        ENTRANCE,
        EXIT,
        DRIVEWAY,
        OBSTACLE
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HintMetadata {
        private String lotName;
        private Integer expectedRows;
        private Integer expectedCols;
        private VehicleTypeEnum defaultVehicleType;
        private BigDecimal scaleHintMetersPerPixel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GridMetadata {
        private int rows;
        private int cols;
        @Builder.Default
        private int indexBase = 0;
        @Builder.Default
        private String dimensionUnit = "METER";
        private int imageWidthPx;
        private int imageHeightPx;
        private String imageMimeType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DimensionsMeters {
        private BigDecimal width;
        private BigDecimal length;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Cell {
        private int row;
        private int col;
        private LayoutCellType cellType;
        private String label;
        private VehicleTypeEnum vehicleType;
        private DimensionsMeters dimensionsMeters;
        private double confidence;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private GridMetadata grid;
        private List<Cell> cells;
        private double overallConfidence;
        private boolean needsHumanReview;
        private List<String> reviewWarnings;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BenchmarkEvaluationResult {
        private String fixtureName;
        private int totalGroundTruthCells;
        private int matchedCoordinateCells;
        private int matchedCellTypeCells;
        private double coordinatePrecision;
        private double coordinateRecall;
        private double cellTypeAccuracy;
        private boolean schemaValid;
        private boolean needsHumanReviewTriggeredAsExpected;
    }
}


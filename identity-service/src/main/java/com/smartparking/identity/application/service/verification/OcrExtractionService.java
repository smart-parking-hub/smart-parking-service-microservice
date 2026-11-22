package com.smartparking.identity.application.service.verification;

import com.smartparking.identity.domain.model.verification.OcrMethod;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class OcrExtractionService {

    @Value("${app.ocr.tesseract-path:tesseract}")
    private String tesseractPath;

    @Value("${app.ocr.enabled:true}")
    private boolean ocrEnabled;

    private static final Pattern REG_NO_PATTERN = Pattern.compile(
            "(?:Mã số doanh nghiệp|Ma so doanh nghiep|Mã số thuế|Ma so thue|Mã số DN|Ma so DN|Số đăng ký|So dang ky|Số GPKD|So GPKD|Số|So)[:\\s.]*([0-9]{10}(?:-[0-9]{3})?|[0-9]{11,13})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern FALLBACK_DIGITS_PATTERN = Pattern.compile("\\b([0-9]{10}(?:-[0-9]{3})?|[0-9]{11,13})\\b");

    private static final Pattern LEGAL_NAME_PATTERN = Pattern.compile(
            "(?:Tên doanh nghiệp viết bằng tiếng Việt|Ten doanh nghiep viet bang tieng Viet|Tên doanh nghiệp|Ten doanh nghiep|Tên công ty|Ten cong ty)[:\\s.]*([^\n\r;]{3,150})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    @Data
    @Builder
    public static class ExtractionResult {
        private boolean engineAvailable;
        private boolean readable;
        private String rawText;
        private String extractedRegistrationNumber;
        private String extractedLegalName;
        private String failureReason;
        private OcrMethod method;
    }

    public ExtractionResult extract(byte[] fileBytes, String contentType, String filename) {
        if (!ocrEnabled) {
            return ExtractionResult.builder()
                    .engineAvailable(false)
                    .readable(false)
                    .failureReason("OCR engine bị vô hiệu hóa bởi cấu hình")
                    .build();
        }

        String ct = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";

        if ("application/pdf".equals(ct)) {
            return extractFromPdf(fileBytes);
        } else if (ct.startsWith("image/")) {
            return extractFromImage(fileBytes);
        }

        return ExtractionResult.builder()
                .engineAvailable(false)
                .readable(false)
                .failureReason("Định dạng tệp không được hỗ trợ để OCR")
                .build();
    }

    private ExtractionResult extractFromPdf(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            // First attempt: direct text stripper for digital PDF
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            if (text != null && text.trim().length() >= 10) {
                log.info("Successfully extracted text from digital PDF (length={})", text.length());
                return withMethod(parseExtractedText(text, true), OcrMethod.PDF_TEXT);
            }

            // Fallback: scanned PDF -> render first page to image and OCR
            PDFRenderer renderer = new PDFRenderer(document);
            var pageBox = document.getPage(0).getCropBox();
            double area = (double) pageBox.getWidth() * pageBox.getHeight();
            if (!Double.isFinite(area) || area <= 0) {
                return unreadablePdf();
            }
            float dpi = (float) Math.min(200, 72 * Math.sqrt(12_000_000 / area));
            if (dpi < 30) {
                return unreadablePdf();
            }
            renderer.setSubsamplingAllowed(true);
            return runImageOcr(renderer.renderImageWithDPI(0, dpi), OcrMethod.SCANNED_PDF_OCR);
        } catch (Exception e) {
            log.warn("Could not read verification PDF ({})", e.getClass().getSimpleName());
            return unreadablePdf();
        }
    }

    private ExtractionResult unreadablePdf() {
        return ExtractionResult.builder()
                .engineAvailable(checkTesseractAvailable())
                .failureReason("Không thể đọc nội dung tài liệu PDF")
                .build();
    }

    private ExtractionResult extractFromImage(byte[] imageBytes) {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                return ExtractionResult.builder()
                        .engineAvailable(checkTesseractAvailable())
                        .readable(false)
                        .failureReason("Tệp ảnh không hợp lệ hoặc không thể giải mã")
                        .build();
            }
            return runImageOcr(image, OcrMethod.IMAGE_OCR);
        } catch (Exception e) {
            log.warn("Could not decode verification image ({})", e.getClass().getSimpleName());
            return ExtractionResult.builder()
                    .engineAvailable(checkTesseractAvailable())
                    .readable(false)
                    .failureReason("Không thể giải mã hình ảnh")
                    .build();
        }
    }

    private ExtractionResult runImageOcr(BufferedImage image, OcrMethod method) {
        String binary = resolveTesseractBinary();
        if (binary == null) {
            return ExtractionResult.builder()
                    .engineAvailable(false)
                    .readable(false)
                    .failureReason("Tesseract OCR engine không khả dụng trên hệ thống")
                    .build();
        }

        Path tempInput = null;
        Path tempOutput = null;
        Process process = null;
        try {
            tempInput = Files.createTempFile("ocr_input_", ".png");
            tempOutput = Files.createTempFile("ocr_output_", ".txt");
            ImageIO.write(image, "png", tempInput.toFile());

            process = new ProcessBuilder(binary, tempInput.toString(), "stdout", "-l", "vie+eng", "--psm", "3")
                    .redirectOutput(tempOutput.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                return withMethod(ocrUnavailable("Quá trình OCR vượt quá thời gian cho phép"), method);
            }
            if (process.exitValue() != 0) {
                log.warn("Tesseract process exited with code {}", process.exitValue());
                return withMethod(ocrUnavailable("OCR engine không thể nhận dạng hình ảnh"), method);
            }
            try (InputStream input = Files.newInputStream(tempOutput)) {
                String text = new String(input.readNBytes(256 * 1024), StandardCharsets.UTF_8);
                return withMethod(parseExtractedText(text, true), method);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ocrUnavailable("Kiểm tra OCR bị gián đoạn");
        } catch (IOException error) {
            log.warn("Could not run local OCR ({})", error.getClass().getSimpleName());
            return ocrUnavailable("Không thể chạy OCR cục bộ");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteTemporaryFile(tempInput);
            deleteTemporaryFile(tempOutput);
        }
    }

    private ExtractionResult withMethod(ExtractionResult result, OcrMethod method) {
        result.setMethod(method);
        return result;
    }

    private ExtractionResult ocrUnavailable(String reason) {
        return ExtractionResult.builder().engineAvailable(false).failureReason(reason).build();
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            log.warn("Could not remove temporary OCR file ({})", error.getClass().getSimpleName());
        }
    }

    public ExtractionResult parseExtractedText(String rawText, boolean engineAvailable) {
        if (rawText == null || rawText.isBlank()) {
            return ExtractionResult.builder()
                    .engineAvailable(engineAvailable)
                    .readable(false)
                    .failureReason("Không thể trích xuất ký tự từ tài liệu")
                    .build();
        }

        String regNumber = findRegistrationNumber(rawText);
        String legalName = findLegalName(rawText);

        return ExtractionResult.builder()
                .engineAvailable(engineAvailable)
                .readable(true)
                .rawText(rawText)
                .extractedRegistrationNumber(regNumber)
                .extractedLegalName(legalName)
                .build();
    }

    public static String findRegistrationNumber(String text) {
        if (text == null) return null;
        Matcher m = REG_NO_PATTERN.matcher(text);
        if (m.find()) {
            return cleanDigits(m.group(1));
        }
        Matcher fb = FALLBACK_DIGITS_PATTERN.matcher(text);
        if (fb.find()) {
            return cleanDigits(fb.group(1));
        }
        return null;
    }

    public static String findLegalName(String text) {
        if (text == null) return null;
        Matcher m = LEGAL_NAME_PATTERN.matcher(text);
        if (m.find()) {
            String name = m.group(1).trim();
            // clean up trailing punctuation
            name = name.replaceAll("^[\\s:.-]+|[\\s:.-]+$", "");
            if (name.length() >= 3) return name;
        }
        return null;
    }

    public static String cleanDigits(String val) {
        if (val == null) return null;
        return val.replaceAll("[^0-9]", "");
    }

    public static String normalizeText(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);
        return normalized.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public boolean checkTesseractAvailable() {
        return resolveTesseractBinary() != null;
    }

    private String resolveTesseractBinary() {
        List<String> candidates = List.of(
                tesseractPath,
                "tesseract",
                "C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
                "C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe",
                "/usr/bin/tesseract",
                "/usr/local/bin/tesseract"
        );

        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}

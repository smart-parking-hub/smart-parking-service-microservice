package com.smartparking.identity.application.service.verification;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
public class FileValidationService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L; // 5 MiB
    public static final long MAX_IMAGE_PIXELS = 20_000_000L; // 20 megapixels
    public static final int MAX_PDF_PAGES = 5;

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public static final long MAX_REQUEST_FILE_BYTES = 16 * 1024 * 1024L;

    public record ValidatedFile(byte[] bytes, String contentType) {}

    public ValidatedFile validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tệp không được để trống!");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw VerificationUploadException.tooLarge("Dung lượng tệp vượt quá giới hạn 5MB!");
        }

        String rawContentType = file.getContentType();
        String contentType = rawContentType != null ? rawContentType.toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw VerificationUploadException.unsupportedType("Chỉ chấp nhận tệp PDF hoặc ảnh JPEG, PNG, WebP!");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể đọc nội dung tệp!");
        }

        if ("application/pdf".equals(contentType)) {
            validatePdf(bytes);
            return new ValidatedFile(bytes, "application/pdf");
        }
        return new ValidatedFile(bytes, validateImage(bytes, contentType));
    }

    private void validatePdf(byte[] bytes) {
        // PDF magic header verification: must start with %PDF-
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tệp PDF không hợp lệ hoặc bị hỏng!");
        }

        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Tệp PDF có mật khẩu bảo vệ!");
            }
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Tệp PDF vượt quá " + MAX_PDF_PAGES + " trang cho phép!");
            }
        } catch (AppException e) {
            throw e;
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tệp PDF có mật khẩu bảo vệ!");
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("password")) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Tệp PDF có mật khẩu bảo vệ!");
            }
            log.warn("Could not open uploaded PDF");
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể mở hoặc xử lý tệp PDF!");
        }
    }

    private String validateImage(byte[] bytes, String contentType) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Tệp ảnh không hợp lệ hoặc bị hỏng!");
            }

            ImageReader reader = readers.next();
            try {
                String detectedType = "image/" + reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!detectedType.equals(contentType)) {
                    throw VerificationUploadException.unsupportedType(
                            "Định dạng thực của ảnh không khớp với loại tệp khai báo!");
                }
                reader.setInput(input, true, true);
                long totalPixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (totalPixels > MAX_IMAGE_PIXELS) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Ảnh vượt quá độ phân giải 20 megapixel cho phép!");
                }
                reader.read(0);
                return detectedType;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.warn("Could not decode uploaded image");
            throw new AppException(ErrorCode.BAD_REQUEST, "Lỗi khi giải mã hình ảnh!");
        }
    }

    public static String computeSha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "document";
        String clean = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.length() > 100) {
            clean = clean.substring(clean.length() - 100);
        }
        return clean;
    }
}

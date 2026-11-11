package com.smartparking.payment.application.service.impl;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.security.SecurityConstants;
import com.smartparking.payment.application.dto.DisputeEvidenceResponse;
import com.smartparking.payment.application.service.DisputeEvidenceService;
import com.smartparking.payment.domain.model.Dispute;
import com.smartparking.payment.domain.model.DisputeEvidenceFile;
import com.smartparking.payment.domain.repository.DisputeEvidenceFileRepository;
import com.smartparking.payment.domain.repository.DisputeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DisputeEvidenceServiceImpl implements DisputeEvidenceService {

    public static final String EVIDENCE_PATH_PREFIX = "/api/v1/disputes/evidence/";

    private static final int MAX_FILES_PER_REQUEST = 5;
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
    private static final Pattern SAFE_STORED_FILE_NAME_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|webp)$");

    private final DisputeEvidenceFileRepository evidenceFileRepository;
    private final DisputeRepository disputeRepository;
    private final Path storageRoot;

    public DisputeEvidenceServiceImpl(
            DisputeEvidenceFileRepository evidenceFileRepository,
            DisputeRepository disputeRepository,
            @Value("${app.storage.dispute-evidence-dir:uploads/dispute-evidence}") String storageDirectory) {
        this.evidenceFileRepository = evidenceFileRepository;
        this.disputeRepository = disputeRepository;
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public List<DisputeEvidenceResponse> upload(Long driverUserId, List<MultipartFile> files, String baseUrl) {
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng chọn ít nhất một ảnh chứng từ");
        }
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ được tải lên tối đa 5 ảnh cho mỗi lần gửi");
        }

        String normalizedBase = normalizeBaseUrl(baseUrl);
        List<Path> writtenPaths = new ArrayList<>();
        List<DisputeEvidenceResponse> responses = new ArrayList<>();

        try {
            Files.createDirectories(storageRoot);
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty() || file.getSize() <= 0) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Tệp ảnh tải lên không được để trống");
                }
                if (file.getSize() > MAX_FILE_SIZE) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mỗi ảnh chứng từ không được vượt quá 5 MB");
                }

                String safeOriginalName = sanitizeOriginalFileName(file.getOriginalFilename());
                String ext = getLowercaseExtension(safeOriginalName);
                if (!ALLOWED_EXTENSIONS.contains(ext)) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ chấp nhận ảnh định dạng JPG, PNG hoặc WebP");
                }

                String detectedContentType = detectAndVerifyImageMimeType(file, ext);
                String storedFileName = UUID.randomUUID() + ext;
                Path destination = storageRoot.resolve(storedFileName).normalize();
                if (!destination.startsWith(storageRoot)) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Đường dẫn lưu trữ không hợp lệ");
                }

                long writtenBytes = copyWithSizeLimit(file, destination);
                writtenPaths.add(destination);

                DisputeEvidenceFile saved = evidenceFileRepository.saveAndFlush(DisputeEvidenceFile.builder()
                        .storedFileName(storedFileName)
                        .originalFileName(safeOriginalName)
                        .contentType(detectedContentType)
                        .sizeBytes(writtenBytes)
                        .uploaderDriverId(driverUserId)
                        .build());

                String contentUrl = EVIDENCE_PATH_PREFIX + saved.getStoredFileName();
                String fullUrl = normalizedBase != null ? normalizedBase + contentUrl : contentUrl;
                responses.add(DisputeEvidenceResponse.builder()
                        .fileName(saved.getStoredFileName())
                        .originalFileName(saved.getOriginalFileName())
                        .contentType(saved.getContentType())
                        .sizeBytes(saved.getSizeBytes())
                        .contentUrl(contentUrl)
                        .url(fullUrl)
                        .build());
            }
            return responses;
        } catch (IOException ex) {
            cleanupFiles(writtenPaths);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh chứng từ: " + ex.getMessage());
        } catch (RuntimeException ex) {
            cleanupFiles(writtenPaths);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StoredEvidence getContent(Long userId, String role, String fileName) {
        if (fileName == null || !SAFE_STORED_FILE_NAME_PATTERN.matcher(fileName).matches()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên tệp chứng từ không hợp lệ");
        }

        DisputeEvidenceFile evidenceFile = evidenceFileRepository.findByStoredFileName(fileName)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy ảnh chứng từ"));

        if (!isAuthorizedToView(userId, role, evidenceFile)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập ảnh chứng từ này");
        }

        try {
            Path resolved = storageRoot.resolve(evidenceFile.getStoredFileName()).normalize();
            if (!resolved.startsWith(storageRoot)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Đường dẫn tệp chứng từ không hợp lệ");
            }
            Resource resource = new UrlResource(resolved.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy file ảnh chứng từ");
            }
            return new StoredEvidence(resource, evidenceFile.getContentType(), evidenceFile.getOriginalFileName());
        } catch (IOException ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc ảnh chứng từ");
        }
    }

    public static Optional<String> extractInternalFileName(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException ex) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đường dẫn ảnh chứng từ không hợp lệ");
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith(EVIDENCE_PATH_PREFIX)) {
            return Optional.empty();
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đường dẫn ảnh chứng từ không hợp lệ");
        }
        String candidate = path.substring(EVIDENCE_PATH_PREFIX.length());
        if (!SAFE_STORED_FILE_NAME_PATTERN.matcher(candidate).matches()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên tệp ảnh chứng từ không hợp lệ");
        }
        return Optional.of(candidate);
    }

    private boolean isAuthorizedToView(Long userId, String role, DisputeEvidenceFile evidenceFile) {
        if (SecurityConstants.ROLE_ADMIN.equals(role) || "ADMIN".equals(role)) {
            return true;
        }
        if (SecurityConstants.ROLE_DRIVER.equals(role) || "DRIVER".equals(role)) {
            return evidenceFile.getUploaderDriverId().equals(userId);
        }
        if (SecurityConstants.ROLE_MERCHANT.equals(role) || "MERCHANT".equals(role)) {
            if (evidenceFile.getDisputeId() == null) {
                return false;
            }
            Dispute dispute = disputeRepository.findById(evidenceFile.getDisputeId()).orElse(null);
            return dispute != null && dispute.getMerchantId() != null && dispute.getMerchantId().equals(userId);
        }
        return false;
    }

    private String detectAndVerifyImageMimeType(MultipartFile file, String lowercaseExt) throws IOException {
        byte[] header = new byte[12];
        int read = 0;
        try (InputStream input = file.getInputStream()) {
            while (read < header.length) {
                int n = input.read(header, read, header.length - read);
                if (n == -1) {
                    break;
                }
                read += n;
            }
        }

        String detected = null;
        if (read >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            detected = "image/jpeg";
        } else if (read >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            detected = "image/png";
        } else if (read >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P') {
            detected = "image/webp";
        }

        if (detected == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Nội dung tệp không phải ảnh JPG, PNG hoặc WebP hợp lệ");
        }

        boolean matchesExtension = switch (detected) {
            case "image/jpeg" -> ".jpg".equals(lowercaseExt) || ".jpeg".equals(lowercaseExt);
            case "image/png" -> ".png".equals(lowercaseExt);
            case "image/webp" -> ".webp".equals(lowercaseExt);
            default -> false;
        };
        if (!matchesExtension) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phần mở rộng tệp không khớp với định dạng ảnh thực tế");
        }
        return detected;
    }

    private long copyWithSizeLimit(MultipartFile file, Path destination) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        try (InputStream input = file.getInputStream();
             OutputStream output = Files.newOutputStream(destination)) {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                total += bytesRead;
                if (total > MAX_FILE_SIZE) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mỗi ảnh chứng từ không được vượt quá 5 MB");
                }
                output.write(buffer, 0, bytesRead);
            }
        }
        if (total == 0L) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tệp ảnh tải lên không được để trống");
        }
        return total;
    }

    private String sanitizeOriginalFileName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "evidence.jpg";
        }
        int lastSlash = Math.max(rawName.lastIndexOf('/'), rawName.lastIndexOf('\\'));
        String baseName = lastSlash >= 0 ? rawName.substring(lastSlash + 1) : rawName;
        StringBuilder clean = new StringBuilder(baseName.length());
        for (int i = 0; i < baseName.length(); i++) {
            char c = baseName.charAt(i);
            if (!Character.isISOControl(c)) {
                clean.append(c);
            }
        }
        String result = clean.toString().trim();
        if (result.isEmpty()) {
            return "evidence.jpg";
        }
        if (result.length() > 255) {
            String ext = getLowercaseExtension(result);
            int maxBase = Math.max(1, 255 - ext.length());
            return result.substring(0, maxBase) + ext;
        }
        return result;
    }

    private String getLowercaseExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx).toLowerCase(Locale.ROOT);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void cleanupFiles(List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                log.warn("Could not remove orphaned dispute evidence file {}", path);
            }
        }
    }
}


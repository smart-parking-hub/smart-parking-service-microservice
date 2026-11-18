package com.smartparking.identity.application.service.verification;

import com.smartparking.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidationServiceTest {

    private FileValidationService service;

    @BeforeEach
    void setUp() {
        service = new FileValidationService();
    }

    @Test
    @DisplayName("AC2: Valid PDF passes validation")
    void validPdfPasses() throws IOException {
        byte[] pdfBytes = createPdf(1, false);
        MockMultipartFile file = new MockMultipartFile(
                "files", "gpkd.pdf", "application/pdf", pdfBytes);

        service.validateFile(file);
    }

    @Test
    @DisplayName("AC2: Password-encrypted PDF fails with specific 400 error")
    void encryptedPdfFails() throws IOException {
        byte[] pdfBytes = createPdf(1, true);
        MockMultipartFile file = new MockMultipartFile(
                "files", "encrypted.pdf", "application/pdf", pdfBytes);

        assertThatThrownBy(() -> service.validateFile(file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("mật khẩu bảo vệ");
    }

    @Test
    @DisplayName("AC2: PDF exceeding 5 pages fails with specific 400 error")
    void pdfOver5PagesFails() throws IOException {
        byte[] pdfBytes = createPdf(6, false);
        MockMultipartFile file = new MockMultipartFile(
                "files", "long.pdf", "application/pdf", pdfBytes);

        assertThatThrownBy(() -> service.validateFile(file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("vượt quá 5 trang");
    }

    @Test
    @DisplayName("AC2: Fake PDF (corrupted header) fails validation")
    void fakePdfFails() {
        byte[] fakeBytes = "NOT A REAL PDF FILE CONTENT".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "files", "fake.pdf", "application/pdf", fakeBytes);

        assertThatThrownBy(() -> service.validateFile(file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không hợp lệ hoặc bị hỏng");
    }

    @Test
    @DisplayName("AC2: Valid PNG and JPEG images pass validation")
    void validImagesPass() throws IOException {
        byte[] pngBytes = createImage(800, 600, "png");
        MockMultipartFile pngFile = new MockMultipartFile(
                "files", "photo.png", "image/png", pngBytes);
        service.validateFile(pngFile);

        byte[] jpgBytes = createImage(1200, 800, "jpeg");
        MockMultipartFile jpgFile = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", jpgBytes);
        service.validateFile(jpgFile);
    }

    @Test
    @DisplayName("AC2: Image exceeding 20 megapixels fails with specific 400 error")
    void imageOver20MegapixelsFails() throws IOException {
        // 5000 x 4500 = 22.5 megapixels
        byte[] largeImageBytes = createImage(5000, 4500, "png");
        MockMultipartFile file = new MockMultipartFile(
                "files", "giant.png", "image/png", largeImageBytes);

        assertThatThrownBy(() -> service.validateFile(file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("20 megapixel");
    }

    @Test
    @DisplayName("AC2: File exceeding 5MB fails validation")
    void fileOver5MbFails() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "files", "huge.pdf", "application/pdf", big);

        assertThatThrownBy(() -> service.validateFile(file))
                .isInstanceOfSatisfying(VerificationUploadException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE))
                .hasMessageContaining("vượt quá giới hạn 5MB");
    }

    @Test
    @DisplayName("AC2: Unsupported content-type (e.g. text/html, exe) fails validation")
    void unsupportedTypeFails() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "malicious.sh", "application/x-sh", "rm -rf /".getBytes());

        assertThatThrownBy(() -> service.validateFile(file))
                .isInstanceOfSatisfying(VerificationUploadException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE))
                .hasMessageContaining("Chỉ chấp nhận tệp PDF hoặc ảnh");
    }

    @Test
    void rejectsPngDeclaredAsJpeg() throws IOException {
        var file = new MockMultipartFile("files", "photo.jpg", "image/jpeg", createImage(32, 32, "png"));
        assertThatThrownBy(() -> service.validateFile(file))
                .isInstanceOfSatisfying(VerificationUploadException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void detectsWebpWithTheRuntimeImageReader() {
        // A real 1x1 lossless WebP.
        byte[] webp = java.util.Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
        var file = new MockMultipartFile("files", "photo.webp", "image/webp", webp);
        assertThat(service.validateFile(file).contentType()).isEqualTo("image/webp");
    }

    private byte[] createPdf(int pages, boolean encrypt) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            if (encrypt) {
                AccessPermission ap = new AccessPermission();
                StandardProtectionPolicy spp = new StandardProtectionPolicy("secret", "secret", ap);
                spp.setEncryptionKeyLength(128);
                doc.protect(spp);
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createImage(int width, int height, String format) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, format, baos);
            return baos.toByteArray();
        }
    }
}

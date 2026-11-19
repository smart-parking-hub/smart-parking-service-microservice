package com.smartparking.identity.application.service.verification;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class OcrExtractionServiceTest {

    private OcrExtractionService service;

    @BeforeEach
    void setUp() {
        service = new OcrExtractionService();
        ReflectionTestUtils.setField(service, "ocrEnabled", true);
        ReflectionTestUtils.setField(service, "tesseractPath", "non_existent_tesseract_binary");
    }

    @Test
    @DisplayName("AC3: Real local PDF extraction extracts registration number and legal name from synthetic Vietnamese PDF")
    void extractFromDigitalPdf() throws IOException {
        byte[] pdfBytes = createSyntheticBusinessRegistrationPdf(
                "0101234567",
                "CONG TY TNHH SMART PARKING VIET NAM"
        );

        OcrExtractionService.ExtractionResult result = service.extract(pdfBytes, "application/pdf", "gpkd.pdf");

        assertThat(result.isReadable()).isTrue();
        assertThat(result.getExtractedRegistrationNumber()).isEqualTo("0101234567");
        assertThat(result.getExtractedLegalName()).contains("SMART PARKING");
    }

    @Test
    @DisplayName("AC3: Extraction recognizes 13-digit branch registration numbers")
    void extract13DigitNumber() throws IOException {
        byte[] pdfBytes = createSyntheticBusinessRegistrationPdf(
                "0101234567-001",
                "CHI NHANH CONG TY TNHH SMART PARKING"
        );

        OcrExtractionService.ExtractionResult result = service.extract(pdfBytes, "application/pdf", "chinhanh.pdf");

        assertThat(result.isReadable()).isTrue();
        assertThat(result.getExtractedRegistrationNumber()).isEqualTo("0101234567001");
    }

    @Test
    @DisplayName("AC3: Parse extracted text handles mismatch and absent fields")
    void parseExtractedTextHandling() {
        String randomText = "Day la mot tai lieu bat ky khong co so dang ky va ten phap ly.";
        OcrExtractionService.ExtractionResult result = service.parseExtractedText(randomText, true);

        assertThat(result.isReadable()).isTrue();
        assertThat(result.getExtractedRegistrationNumber()).isNull();
        assertThat(result.getExtractedLegalName()).isNull();
    }

    @Test
    @DisplayName("AC3: When OCR engine is unavailable, image extraction reports UNAVAILABLE status gracefully")
    void imageOcrUnavailableFallback() {
        byte[] dummyImage = new byte[]{1, 2, 3, 4};
        OcrExtractionService.ExtractionResult result = service.extract(dummyImage, "image/png", "scan.png");

        assertThat(result.isEngineAvailable()).isFalse();
        assertThat(result.isReadable()).isFalse();
        assertThat(result.getFailureReason()).isNotEmpty();
    }

    private byte[] createSyntheticBusinessRegistrationPdf(String regNo, String companyName) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("CONG HOA XA HOI CHU NGHIA VIET NAM");
                cs.newLineAtOffset(0, -20);
                cs.showText("GIAY CHUNG NHAN DANG KY DOANH NGHIEP");
                cs.newLineAtOffset(0, -30);

                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("Ma so doanh nghiep: " + regNo);
                cs.newLineAtOffset(0, -20);
                cs.showText("Ten cong ty: " + companyName);
                cs.newLineAtOffset(0, -20);
                cs.showText("Dia chi tru so chinh: 123 Nguyen Trai, Ha Noi");
                cs.endText();
            }

            doc.save(baos);
            return baos.toByteArray();
        }
    }
}

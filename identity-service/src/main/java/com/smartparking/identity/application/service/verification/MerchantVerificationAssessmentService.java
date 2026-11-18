package com.smartparking.identity.application.service.verification;

import com.smartparking.identity.domain.model.verification.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MerchantVerificationAssessmentService {

    private final OcrExtractionService ocrService;
    private final VerificationDocumentStorage documentStorage;
    record AssessmentResult(AssessmentStatus status, List<MerchantVerificationAssessmentCheck> checks,
                            String documentId, OcrMethod method) {
        AssessmentResult(AssessmentStatus status, List<MerchantVerificationAssessmentCheck> checks) {
            this(status, checks, null, null);
        }
    }

    AssessmentResult assess(MerchantVerificationSubmission submission, boolean expired) {
        List<MerchantVerificationAssessmentCheck> checks = new ArrayList<>();

        // Older pending submissions had no persisted selection.
        Optional<MerchantVerificationDocument> bizRegDocOpt = submission.getDocuments().stream()
                .filter(d -> d.getDocumentType() == DocumentType.BUSINESS_REGISTRATION)
                .filter(d -> submission.getOcrDocumentId() == null || d.getId().equals(submission.getOcrDocumentId()))
                .min(Comparator.comparing(MerchantVerificationDocument::getUploadedAt)
                        .thenComparing(MerchantVerificationDocument::getId));

        // Check 1: REQUIRED_DOCUMENT
        if (bizRegDocOpt.isEmpty()) {
            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.REQUIRED_DOCUMENT.name())
                    .result(CheckResult.WARN.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .field("document")
                    .message("Thiếu Giấy chứng nhận đăng ký kinh doanh bắt buộc")
                    .build());

            // Without required doc, remaining checks cannot proceed
            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.READABILITY.name())
                    .result(CheckResult.NOT_CHECKED.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .message("Không có tài liệu để kiểm tra tính dễ đọc")
                    .build());

            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.REGISTRATION_NUMBER_MATCH.name())
                    .result(CheckResult.NOT_CHECKED.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .field("businessRegistrationNumber")
                    .declaredValue(submission.getBusinessRegistrationNumber())
                    .message("Không có tài liệu để đối chiếu mã số doanh nghiệp")
                    .build());

            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.LEGAL_NAME_MATCH.name())
                    .result(CheckResult.NOT_CHECKED.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .field("legalName")
                    .declaredValue(submission.getLegalName())
                    .message("Không có tài liệu để đối chiếu tên doanh nghiệp")
                    .build());

            return new AssessmentResult(AssessmentStatus.COMPLETED, checks);
        }

        MerchantVerificationDocument doc = bizRegDocOpt.get();
        checks.add(MerchantVerificationAssessmentCheck.builder()
                .submission(submission)
                .code(CheckCode.REQUIRED_DOCUMENT.name())
                .result(CheckResult.PASS.name())
                .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                .field("document")
                .message("Đã nộp Giấy chứng nhận đăng ký kinh doanh")
                .build());

        // Load document bytes
        byte[] bytes = expired ? null : documentStorage.read(doc);

        if (!expired && (bytes == null || bytes.length == 0)) {
            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.READABILITY.name())
                    .result(CheckResult.WARN.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .message("Nội dung tệp tài liệu rỗng hoặc không thể truy xuất")
                    .build());

            return new AssessmentResult(AssessmentStatus.COMPLETED, checks);
        }

        // Run OCR extraction
        OcrExtractionService.ExtractionResult result = expired
                ? OcrExtractionService.ExtractionResult.builder()
                        .failureReason("Quá thời hạn kiểm tra tự động. Quản trị viên sẽ kiểm tra thủ công.")
                        .build()
                : ocrService.extract(bytes, doc.getContentType(), doc.getOriginalName());

        if (!result.isEngineAvailable()) {
            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.READABILITY.name())
                    .result(CheckResult.NOT_CHECKED.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .message(result.getFailureReason() != null ? result.getFailureReason() : "OCR engine không khả dụng trên hệ thống")
                    .build());

            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.REGISTRATION_NUMBER_MATCH.name())
                    .result(CheckResult.NOT_CHECKED.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .field("businessRegistrationNumber")
                    .declaredValue(submission.getBusinessRegistrationNumber())
                    .message("Chưa đối chiếu do OCR engine không khả dụng")
                    .build());

            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.LEGAL_NAME_MATCH.name())
                    .result(CheckResult.NOT_CHECKED.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .field("legalName")
                    .declaredValue(submission.getLegalName())
                    .message("Chưa đối chiếu do OCR engine không khả dụng")
                    .build());

            return new AssessmentResult(AssessmentStatus.UNAVAILABLE, checks, doc.getId(), result.getMethod());
        }

        // Check 2: READABILITY
        if (result.isReadable()) {
            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.READABILITY.name())
                    .result(CheckResult.PASS.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .message("Tài liệu đọc rõ ràng")
                    .build());
        } else {
            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.READABILITY.name())
                    .result(CheckResult.WARN.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .message(result.getFailureReason() != null ? result.getFailureReason() : "Tài liệu bị mờ hoặc không thể đọc được")
                    .build());
        }

        // Check 3: REGISTRATION_NUMBER_MATCH
        String declaredNo = OcrExtractionService.cleanDigits(submission.getBusinessRegistrationNumber());
        String extractedNo = result.getExtractedRegistrationNumber();

        if (extractedNo != null && !extractedNo.isBlank()) {
            boolean matches = declaredNo != null && declaredNo.equals(extractedNo);
            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.REGISTRATION_NUMBER_MATCH.name())
                    .result(matches ? CheckResult.PASS.name() : CheckResult.WARN.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .field("businessRegistrationNumber")
                    .declaredValue(submission.getBusinessRegistrationNumber())
                    .extractedValue(extractedNo)
                    .message(matches ? "Mã số doanh nghiệp khớp hoàn toàn" : "Mã số doanh nghiệp không khớp với thông tin đã khai")
                    .build());
        } else {
            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.REGISTRATION_NUMBER_MATCH.name())
                    .result(CheckResult.WARN.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .field("businessRegistrationNumber")
                    .declaredValue(submission.getBusinessRegistrationNumber())
                    .extractedValue(null)
                    .message("Không thể trích xuất mã số doanh nghiệp từ tài liệu")
                    .build());
        }

        // Check 4: LEGAL_NAME_MATCH
        String declaredName = submission.getLegalName();
        String extractedName = result.getExtractedLegalName();

        if (extractedName != null && !extractedName.isBlank()) {
            String normDeclared = OcrExtractionService.normalizeText(declaredName);
            String normExtracted = OcrExtractionService.normalizeText(extractedName);
            boolean matches = normDeclared.equals(normExtracted);

            checks.add(MerchantVerificationAssessmentCheck.builder()
                    .submission(submission)
                    .code(CheckCode.LEGAL_NAME_MATCH.name())
                    .result(matches ? CheckResult.PASS.name() : CheckResult.WARN.name())
                    .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                    .field("legalName")
                    .declaredValue(declaredName)
                    .extractedValue(extractedName)
                    .message(matches ? "Tên doanh nghiệp khớp" : "Tên doanh nghiệp không khớp với thông tin đã khai")
                    .build());
        } else {
            // Check if raw text contains declared name
            String normDeclared = OcrExtractionService.normalizeText(declaredName);
            String normRaw = OcrExtractionService.normalizeText(result.getRawText());
            if (normRaw != null && normDeclared != null && !normDeclared.isBlank() && normRaw.contains(normDeclared)) {
                checks.add(MerchantVerificationAssessmentCheck.builder()
                        .submission(submission)
                        .code(CheckCode.LEGAL_NAME_MATCH.name())
                        .result(CheckResult.PASS.name())
                        .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                        .field("legalName")
                        .declaredValue(declaredName)
                        .extractedValue(declaredName)
                        .message("Tên doanh nghiệp khớp trong nội dung tài liệu")
                        .build());
            } else {
                checks.add(MerchantVerificationAssessmentCheck.builder()
                        .submission(submission)
                        .code(CheckCode.LEGAL_NAME_MATCH.name())
                        .result(CheckResult.WARN.name())
                        .documentType(DocumentType.BUSINESS_REGISTRATION.name())
                        .field("legalName")
                        .declaredValue(declaredName)
                        .extractedValue(null)
                        .message("Không thể trích xuất tên doanh nghiệp từ tài liệu")
                        .build());
            }
        }

        return new AssessmentResult(AssessmentStatus.COMPLETED, checks, doc.getId(), result.getMethod());
    }
}

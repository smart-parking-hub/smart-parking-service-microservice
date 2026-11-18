package com.smartparking.identity.application.service.verification;

import com.smartparking.common.event.MerchantVerificationEvent;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.messaging.TransactionalEventOutbox;
import com.smartparking.identity.application.dto.verification.*;
import com.smartparking.identity.domain.model.Merchant;
import com.smartparking.identity.domain.model.verification.*;
import com.smartparking.identity.domain.repository.*;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantVerificationServiceImpl implements MerchantVerificationService {

    private final MerchantVerificationDossierRepository dossierRepository;
    private final MerchantVerificationDocumentRepository documentRepository;
    private final VerificationDocumentStorage documentStorage;
    private final MerchantVerificationSubmissionRepository submissionRepository;
    private final MerchantVerificationHistoryRepository historyRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionalEventOutbox outbox;

    private static final Set<String> VALID_ITEM_KEYS = Set.of(
            "BUSINESS_INFO",
            DocumentType.BUSINESS_REGISTRATION.name(),
            DocumentType.AUTHORIZATION_LETTER.name(),
            DocumentType.SUPPORTING_DOCUMENT.name()
    );

    @Override
    @Transactional(readOnly = true)
    public MerchantVerificationDetailResponse getMerchantVerification(Long merchantUserId) {
        Merchant merchant = requireMerchant(merchantUserId);
        MerchantVerificationDossier dossier = dossierRepository.findByMerchantId(merchant.getId())
                .orElseGet(() -> newDraft(merchant));
        return toDetailResponse(dossier, false);
    }

    @Override
    @Transactional
    public MerchantVerificationDetailResponse updateVerificationDetails(Long merchantUserId, UpdateVerificationDetailsRequest request) {
        Merchant merchant = requireMerchantForUpdate(merchantUserId);
        MerchantVerificationDossier dossier = dossierRepository.findByMerchantIdForUpdate(merchant.getId())
                .orElseGet(() -> getOrCreateDossier(merchant));

        validateDraftRevision(dossier, request.getDraftRevision());
        validateEditable(dossier, "BUSINESS_INFO");

        dossier.setBusinessRegistrationNumber(request.getBusinessRegistrationNumber().trim());
        dossier.setLegalName(request.getLegalName().trim());
        String note = request.getResubmissionNote();
        dossier.setResubmissionNote(note == null || note.isBlank() ? null : note.trim());
        dossier.setDraftRevision(dossier.getDraftRevision() + 1);

        dossierRepository.save(dossier);
        log.info("Updated verification details for merchant {}, new draftRevision={}",
                merchant.getId(), dossier.getDraftRevision());

        return toDetailResponse(dossier, false);
    }

    @Override
    @Transactional
    public MerchantVerificationDetailResponse replaceDocuments(
            Long merchantUserId, String rawDocumentType, Long draftRevision, List<MultipartFile> files) {
        Merchant merchant = requireMerchantForUpdate(merchantUserId);
        MerchantVerificationDossier dossier = dossierRepository.findByMerchantIdForUpdate(merchant.getId())
                .orElseGet(() -> getOrCreateDossier(merchant));

        validateDraftRevision(dossier, draftRevision);

        DocumentType docType;
        try {
            docType = DocumentType.valueOf(rawDocumentType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Loại tài liệu không hợp lệ: " + rawDocumentType);
        }

        validateEditable(dossier, docType.name());

        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cần ít nhất một tệp cho loại tài liệu này!");
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > FileValidationService.MAX_REQUEST_FILE_BYTES) {
            throw VerificationUploadException.tooLarge("Tổng dung lượng tệp vượt quá 16MB!");
        }
        if (files.size() > 3) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ được tải lên tối đa 3 tệp cho mỗi loại tài liệu!");
        }
        documentStorage.replaceGroup(dossier, docType, files);

        dossier.setDraftRevision(dossier.getDraftRevision() + 1);
        dossierRepository.save(dossier);
        log.info("Replaced {} documents for type {} in dossier {}, new draftRevision={}",
                files.size(), docType, dossier.getId(), dossier.getDraftRevision());

        return toDetailResponse(dossier, false);
    }

    @Override
    @Transactional
    public MerchantVerificationDetailResponse deleteDocuments(Long merchantUserId, String rawDocumentType, Long draftRevision) {
        Merchant merchant = requireMerchantForUpdate(merchantUserId);
        MerchantVerificationDossier dossier = dossierRepository.findByMerchantIdForUpdate(merchant.getId())
                .orElseGet(() -> getOrCreateDossier(merchant));

        validateDraftRevision(dossier, draftRevision);

        DocumentType docType;
        try {
            docType = DocumentType.valueOf(rawDocumentType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Loại tài liệu không hợp lệ: " + rawDocumentType);
        }

        validateEditable(dossier, docType.name());

        documentRepository.markDraftDocumentsInactive(dossier.getId(), docType);
        dossier.setDraftRevision(dossier.getDraftRevision() + 1);
        dossierRepository.save(dossier);

        log.info("Deleted draft documents for type {} in dossier {}", docType, dossier.getId());
        return toDetailResponse(dossier, false);
    }

    @Override
    @Transactional
    public MerchantVerificationDetailResponse submitVerification(Long merchantUserId, SubmitVerificationRequest request) {
        Merchant merchant = requireMerchantForUpdate(merchantUserId);
        MerchantVerificationDossier dossier = dossierRepository.findByMerchantIdForUpdate(merchant.getId())
                .orElseGet(() -> getOrCreateDossier(merchant));

        validateDraftRevision(dossier, request.getDraftRevision());

        if (dossier.getStatus() == VerificationStatus.PENDING_REVIEW || dossier.getStatus() == VerificationStatus.VERIFIED) {
            throw new AppException(ErrorCode.CONFLICT, "Hồ sơ đang trong quá trình xét duyệt hoặc đã được xác minh!");
        }

        // Validate mandatory information
        if (dossier.getBusinessRegistrationNumber() == null || dossier.getBusinessRegistrationNumber().isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã số đăng ký kinh doanh không được để trống khi gửi hồ sơ!");
        }
        if (dossier.getLegalName() == null || dossier.getLegalName().isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên doanh nghiệp không được để trống khi gửi hồ sơ!");
        }

        List<MerchantVerificationDocument> draftDocs = documentRepository.findByDossierIdAndInDraftTrue(dossier.getId());
        var registrationDocuments = draftDocs.stream()
                .filter(doc -> doc.getDocumentType() == DocumentType.BUSINESS_REGISTRATION)
                .toList();
        String ocrDocumentId = selectOcrDocument(registrationDocuments, request.getOcrDocumentId());

        if (dossier.getStatus() == VerificationStatus.CHANGES_REQUESTED) {
            var previous = submissionRepository.findLatestByDossierId(dossier.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.CONFLICT, "Không tìm thấy bản nộp cần bổ sung!"));
            for (var item : previous.getRequestedItems()) {
                if (!isRequestedChangeAddressed(dossier, previous, draftDocs, item.getItemKey())) {
                    String message = "BUSINESS_INFO".equals(item.getItemKey())
                            ? "Vui lòng cập nhật hoặc xác nhận lại thông tin doanh nghiệp kèm giải trình!"
                            : "Vui lòng tải lên tài liệu mới cho mục: " + item.getItemKey();
                    throw new AppException(ErrorCode.BAD_REQUEST, message);
                }
            }
        }

        int newVersion = dossier.getSubmissionVersion() == null ? 1 : dossier.getSubmissionVersion() + 1;
        dossier.setStatus(VerificationStatus.PENDING_REVIEW);
        dossier.setSubmissionVersion(newVersion);
        dossier.setDraftRevision(dossier.getDraftRevision() + 1);
        dossierRepository.save(dossier);

        // Create immutable submission snapshot
        MerchantVerificationSubmission submission = MerchantVerificationSubmission.builder()
                .dossier(dossier)
                .submissionVersion(newVersion)
                .businessRegistrationNumber(dossier.getBusinessRegistrationNumber())
                .legalName(dossier.getLegalName())
                .resubmissionNote(dossier.getResubmissionNote())
                .submittedAt(LocalDateTime.now())
                .assessmentStatus(AssessmentStatus.PENDING)
                .ocrDocumentId(ocrDocumentId)
                .documents(new HashSet<>(draftDocs))
                .build();
        submissionRepository.save(submission);

        // Record history
        MerchantVerificationHistory history = MerchantVerificationHistory.builder()
                .dossier(dossier)
                .submissionVersion(newVersion)
                .action("SUBMITTED")
                .occurredAt(LocalDateTime.now())
                .build();
        historyRepository.save(history);

        log.info("Submitted verification dossier {} with version {}", dossier.getId(), newVersion);
        return toDetailResponse(dossier, false);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContentResponse getMerchantDocumentContent(Long merchantUserId, String documentId) {
        Merchant merchant = requireMerchant(merchantUserId);
        MerchantVerificationDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy tài liệu!"));

        if (!doc.getDossier().getMerchant().getId().equals(merchant.getId())
                || (!doc.isInDraft() && !submissionRepository.existsByDocumentId(doc.getId()))) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy tài liệu!");
        }

        return readDocumentContent(doc);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminVerificationSummaryResponse> getAdminVerifications(VerificationStatus status, Pageable pageable) {
        if (status == VerificationStatus.DRAFT) {
            return Page.empty(pageable);
        }
        return submissionRepository.findAdminSummaries(status, pageable).map(summary ->
                AdminVerificationSummaryResponse.builder()
                        .id(summary.getId())
                        .merchantId(summary.getMerchantId())
                        .legalName(summary.getLegalName())
                        .businessRegistrationNumber(summary.getBusinessRegistrationNumber())
                        .status(summary.getStatus().name())
                        .assessmentStatus(summary.getAssessmentStatus().name())
                        .submissionVersion(summary.getSubmissionVersion())
                        .submittedAt(summary.getSubmittedAt())
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantVerificationDetailResponse getAdminVerificationDetail(Long dossierId) {
        MerchantVerificationDossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác minh!"));
        return toDetailResponse(dossier, true);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContentResponse getAdminDocumentContent(Long dossierId, Integer submissionVersion, String documentId) {
        MerchantVerificationSubmission submission = submissionRepository
                .findByDossierIdAndSubmissionVersion(dossierId, submissionVersion)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên bản nộp!"));

        MerchantVerificationDocument doc = submission.getDocuments().stream()
                .filter(document -> document.getId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Tài liệu không thuộc phiên bản nộp này!"));
        return readDocumentContent(doc);
    }

    @Override
    @Transactional
    public MerchantVerificationDetailResponse adminApprove(
            Long dossierId, CustomUserDetails adminUser, AdminApproveVerificationRequest request) {
        // Approval also writes the Merchant, so lock it first as Merchant edits do.
        Merchant merchant = dossierRepository.findMerchantId(dossierId)
                .flatMap(merchantRepository::findByIdForUpdate)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác minh!"));
        MerchantVerificationDossier dossier = dossierRepository.findByIdForUpdate(dossierId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác minh!"));

        if (dossier.getStatus() != VerificationStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.CONFLICT, "Hồ sơ không ở trạng thái chờ duyệt (có thể đã được quyết định bởi quản trị viên khác)!");
        }

        if (!Objects.equals(dossier.getSubmissionVersion(), request.getSubmissionVersion())) {
            throw new AppException(ErrorCode.CONFLICT, "Phiên bản nộp không khớp với phiên bản đang chờ duyệt hiện tại!");
        }

        if (!Boolean.TRUE.equals(request.getDocumentsReviewed())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Admin phải xác nhận đã xem tài liệu đính kèm!");
        }

        MerchantVerificationSubmission submission = submissionRepository
                .findByDossierIdAndSubmissionVersion(dossierId, request.getSubmissionVersion())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên bản nộp!"));

        if (submission.getAssessmentStatus() == AssessmentStatus.PENDING) {
            throw new AppException(ErrorCode.CONFLICT, "Hệ thống đang kiểm tra OCR, vui lòng chờ trong giây lát!");
        }

        boolean hasWarnOrUnavailable = submission.getAssessmentStatus() == AssessmentStatus.UNAVAILABLE
                || submission.getAssessmentChecks().stream().anyMatch(c -> CheckResult.WARN.name().equals(c.getResult()));

        if (hasWarnOrUnavailable) {
            if (request.getNote() == null || request.getNote().trim().isBlank()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Ghi chú giải trình là bắt buộc khi kết quả kiểm tra có cảnh báo hoặc OCR không khả dụng!");
            }
        }

        // Apply approval
        dossier.setStatus(VerificationStatus.VERIFIED);
        dossierRepository.save(dossier);

        submission.setDecisionAction("APPROVED");
        submission.setDecisionActor(resolveActorName(adminUser));
        submission.setDecisionNote(request.getNote() != null ? request.getNote().trim() : null);
        submission.setDecisionAt(LocalDateTime.now());
        submissionRepository.save(submission);

        // Update merchant businessLicense
        merchant.setBusinessLicense(submission.getBusinessRegistrationNumber());
        merchantRepository.save(merchant);

        // Record history
        MerchantVerificationHistory history = MerchantVerificationHistory.builder()
                .dossier(dossier)
                .submissionVersion(request.getSubmissionVersion())
                .action("APPROVED")
                .actor(resolveActorName(adminUser))
                .internalNote(request.getNote() != null ? request.getNote().trim() : null)
                .occurredAt(LocalDateTime.now())
                .build();
        historyRepository.save(history);

        // Enqueue outbox notification event
        MerchantVerificationEvent event = MerchantVerificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .schemaVersion(1)
                .type(MerchantVerificationEvent.APPROVED)
                .merchantUserId(dossier.getUser().getId())
                .dossierId(dossier.getId())
                .submissionVersion(request.getSubmissionVersion())
                .occurredAt(Instant.now())
                .build();
        outbox.enqueue(MerchantVerificationEvent.TOPIC, String.valueOf(dossier.getId()), event);

        log.info("Approved verification dossier {} (version {})", dossierId, request.getSubmissionVersion());

        return toDetailResponse(dossier, true);
    }

    @Override
    @Transactional
    public MerchantVerificationDetailResponse adminRequestChanges(
            Long dossierId, CustomUserDetails adminUser, AdminRequestChangesRequest request) {
        MerchantVerificationDossier dossier = dossierRepository.findByIdForUpdate(dossierId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác minh!"));

        if (dossier.getStatus() != VerificationStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.CONFLICT, "Hồ sơ không ở trạng thái chờ duyệt!");
        }

        if (!Objects.equals(dossier.getSubmissionVersion(), request.getSubmissionVersion())) {
            throw new AppException(ErrorCode.CONFLICT, "Phiên bản nộp không khớp với phiên bản đang chờ duyệt hiện tại!");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Danh sách yêu cầu bổ sung không được để trống!");
        }

        Set<String> seenKeys = new HashSet<>();
        for (RequestedItemDto item : request.getItems()) {
            if (item.getKey() == null || !VALID_ITEM_KEYS.contains(item.getKey().trim())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Mục yêu cầu bổ sung không hợp lệ: " + item.getKey());
            }
            if (item.getReason() == null || item.getReason().trim().isBlank()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Lý do bổ sung cho mục " + item.getKey() + " không được để trống!");
            }
            if (!seenKeys.add(item.getKey().trim())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Mục yêu cầu bổ sung bị trùng lặp: " + item.getKey());
            }
        }

        MerchantVerificationSubmission submission = submissionRepository
                .findByDossierIdAndSubmissionVersion(dossierId, request.getSubmissionVersion())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên bản nộp!"));

        if (request.isResetLegalName()) {
            boolean hasNameMismatch = submission.getAssessmentChecks().stream().anyMatch(check ->
                    CheckCode.LEGAL_NAME_MATCH.name().equals(check.getCode())
                            && CheckResult.WARN.name().equals(check.getResult())
                            && check.getExtractedValue() != null && !check.getExtractedValue().isBlank());
            if (!seenKeys.contains("BUSINESS_INFO") || !hasNameMismatch) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Yêu cầu sửa tên pháp lý cần chọn mục thông tin doanh nghiệp và có kết quả đối chiếu tên không khớp.");
            }
            dossier.setLegalName(null);
        }

        // A confirmation note applies only to the new correction round.
        dossier.setResubmissionNote(null);
        dossier.setStatus(VerificationStatus.CHANGES_REQUESTED);
        dossier.setDraftRevision(dossier.getDraftRevision() + 1);
        dossierRepository.save(dossier);

        submission.setDecisionAction("CHANGES_REQUESTED");
        submission.setDecisionActor(resolveActorName(adminUser));
        submission.setDecisionAt(LocalDateTime.now());

        submission.getRequestedItems().clear();
        for (RequestedItemDto item : request.getItems()) {
            submission.getRequestedItems().add(MerchantVerificationRequestedItem.builder()
                    .submission(submission)
                    .itemKey(item.getKey().trim())
                    .reason(item.getReason().trim())
                    .build());
        }
        submissionRepository.save(submission);

        // Record history
        MerchantVerificationHistory history = MerchantVerificationHistory.builder()
                .dossier(dossier)
                .submissionVersion(request.getSubmissionVersion())
                .action("CHANGES_REQUESTED")
                .actor(resolveActorName(adminUser))
                .occurredAt(LocalDateTime.now())
                .build();
        historyRepository.save(history);

        // Enqueue outbox notification event
        MerchantVerificationEvent event = MerchantVerificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .schemaVersion(1)
                .type(MerchantVerificationEvent.CHANGES_REQUESTED)
                .merchantUserId(dossier.getUser().getId())
                .dossierId(dossier.getId())
                .submissionVersion(request.getSubmissionVersion())
                .occurredAt(Instant.now())
                .build();
        outbox.enqueue(MerchantVerificationEvent.TOPIC, String.valueOf(dossier.getId()), event);

        log.info("Requested changes on verification dossier {} (version {}) with {} items",
                dossierId, request.getSubmissionVersion(), request.getItems().size());

        return toDetailResponse(dossier, true);
    }

    private String selectOcrDocument(List<MerchantVerificationDocument> candidates, String selectedId) {
        if (candidates.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Bắt buộc phải đính kèm Giấy chứng nhận đăng ký kinh doanh!");
        }
        if (selectedId == null && candidates.size() == 1) {
            return candidates.getFirst().getId();
        }
        if (selectedId == null || candidates.stream().noneMatch(doc -> doc.getId().equals(selectedId))) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Vui lòng chọn tệp dùng để đối chiếu trong nhóm giấy đăng ký kinh doanh của bản nháp.");
        }
        return selectedId;
    }

    private AssessmentSourceDto assessmentSource(MerchantVerificationSubmission submission) {
        if (submission.getOcrMethod() == null || submission.getOcrDocumentId() == null) {
            return null;
        }
        return submission.getDocuments().stream()
                .filter(doc -> doc.getId().equals(submission.getOcrDocumentId()))
                .findFirst()
                .map(doc -> new AssessmentSourceDto(doc.getId(), doc.getOriginalName(), submission.getOcrMethod(),
                        submission.getSubmissionVersion()))
                .orElse(null);
    }

    private DocumentContentResponse readDocumentContent(MerchantVerificationDocument document) {
        byte[] bytes = documentStorage.read(document);
        if (bytes == null) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy nội dung tệp!");
        }
        return new DocumentContentResponse(bytes, document.getContentType(), document.getOriginalName());
    }

    private Merchant requireMerchant(Long userId) {
        return merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ chủ bãi xe!"));
    }

    private Merchant requireMerchantForUpdate(Long userId) {
        return merchantRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ chủ bãi xe!"));
    }

    private MerchantVerificationDossier getOrCreateDossier(Merchant merchant) {
        return dossierRepository.save(newDraft(merchant));
    }

    private MerchantVerificationDossier newDraft(Merchant merchant) {
        return MerchantVerificationDossier.builder()
                .merchant(merchant)
                .user(merchant.getUser())
                .status(VerificationStatus.DRAFT)
                .draftRevision(1L)
                .businessRegistrationNumber(merchant.getBusinessLicense())
                .build();
    }

    private void validateDraftRevision(MerchantVerificationDossier dossier, Long expectedRevision) {
        if (expectedRevision == null || !dossier.getDraftRevision().equals(expectedRevision)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Phiên bản chỉnh sửa không khớp (dữ liệu đã thay đổi ở phiên khác)");
        }
    }

    private void validateEditable(MerchantVerificationDossier dossier, String itemKey) {
        if (dossier.getStatus() == VerificationStatus.VERIFIED || dossier.getStatus() == VerificationStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Hồ sơ đang trong quá trình xét duyệt hoặc đã được xác minh!");
        }

        if (dossier.getStatus() == VerificationStatus.CHANGES_REQUESTED) {
            // Must check if this key was requested
            Optional<MerchantVerificationSubmission> latestSub = submissionRepository.findLatestByDossierId(dossier.getId());
            boolean isRequested = latestSub.map(s -> s.getRequestedItems().stream().anyMatch(i -> i.getItemKey().equals(itemKey)))
                    .orElse(false);
            if (!isRequested) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Mục " + itemKey + " không thuộc danh sách Admin yêu cầu bổ sung!");
            }
        }
    }

    private boolean isRequestedChangeAddressed(
            MerchantVerificationDossier dossier, MerchantVerificationSubmission previous,
            List<MerchantVerificationDocument> draftDocuments, String itemKey) {
        if ("BUSINESS_INFO".equals(itemKey)) {
            return dossier.getLegalName() != null && !dossier.getLegalName().isBlank()
                    && (!Objects.equals(dossier.getLegalName(), previous.getLegalName())
                        || !Objects.equals(dossier.getBusinessRegistrationNumber(), previous.getBusinessRegistrationNumber())
                        || (dossier.getResubmissionNote() != null && !dossier.getResubmissionNote().isBlank()));
        }

        DocumentType type = DocumentType.valueOf(itemKey);
        var previousDocuments = previous.getDocuments().stream()
                .filter(doc -> doc.getDocumentType() == type)
                .toList();
        return draftDocuments.stream().filter(doc -> doc.getDocumentType() == type)
                .anyMatch(doc -> previousDocuments.stream().noneMatch(old ->
                        Objects.equals(doc.getId(), old.getId()) || Objects.equals(doc.getSha256Hash(), old.getSha256Hash())));
    }

    private List<RequestedItemDto> requestedItemDtos(MerchantVerificationSubmission submission) {
        return submission.getRequestedItems().stream()
                .map(item -> RequestedItemDto.builder().key(item.getItemKey()).reason(item.getReason()).build())
                .toList();
    }

    private MerchantVerificationDetailResponse toDetailResponse(MerchantVerificationDossier dossier, boolean isAdmin) {
        Optional<MerchantVerificationSubmission> latestSub = dossier.getId() == null
                ? Optional.empty() : submissionRepository.findLatestByDossierId(dossier.getId());

        if (isAdmin && (dossier.getStatus() == VerificationStatus.DRAFT || latestSub.isEmpty())) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ đã nộp!");
        }
        boolean showDraft = !isAdmin && (dossier.getStatus() == VerificationStatus.DRAFT
                || dossier.getStatus() == VerificationStatus.CHANGES_REQUESTED);
        var snapshot = latestSub.orElse(null);

        List<MerchantVerificationDocument> docs;
        if (showDraft) {
            docs = dossier.getId() == null ? List.of() : documentRepository.findByDossierIdAndInDraftTrue(dossier.getId());
        } else {
            docs = latestSub.map(s -> new ArrayList<>(s.getDocuments())).orElseGet(ArrayList::new);
        }

        List<VerificationDocumentDto> docDtos = docs.stream().map(d -> VerificationDocumentDto.builder()
                .id(d.getId())
                .type(d.getDocumentType().name())
                .originalName(d.getOriginalName())
                .contentType(d.getContentType())
                .sizeBytes(d.getSizeBytes())
                .uploadedAt(d.getUploadedAt())
                .build()).collect(Collectors.toList());

        // Requirements
        List<DocumentRequirementDto> requirements = List.of(
                DocumentRequirementDto.builder().type(DocumentType.BUSINESS_REGISTRATION.name()).required(true).maxFiles(3).build(),
                DocumentRequirementDto.builder().type(DocumentType.AUTHORIZATION_LETTER.name()).required(false).maxFiles(3).build(),
                DocumentRequirementDto.builder().type(DocumentType.SUPPORTING_DOCUMENT.name()).required(false).maxFiles(3).build()
        );

        // Editable items
        List<String> editableItems = new ArrayList<>();
        if (!isAdmin && dossier.getStatus() == VerificationStatus.DRAFT) {
            editableItems.addAll(List.of("BUSINESS_INFO", DocumentType.BUSINESS_REGISTRATION.name(),
                    DocumentType.AUTHORIZATION_LETTER.name(), DocumentType.SUPPORTING_DOCUMENT.name()));
        } else if (!isAdmin && dossier.getStatus() == VerificationStatus.CHANGES_REQUESTED) {
            latestSub.ifPresent(s -> s.getRequestedItems().forEach(i -> editableItems.add(i.getItemKey())));
        }

        // Requested items
        List<RequestedItemDto> requestedItems = List.of();
        if (dossier.getStatus() == VerificationStatus.CHANGES_REQUESTED && snapshot != null) {
            requestedItems = requestedItemDtos(snapshot);
            if (!isAdmin) {
                for (var item : requestedItems) {
                    item.setAddressed(isRequestedChangeAddressed(dossier, snapshot, docs, item.getKey()));
                }
            }
        }

        // Assessment
        AssessmentSummaryDto assessmentDto = latestSub.map(s -> {
            List<AssessmentCheckDto> checkDtos = s.getAssessmentChecks().stream().map(c -> AssessmentCheckDto.builder()
                    .code(c.getCode())
                    .result(c.getResult())
                    .documentType(c.getDocumentType())
                    .field(c.getField())
                    .declaredValue(c.getDeclaredValue())
                    .extractedValue(isAdmin ? c.getExtractedValue() : null)
                    .message(c.getMessage())
                    .build()).collect(Collectors.toList());

            return AssessmentSummaryDto.builder()
                    .status(s.getAssessmentStatus().name())
                    .checks(checkDtos)
                    .source(isAdmin ? assessmentSource(s) : null)
                    .checkedAt(s.getAssessmentCheckedAt())
                    .build();
        }).orElseGet(() -> AssessmentSummaryDto.builder().status("NOT_STARTED").build());

        // History
        List<MerchantVerificationHistory> histories = dossier.getId() == null
                ? List.of() : historyRepository.findByDossierIdOrderByOccurredAtAsc(dossier.getId());
        List<VerificationHistoryDto> historyDtos = histories.stream().map(h -> {
            List<RequestedItemDto> items = "CHANGES_REQUESTED".equals(h.getAction())
                    ? submissionRepository.findByDossierIdAndSubmissionVersion(dossier.getId(), h.getSubmissionVersion())
                            .map(this::requestedItemDtos).orElseGet(List::of)
                    : null;

            return VerificationHistoryDto.builder()
                    .submissionVersion(h.getSubmissionVersion())
                    .action(h.getAction())
                    .actor(isAdmin ? h.getActor() : null)
                    .note(isAdmin ? h.getInternalNote() : null)
                    .items(items)
                    .occurredAt(h.getOccurredAt())
                    .build();
        }).collect(Collectors.toList());

        return MerchantVerificationDetailResponse.builder()
                .id(dossier.getId())
                .merchantId(dossier.getMerchant().getId())
                .status(dossier.getStatus().name())
                .draftRevision(isAdmin ? null : dossier.getDraftRevision())
                .submissionVersion(snapshot == null ? null : snapshot.getSubmissionVersion())
                .businessRegistrationNumber(showDraft ? dossier.getBusinessRegistrationNumber() : snapshot.getBusinessRegistrationNumber())
                .legalName(showDraft ? dossier.getLegalName() : snapshot.getLegalName())
                .resubmissionNote(showDraft ? dossier.getResubmissionNote() : snapshot.getResubmissionNote())
                .documents(docDtos)
                .assessment(assessmentDto)
                .requestedItems(requestedItems)
                .requirements(requirements)
                .editableItems(editableItems)
                .history(historyDtos)
                .updatedAt(isAdmin && snapshot != null
                        ? (snapshot.getDecisionAt() == null ? snapshot.getSubmittedAt() : snapshot.getDecisionAt())
                        : dossier.getUpdatedAt())
                .build();
    }

    private static String resolveActorName(CustomUserDetails user) {
        if (user == null) return "admin";
        if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
        if (user.getId() != null) return "user#" + user.getId();
        return "admin";
    }
}

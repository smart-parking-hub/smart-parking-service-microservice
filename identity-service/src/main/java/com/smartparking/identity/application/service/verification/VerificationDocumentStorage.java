package com.smartparking.identity.application.service.verification;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.domain.model.verification.DocumentType;
import com.smartparking.identity.domain.model.verification.MerchantVerificationDocument;
import com.smartparking.identity.domain.model.verification.MerchantVerificationDocumentBytes;
import com.smartparking.identity.domain.model.verification.MerchantVerificationDossier;
import com.smartparking.identity.domain.repository.MerchantVerificationDocumentBytesRepository;
import com.smartparking.identity.domain.repository.MerchantVerificationDocumentRepository;
import com.smartparking.identity.infrastructure.cloudinary.VerificationCloudinaryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationDocumentStorage {
    private final MerchantVerificationDocumentRepository documents;
    private final MerchantVerificationDocumentBytesRepository databaseBytes;
    private final FileValidationService validation;
    private final VerificationCloudinaryClient cloudinary;
    private final VerificationCloudinaryCleanup cleanup;

    @Value("${app.verification.cloudinary.writes-enabled:false}")
    private boolean writesEnabled;

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceGroup(MerchantVerificationDossier dossier, DocumentType type, List<MultipartFile> files) {
        var validatedFiles = files.stream().map(validation::validateFile).toList();
        documents.markDraftDocumentsInactive(dossier.getId(), type);

        for (int index = 0; index < files.size(); index++) {
            var file = validatedFiles.get(index);
            byte[] bytes = file.bytes();
            String id = UUID.randomUUID().toString();
            var document = MerchantVerificationDocument.builder()
                    .id(id)
                    .dossier(dossier)
                    .documentType(type)
                    .originalName(FileValidationService.sanitizeFilename(files.get(index).getOriginalFilename()))
                    .contentType(file.contentType())
                    .sizeBytes((long) bytes.length)
                    .sha256Hash(FileValidationService.computeSha256(bytes))
                    .inDraft(true)
                    .build();

            if (writesEnabled && file.contentType().startsWith("image/")) {
                String publicId = "smartparking/merchant-kyc/" + id;
                cleanup.trackUpload(publicId);
                cleanup.retainOnCommit(publicId);
                cloudinary.upload(publicId, imageFormat(file.contentType()), bytes);
                document.setCloudinaryPublicId(publicId);
            }

            documents.save(document);
            if (document.getCloudinaryPublicId() == null) {
                databaseBytes.save(MerchantVerificationDocumentBytes.builder().documentId(id).fileBytes(bytes).build());
            }
        }
    }

    public byte[] read(MerchantVerificationDocument document) {
        if (document.getCloudinaryPublicId() == null) {
            return databaseBytes.findById(document.getId()).map(MerchantVerificationDocumentBytes::getFileBytes).orElse(null);
        }
        byte[] bytes = cloudinary.download(document.getCloudinaryPublicId(), imageFormat(document.getContentType()),
                Math.toIntExact(document.getSizeBytes()));
        if (!document.getSha256Hash().equals(FileValidationService.computeSha256(bytes))) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Nội dung tệp không khớp với hồ sơ đã lưu.");
        }
        return bytes;
    }

    private String imageFormat(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported verification image type");
        };
    }
}

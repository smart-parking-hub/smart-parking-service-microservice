package com.smartparking.payment.application.service;

import com.smartparking.payment.application.dto.DisputeEvidenceResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DisputeEvidenceService {

    record StoredEvidence(Resource resource, String contentType, String originalFileName) {}

    List<DisputeEvidenceResponse> upload(Long driverUserId, List<MultipartFile> files, String baseUrl);

    StoredEvidence getContent(Long userId, String role, String fileName);
}


package com.smartparking.payment.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.MerchantLedgerEntryResponse;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantLedgerQueryService {
    private final WalletLedgerEntryRepository ledgerRepository;

    @Transactional(readOnly = true)
    public PageResponse<MerchantLedgerEntryResponse> getLedger(Long merchantId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trang phải từ 0; kích thước trang phải từ 1 đến 100");
        }
        // The repository fixes createdAt DESC; id DESC provides stable ordering for tied timestamps.
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var entries = ledgerRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
        return new PageResponse<>(entries.map(entry -> new MerchantLedgerEntryResponse(
                entry.getId(), entry.getBookingId(), entry.getEntryType(),
                entry.getGrossAmount(), entry.getFeePercent(), entry.getFeeAmount(),
                entry.getNetAmount(), entry.getNote(), entry.getCreatedAt())));
    }
}

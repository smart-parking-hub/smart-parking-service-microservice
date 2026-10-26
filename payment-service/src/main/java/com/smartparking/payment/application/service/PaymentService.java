package com.smartparking.payment.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.application.dto.AdminDashboardResponse;
import com.smartparking.payment.application.dto.AdminTransactionResponse;
import com.smartparking.payment.application.dto.CreatePaymentRequest;
import com.smartparking.payment.application.dto.DemoPaymentRequest;
import com.smartparking.payment.application.dto.PaymentDto;
import org.springframework.data.domain.Pageable;

public interface PaymentService {
    String createPaymentLink(Long driverUserId, CreatePaymentRequest request);
    PaymentDto confirmDemoDeposit(Long driverUserId, Long bookingId);
    PaymentDto confirmDemoDeposit(Long driverUserId, DemoPaymentRequest request);
    PaymentDto syncPayOSPayment(Long driverUserId, Long orderCode, Long bookingId, String paymentLinkId);
    void handleWebhook(String webhookBody);
    PageResponse<PaymentDto> getMyPayments(Long driverUserId, Pageable pageable);

    PageResponse<AdminTransactionResponse> getAllTransactionsForAdmin(Pageable pageable);
    AdminTransactionResponse getTransactionForAdmin(Long id);
    AdminDashboardResponse getDashboardStats();
}

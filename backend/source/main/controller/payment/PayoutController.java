package controller.payment;

import dto.request.payout.CreatePayoutBatchRequest;
import dto.response.API.APIResponse;
import dto.response.payout.PayoutBatchResponse;
import dto.response.payout.PayoutSummaryResponse;
import enums.PayoutBatchStatus;
import service.payment.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
@Slf4j
public class PayoutController {

    private final PayoutService payoutService;

    /**
     * Tạo payout batch mới cho store
     * POST /api/v1/payouts/batches
     */
    @PostMapping("/batches")
    public ResponseEntity<APIResponse<PayoutBatchResponse>> createPayoutBatch(
            @Valid @RequestBody CreatePayoutBatchRequest request) {
        log.info("Creating payout batch for store: {}", request.getStoreId());
        PayoutBatchResponse response = payoutService.createPayoutBatch(request);
        return ResponseEntity.ok(APIResponse.<PayoutBatchResponse>builder()
                .code(200)
                .message("Payout batch created successfully")
                .result(response)
                .build());
    }

    /**
     * Lấy tổng hợp thông tin payout của store
     * GET /api/v1/payouts/stores/{storeId}/summary
     */
    @GetMapping("/stores/{storeId}/summary")
    public ResponseEntity<APIResponse<PayoutSummaryResponse>> getPayoutSummary(
            @PathVariable Long storeId) {
        log.info("Getting payout summary for store: {}", storeId);
        PayoutSummaryResponse response = payoutService.getPayoutSummary(storeId);
        return ResponseEntity.ok(APIResponse.<PayoutSummaryResponse>builder()
                .code(200)
                .message("Payout summary retrieved successfully")
                .result(response)
                .build());
    }

    /**
     * Xử lý thanh toán cho payout batch
     * POST /api/v1/payouts/batches/{batchId}/process
     */
    @PostMapping("/batches/{batchId}/process")
    public ResponseEntity<APIResponse<PayoutBatchResponse>> processPayoutBatch(
            @PathVariable Long batchId) {
        log.info("Processing payout batch: {}", batchId);
        PayoutBatchResponse response = payoutService.processPayoutBatch(batchId);
        return ResponseEntity.ok(APIResponse.<PayoutBatchResponse>builder()
                .code(200)
                .message("Payout batch processing initiated")
                .result(response)
                .build());
    }

    /**
     * Đánh dấu payout batch đã thanh toán thành công
     * POST /api/v1/payouts/batches/{batchId}/mark-paid
     */
    @PostMapping("/batches/{batchId}/mark-paid")
    public ResponseEntity<APIResponse<PayoutBatchResponse>> markAsPaid(
            @PathVariable Long batchId,
            @RequestParam(required = false) String transactionCode) {
        log.info("Marking payout batch {} as PAID", batchId);
        PayoutBatchResponse response = payoutService.markAsPaid(batchId, transactionCode);
        return ResponseEntity.ok(APIResponse.<PayoutBatchResponse>builder()
                .code(200)
                .message("Payout batch marked as PAID successfully")
                .result(response)
                .build());
    }

    /**
     * Đánh dấu payout batch thất bại
     * POST /api/v1/payouts/batches/{batchId}/mark-failed
     */
    @PostMapping("/batches/{batchId}/mark-failed")
    public ResponseEntity<APIResponse<PayoutBatchResponse>> markAsFailed(
            @PathVariable Long batchId,
            @RequestParam String reason) {
        log.info("Marking payout batch {} as FAILED", batchId);
        PayoutBatchResponse response = payoutService.markAsFailed(batchId, reason);
        return ResponseEntity.ok(APIResponse.<PayoutBatchResponse>builder()
                .code(200)
                .message("Payout batch marked as FAILED")
                .result(response)
                .build());
    }

    /**
     * Retry payout batch thất bại
     * POST /api/v1/payouts/batches/{batchId}/retry
     */
    @PostMapping("/batches/{batchId}/retry")
    public ResponseEntity<APIResponse<PayoutBatchResponse>> retryPayoutBatch(
            @PathVariable Long batchId) {
        log.info("Retrying payout batch: {}", batchId);
        PayoutBatchResponse response = payoutService.retryPayoutBatch(batchId);
        return ResponseEntity.ok(APIResponse.<PayoutBatchResponse>builder()
                .code(200)
                .message("Payout batch reset for retry")
                .result(response)
                .build());
    }

    /**
     * Lấy chi tiết payout batch
     * GET /api/v1/payouts/batches/{batchId}
     */
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<APIResponse<PayoutBatchResponse>> getPayoutBatchById(
            @PathVariable Long batchId) {
        PayoutBatchResponse response = payoutService.getPayoutBatchById(batchId);
        return ResponseEntity.ok(APIResponse.<PayoutBatchResponse>builder()
                .code(200)
                .message("Payout batch retrieved successfully")
                .result(response)
                .build());
    }

    /**
     * Lấy danh sách payout batch của store
     * GET /api/v1/payouts/stores/{storeId}/batches
     */
    @GetMapping("/stores/{storeId}/batches")
    public ResponseEntity<APIResponse<List<PayoutBatchResponse>>> getPayoutBatchesByStore(
            @PathVariable Long storeId) {
        List<PayoutBatchResponse> response = payoutService.getPayoutBatchesByStore(storeId);
        return ResponseEntity.ok(APIResponse.<List<PayoutBatchResponse>>builder()
                .code(200)
                .message("Payout batches retrieved successfully")
                .result(response)
                .build());
    }

    /**
     * Lấy danh sách payout batch theo status
     * GET /api/v1/payouts/batches?status=PENDING
     */
    @GetMapping("/batches")
    public ResponseEntity<APIResponse<List<PayoutBatchResponse>>> getPayoutBatchesByStatus(
            @RequestParam(required = false) PayoutBatchStatus status) {
        List<PayoutBatchResponse> response;
        if (status != null) {
            response = payoutService.getPayoutBatchesByStatus(status);
        } else {
            response = payoutService.getAllPayoutBatches();
        }
        return ResponseEntity.ok(APIResponse.<List<PayoutBatchResponse>>builder()
                .code(200)
                .message("Payout batches retrieved successfully")
                .result(response)
                .build());
    }

    /**
     * Lấy payout batch theo khoảng thời gian
     * GET /api/v1/payouts/batches/date-range?startDate=...&endDate=...
     */
    @GetMapping("/batches/date-range")
    public ResponseEntity<APIResponse<List<PayoutBatchResponse>>> getPayoutBatchesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<PayoutBatchResponse> response = payoutService.getPayoutBatchesByDateRange(startDate, endDate);
        return ResponseEntity.ok(APIResponse.<List<PayoutBatchResponse>>builder()
                .code(200)
                .message("Payout batches retrieved successfully")
                .result(response)
                .build());
    }
}


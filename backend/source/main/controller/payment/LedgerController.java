package controller.payment;

import dto.response.API.APIResponse;
import entity.PayoutBatch;
import service.payment.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Slf4j
public class LedgerController {

    private final LedgerService ledgerService;

    /**
     * Get unpaid amount for a store
     */
    @GetMapping("/store/{storeId}/unpaid-amount")
    public ResponseEntity<APIResponse<BigDecimal>> getUnpaidAmount(@PathVariable Long storeId) {
        log.info("Getting unpaid amount for store: {}", storeId);

        BigDecimal unpaidAmount = ledgerService.getUnpaidAmountForStore(storeId);

        return ResponseEntity.ok(APIResponse.<BigDecimal>builder()
                .code(200)
                .message("Unpaid amount retrieved successfully")
                .result(unpaidAmount)
                .build());
    }

    /**
     * Create payout batch for a store (Admin function)
     */
    @PostMapping("/store/{storeId}/payout")
    public ResponseEntity<APIResponse<PayoutBatch>> createPayoutBatch(@PathVariable Long storeId) {
        log.info("Creating payout batch for store: {}", storeId);

        PayoutBatch payoutBatch = ledgerService.createPayoutBatchForStore(storeId);

        return ResponseEntity.ok(APIResponse.<PayoutBatch>builder()
                .code(200)
                .message("Payout batch created successfully")
                .result(payoutBatch)
                .build());
    }

    /**
     * Mark payout batch as paid (Admin function)
     */
    @PostMapping("/payout/{payoutBatchId}/mark-paid")
    public ResponseEntity<APIResponse<Void>> markPayoutAsPaid(
            @PathVariable Long payoutBatchId,
            @RequestParam String transactionCode) {
        log.info("Marking payout batch as paid: {}", payoutBatchId);

        ledgerService.markPayoutBatchAsPaid(payoutBatchId, transactionCode);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .code(200)
                .message("Payout batch marked as paid successfully")
                .build());
    }

    /**
     * Get all payout batches for a store
     */
    @GetMapping("/store/{storeId}/payouts")
    public ResponseEntity<APIResponse<List<PayoutBatch>>> getPayoutBatches(@PathVariable Long storeId) {
        log.info("Getting payout batches for store: {}", storeId);

        List<PayoutBatch> payoutBatches = ledgerService.getPayoutBatchesByStore(storeId);

        return ResponseEntity.ok(APIResponse.<List<PayoutBatch>>builder()
                .code(200)
                .message("Payout batches retrieved successfully")
                .result(payoutBatches)
                .build());
    }
}

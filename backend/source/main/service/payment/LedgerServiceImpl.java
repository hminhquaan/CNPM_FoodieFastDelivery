package service.payment;

import repository.payment.StoreLedgerRepository;
import entity.Order;
import entity.PayoutBatch;
import entity.StoreLedger;
import enums.PayoutBatchStatus;
import enums.StoreLedgerStatus;
import exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.payment.PayoutBatchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerServiceImpl implements LedgerService {

    private final StoreLedgerRepository storeLedgerRepository;
    private final PayoutBatchRepository payoutBatchRepository;

    @Value("${app.commission.rate:0.20}")
    private BigDecimal commissionRate;

    @Value("${app.payment-gateway.fee-rate:0.01}")
    private BigDecimal paymentGatewayFeeRate;

    @Override
    @Transactional
    public void createLedgerEntryForOrder(Order order) {
        log.info("Creating ledger entry for order: {}", order.getOrderCode());

        if (storeLedgerRepository.findByOrderId(order.getId()).isPresent()) {
            log.warn("Ledger entry already exists for order: {}", order.getId());
            return;
        }

        try {
            BigDecimal totalOrderAmount = order.getTotalPayable();

            BigDecimal appCommission = totalOrderAmount
                    .multiply(commissionRate)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal gatewayFee = totalOrderAmount
                    .multiply(paymentGatewayFeeRate)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal netAmountOwed = totalOrderAmount
                    .subtract(appCommission)
                    .subtract(gatewayFee)
                    .setScale(2, RoundingMode.HALF_UP);

            StoreLedger ledger = StoreLedger.builder()
                    .storeId(order.getStoreId())
                    .orderId(order.getId())
                    .totalOrderAmount(totalOrderAmount)
                    .appCommissionAmount(appCommission)
                    .paymentGatewayFee(gatewayFee)
                    .netAmountOwed(netAmountOwed)
                    .status(StoreLedgerStatus.UNPAID)
                    .build();

            storeLedgerRepository.save(ledger);

            log.info("Ledger entry created successfully for order: {} | Total: {} | Commission: {} | Gateway Fee: {} | Net: {}",
                    order.getOrderCode(), totalOrderAmount, appCommission, gatewayFee, netAmountOwed);

        } catch (Exception e) {
            log.error("Error creating ledger entry for order {}: {}", order.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create ledger entry", e);
        }
    }

    @Override
    public BigDecimal getUnpaidAmountForStore(Long storeId) {
        log.info("Getting unpaid amount for store: {}", storeId);

        BigDecimal unpaidAmount = storeLedgerRepository.sumNetAmountOwedByStoreIdAndStatus(
                storeId, StoreLedgerStatus.UNPAID);

        return unpaidAmount != null ? unpaidAmount : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public PayoutBatch createPayoutBatchForStore(Long storeId) {
        log.info("Creating payout batch for store: {}", storeId);

        try {
            List<StoreLedger> unpaidLedgers = storeLedgerRepository.findUnpaidByStoreId(storeId);

            if (unpaidLedgers.isEmpty()) {
                throw new IllegalStateException("No unpaid ledger entries found for store: " + storeId);
            }

            BigDecimal totalPayoutAmount = unpaidLedgers.stream()
                    .map(StoreLedger::getNetAmountOwed)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            PayoutBatch payoutBatch = PayoutBatch.builder()
                    .storeId(storeId)
                    .totalPayoutAmount(totalPayoutAmount)
                    .status(PayoutBatchStatus.PENDING)
                    .notes("Payout for " + unpaidLedgers.size() + " orders")
                    .build();

            payoutBatch = payoutBatchRepository.save(payoutBatch);

            for (StoreLedger ledger : unpaidLedgers) {
                ledger.setStatus(StoreLedgerStatus.PROCESSING);
                ledger.setPayoutBatchId(payoutBatch.getId());
                storeLedgerRepository.save(ledger);
            }

            log.info("Payout batch created successfully: ID={}, Store={}, Amount={}, Orders={}",
                    payoutBatch.getId(), storeId, totalPayoutAmount, unpaidLedgers.size());

            return payoutBatch;

        } catch (Exception e) {
            log.error("Error creating payout batch for store {}: {}", storeId, e.getMessage(), e);
            throw new RuntimeException("Failed to create payout batch", e);
        }
    }

    @Override
    @Transactional
    public void markPayoutBatchAsPaid(Long payoutBatchId, String transactionCode) {
        log.info("Marking payout batch as PAID: {}", payoutBatchId);

        try {
            PayoutBatch payoutBatch = payoutBatchRepository.findById(payoutBatchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payout batch not found: " + payoutBatchId));

            payoutBatch.setStatus(PayoutBatchStatus.PAID);
            payoutBatch.setTransactionCode(transactionCode);
            payoutBatch.setProcessedAt(LocalDateTime.now());
            payoutBatchRepository.save(payoutBatch);

            List<StoreLedger> ledgers = storeLedgerRepository.findByPayoutBatchId(payoutBatchId);
            for (StoreLedger ledger : ledgers) {
                ledger.setStatus(StoreLedgerStatus.PAID);
                storeLedgerRepository.save(ledger);
            }

            log.info("Payout batch marked as PAID successfully: ID={}, Transaction={}, Ledgers={}",
                    payoutBatchId, transactionCode, ledgers.size());

        } catch (Exception e) {
            log.error("Error marking payout batch as paid {}: {}", payoutBatchId, e.getMessage(), e);
            throw new RuntimeException("Failed to mark payout batch as paid", e);
        }
    }

    @Override
    public List<PayoutBatch> getPayoutBatchesByStore(Long storeId) {
        log.info("Getting payout batches for store: {}", storeId);
        return payoutBatchRepository.findByStoreId(storeId);
    }
}

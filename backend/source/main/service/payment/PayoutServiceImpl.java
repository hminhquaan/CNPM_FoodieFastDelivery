package service.payment;

import repository.payment.PayoutBatchRepository;
import repository.payment.StoreLedgerRepository;
import dto.request.payout.CreatePayoutBatchRequest;
import dto.response.payout.PayoutBatchResponse;
import dto.response.payout.PayoutSummaryResponse;
import entity.PayoutBatch;
import entity.Store;
import entity.StoreLedger;
import enums.PayoutBatchStatus;
import enums.StoreLedgerStatus;
import exception.BadRequestException;
import exception.ResourceNotFoundException;
import repository.store.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutServiceImpl implements PayoutService {

    private final PayoutBatchRepository payoutBatchRepository;
    private final StoreLedgerRepository storeLedgerRepository;
    private final StoreRepository storeRepository;

    @Override
    @Transactional
    public PayoutBatchResponse createPayoutBatch(CreatePayoutBatchRequest request) {
        log.info("Creating payout batch for store: {}", request.getStoreId());

        // Kiểm tra store tồn tại
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + request.getStoreId()));

        // Lấy các ledger chưa thanh toán
        List<StoreLedger> unpaidLedgers = storeLedgerRepository.findUnpaidByStoreId(request.getStoreId());

        if (unpaidLedgers.isEmpty()) {
            throw new BadRequestException("No unpaid ledgers found for store: " + request.getStoreId());
        }

        // Tính tổng số tiền cần chi trả
        BigDecimal totalAmount = unpaidLedgers.stream()
                .map(StoreLedger::getNetAmountOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Tạo payout batch
        PayoutBatch payoutBatch = PayoutBatch.builder()
                .storeId(request.getStoreId())
                .totalPayoutAmount(totalAmount)
                .status(PayoutBatchStatus.PENDING)
                .notes(request.getNotes())
                .build();

        payoutBatch = payoutBatchRepository.save(payoutBatch);

        // Cập nhật các ledger sang trạng thái PROCESSING
        Long batchId = payoutBatch.getId();
        for (StoreLedger ledger : unpaidLedgers) {
            ledger.setStatus(StoreLedgerStatus.PROCESSING);
            ledger.setPayoutBatchId(batchId);
        }
        storeLedgerRepository.saveAll(unpaidLedgers);

        log.info("Created payout batch {} with {} ledgers, total: {}",
                batchId, unpaidLedgers.size(), totalAmount);

        return mapToPayoutBatchResponse(payoutBatch, store, unpaidLedgers.size());
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutSummaryResponse getPayoutSummary(Long storeId) {
        log.info("Getting payout summary for store: {}", storeId);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + storeId));

        List<StoreLedger> allLedgers = storeLedgerRepository.findByStoreId(storeId);
        List<StoreLedger> unpaidLedgers = storeLedgerRepository.findUnpaidByStoreId(storeId);

        BigDecimal totalRevenue = allLedgers.stream()
                .map(StoreLedger::getTotalOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCommission = allLedgers.stream()
                .map(StoreLedger::getAppCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGatewayFee = allLedgers.stream()
                .map(StoreLedger::getPaymentGatewayFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNetAmount = allLedgers.stream()
                .map(StoreLedger::getNetAmountOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = storeLedgerRepository.sumNetAmountOwedByStoreIdAndStatus(
                storeId, StoreLedgerStatus.PAID) != null
                ? storeLedgerRepository.sumNetAmountOwedByStoreIdAndStatus(storeId, StoreLedgerStatus.PAID)
                : BigDecimal.ZERO;

        BigDecimal totalPending = storeLedgerRepository.sumNetAmountOwedByStoreIdAndStatus(
                storeId, StoreLedgerStatus.PROCESSING) != null
                ? storeLedgerRepository.sumNetAmountOwedByStoreIdAndStatus(storeId, StoreLedgerStatus.PROCESSING)
                : BigDecimal.ZERO;

        BigDecimal availableForPayout = unpaidLedgers.stream()
                .map(StoreLedger::getNetAmountOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PayoutSummaryResponse.builder()
                .storeId(storeId)
                .storeName(store.getName())
                .totalRevenue(totalRevenue)
                .totalCommission(totalCommission)
                .totalGatewayFee(totalGatewayFee)
                .totalNetAmount(totalNetAmount)
                .totalPaid(totalPaid)
                .totalPending(totalPending)
                .availableForPayout(availableForPayout)
                .unpaidLedgerCount(unpaidLedgers.size())
                .totalOrderCount(allLedgers.size())
                .bankAccountName(store.getBankAccountName())
                .bankAccountNumber(store.getBankAccountNumber())
                .bankName(store.getBankName())
                .bankBranch(store.getBankBranch())
                .payoutEmail(store.getPayoutEmail())
                .build();
    }

    @Override
    @Transactional
    public PayoutBatchResponse processPayoutBatch(Long batchId) {
        log.info("Processing payout batch: {}", batchId);

        PayoutBatch batch = payoutBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout batch not found with id: " + batchId));

        if (batch.getStatus() != PayoutBatchStatus.PENDING) {
            throw new BadRequestException("Payout batch is not in PENDING status: " + batch.getStatus());
        }

        batch.setStatus(PayoutBatchStatus.PROCESSING);
        batch = payoutBatchRepository.save(batch);

        Store store = storeRepository.findById(batch.getStoreId()).orElse(null);
        int ledgerCount = storeLedgerRepository.countByPayoutBatchId(batchId);

        return mapToPayoutBatchResponse(batch, store, ledgerCount);
    }

    @Override
    @Transactional
    public PayoutBatchResponse markAsPaid(Long batchId, String transactionCode) {
        log.info("Marking payout batch {} as PAID with transaction: {}", batchId, transactionCode);

        PayoutBatch batch = payoutBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout batch not found with id: " + batchId));

        batch.setStatus(PayoutBatchStatus.PAID);
        batch.setTransactionCode(transactionCode);
        batch.setProcessedAt(LocalDateTime.now());
        batch = payoutBatchRepository.save(batch);

        // Cập nhật các ledger sang PAID
        List<StoreLedger> ledgers = storeLedgerRepository.findByPayoutBatchId(batchId);
        for (StoreLedger ledger : ledgers) {
            ledger.setStatus(StoreLedgerStatus.PAID);
        }
        storeLedgerRepository.saveAll(ledgers);

        Store store = storeRepository.findById(batch.getStoreId()).orElse(null);

        return mapToPayoutBatchResponse(batch, store, ledgers.size());
    }

    @Override
    @Transactional
    public PayoutBatchResponse markAsFailed(Long batchId, String reason) {
        log.info("Marking payout batch {} as FAILED. Reason: {}", batchId, reason);

        PayoutBatch batch = payoutBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout batch not found with id: " + batchId));

        batch.setStatus(PayoutBatchStatus.FAILED);
        batch.setNotes(batch.getNotes() != null ? batch.getNotes() + " | FAILED: " + reason : "FAILED: " + reason);
        batch.setProcessedAt(LocalDateTime.now());
        batch = payoutBatchRepository.save(batch);

        // Trả các ledger về UNPAID để có thể retry
        List<StoreLedger> ledgers = storeLedgerRepository.findByPayoutBatchId(batchId);
        for (StoreLedger ledger : ledgers) {
            ledger.setStatus(StoreLedgerStatus.UNPAID);
            ledger.setPayoutBatchId(null);
        }
        storeLedgerRepository.saveAll(ledgers);

        Store store = storeRepository.findById(batch.getStoreId()).orElse(null);

        return mapToPayoutBatchResponse(batch, store, ledgers.size());
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutBatchResponse getPayoutBatchById(Long batchId) {
        PayoutBatch batch = payoutBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout batch not found with id: " + batchId));

        Store store = storeRepository.findById(batch.getStoreId()).orElse(null);
        int ledgerCount = storeLedgerRepository.countByPayoutBatchId(batchId);

        return mapToPayoutBatchResponse(batch, store, ledgerCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutBatchResponse> getPayoutBatchesByStore(Long storeId) {
        List<PayoutBatch> batches = payoutBatchRepository.findByStoreId(storeId);
        Store store = storeRepository.findById(storeId).orElse(null);

        return batches.stream()
                .map(batch -> {
                    int ledgerCount = storeLedgerRepository.countByPayoutBatchId(batch.getId());
                    return mapToPayoutBatchResponse(batch, store, ledgerCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutBatchResponse> getPayoutBatchesByStatus(PayoutBatchStatus status) {
        List<PayoutBatch> batches = payoutBatchRepository.findByStatusOrderByCreatedAtDesc(status);

        return batches.stream()
                .map(batch -> {
                    Store store = storeRepository.findById(batch.getStoreId()).orElse(null);
                    int ledgerCount = storeLedgerRepository.countByPayoutBatchId(batch.getId());
                    return mapToPayoutBatchResponse(batch, store, ledgerCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutBatchResponse> getAllPayoutBatches() {
        List<PayoutBatch> batches = payoutBatchRepository.findAll();

        return batches.stream()
                .map(batch -> {
                    Store store = storeRepository.findById(batch.getStoreId()).orElse(null);
                    int ledgerCount = storeLedgerRepository.countByPayoutBatchId(batch.getId());
                    return mapToPayoutBatchResponse(batch, store, ledgerCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutBatchResponse> getPayoutBatchesByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        List<PayoutBatch> batches = payoutBatchRepository.findByDateRange(startDate, endDate);

        return batches.stream()
                .map(batch -> {
                    Store store = storeRepository.findById(batch.getStoreId()).orElse(null);
                    int ledgerCount = storeLedgerRepository.countByPayoutBatchId(batch.getId());
                    return mapToPayoutBatchResponse(batch, store, ledgerCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PayoutBatchResponse retryPayoutBatch(Long batchId) {
        log.info("Retrying payout batch: {}", batchId);

        PayoutBatch batch = payoutBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout batch not found with id: " + batchId));

        if (batch.getStatus() != PayoutBatchStatus.FAILED) {
            throw new BadRequestException("Only FAILED payout batches can be retried");
        }

        // Tạo batch mới với cùng thông tin
        List<StoreLedger> unpaidLedgers = storeLedgerRepository.findUnpaidByStoreId(batch.getStoreId());

        if (unpaidLedgers.isEmpty()) {
            throw new BadRequestException("No unpaid ledgers found for retry");
        }

        BigDecimal totalAmount = unpaidLedgers.stream()
                .map(StoreLedger::getNetAmountOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PayoutBatch newBatch = PayoutBatch.builder()
                .storeId(batch.getStoreId())
                .totalPayoutAmount(totalAmount)
                .status(PayoutBatchStatus.PENDING)
                .notes("Retry of batch #" + batchId + (batch.getNotes() != null ? " | " + batch.getNotes() : ""))
                .build();

        newBatch = payoutBatchRepository.save(newBatch);

        // Cập nhật ledgers
        Long newBatchId = newBatch.getId();
        for (StoreLedger ledger : unpaidLedgers) {
            ledger.setStatus(StoreLedgerStatus.PROCESSING);
            ledger.setPayoutBatchId(newBatchId);
        }
        storeLedgerRepository.saveAll(unpaidLedgers);

        Store store = storeRepository.findById(batch.getStoreId()).orElse(null);

        return mapToPayoutBatchResponse(newBatch, store, unpaidLedgers.size());
    }

    private PayoutBatchResponse mapToPayoutBatchResponse(PayoutBatch batch, Store store, int ledgerCount) {
        return PayoutBatchResponse.builder()
                .id(batch.getId())
                .storeId(batch.getStoreId())
                .storeName(store != null ? store.getName() : null)
                .totalPayoutAmount(batch.getTotalPayoutAmount())
                .status(batch.getStatus())
                .transactionCode(batch.getTransactionCode())
                .notes(batch.getNotes())
                .createdAt(batch.getCreatedAt())
                .processedAt(batch.getProcessedAt())
                .ledgerCount(ledgerCount)
                .bankAccountName(store != null ? store.getBankAccountName() : null)
                .bankAccountNumber(store != null ? store.getBankAccountNumber() : null)
                .bankName(store != null ? store.getBankName() : null)
                .build();
    }
}


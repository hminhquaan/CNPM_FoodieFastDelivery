package service.payment;

import config.payment.VnPayConfig;
import repository.payment.PaymentTransactionRepository;
import dto.request.payment.PaymentInitRequest;
import dto.request.payment.VnPayWebhookPayload;
import dto.response.payment.PaymentResponse;
import entity.Order;
import entity.PaymentTransaction;
import enums.OrderStatus;
import enums.PaymentStatus;
import enums.PaymentTransactionStatus;
import exception.ResourceNotFoundException;
import repository.order.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final LedgerService ledgerService;
    private final VnPayConfig vnPayConfig;
    private final ObjectMapper objectMapper;
    private final NgrokUrlService ngrokUrlService;

    @Override
    @Transactional
    public PaymentResponse initPayment(PaymentInitRequest request) {
        log.info("Initializing payment for order ID: {}", request.getOrderId());

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + request.getOrderId()));

        // Kiểm tra payment status của order
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.error("Cannot init payment for order {} - already paid", order.getId());
            throw new IllegalStateException("Order already paid");
        }

        // Cho phép thanh toán lại nếu payment status là FAILED hoặc PENDING
        if (order.getPaymentStatus() == PaymentStatus.FAILED) {
            log.info("Order {} payment status is FAILED, allowing retry", order.getId());
        } else if (order.getPaymentStatus() == PaymentStatus.PENDING) {
            log.info("Order {} payment status is PENDING, allowing retry", order.getId());
        }

        // Kiểm tra payment transaction hiện tại
        Optional<PaymentTransaction> existingPayment = paymentTransactionRepository.findByOrderId(order.getId());

        // Double check: Chỉ chặn nếu transaction đã SUCCESS
        if (existingPayment.isPresent() && existingPayment.get().getStatus() == PaymentTransactionStatus.SUCCESS) {
            log.error("Cannot init payment for order {} - transaction already successful", order.getId());
            throw new IllegalStateException("Order already paid");
        }

        try {
            PaymentTransaction transaction;

            // Tạo vnp_TxnRef unique cho mỗi lần payment (để tránh lỗi "Giao dịch đã tồn tại" từ VNPay)
            String vnpTxnRef = generateUniqueTxnRef(order.getOrderCode());

            // Nếu đã có transaction, cập nhật lại (cho phép retry)
            if (existingPayment.isPresent()) {
                transaction = existingPayment.get();
                log.info("Reusing existing payment transaction ID: {} with status: {}",
                         transaction.getId(), transaction.getStatus());

                // Reset transaction về trạng thái INIT với vnp_TxnRef mới
                transaction.setProvider(request.getProvider());
                transaction.setMethod(request.getMethod());
                transaction.setAmount(order.getTotalPayable());
                transaction.setCurrency("VND");
                transaction.setStatus(PaymentTransactionStatus.INIT);
                transaction.setVnpTxnRef(vnpTxnRef);  // Mã mới cho mỗi lần retry
                transaction.setProviderTransactionId(null);
                transaction.setCompletedAt(null);
                transaction.setResponsePayload(null);

                log.info("Generated new vnp_TxnRef for retry: {}", vnpTxnRef);
            } else {
                // Tạo mới nếu chưa có
                transaction = PaymentTransaction.builder()
                        .orderId(order.getId())
                        .provider(request.getProvider())
                        .method(request.getMethod())
                        .amount(order.getTotalPayable())
                        .currency("VND")
                        .status(PaymentTransactionStatus.INIT)
                        .vnpTxnRef(vnpTxnRef)
                        .build();
                log.info("Creating new payment transaction for order: {} with vnp_TxnRef: {}",
                         order.getId(), vnpTxnRef);
            }

            String paymentUrl = generateVnPayUrl(order, transaction);

            transaction.setRequestPayload(objectMapper.writeValueAsString(request));
            transaction = paymentTransactionRepository.save(transaction);

            // Cập nhật trạng thái đơn hàng về PENDING_PAYMENT
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setPaymentStatus(PaymentStatus.PENDING);
            orderRepository.save(order);

            log.info("Payment initialized successfully with ID: {} for order: {}",
                     transaction.getId(), order.getOrderCode());

            return PaymentResponse.builder()
                    .id(transaction.getId())
                    .orderId(transaction.getOrderId())
                    .provider(transaction.getProvider())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .status(transaction.getStatus())
                    .paymentUrl(paymentUrl)
                    .createdAt(transaction.getCreatedAt())
                    .build();

        } catch (Exception e) {
            log.error("Error initializing payment for order {}: {}", request.getOrderId(), e.getMessage());
            throw new RuntimeException("Failed to initialize payment", e);
        }
    }

    /**
     * Tạo vnp_TxnRef unique cho mỗi lần payment
     * Format: {ORDER_CODE}_{TIMESTAMP_MILLIS}
     * Ví dụ: ORD1762133045478C3B6F313_1730620537354
     */
    private String generateUniqueTxnRef(String orderCode) {
        long timestamp = System.currentTimeMillis();
        return orderCode + "_" + timestamp;
    }

    @Override
    @Transactional
    public void processVnPayWebhook(VnPayWebhookPayload payload) {
        log.info("Processing VNPay webhook for transaction: {}", payload.getVnp_TxnRef());

        try {
            if (!verifyVnPaySignature(payload)) {
                log.error("Invalid VNPay signature for transaction: {}", payload.getVnp_TxnRef());
                throw new SecurityException("Invalid signature");
            }

            String vnpTxnRef = payload.getVnp_TxnRef();
            // Extract order code from vnp_TxnRef (format: ORDER_CODE_{TIMESTAMP})
            String orderCode = extractOrderCode(vnpTxnRef);

            Order order = orderRepository.findByOrderCode(orderCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with code: " + orderCode));

            PaymentTransaction transaction = paymentTransactionRepository.findByOrderId(order.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found for order: " + order.getId()));

            transaction.setResponsePayload(objectMapper.writeValueAsString(payload));
            transaction.setProviderTransactionId(payload.getVnp_TransactionNo());

            String responseCode = payload.getVnp_ResponseCode();
            String transactionStatus = payload.getVnp_TransactionStatus();

            if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
                transaction.setStatus(PaymentTransactionStatus.SUCCESS);
                transaction.setCompletedAt(LocalDateTime.now());

                order.setStatus(OrderStatus.PAID);
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setUpdatedAt(LocalDateTime.now());

                log.info("Payment successful for order: {}", order.getOrderCode());

            } else {
                transaction.setStatus(PaymentTransactionStatus.FAILED);
                transaction.setCompletedAt(LocalDateTime.now());

                order.setStatus(OrderStatus.CREATED);
                order.setPaymentStatus(PaymentStatus.FAILED);
                order.setUpdatedAt(LocalDateTime.now());

                log.warn("Payment failed for order: {} with code: {}", order.getOrderCode(), responseCode);
            }

            paymentTransactionRepository.save(transaction);
            orderRepository.save(order);

        } catch (Exception e) {
            log.error("Error processing VNPay webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process webhook", e);
        }
    }

    @Override
    @Transactional
    public String processVnPayIPN(VnPayWebhookPayload payload) {
        log.info("=== Processing VNPay IPN ===");
        log.info("vnp_TxnRef: {}", payload.getVnp_TxnRef());

        try {
            // Extract order code from vnp_TxnRef (format: ORDER_CODE_{TIMESTAMP})
            String vnpTxnRef = payload.getVnp_TxnRef();
            String orderCode = extractOrderCode(vnpTxnRef);
            log.info("Extracted order code: {}", orderCode);

            // Step 2: Find transaction (vnp_TxnRef) in database
            Optional<Order> orderOpt = orderRepository.findByOrderCode(orderCode);

            if (orderOpt.isEmpty()) {
                log.error("Order not found: {}", orderCode);
                return "01"; // Order not Found
            }

            Order order = orderOpt.get();
            log.info("Found order: ID={}, Status={}, PaymentStatus={}",
                     order.getId(), order.getStatus(), order.getPaymentStatus());

            // Step 3: Check payment status before updating (checkOrderStatus)
            Optional<PaymentTransaction> transactionOpt = paymentTransactionRepository.findByOrderId(order.getId());

            if (transactionOpt.isEmpty()) {
                log.error("Payment transaction not found for order: {}", orderCode);
                return "01"; // Order not Found
            }

            PaymentTransaction transaction = transactionOpt.get();

            // Check if already confirmed
            if (transaction.getStatus() == PaymentTransactionStatus.SUCCESS) {
                log.info("Order already confirmed: {}", orderCode);
                return "02"; // Order already confirmed
            }

            // Step 4: Check amount (vnp_Amount) before updating
            String vnpAmount = payload.getVnp_Amount(); // Amount from VNPay (multiplied by 100)
            BigDecimal expectedAmount = order.getTotalPayable().multiply(new BigDecimal(100));
            BigDecimal receivedAmount = new BigDecimal(vnpAmount);

            if (receivedAmount.compareTo(expectedAmount) != 0) {
                log.error("Amount mismatch. Expected: {}, Received: {}", expectedAmount, receivedAmount);
                return "04"; // Invalid Amount
            }

            // Step 5: Update results to Database
            transaction.setResponsePayload(objectMapper.writeValueAsString(payload));
            transaction.setProviderTransactionId(payload.getVnp_TransactionNo());

            String responseCode = payload.getVnp_ResponseCode();
            String transactionStatus = payload.getVnp_TransactionStatus();

            if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
                // Payment successful
                transaction.setStatus(PaymentTransactionStatus.SUCCESS);
                transaction.setCompletedAt(LocalDateTime.now());

                order.setStatus(OrderStatus.PAID);
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setUpdatedAt(LocalDateTime.now());

                log.info("✓ Payment successful for order: {}", order.getOrderCode());

            } else {
                // Payment failed
                transaction.setStatus(PaymentTransactionStatus.FAILED);
                transaction.setCompletedAt(LocalDateTime.now());

                order.setStatus(OrderStatus.CREATED);
                order.setPaymentStatus(PaymentStatus.FAILED);
                order.setUpdatedAt(LocalDateTime.now());

                log.warn("✗ Payment failed for order: {} with code: {}", order.getOrderCode(), responseCode);
            }

            paymentTransactionRepository.save(transaction);
            orderRepository.save(order);

            log.info("IPN processing completed successfully for order: {}", orderCode);
            return "00"; // Confirm Success

        } catch (Exception e) {
            log.error("Error processing VNPay IPN: {}", e.getMessage(), e);
            return "99"; // Unknown error
        }
    }

    @Override
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));

        return PaymentResponse.builder()
                .id(transaction.getId())
                .orderId(transaction.getOrderId())
                .provider(transaction.getProvider())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .providerTransactionId(transaction.getProviderTransactionId())
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }

    @Override
    public boolean verifyVnPaySignature(VnPayWebhookPayload payload) {
        try {
            log.info("=== Verifying VNPay Signature ===");
            String receivedHash = payload.getVnp_SecureHash();
            log.info("Received hash: {}", receivedHash);

            // Dùng TreeMap để tự động sort theo key (tăng dần)
            Map<String, String> params = new TreeMap<>();

            // Ưu tiên lấy từ additionalParams nếu có
            if (payload.getAdditionalParams() != null && !payload.getAdditionalParams().isEmpty()) {
                for (Map.Entry<String, String> entry : payload.getAdditionalParams().entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (value != null && !value.isEmpty()
                            && !key.equalsIgnoreCase("vnp_SecureHash")
                            && !key.equalsIgnoreCase("vnp_SecureHashType")) {
                        params.put(key, value);
                    }
                }
            } else {
                // fallback nếu payload không chứa additionalParams
                if (payload.getVnp_TmnCode() != null) params.put("vnp_TmnCode", payload.getVnp_TmnCode());
                if (payload.getVnp_Amount() != null) params.put("vnp_Amount", payload.getVnp_Amount());
                if (payload.getVnp_BankCode() != null) params.put("vnp_BankCode", payload.getVnp_BankCode());
                if (payload.getVnp_BankTranNo() != null) params.put("vnp_BankTranNo", payload.getVnp_BankTranNo());
                if (payload.getVnp_CardType() != null) params.put("vnp_CardType", payload.getVnp_CardType());
                if (payload.getVnp_PayDate() != null) params.put("vnp_PayDate", payload.getVnp_PayDate());
                if (payload.getVnp_OrderInfo() != null) params.put("vnp_OrderInfo", payload.getVnp_OrderInfo());
                if (payload.getVnp_ResponseCode() != null) params.put("vnp_ResponseCode", payload.getVnp_ResponseCode());
                if (payload.getVnp_TransactionNo() != null) params.put("vnp_TransactionNo", payload.getVnp_TransactionNo());
                if (payload.getVnp_TransactionStatus() != null) params.put("vnp_TransactionStatus", payload.getVnp_TransactionStatus());
                if (payload.getVnp_TxnRef() != null) params.put("vnp_TxnRef", payload.getVnp_TxnRef());
            }

            // === Build hash data (theo đúng chuẩn VNPay, encode từng value) ===
            StringBuilder hashData = new StringBuilder();
            Iterator<Map.Entry<String, String>> itr = params.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<String, String> entry = itr.next();
                String key = entry.getKey();
                String value = entry.getValue();
                hashData.append(key)
                        .append("=")
                        .append(URLEncoder.encode(value, StandardCharsets.US_ASCII.toString()));
                if (itr.hasNext()) {
                    hashData.append("&");
                }
            }

            // === Hash Secret (trim để loại bỏ khoảng trắng thừa nếu có copy từ email) ===
            String secret = vnPayConfig.getHashSecret().trim();

            log.info("Hash data string: {}", hashData);
            log.info("Using secret (prefix): {}...", secret.substring(0, 5));

            String calculatedHash = hmacSHA512(secret, hashData.toString());
            log.info("Calculated hash: {}", calculatedHash);

            boolean isValid = calculatedHash.equalsIgnoreCase(receivedHash);
            log.info("Signature valid: {}", isValid);

            return isValid;
        } catch (Exception e) {
            log.error("Error verifying VNPay signature: {}", e.getMessage(), e);
            return false;
        }
    }


    private String generateVnPayUrl(Order order, PaymentTransaction transaction) throws UnsupportedEncodingException {
        Map<String, String> vnpParams = new TreeMap<>();

        String returnUrl = vnPayConfig.getReturnUrl();
        vnpParams.put("vnp_Version", vnPayConfig.getVersion());
        vnpParams.put("vnp_Command", vnPayConfig.getCommand());
        vnpParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(order.getTotalPayable().multiply(new BigDecimal(100)).longValue()));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", transaction.getVnpTxnRef());  // Dùng vnp_TxnRef unique từ transaction
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderCode());
        vnpParams.put("vnp_OrderType", vnPayConfig.getOrderType());
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", returnUrl);
        vnpParams.put("vnp_IpAddr", "127.0.0.1");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String createDate = LocalDateTime.now().format(formatter);
        vnpParams.put("vnp_CreateDate", createDate);

        // === Build hashData (encode từng value) ===
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<Map.Entry<String, String>> itr = vnpParams.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, String> entry = itr.next();
            String key = entry.getKey();
            String value = entry.getValue();

            hashData.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.US_ASCII.toString()));
            query.append(URLEncoder.encode(key, StandardCharsets.US_ASCII.toString()))
                    .append("=")
                    .append(URLEncoder.encode(value, StandardCharsets.US_ASCII.toString()));
            if (itr.hasNext()) {
                hashData.append("&");
                query.append("&");
            }
        }

        String vnpSecureHash = hmacSHA512(vnPayConfig.getHashSecret(), hashData.toString());

        return vnPayConfig.getVnpUrl() + "?" + query + "&vnp_SecureHash=" + vnpSecureHash;
    }


    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating HMAC SHA512", e);
        }
    }

    /**
     * Trích xuất order code từ vnp_TxnRef
     * Format: ORDER_CODE_{TIMESTAMP} -> ORDER_CODE
     * Ví dụ: ORD1762133045478C3B6F313_1730620537354 -> ORD1762133045478C3B6F313
     */
    private String extractOrderCode(String vnpTxnRef) {
        if (vnpTxnRef == null || !vnpTxnRef.contains("_")) {
            return vnpTxnRef; // Fallback nếu không có timestamp
        }
        return vnpTxnRef.substring(0, vnpTxnRef.lastIndexOf("_"));
    }
}

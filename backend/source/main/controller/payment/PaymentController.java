package controller.payment;

import dto.request.payment.PaymentInitRequest;
import dto.request.payment.VnPayWebhookPayload;
import dto.response.API.APIResponse;
import dto.response.payment.PaymentResponse;
import enums.PaymentMethod;
import enums.PaymentProvider;
import service.payment.PaymentService;
import repository.order.OrderRepository;
import entity.Order;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    @org.springframework.beans.factory.annotation.Value("${frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @org.springframework.beans.factory.annotation.Value("${app.payments.allow-simulate:false}")
    private boolean allowSimulate;

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    @PostMapping("/init")
    public ResponseEntity<APIResponse<PaymentResponse>> initPayment(
            @Valid @RequestBody PaymentInitRequest request) {
        log.info("Received payment init request for order: {}", request.getOrderId());
        PaymentResponse response = paymentService.initPayment(request);
        return ResponseEntity.ok(APIResponse.<PaymentResponse>builder()
                .code(200)
                .message("Payment initialized successfully")
                .result(response)
                .build());
    }

    /**
     * Dev-only: simulate a successful VNPay payment to drive order/drone flows without real card.
     * Enabled only when app.payments.allow-simulate=true (dev profile recommended).
     */
    @PostMapping(value = "/simulate-success")
    public ResponseEntity<APIResponse<Map<String, Object>>> simulateSuccess(@RequestParam("orderId") long orderId) {
        if (!allowSimulate) {
            return ResponseEntity.status(403).body(APIResponse.<Map<String, Object>>builder()
                    .code(403)
                    .message("Simulate disabled. Set app.payments.allow-simulate=true in dev config.")
                    .build());
        }
        try {
            // Ensure a payment transaction exists (init if missing or needs retry)
            PaymentInitRequest initReq = PaymentInitRequest.builder()
                .orderId(orderId)
                .provider(PaymentProvider.VNPAY)
                .method(PaymentMethod.QR)
                .bankCode("")
                .build();
            try {
                paymentService.initPayment(initReq); // will reuse existing and reset if in FAILED/PENDING
            } catch (Exception ignored) {
                // ignore if already PAID or other non‑retryable state; we'll proceed to attempt marking success
            }

            // Load order & payment transaction
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("orderId", orderId);
                result.put("rspCode", "01"); // Order not found
                return ResponseEntity.ok(APIResponse.<Map<String, Object>>builder()
                        .code(200)
                        .message("Order not found")
                        .result(result)
                        .build());
            }

            // We derive orderCode and attempt to reuse existing transaction's vnp_TxnRef so amount/status checks pass
            String orderCode = order.getOrderCode();
            String txnRefGuess = null;
            try {
                // Reflectively obtain vnpTxnRef from the PaymentTransaction via paymentService (not exposed in PaymentResponse)
                // Instead of reflection, we will accept generating a fresh ref; IPN logic extracts orderCode only and matches orderId, not ref equality.
                txnRefGuess = orderCode + "_" + System.currentTimeMillis();
            } catch (Exception ex) {
                txnRefGuess = orderCode + "_SIM" + System.currentTimeMillis();
            }

            // Amount must match expected *100
            java.math.BigDecimal expectedAmount = order.getTotalPayable().multiply(new java.math.BigDecimal(100));
            String vnpAmount = String.valueOf(expectedAmount.longValue());
            String now = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());

            Map<String, String> additional = new HashMap<>();
            additional.put("vnp_ResponseCode", "00");
            additional.put("vnp_TransactionStatus", "00");
            additional.put("vnp_PayDate", now);
            additional.put("vnp_TxnRef", txnRefGuess);
            additional.put("vnp_Amount", vnpAmount);
            additional.put("vnp_OrderInfo", "Thanh toan don hang " + orderCode);

            VnPayWebhookPayload payload = VnPayWebhookPayload.builder()
                .vnp_ResponseCode("00")
                .vnp_TransactionStatus("00")
                .vnp_TxnRef(txnRefGuess)
                .vnp_Amount(vnpAmount)
                .vnp_OrderInfo("Thanh toan don hang " + orderCode)
                .additionalParams(additional)
                .build();

            String rspCode = paymentService.processVnPayIPN(payload);
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", orderId);
            result.put("rspCode", rspCode);
            return ResponseEntity.ok(APIResponse.<Map<String, Object>>builder()
                    .code(200)
                    .message("Simulated payment success processed")
                    .result(result)
                    .build());
        } catch (Exception e) {
            log.error("Error simulating payment success: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(APIResponse.<Map<String, Object>>builder()
                    .code(9999)
                    .message("Simulate failed: " + e.getMessage())
                    .build());
        }
    }

    @GetMapping(value = "/vnpay-ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> handleVnPayIPN(@RequestParam Map<String, String> params) {
        log.info("=== VNPAY IPN RECEIVED ===");
        Map<String, String> response = new HashMap<>();
        try {
            String vnp_TxnRef = params.get("vnp_TxnRef");
            VnPayWebhookPayload payload = VnPayWebhookPayload.builder()
                    .vnp_TmnCode(params.get("vnp_TmnCode"))
                    .vnp_Amount(params.get("vnp_Amount"))
                    .vnp_BankCode(params.get("vnp_BankCode"))
                    .vnp_BankTranNo(params.get("vnp_BankTranNo"))
                    .vnp_CardType(params.get("vnp_CardType"))
                    .vnp_PayDate(params.get("vnp_PayDate"))
                    .vnp_OrderInfo(params.get("vnp_OrderInfo"))
                    .vnp_TransactionNo(params.get("vnp_TransactionNo"))
                    .vnp_ResponseCode(params.get("vnp_ResponseCode"))
                    .vnp_TransactionStatus(params.get("vnp_TransactionStatus"))
                    .vnp_TxnRef(vnp_TxnRef)
                    .vnp_SecureHashType(params.get("vnp_SecureHashType"))
                    .vnp_SecureHash(params.get("vnp_SecureHash"))
                    .additionalParams(params)
                    .build();
            if (!paymentService.verifyVnPaySignature(payload)) {
                response.put("RspCode", "97");
                response.put("Message", "Invalid Checksum");
                return ResponseEntity.ok(response);
            }
            String rspCode = paymentService.processVnPayIPN(payload);
            switch (rspCode) {
                case "00" -> {
                    response.put("RspCode", "00");
                    response.put("Message", "Confirm Success");
                }
                case "01" -> {
                    response.put("RspCode", "01");
                    response.put("Message", "Order not Found");
                }
                case "02" -> {
                    response.put("RspCode", "02");
                    response.put("Message", "Order already confirmed");
                }
                case "04" -> {
                    response.put("RspCode", "04");
                    response.put("Message", "Invalid Amount");
                }
                default -> {
                    response.put("RspCode", "99");
                    response.put("Message", "Unknown error");
                }
            }
        } catch (Exception e) {
            log.error("Exception processing VNPay IPN: {}", e.getMessage(), e);
            response.put("RspCode", "99");
            response.put("Message", "Unknown error");
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/vnpay-return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleVnPayReturn(@RequestParam Map<String, String> params) {
        log.info("=== VNPAY RETURN URL RECEIVED ===");
        String vnp_ResponseCode = params.getOrDefault("vnp_ResponseCode", "");
        String vnp_TxnRef = params.getOrDefault("vnp_TxnRef", "");
        String vnp_TransactionStatus = params.getOrDefault("vnp_TransactionStatus", "");

        VnPayWebhookPayload payload = VnPayWebhookPayload.builder()
                .vnp_TmnCode(params.get("vnp_TmnCode"))
                .vnp_Amount(params.get("vnp_Amount"))
                .vnp_BankCode(params.get("vnp_BankCode"))
                .vnp_BankTranNo(params.get("vnp_BankTranNo"))
                .vnp_CardType(params.get("vnp_CardType"))
                .vnp_PayDate(params.get("vnp_PayDate"))
                .vnp_OrderInfo(params.get("vnp_OrderInfo"))
                .vnp_TransactionNo(params.get("vnp_TransactionNo"))
                .vnp_ResponseCode(vnp_ResponseCode)
                .vnp_TransactionStatus(vnp_TransactionStatus)
                .vnp_TxnRef(vnp_TxnRef)
                .vnp_SecureHashType(params.get("vnp_SecureHashType"))
                .vnp_SecureHash(params.get("vnp_SecureHash"))
                .additionalParams(params)
                .build();

        // Verify signature
        if (!paymentService.verifyVnPaySignature(payload)) {
            log.error("Invalid signature for order: {}", vnp_TxnRef);
        return ResponseEntity.ok()
            .header("Content-Type", "text/html; charset=UTF-8")
            .body(buildInvalidSignatureHtml(vnp_TxnRef));
        }

        String html;
        if ("00".equals(vnp_ResponseCode) && "00".equals(vnp_TransactionStatus)) {
            // ✅ CẬP NHẬT DATABASE khi thanh toán thành công
            try {
                log.info("Processing payment success for order: {}", vnp_TxnRef);
                String rspCode = paymentService.processVnPayIPN(payload);
                log.info("Payment processing result: {}", rspCode);

                if (!"00".equals(rspCode) && !"02".equals(rspCode)) {
                    // Có lỗi khi xử lý
                    log.error("Failed to process payment. Response code: {}", rspCode);
                }
            } catch (Exception e) {
                log.error("Error processing payment in return URL: {}", e.getMessage(), e);
            }

            html = buildSuccessHtml(vnp_TxnRef, params.get("vnp_Amount"),
                    params.get("vnp_TransactionNo"), params.get("vnp_PayDate"), params.get("vnp_OrderInfo"));
        } else {
            // ✅ CẬP NHẬT DATABASE khi thanh toán thất bại
            try {
                log.info("Processing payment failure for order: {}", vnp_TxnRef);
                String rspCode = paymentService.processVnPayIPN(payload);
                log.info("Payment processing result: {}", rspCode);
            } catch (Exception e) {
                log.error("Error processing payment failure in return URL: {}", e.getMessage(), e);
            }

            html = buildFailureHtml(vnp_TxnRef, vnp_ResponseCode, params.get("vnp_OrderInfo"));
        }
    return ResponseEntity.ok()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body(html);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<APIResponse<PaymentResponse>> getPaymentByOrderId(@PathVariable Long orderId) {
        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(APIResponse.<PaymentResponse>builder()
                .code(200)
                .message("Payment retrieved successfully")
                .result(response)
                .build());
    }

    private String buildInvalidSignatureHtml(String orderCode) {
        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'><title>Lỗi xác thực</title>" + commonHead() + "</head>" +
                "<body class='pay-result'><div class='pay-wrapper'><div class='pay-card fail'><h1>Chữ ký không hợp lệ</h1><p>Mã đơn hàng: " + orderCode + "</p>" +
                actionButtons() + "</div></div></body></html>";
    }

    private String buildSuccessHtml(String orderCode, String amount, String transactionNo, String payDate, String orderInfo) {
        String formattedAmount = formatAmount(amount);
        String formattedDate = formatDate(payDate);
    return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'><title>Thanh toán thành công</title>" + commonHead() + "</head>" +
        "<body class='pay-result'><div class='pay-wrapper'><div class='pay-card success'><h1>Thanh toán thành công</h1>" +
                "<ul class='details'>" +
                li("Mã đơn hàng", orderCode) +
                li("Số tiền", formattedAmount + " VNĐ") +
                li("Mã giao dịch", transactionNo) +
                li("Thời gian", formattedDate) +
                "</ul>" +
        "<div class='actions'><a class='btn btn-primary' href='" + frontendBaseUrl + "/orders.html'>Xem đơn hàng</a>" +
        "<a class='btn btn-outline' href='" + frontendBaseUrl + "/index.html'>Trang chủ</a></div>" +
        "</div></div></body></html>";
    }

    private String buildFailureHtml(String orderCode, String responseCode, String orderInfo) {
        String errorMessage = getErrorMessage(responseCode);
        boolean canRetry = !"24".equals(responseCode); // user-cancelled (24) => no retry button
        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'><title>Thanh toán thất bại</title>" + commonHead() + "</head>" +
                "<body class='pay-result'><div class='pay-wrapper'><div class='pay-card fail'><h1>Thanh toán thất bại</h1>" +
                "<ul class='details'>" +
                li("Mã đơn hàng", orderCode) +
                li("Mã lỗi", responseCode) +
                li("Lý do", errorMessage) +
                (canRetry ? li("Hành động", "Bạn có thể thử thanh toán lại.") : li("Hành động", "Đơn đã hủy, không thể thanh toán lại.")) +
                "</ul>" +
                "<div class='actions'>" +
                (canRetry ? "<a class='btn btn-primary' href='" + frontendBaseUrl + "/orders.html'>Thử lại</a>" : "") +
                "<a class='btn btn-outline' href='" + frontendBaseUrl + "/index.html'>Trang chủ</a></div>" +
                "</div></div></body></html>";
    }

    private String commonHead() {
        return "<link rel='stylesheet' href='" + frontendBaseUrl + "/css/style.css'>" +
                "<style>:root{--primary:#3A5A9F;--primary-600:#2F4D8A;--accent:#9DB2CE;--success:#2E7D32;--warning:#B08900;--danger:#B91C1C;--bg:#FAFAFB;--surface:#FFFFFF;--surface-2:#FFFFFF;--text:#111827;--muted:#6B7280;--line:rgba(17,24,39,0.08);--radius:10px;--radius-sm:8px;--shadow-1:0 2px 8px rgba(17,24,39,0.06);--shadow-2:0 6px 20px rgba(17,24,39,0.08);--blur:none;--tr:160ms ease;}" +
                "body.pay-result{font-family:Inter,system-ui,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:var(--bg);color:var(--text);min-height:100vh;margin:0;padding:40px;display:flex;justify-content:center;}" +
                ".pay-wrapper{width:100%;max-width:640px;}" +
                ".pay-card{background:var(--surface);border:1px solid var(--line);border-radius:20px;padding:36px;box-shadow:var(--shadow-2);}" +
                ".pay-card.success{border-color:var(--success);} .pay-card.fail{border-color:var(--danger);}" +
                ".pay-card h1{margin:0 0 22px;font-size:1.9rem;font-weight:900;letter-spacing:.5px;background:linear-gradient(90deg,var(--primary),var(--accent));-webkit-background-clip:text;color:transparent;}" +
                ".details{list-style:none;padding:0;margin:0 0 28px;display:grid;gap:10px;}" +
                ".details li{background:color-mix(in srgb,var(--surface),transparent 6%);padding:12px 16px;border-radius:14px;font-size:.95rem;border:1px solid var(--line);}" +
                ".details li strong{display:block;font-size:.65rem;letter-spacing:.6px;text-transform:uppercase;color:var(--muted);margin-bottom:4px;}" +
                ".actions{display:flex;gap:14px;flex-wrap:wrap;} .actions .btn{flex:1;}" +
                "@media(max-width:680px){.pay-card{padding:28px;} .pay-card h1{font-size:1.6rem;}}" +
                "</style>";
    }

    private String li(String label, String value) {
        return "<li><strong>" + label + "</strong>" + (value == null ? "" : value) + "</li>";
    }

    private String actionButtons() {
        return "<div class='actions'><a class='btn btn-primary' href='" + frontendBaseUrl + "/index.html'>Trang chủ</a>" +
                "<a class='btn btn-outline' href='" + frontendBaseUrl + "/orders.html'>Đơn hàng</a></div>";
    }

    private String formatAmount(String amount) {
        try {
            long amountInVnd = Long.parseLong(amount) / 100;
            return String.format("%,d", amountInVnd);
        } catch (Exception e) {
            return amount;
        }
    }

    private String formatDate(String payDate) {
        try {
            if (payDate != null && payDate.length() == 14) {
                String year = payDate.substring(0, 4);
                String month = payDate.substring(4, 6);
                String day = payDate.substring(6, 8);
                String hour = payDate.substring(8, 10);
                String minute = payDate.substring(10, 12);
                String second = payDate.substring(12, 14);
                return day + "/" + month + "/" + year + " " + hour + ":" + minute + ":" + second;
            }
        } catch (Exception e) {
            log.error("Error formatting date: {}", e.getMessage());
        }
        return payDate;
    }

    private String getErrorMessage(String responseCode) {
        return switch (responseCode) {
            case "07" -> "Trừ tiền thành công. Giao dịch bị nghi ngờ";
            case "09" -> "Thẻ/Tài khoản chưa đăng ký InternetBanking";
            case "10" -> "Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần";
            case "11" -> "Đã hết hạn chờ thanh toán";
            case "12" -> "Thẻ/Tài khoản bị khóa";
            case "13" -> "Nhập sai mật khẩu OTP";
            case "24" -> "Khách hàng hủy giao dịch";
            case "51" -> "Tài khoản không đủ số dư";
            case "65" -> "Vượt quá hạn mức giao dịch trong ngày";
            case "75" -> "Ngân hàng thanh toán đang bảo trì";
            case "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định";
            default -> "Lỗi không xác định";
        };
    }
}

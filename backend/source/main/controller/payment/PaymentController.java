package controller.payment;

import dto.request.payment.PaymentInitRequest;
import dto.request.payment.VnPayWebhookPayload;
import dto.response.API.APIResponse;
import dto.response.payment.PaymentResponse;
import service.payment.PaymentService;
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

    private final PaymentService paymentService;

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
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
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
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
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
        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'><title>Lỗi xác thực</title></head>" +
                "<body><h1>Chữ ký không hợp lệ</h1><p>Mã đơn hàng: " + orderCode + "</p>" +
                "<a href='/home'>Quay về trang chủ</a></body></html>";
    }

    private String buildSuccessHtml(String orderCode, String amount, String transactionNo, String payDate, String orderInfo) {
        String formattedAmount = formatAmount(amount);
        String formattedDate = formatDate(payDate);
        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'><title>Thanh toán thành công</title></head>" +
                "<body><h1>Thanh toán thành công!</h1>" +
                "<p>Mã đơn hàng: " + orderCode + "</p>" +
                "<p>Số tiền: " + formattedAmount + " VNĐ</p>" +
                "<p>Mã giao dịch: " + transactionNo + "</p>" +
                "<p>Thời gian: " + formattedDate + "</p>" +
                "<a href='/home/orders.html'>Xem đơn hàng</a></body></html>";
    }

    private String buildFailureHtml(String orderCode, String responseCode, String orderInfo) {
        String errorMessage = getErrorMessage(responseCode);
        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'><title>Thanh toán thất bại</title></head>" +
                "<body><h1>Thanh toán thất bại!</h1>" +
                "<p>Mã đơn hàng: " + orderCode + "</p>" +
                "<p>Mã lỗi: " + responseCode + "</p>" +
                "<p>Lý do: " + errorMessage + "</p>" +
                "<a href='/home/orders.html'>Thử lại</a> | <a href='/home'>Quay về trang chủ</a></body></html>";
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

package service.payment;

import dto.request.payment.PaymentInitRequest;
import dto.request.payment.VnPayWebhookPayload;
import dto.response.payment.PaymentResponse;

public interface PaymentService {

    /**
     * Initialize payment transaction
     */
    PaymentResponse initPayment(PaymentInitRequest request);

    /**
     * Process VNPay webhook callback
     */
    void processVnPayWebhook(VnPayWebhookPayload payload);

    /**
     * Process VNPay IPN (Instant Payment Notification)
     * @param payload VNPay IPN payload
     * @return Response code: "00" = Success, "01" = Order not found, "02" = Already confirmed, "04" = Invalid amount, "99" = Error
     */
    String processVnPayIPN(VnPayWebhookPayload payload);

    /**
     * Get payment by order ID
     */
    PaymentResponse getPaymentByOrderId(Long orderId);

    /**
     * Verify VNPay signature
     */
    boolean verifyVnPaySignature(VnPayWebhookPayload payload);
}

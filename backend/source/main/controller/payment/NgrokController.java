package controller.payment;

import service.payment.NgrokUrlService;
import dto.response.API.APIResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ngrok")
@RequiredArgsConstructor
@Slf4j
public class NgrokController {

    private final NgrokUrlService ngrokUrlService;

    /**
     * Get current ngrok public URL
     */
    @GetMapping("/url")
    public ResponseEntity<APIResponse<Map<String, Object>>> getNgrokUrl() {
        String publicUrl = ngrokUrlService.getPublicUrl();
        boolean isNgrokRunning = ngrokUrlService.isNgrokRunning();

        Map<String, Object> result = new HashMap<>();
        result.put("publicUrl", publicUrl);
        result.put("isNgrokRunning", isNgrokRunning);
        result.put("callbackUrl", ngrokUrlService.buildCallbackUrl("/api/v1/payments/vnpay-return"));

        return ResponseEntity.ok(APIResponse.<Map<String, Object>>builder()
                .code(200)
                .message(isNgrokRunning ? "Ngrok is running" : "Ngrok is not running - using localhost fallback")
                .result(result)
                .build());
    }

    /**
     * Clear ngrok URL cache and refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<APIResponse<Map<String, Object>>> refreshNgrokUrl() {
        log.info("Refreshing ngrok URL cache");

        ngrokUrlService.clearCache();
        String newUrl = ngrokUrlService.getPublicUrl();
        boolean isNgrokRunning = ngrokUrlService.isNgrokRunning();

        Map<String, Object> result = new HashMap<>();
        result.put("publicUrl", newUrl);
        result.put("isNgrokRunning", isNgrokRunning);
        result.put("callbackUrl", ngrokUrlService.buildCallbackUrl("/api/v1/payments/vnpay-return"));

        return ResponseEntity.ok(APIResponse.<Map<String, Object>>builder()
                .code(200)
                .message("Ngrok URL cache cleared and refreshed")
                .result(result)
                .build());
    }

    /**
     * Check ngrok status
     */
    @GetMapping("/status")
    public ResponseEntity<APIResponse<Map<String, Object>>> checkNgrokStatus() {
        boolean isRunning = ngrokUrlService.isNgrokRunning();

        Map<String, Object> result = new HashMap<>();
        result.put("isNgrokRunning", isRunning);
        result.put("ngrokWebInterface", "http://localhost:4040");

        if (isRunning) {
            result.put("currentUrl", ngrokUrlService.getPublicUrl());
        } else {
            result.put("message", "Ngrok is not running. Please start ngrok with: ngrok http 8080");
        }

        return ResponseEntity.ok(APIResponse.<Map<String, Object>>builder()
                .code(200)
                .message(isRunning ? "Ngrok is running" : "Ngrok is not running")
                .result(result)
                .build());
    }
}


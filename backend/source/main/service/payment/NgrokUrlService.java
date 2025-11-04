package service.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service to automatically detect ngrok public URL
 */
@Service
@Slf4j
public class NgrokUrlService {

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    private static final String NGROK_API_URL = "http://localhost:4040/api/tunnels";

    private String cachedNgrokUrl;

    /**
     * Get the public ngrok URL
     * @return ngrok HTTPS URL or localhost fallback
     */
    public String getPublicUrl() {
        // Always fetch fresh URL to avoid using expired URLs
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());
            ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory()).setConnectTimeout(2000);
            ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory()).setReadTimeout(2000);

            Map<String, Object> response = restTemplate.getForObject(NGROK_API_URL, Map.class);

            if (response != null && response.containsKey("tunnels")) {
                Object tunnelsObj = response.get("tunnels");
                if (tunnelsObj instanceof java.util.List) {
                    java.util.List<?> tunnels = (java.util.List<?>) tunnelsObj;

                    for (Object tunnelObj : tunnels) {
                        if (tunnelObj instanceof Map) {
                            Map<?, ?> tunnel = (Map<?, ?>) tunnelObj;
                            String proto = (String) tunnel.get("proto");
                            String publicUrl = (String) tunnel.get("public_url");

                            // Prefer HTTPS tunnel
                            if ("https".equals(proto) && publicUrl != null) {
                                // Only cache if different from previous
                                if (!publicUrl.equals(cachedNgrokUrl)) {
                                    log.info("Ngrok URL updated: {} -> {}", cachedNgrokUrl, publicUrl);
                                    cachedNgrokUrl = publicUrl;
                                } else {
                                    log.debug("Using cached ngrok URL: {}", cachedNgrokUrl);
                                }
                                return cachedNgrokUrl;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch ngrok URL from API: {}. Using localhost fallback.", e.getMessage());
            cachedNgrokUrl = null; // Clear cache on error
        }

        // Fallback to localhost
        String fallback = "http://localhost:8080";
        log.info("Using localhost fallback: {}", fallback);
        return fallback;
    }

    /**
     * Build full callback URL with ngrok domain
     * @param endpoint the endpoint path (e.g., /api/v1/payments/vnpay-return)
     * @return full URL
     */
    public String buildCallbackUrl(String endpoint) {
        String baseUrl = getPublicUrl();

        // Remove trailing slash from base URL
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // Ensure endpoint starts with /
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }

        // Add context path if exists and not root
        String contextPathToUse = contextPath;
        if ("/".equals(contextPath)) {
            contextPathToUse = "";
        }

        // Build full URL: baseUrl + contextPath + endpoint
        String fullUrl = baseUrl + contextPathToUse + endpoint;
        log.info("Built callback URL: baseUrl={}, contextPath={}, endpoint={}, fullUrl={}",
                 baseUrl, contextPathToUse, endpoint, fullUrl);
        return fullUrl;
    }

    /**
     * Clear cached URL (useful for testing or when ngrok restarts)
     */
    public void clearCache() {
        cachedNgrokUrl = null;
        log.info("Ngrok URL cache cleared");
    }

    /**
     * Check if ngrok is running
     * @return true if ngrok is accessible
     */
    public boolean isNgrokRunning() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getForObject(NGROK_API_URL, Map.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

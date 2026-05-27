package spring.boot.event.booking.project.Service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import spring.boot.event.booking.project.DTO.EventDTO;
import spring.boot.event.booking.project.exception.BookingFailedException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayPalService {

    private static final Logger log = LoggerFactory.getLogger(PayPalService.class);

    @Value("${paypal.client.id}")
    private String clientId;

    @Value("${paypal.client.secret}")
    private String clientSecret;

    @Value("${paypal.base-url}")
    private String baseUrl;

    @Value("${app.currency.inr-to-usd-rate}")
    private double inrToUsdRate;

    private final RestClient restClient = RestClient.create();

    // Token cache — PayPal tokens are valid for ~9 hours, we refresh after 8
    private String cachedToken = null;
    private long tokenExpiryTime = 0;

    private final EventService eventService;

    // 1. Get OAuth Token from PayPal (cached — avoids redundant auth calls)
    private synchronized String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return cachedToken;
        }

        String encodedAuth = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add("grant_type", "client_credentials");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(baseUrl + "/v1/oauth2/token")
                .header("Authorization", "Basic " + encodedAuth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .body(Map.class);

        cachedToken = response.get("access_token").toString();
        tokenExpiryTime = System.currentTimeMillis() + (8 * 60 * 60 * 1000L); // 8 hours

        log.info("PayPal access token refreshed. Next refresh in 8 hours.");
        return cachedToken;
    }

    // 2. Create the Order
    @SuppressWarnings("unchecked")
    public Map<String, Object> createOrder(double amount) {
        String accessToken = getAccessToken();

        // Note: USD used for Sandbox compatibility.
        // Real INR requires a verified India PayPal business account.
        double usdAmount = amount / inrToUsdRate; // Rough INR → USD conversion

        Map<String, Object> requestBody = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(
                        Map.of("amount", Map.of(
                                "currency_code", "USD",
                                "value", String.format("%.2f", usdAmount)
                        ))
                )
        );

        return restClient.post()
                .uri(baseUrl + "/v2/checkout/orders")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);
    }

    // 3. Capture (Confirm) the Payment
    @SuppressWarnings("unchecked")
    public Map<String, Object> captureOrder(String orderId) {
        String accessToken = getAccessToken();

        return restClient.post()
                .uri(baseUrl + "/v2/checkout/orders/" + orderId + "/capture")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }

    // 4. Process a Full Refund
    public boolean refundCapture(String captureId) {
        try {
            String accessToken = getAccessToken();

            // Empty body = full refund from PayPal's side
            restClient.post()
                    .uri(baseUrl + "/v2/payments/captures/" + captureId + "/refund")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();

            log.info("Refund issued successfully for capture ID: {}", captureId);
            return true;

        } catch (RestClientException e) {
            log.error("PayPal refund failed for capture ID: {}. Reason: {}", captureId, e.getMessage());
            return false;
        }
    }

    // 5. Create Order using DB price — frontend amount is never trusted
    public Map<String, Object> createOrderForEvent(Long eventId) {
        EventDTO event = eventService.findEventById(eventId);

        if (event.getTicketPrice() == null || event.getTicketPrice().doubleValue() <= 0) {
            throw new BookingFailedException("This event does not require payment");
        }

        double correctAmount = event.getTicketPrice().doubleValue();
        log.info("Creating PayPal order for event ID: {} at amount: {}", eventId, correctAmount);

        return createOrder(correctAmount);
    }

    // 6. Verify the Order Status Directly with PayPal (Webhook Security)
    public boolean verifyOrderIsCompleted(String orderId) {
        try {
            String accessToken = getAccessToken();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(baseUrl + "/v2/checkout/orders/" + orderId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);

            // PayPal returns "COMPLETED" only if the money has successfully settled
            String status = (String) response.get("status");
            return "COMPLETED".equals(status);

        } catch (Exception e) {
            log.error("SECURITY ALERT: Failed to verify PayPal order ID: {}. Reason: {}", orderId, e.getMessage());
            return false;
        }
    }
}
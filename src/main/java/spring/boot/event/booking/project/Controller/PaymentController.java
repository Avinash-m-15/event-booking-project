package spring.boot.event.booking.project.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import spring.boot.event.booking.project.Service.BookingService;
import spring.boot.event.booking.project.Service.PayPalService;
import spring.boot.event.booking.project.exception.BookingFailedException;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PayPalService payPalService;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> data) {
        try {
            Long eventId = Long.parseLong(data.get("eventId").toString());
            Map<String, Object> order = payPalService.createOrderForEvent(eventId);
            return ResponseEntity.ok(order);
        } catch (BookingFailedException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create order"));
        }
    }

    @PostMapping("/capture")
    public ResponseEntity<?> captureOrder(@RequestBody Map<String, String> data) {
        try {
            String orderId = data.get("orderID");
            Map<String, Object> captureResponse = payPalService.captureOrder(orderId);

            if ("COMPLETED".equals(captureResponse.get("status"))) {
                // We still return success to the React frontend so the UI updates nicely,
                // but the ACTUAL database update and email sending happens securely in the webhook below.
                return ResponseEntity.ok(Map.of("status", "success", "message", "Payment Captured"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Payment not completed"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Payment verification failed"));
        }
    }

    /**
     * The Asynchronous Webhook Listener
     * PayPal calls this endpoint directly from their servers.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handlePayPalWebhook(@RequestBody String payload) {
        try {
            // 1. Parse the incoming JSON payload from PayPal
            JsonNode rootNode = objectMapper.readTree(payload);
            String eventType = rootNode.path("event_type").asText();

            // 2. PayPal hides the original Order ID deep inside the resource payload.
            // We need to extract it to match it with our DB record.
            if ("PAYMENT.CAPTURE.COMPLETED".equals(eventType) || "PAYMENT.CAPTURE.DENIED".equals(eventType)) {

                String captureId = rootNode.path("resource").path("id").asText();

                // The Order ID is NOT in supplementary_data in PayPal sandbox webhooks.
                // It lives inside the "links" array as the href of the "up" rel link.
                // e.g. https://api.paypal.com/v2/checkout/orders/{ORDER_ID}
                String orderId = "";
                for (JsonNode link : rootNode.path("resource").path("links")) {
                    if ("up".equals(link.path("rel").asText())) {
                        String href = link.path("href").asText();
                        orderId = href.substring(href.lastIndexOf("/") + 1);
                        break;
                    }
                }

                if (!orderId.isEmpty() && !captureId.isEmpty()) {
                    if ("PAYMENT.CAPTURE.COMPLETED".equals(eventType)) {
                        bookingService.confirmBookingAsynchronously(orderId, captureId);
                    } else {
                        bookingService.failBookingAsynchronously(orderId);
                    }
                }
            }

            // Always return HTTP 200 OK so PayPal knows the webhook was received
            // Otherwise, PayPal will aggressively retry sending the webhook
            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            e.printStackTrace();
            // Return 200 even on an internal error to prevent PayPal infinite retries during dev
            return ResponseEntity.ok("Webhook received but encountered an error");
        }
    }
}
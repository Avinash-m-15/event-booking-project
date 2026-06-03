package spring.boot.event.booking.project.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Async
    public void sendBookingConfirmation(String toEmail, String userName, String eventName, double ticketPrice, Long bookingId, LocalDateTime eventDate, String location) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        String qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=" + frontendUrl + "/verify/" + bookingId + "&format=jpeg";

        String htmlContent = String.format(
                "<html><body style='font-family: Helvetica, Arial, sans-serif; background-color: #f4f4f5; padding: 20px;'>" +
                        "<div style='max-width: 500px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.05);'>" +

                        "  <div style='background-color: #2563eb; padding: 30px 20px; text-align: center; color: white;'>" +
                        "    <h1 style='margin: 0; font-size: 24px; font-weight: bold;'>EventBook</h1>" +
                        "    <p style='margin: 5px 0 0 0; opacity: 0.8;'>Booking Confirmed</p>" +
                        "  </div>" +

                        "  <div style='padding: 30px 20px;'>" +
                        "    <h2 style='margin: 0 0 20px 0; color: #0f172a; font-size: 22px;'>%s</h2>" +
                        "    <p style='margin: 5px 0; color: #475569;'><b>Ticket ID:</b> #%d</p>" +
                        "    <p style='margin: 5px 0; color: #475569;'><b>Date:</b> %s</p>" +
                        "    <p style='margin: 5px 0; color: #475569;'><b>Location:</b> %s</p>" +
                        "    <p style='margin: 5px 0; color: #475569;'><b>Total Paid:</b> ₹%.2f</p>" +
                        "  </div>" +

                        "  <div style='background-color: #f8fafc; padding: 30px 20px; text-align: center; border-top: 2px dashed #e2e8f0;'>" +
                        "    <p style='margin: 0 0 15px 0; font-weight: bold; color: #0f172a;'>Scan at venue entry</p>" +
                        "    <img src='%s' alt='Ticket QR Code' style='border: 1px solid #e2e8f0; border-radius: 12px; padding: 10px; background: white;' width='150' height='150' />" +
                        "    <p style='margin: 15px 0 0 0; font-size: 12px; color: #94a3b8;'>Present this email or your in-app ticket at the door.</p>" +
                        "  </div>" +

                        "</div></body></html>",
                eventName, bookingId, eventDate.toString().replace("T", " "), location, ticketPrice, qrCodeUrl);

        String icsContent = generateIcs(eventName, eventDate, location);
        String icsBase64 = Base64.getEncoder().encodeToString(icsContent.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", "EventBook Tickets", "email", "avinash2005m@gmail.com"),
                "to", List.of(Map.of("email", toEmail, "name", userName)),
                "subject", "Your Ticket: " + eventName,
                "htmlContent", htmlContent,
                "attachment", List.of(
                        Map.of("name", "add-to-calendar.ics", "content", icsBase64)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(BREVO_API_URL, HttpMethod.POST, request, String.class);
            log.info("Rich HTML Email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send rich email: {}", e.getMessage());
        }
    }

    private String generateIcs(String eventName, LocalDateTime eventDate, String location) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String start = eventDate.format(formatter);
        String end = eventDate.plusHours(3).format(formatter); // Assuming a 3-hour event

        return "BEGIN:VCALENDAR\n" +
                "VERSION:2.0\n" +
                "PRODID:-//EventBook//EN\n" +
                "BEGIN:VEVENT\n" +
                "DTSTART:" + start + "\n" +
                "DTEND:" + end + "\n" +
                "SUMMARY:" + eventName + "\n" +
                "LOCATION:" + location + "\n" +
                "DESCRIPTION:Your ticket for " + eventName + "\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String userName, String token) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        String htmlContent = String.format(
                "<html><body><h2>Hi %s,</h2>" +
                        "<p>You requested a password reset for your Event Booking account.</p>" +
                        "<p>Your reset token/OTP is: <b>%s</b></p>" +
                        "<p><i>This token will expire in 15 minutes. If you did not request this, please ignore this email.</i></p>" +
                        "</body></html>",
                userName, token);

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", "Event Booking Security", "email", "avinash2005m@gmail.com"),
                "to", List.of(Map.of("email", toEmail, "name", userName)),
                "subject", "Your Password Reset Token",
                "htmlContent", htmlContent
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(BREVO_API_URL, HttpMethod.POST, request, String.class);
            log.info("Password reset email sent asynchronously to: {}", toEmail);
        } catch (HttpStatusCodeException e) {
            log.error("Brevo rejected the request!");
            log.error("Status Code: {}", e.getStatusCode());
            log.error("Brevo Response Body: {}", e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("General failure: {}", e.getMessage());
        }
    }

    @Async
    public void sendCancellationEmail(String toEmail, String userName, String eventName, double refundAmount) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        String htmlContent = String.format(
                "<html><body style='font-family: Helvetica, Arial, sans-serif; background-color: #f4f4f5; padding: 20px;'>" +
                        "<div style='max-width: 500px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.05);'>" +

                        "  <div style='background-color: #ef4444; padding: 30px 20px; text-align: center; color: white;'>" +
                        "    <h1 style='margin: 0; font-size: 24px; font-weight: bold;'>EventBook</h1>" +
                        "    <p style='margin: 5px 0 0 0; opacity: 0.9;'>Booking Cancelled & Refund Initiated</p>" +
                        "  </div>" +

                        "  <div style='padding: 30px 20px;'>" +
                        "    <p style='color: #334155; font-size: 16px; margin-bottom: 20px;'>Hi %s,</p>" +
                        "    <p style='color: #475569; font-size: 16px; line-height: 1.5;'>Your booking for <b>%s</b> has been successfully cancelled as requested.</p>" +

                        "    <div style='background-color: #f8fafc; border-left: 4px solid #ef4444; padding: 15px; margin: 25px 0; border-radius: 0 8px 8px 0;'>" +
                        "      <p style='margin: 0; color: #0f172a; font-weight: bold; font-size: 16px;'>Refund Amount: ₹%.2f</p>" +
                        "      <p style='margin: 8px 0 0 0; color: #64748b; font-size: 14px; line-height: 1.4;'>" +
                        "        A full refund has been issued to your original payment method via PayPal. It usually takes 3-5 business days to appear on your bank statement." +
                        "      </p>" +
                        "    </div>" +

                        "    <p style='color: #475569; font-size: 15px; margin-top: 30px;'>We hope to see you at another event soon!</p>" +
                        "  </div>" +

                        "</div></body></html>",
                userName, eventName, refundAmount);

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", "EventBook Support", "email", "avinash2005m@gmail.com"),
                "to", List.of(Map.of("email", toEmail, "name", userName)),
                "subject", "Booking Cancelled: " + eventName,
                "htmlContent", htmlContent
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(BREVO_API_URL, HttpMethod.POST, request, String.class);
            log.info("Cancellation email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send cancellation email: {}", e.getMessage());
        }
    }
}


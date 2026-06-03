package spring.boot.event.booking.project.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import spring.boot.event.booking.project.DTO.BookingRequest;
import spring.boot.event.booking.project.DTO.BookingResponse;
import spring.boot.event.booking.project.DTO.VerifyBookingResponse;
import spring.boot.event.booking.project.Service.BookingService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<?> createBooking(@Valid @RequestBody BookingRequest request, Principal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.initiateBooking(request, request.getPayPalOrderId()));

    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable Long id) {

        return ResponseEntity.ok(bookingService.getBookingById(id));

    }

    @GetMapping("/my-bookings")
    public ResponseEntity<List<BookingResponse>> getMyBookings() {

        return ResponseEntity.ok(bookingService.getMyBookings());

    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
            bookingService.cancelBooking(id);
            return ResponseEntity.ok().body(Map.of("message", "Booking cancelled successfully!"));
    }

    @GetMapping("/event/{id}")
    public ResponseEntity<List<BookingResponse>> getAttendeesForEvent(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getAttendeesForEvent(id));
    }

    @PostMapping("/verify/{id}")
    public ResponseEntity<VerifyBookingResponse> verifyBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.verifyBooking(id));
    }
}

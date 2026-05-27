package spring.boot.event.booking.project.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import spring.boot.event.booking.project.DTO.BookingRequest;
import spring.boot.event.booking.project.DTO.BookingResponse;
import spring.boot.event.booking.project.Entity.Booking;
import spring.boot.event.booking.project.Entity.Event;
import spring.boot.event.booking.project.Entity.User;
import spring.boot.event.booking.project.Mapper.BookingMapper;
import spring.boot.event.booking.project.Repository.BookingRepository;
import spring.boot.event.booking.project.Repository.EventRepository;
import spring.boot.event.booking.project.Repository.UserRepository;
import spring.boot.event.booking.project.enums.BookingStatus;
import spring.boot.event.booking.project.exception.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.lenient;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingMapper bookingMapper;
    @Mock UserRepository userRepository;
    @Mock EventRepository eventRepository;
    @Mock NotificationService notificationService;
    @Mock PayPalService payPalService;

    @InjectMocks BookingService bookingService;

    private User attendee;
    private User organizer;
    private Event event;
    private BookingRequest request;

    @BeforeEach
    void setUp() {
        organizer = new User();
        organizer.setUserId(1L);
        organizer.setEmail("organizer@test.com");
        organizer.setUsername("OrganizerUser");

        attendee = new User();
        attendee.setUserId(2L);
        attendee.setEmail("attendee@test.com");
        attendee.setUsername("AttendeeUser");

        event = new Event();
        event.setEventId(10L);
        event.setEventName("Tech Summit 2026");
        event.setOrganizer(organizer);
        event.setAvailableSeats(50);
        event.setTotalSeats(50);
        event.setTicketPrice(BigDecimal.valueOf(500));
        event.setCancellable(true);

        request = new BookingRequest();
        request.setEventId(10L);
        request.setPayPalOrderId("PAYPAL-ORDER-123");
    }

    private void mockSecurityContext(String email, boolean isAdmin) {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        when(auth.getName()).thenReturn(email);
        Collection<GrantedAuthority> authorities = isAdmin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        lenient().doReturn(authorities).when(auth).getAuthorities();
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    // ── 1. Happy path new booking ─────────────────────────────────────────────

    @Test
    @DisplayName("Happy path: new booking is created and seat is deducted")
    void newBooking_success() {
        mockSecurityContext("attendee@test.com", false);
        when(userRepository.findByEmail("attendee@test.com")).thenReturn(Optional.of(attendee));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(bookingRepository.findByUserAndEvent(attendee, event)).thenReturn(List.of());

        Booking saved = new Booking();
        saved.setStatus(BookingStatus.PENDING);
        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);
        when(bookingMapper.toResponse(saved)).thenReturn(new BookingResponse());

        BookingResponse result = bookingService.initiateBooking(request, "PAYPAL-ORDER-123");

        assertThat(result).isNotNull();
        assertThat(event.getAvailableSeats()).isEqualTo(49); // seat deducted
        verify(eventRepository).save(event);
        verify(bookingRepository).save(any(Booking.class));
    }

    // ── 2. Organizer booking own event ────────────────────────────────────────

    @Test
    @DisplayName("Organizer booking own event throws BookingFailedException")
    void organizerBookingOwnEvent_throwsException() {
        mockSecurityContext("organizer@test.com", false);
        when(userRepository.findByEmail("organizer@test.com")).thenReturn(Optional.of(organizer));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> bookingService.initiateBooking(request, "ORDER-1"))
                .isInstanceOf(BookingFailedException.class)
                .hasMessageContaining("Organizers cannot book their own events");
    }

    // ── 3. PENDING booking recycled — seat NOT deducted again ─────────────────

    @Test
    @DisplayName("PENDING booking recycled: order ID updated, seat NOT deducted again")
    void pendingBookingRecycled_noSeatDeducted() {
        mockSecurityContext("attendee@test.com", false);
        when(userRepository.findByEmail("attendee@test.com")).thenReturn(Optional.of(attendee));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        Booking existing = new Booking();
        existing.setStatus(BookingStatus.PENDING);
        existing.setPaymentReferenceId("OLD-ORDER");
        when(bookingRepository.findByUserAndEvent(attendee, event)).thenReturn(List.of(existing));
        when(bookingRepository.save(existing)).thenReturn(existing);
        when(bookingMapper.toResponse(existing)).thenReturn(new BookingResponse());

        bookingService.initiateBooking(request, "NEW-ORDER");

        assertThat(existing.getPaymentReferenceId()).isEqualTo("NEW-ORDER");
        assertThat(event.getAvailableSeats()).isEqualTo(50); // seat NOT deducted again
    }

    // ── 4. Fake PayPal order ──────────────────────────────────────────────────

    @Test
    @DisplayName("Fake/unpaid PayPal order: immediately returns without confirming booking")
    void fakeOrder_doesNothing() {
        when(payPalService.verifyOrderIsCompleted("FAKE")).thenReturn(false);

        bookingService.confirmBookingAsynchronously("FAKE", "CAP-X");

        verify(bookingRepository, never()).findByPaymentReferenceId(any());
        verify(bookingRepository, never()).save(any());
    }

    // ── 5. Idempotency — already CONFIRMED not processed twice ────────────────

    @Test
    @DisplayName("Idempotency: already CONFIRMED booking is not processed again")
    void alreadyConfirmed_skipsProcessing() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.CONFIRMED);

        when(payPalService.verifyOrderIsCompleted("ORDER-1")).thenReturn(true);
        when(bookingRepository.findByPaymentReferenceId("ORDER-1")).thenReturn(Optional.of(booking));

        bookingService.confirmBookingAsynchronously("ORDER-1", "CAP-1");

        verify(bookingRepository, never()).save(any());
        verify(notificationService, never()).sendBookingConfirmation(
                any(), any(), any(), anyDouble(), any(), any(), any());
    }

    // ── 6. Late webhook on FAILED booking → auto-refund ──────────────────────

    @Test
    @DisplayName("Late webhook on FAILED booking triggers auto-refund")
    void lateWebhook_failedBooking_triggersRefund() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.FAILED);
        booking.setUser(attendee);
        booking.setEvent(event);

        when(payPalService.verifyOrderIsCompleted("ORDER-1")).thenReturn(true);
        when(bookingRepository.findByPaymentReferenceId("ORDER-1")).thenReturn(Optional.of(booking));

        bookingService.confirmBookingAsynchronously("ORDER-1", "CAP-LATE");

        verify(payPalService).refundCapture("CAP-LATE");
        verify(bookingRepository, never()).save(any());
    }

    // ── 7. PayPal refund failure aborts cancellation ──────────────────────────

    @Test
    @DisplayName("PayPal refund failure aborts cancellation — booking status unchanged")
    void refundFailure_abortsCancel() {
        mockSecurityContext("attendee@test.com", false);

        Booking booking = new Booking();
        booking.setUser(attendee);
        booking.setEvent(event);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCaptureTransactionId("CAP-BAD");

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(payPalService.refundCapture("CAP-BAD")).thenReturn(false);

        assertThatThrownBy(() -> bookingService.cancelBooking(1L))
                .isInstanceOf(BookingFailedException.class)
                .hasMessageContaining("Failed to process refund");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED); // unchanged
    }

    // ── 8. Non-owner cannot cancel ────────────────────────────────────────────

    @Test
    @DisplayName("Non-owner, non-admin cannot cancel — throws UnauthorizedAccessException")
    void nonOwner_cannotCancel_throwsUnauthorized() {
        mockSecurityContext("other@test.com", false);

        Booking booking = new Booking();
        booking.setUser(attendee); // owned by attendee, not other@test.com
        booking.setEvent(event);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(1L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // ── 9. Cron job releases expired bookings ─────────────────────────────────

    @Test
    @DisplayName("Cron: expired PENDING bookings marked FAILED and seats restored in batch")
    void expiredPendingBookings_releasedAndSeatsRestored() {
        Booking b1 = new Booking(); b1.setStatus(BookingStatus.PENDING); b1.setEvent(event);
        Booking b2 = new Booking(); b2.setStatus(BookingStatus.PENDING); b2.setEvent(event);

        when(bookingRepository.findByStatusAndCreatedAtBefore(
                eq(BookingStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(b1, b2));

        bookingService.releaseExpiredPendingBookings();

        assertThat(b1.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(b2.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(event.getAvailableSeats()).isEqualTo(52); // 2 seats restored in one batch
        verify(eventRepository).saveAll(any());
        verify(bookingRepository).saveAll(any());
    }

    // ── 10. Concurrency / Optimistic Locking ──────────────────────────────────

    @Test
    @DisplayName("Concurrency: Throws OptimisticLockingFailureException when two users book the exact same seat")
    void initiateBooking_concurrentModification_throwsOptimisticLock() {
        mockSecurityContext("attendee@test.com", false);
        when(userRepository.findByEmail("attendee@test.com")).thenReturn(Optional.of(attendee));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(bookingRepository.findByUserAndEvent(attendee, event)).thenReturn(List.of());

        doThrow(new ObjectOptimisticLockingFailureException(Event.class, event.getEventId()))
                .when(eventRepository).save(any(Event.class));

        assertThatThrownBy(() -> bookingService.initiateBooking(request, "PAYPAL-ORDER-RACE-CONDITION"))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class)
                .extracting(e -> (ObjectOptimisticLockingFailureException) e)
                .satisfies(e -> {
                    assertThat(e.getPersistentClass()).isEqualTo(Event.class);
                    assertThat(e.getIdentifier()).isEqualTo(event.getEventId());
                });
    }
}
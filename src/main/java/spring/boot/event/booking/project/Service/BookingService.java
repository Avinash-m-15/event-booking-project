package spring.boot.event.booking.project.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import spring.boot.event.booking.project.DTO.BookingRequest;
import spring.boot.event.booking.project.DTO.BookingResponse;
import spring.boot.event.booking.project.DTO.VerifyBookingResponse;
import spring.boot.event.booking.project.Entity.Booking;
import spring.boot.event.booking.project.Entity.Event;
import spring.boot.event.booking.project.Entity.User;
import spring.boot.event.booking.project.Mapper.BookingMapper;
import spring.boot.event.booking.project.Repository.BookingRepository;
import spring.boot.event.booking.project.Repository.EventRepository;
import spring.boot.event.booking.project.Repository.UserRepository;
import spring.boot.event.booking.project.enums.BookingStatus;
import spring.boot.event.booking.project.exception.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final PayPalService payPalService;

    @Transactional
    public BookingResponse initiateBooking(BookingRequest request, String payPalOrderId) {

        String loggedUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(loggedUserEmail)
                .orElseThrow(() -> new UserNotFoundException("Logged in user not found"));

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with id:" + request.getEventId()));

        if (event.getOrganizer().getEmail().equals(loggedUserEmail)) {
            throw new BookingFailedException("Organizers cannot book their own events");
        }

        List<Booking> existingBookings = bookingRepository.findByUserAndEvent(user, event);

        if (!existingBookings.isEmpty()) {
            Booking existing = existingBookings.get(0);

            if (existing.getStatus() == BookingStatus.CONFIRMED) {
                throw new BookingFailedException("You have already booked a ticket for this event");
            }

            if (existing.getStatus() == BookingStatus.PENDING) {
                existing.setPaymentReferenceId(payPalOrderId);
                return bookingMapper.toResponse(bookingRepository.save(existing));
            }

            if (existing.getStatus() == BookingStatus.FAILED || existing.getStatus() == BookingStatus.CANCELLED) {
                if (event.getAvailableSeats() <= 0) {
                    throw new BookingFailedException("Booking failed: No seats are available for this event");
                }
                event.setAvailableSeats(event.getAvailableSeats() - 1);

                existing.setStatus(BookingStatus.PENDING);
                existing.setPaymentReferenceId(payPalOrderId);
                existing.setCreatedAt(LocalDateTime.now());

                eventRepository.save(event);
                return bookingMapper.toResponse(bookingRepository.save(existing));
            }
        }

        if (event.getAvailableSeats() <= 0) {
            throw new BookingFailedException("Booking failed: No seats are available for this event");
        }
        event.setAvailableSeats(event.getAvailableSeats() - 1);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setEvent(event);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPaymentReferenceId(payPalOrderId);
        booking.setCreatedAt(LocalDateTime.now());

        eventRepository.save(event);
        Booking savedBooking = bookingRepository.save(booking);

        return bookingMapper.toResponse(savedBooking);
    }

    @Transactional
    public void confirmBookingAsynchronously(String payPalOrderId, String captureId) {

        boolean isActuallyPaid = payPalService.verifyOrderIsCompleted(payPalOrderId);

        if (!isActuallyPaid) {
            log.error("SECURITY ALERT: Received webhook for unpaid or fake Order ID: {}", payPalOrderId);
            return;
        }

        Optional<Booking> bookingOpt = bookingRepository.findByPaymentReferenceId(payPalOrderId);

        if (bookingOpt.isEmpty()) {
            log.error("WEBHOOK WARNING: Received PayPal payment for unknown Order ID: {}. Ignoring.", payPalOrderId);
            return;
        }

        Booking booking = bookingOpt.get();

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return;
        }

        if (booking.getStatus() == BookingStatus.FAILED || booking.getStatus() == BookingStatus.CANCELLED) {
            log.error("WEBHOOK WARNING: Payment received for FAILED/CANCELLED booking. Initiating Auto-Refund.");

            payPalService.refundCapture(captureId);
            return;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCaptureTransactionId(captureId);
        bookingRepository.save(booking);

        notificationService.sendBookingConfirmation(
                booking.getUser().getEmail(),
                booking.getUser().getUsername(),
                booking.getEvent().getEventName(),
                booking.getEvent().getTicketPrice() != null ? booking.getEvent().getTicketPrice().doubleValue() : 0.0,
                booking.getBookingId(),
                booking.getEvent().getEventDate(),
                booking.getEvent().getLocation()
        );
    }

    @Transactional
    public void failBookingAsynchronously(String payPalOrderId) {
        Optional<Booking> bookingOpt = bookingRepository.findByPaymentReferenceId(payPalOrderId);

        if (bookingOpt.isEmpty()) {
            return;
        }

        Booking booking = bookingOpt.get();

        if (booking.getStatus() != BookingStatus.PENDING) {
            return;
        }

        booking.setStatus(BookingStatus.FAILED);

        Event event = booking.getEvent();
        event.setAvailableSeats(event.getAvailableSeats() + 1);

        bookingRepository.save(booking);
        eventRepository.save(event);
    }

    public BookingResponse getBookingById(Long id) {

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found!"));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loggedUserEmail = authentication.getName();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if(!booking.getUser().getEmail().equals(loggedUserEmail) && !isAdmin) {

            throw new UnauthorizedAccessException("You do not have permission to view this booking");

        }
        return bookingMapper.toResponse(booking);
    }

    public List<BookingResponse> getMyBookings() {

        String loggedUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(loggedUserEmail)
                .orElseThrow(() -> new UserNotFoundException("Logged in user not found"));

        List<Booking> myBookings = bookingRepository.findByUser(user);

        return myBookings.stream()
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelBooking(Long id) {

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingFailedException("Booking not found!"));

        if (BookingStatus.CANCELLED.equals(booking.getStatus())) {
            throw new BookingFailedException("Booking is already cancelled!");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loggedUserEmail = authentication.getName();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if(!booking.getUser().getEmail().equals(loggedUserEmail) && !isAdmin) {
            throw new UnauthorizedAccessException("You cannot cancel booking!");
        }
        Event event = booking.getEvent();

        if(!event.isCancellable()) {
            throw new BookingFailedException("This event does not allow cancellations");
        }

        // NEW: Issue the API Refund
        if (booking.getCaptureTransactionId() != null) {
            boolean isRefunded = payPalService.refundCapture(booking.getCaptureTransactionId());
            if (!isRefunded) {
                throw new BookingFailedException("Failed to process refund with PayPal. Cancellation aborted.");
            }
        } else {
            log.info("No Capture ID found, proceeding with local cancellation.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        event.setAvailableSeats(event.getAvailableSeats() + 1);

        bookingRepository.save(booking);
        eventRepository.save(event);

        if (booking.getCaptureTransactionId() != null) {
            notificationService.sendCancellationEmail(
                    booking.getUser().getEmail(),
                    booking.getUser().getUsername(),
                    event.getEventName(),
                    event.getTicketPrice() != null ? event.getTicketPrice().doubleValue() : 0.0
            );
        }
    }

    public List<BookingResponse> getAttendeesForEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found!"));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loggedUserEmail = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if (!event.getOrganizer().getEmail().equals(loggedUserEmail) && !isAdmin) {
            throw new UnauthorizedAccessException("You do not have permission to view these attendees");
        }

        List<Booking> attendees = bookingRepository.findByEvent(event);

        return attendees.stream()
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
    }

    public VerifyBookingResponse verifyBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found!"));
        return bookingMapper.toVerifyResponse(booking);
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void releaseExpiredPendingBookings() {

        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);

        List<Booking> expiredBookings = bookingRepository.findByStatusAndCreatedAtBefore(
                BookingStatus.PENDING, tenMinutesAgo
        );

        if (expiredBookings.isEmpty()) {
            return;
        }

        Map<Long, Event> eventsToUpdate = new HashMap<>();

        for (Booking booking : expiredBookings) {
            booking.setStatus(BookingStatus.FAILED);

            Event event = booking.getEvent();
            Long eventId = event.getEventId();

            if (eventsToUpdate.containsKey(eventId)) {
                eventsToUpdate.get(eventId)
                        .setAvailableSeats(eventsToUpdate.get(eventId).getAvailableSeats() + 1);
            } else {
                event.setAvailableSeats(event.getAvailableSeats() + 1);
                eventsToUpdate.put(eventId, event);
            }
        }

        eventRepository.saveAll(eventsToUpdate.values());
        bookingRepository.saveAll(expiredBookings);

        log.info("CRON: Released {} expired pending bookings across {} events.",
                expiredBookings.size(), eventsToUpdate.size());
    }
}
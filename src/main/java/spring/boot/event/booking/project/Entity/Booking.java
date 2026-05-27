package spring.boot.event.booking.project.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import spring.boot.event.booking.project.enums.BookingStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_id"}))
@Data
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long bookingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    @Column(name = "booking_date", insertable = false, updatable = false)
    private LocalDateTime bookingDate;

    @Column(name = "payment_reference_id")
    private String paymentReferenceId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "capture_transaction_id")
    private String captureTransactionId;

}

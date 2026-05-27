package spring.boot.event.booking.project.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import spring.boot.event.booking.project.Entity.Booking;
import spring.boot.event.booking.project.Entity.Event;
import spring.boot.event.booking.project.Entity.User;
import spring.boot.event.booking.project.enums.BookingStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByUserAndEvent(User user, Event event);

    List<Booking> findByUserAndEvent(User user, Event event);

    List<Booking> findByUser(User user);

    List<Booking> findByEvent(Event event);

    Optional<Booking> findByPaymentReferenceId(String paymentReferenceId);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime time);

}

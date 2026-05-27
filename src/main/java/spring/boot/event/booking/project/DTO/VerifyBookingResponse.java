package spring.boot.event.booking.project.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import spring.boot.event.booking.project.enums.BookingStatus;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerifyBookingResponse {
    private Long bookingId;
    private String eventName;
    private LocalDateTime eventDate;
    private String location;
    private BookingStatus status;
}
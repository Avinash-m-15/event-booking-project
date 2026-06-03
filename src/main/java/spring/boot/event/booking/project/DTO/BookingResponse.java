package spring.boot.event.booking.project.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import spring.boot.event.booking.project.enums.BookingStatus;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingResponse {
    private Long userId;
    private String username;
    private String email;
    private Long eventId;
    private String eventName;
    private LocalDateTime eventDate;
    private String location;
    private double ticketPrice;
    private Long bookingId;
    private BookingStatus status;
    private LocalDateTime bookingDate;
    @JsonProperty("isCancellable")
    private boolean cancellable;
    private boolean checkedIn;
}

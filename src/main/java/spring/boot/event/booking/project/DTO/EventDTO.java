package spring.boot.event.booking.project.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventDTO implements Serializable {

    private Long eventId;
    @NotBlank(message = "Event name is mandatory")
    private String eventName;

    private String description;
    private String location;

    @NotNull(message = "Total seats must be specified")
    @Min(value = 1, message = "Total seats must be at least 1")
    private LocalDateTime eventDate;

    @NotNull(message = "Total seats must be specified")
    @Min(value = 1, message = "Total seats must be at least 1")
    private Integer totalSeats;

    private Integer availableSeats;
    private BigDecimal ticketPrice;
    private String organizerName;

    @JsonProperty("isCancellable")
    private boolean cancellable;

    private String imageUrl;

}

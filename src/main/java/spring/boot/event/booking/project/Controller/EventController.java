package spring.boot.event.booking.project.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import spring.boot.event.booking.project.DTO.BookingResponse;
import spring.boot.event.booking.project.DTO.EventDTO;
import spring.boot.event.booking.project.DTO.PageResponse;
import spring.boot.event.booking.project.Service.BookingService;
import spring.boot.event.booking.project.Service.EventService;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final BookingService bookingService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventDTO> createEvent(
            @RequestPart("event") @Valid EventDTO eventDTO,
            @RequestPart(value = "image", required = false) MultipartFile image) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(eventDTO, image));
    }

    @GetMapping
    public ResponseEntity<PageResponse<EventDTO>> getAllEvents(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("eventDate").ascending());

        if(search != null && !search.isBlank()) {
            return ResponseEntity.ok(eventService.searchEvents(search, pageable));
        }
        return ResponseEntity.ok(eventService.getAllEvents(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.findEventById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEventById(@PathVariable Long id) {
        eventService.deleteEventById(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventDTO> updateEvent(
            @PathVariable Long id,
            @RequestPart("event") @Valid EventDTO eventDTO,
            @RequestPart(value = "image", required = false) MultipartFile image) throws Exception {
        return ResponseEntity.ok(eventService.updateEvent(id, eventDTO, image));
    }

    @GetMapping("/{eventId}/export-attendees")
    public void exportAttendeesToCSV(@PathVariable Long eventId, HttpServletResponse response) throws IOException {

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"attendees_event_" + eventId + ".csv\"");

        List<BookingResponse> attendees = bookingService.getAttendeesForEvent(eventId);

        PrintWriter writer = response.getWriter();

        writer.println("Booking ID,Attendee Name,Email,Ticket Status,Booking Date");

        for (BookingResponse attendee : attendees) {
            writer.println(
                    attendee.getBookingId() + "," +
                            attendee.getUsername() + "," +
                            attendee.getEmail() + "," +
                            attendee.getStatus() + "," +
                            attendee.getBookingDate()
            );
        }

        writer.flush();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}

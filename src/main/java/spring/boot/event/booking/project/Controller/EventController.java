package spring.boot.event.booking.project.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    @PostMapping
    public ResponseEntity<EventDTO> createEvent(@Valid @RequestBody EventDTO eventDTO ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(eventDTO));
    }

    @GetMapping
    public ResponseEntity<PageResponse<EventDTO>> getAllEvents(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("eventDate").ascending());

        if(search != null && !search.isBlank()) {
            // Make sure your service method also returns PageResponseDTO!
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

    @PutMapping("/{id}")
    public ResponseEntity<EventDTO> updateEvent(@PathVariable Long id, @Valid @RequestBody EventDTO eventDTO) {
        return ResponseEntity.ok(eventService.updateEvent(id, eventDTO));
    }

    @GetMapping("/{eventId}/export-attendees")
    public void exportAttendeesToCSV(@PathVariable Long eventId, HttpServletResponse response) throws IOException {

        // 1. Set the correct HTTP Headers so the browser knows it is receiving a file download
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"attendees_event_" + eventId + ".csv\"");

        // 2. Fetch the data using the method you already wrote in BookingService
        List<BookingResponse> attendees = bookingService.getAttendeesForEvent(eventId);

        // 3. Open a PrintWriter to stream data directly to the client
        PrintWriter writer = response.getWriter();

        // Write the CSV Column Headers
        writer.println("Booking ID,Attendee Name,Email,Ticket Status,Booking Date");

        // Write the data rows
        for (BookingResponse attendee : attendees) {
            writer.println(
                    attendee.getBookingId() + "," +
                            attendee.getUsername() + "," +
                            attendee.getEmail() + "," +
                            attendee.getStatus() + "," +
                            attendee.getBookingDate()
            );
        }

        // Flush the writer to ensure all data is sent out over the network
        writer.flush();
    }
}

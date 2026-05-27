package spring.boot.event.booking.project.Exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import spring.boot.event.booking.project.DTO.ErrorResponse;
import spring.boot.event.booking.project.exception.EventNotFoundException;
import spring.boot.event.booking.project.exception.GlobalExceptionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void handleEventNotFound_ShouldReturn404AndCleanMessage() {
        // Arrange
        EventNotFoundException exception = new EventNotFoundException("Event ID 500 not found");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleEventNotFoundException(exception);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Event ID 500 not found", response.getBody().getMessage());
        assertEquals(404, response.getBody().getStatus());
    }

    @Test
    void handleGenericException_ShouldReturn500AndHideStackTrace() {
        // Arrange: A terrifying database crash or null pointer
        NullPointerException criticalCrash = new NullPointerException("Database completely offline");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(criticalCrash);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());

        // Critical: Ensure the frontend gets a generic message, NOT the "Database completely offline" string
        assertEquals("An unexpected error occurred on the server!", response.getBody().getMessage());
    }
}
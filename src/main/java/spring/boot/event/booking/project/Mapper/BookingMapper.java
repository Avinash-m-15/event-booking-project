package spring.boot.event.booking.project.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import spring.boot.event.booking.project.DTO.BookingRequest;
import spring.boot.event.booking.project.DTO.BookingResponse;
import spring.boot.event.booking.project.DTO.VerifyBookingResponse;
import spring.boot.event.booking.project.Entity.Booking;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "userId", source = "user.userId")
    @Mapping(target = "eventId", source = "event.eventId")
    @Mapping(target = "cancellable", source = "event.cancellable")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "eventName", source = "event.eventName")
    @Mapping(target = "eventDate", source = "event.eventDate")
    @Mapping(target = "location", source = "event.location")
    @Mapping(target = "ticketPrice", source = "event.ticketPrice")
    BookingResponse toResponse(Booking booking);

    @Mapping(target = "bookingId", source = "bookingId")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "eventName", source = "event.eventName")
    @Mapping(target = "eventDate", source = "event.eventDate")
    @Mapping(target = "location", source = "event.location")
    VerifyBookingResponse toVerifyResponse(Booking booking);

}
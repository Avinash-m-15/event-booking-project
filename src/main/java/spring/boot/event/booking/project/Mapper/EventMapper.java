package spring.boot.event.booking.project.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import spring.boot.event.booking.project.DTO.EventDTO;
import spring.boot.event.booking.project.Entity.Event;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "organizerName", source = "organizer.username")
    EventDTO toDTO(Event event);

    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "cancellable", source = "cancellable")
    Event toEntity(EventDTO eventDTO);

    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "organizer", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "cancellable", source = "cancellable")
    void updateEntityFromDTO(EventDTO dto, @MappingTarget Event event);
}

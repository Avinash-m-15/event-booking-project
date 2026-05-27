package spring.boot.event.booking.project.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import spring.boot.event.booking.project.DTO.EventDTO;
import spring.boot.event.booking.project.DTO.PageResponse;
import spring.boot.event.booking.project.Entity.Event;
import spring.boot.event.booking.project.Entity.User;
import spring.boot.event.booking.project.Mapper.EventMapper;
import spring.boot.event.booking.project.Repository.EventRepository;
import spring.boot.event.booking.project.Repository.UserRepository;
import spring.boot.event.booking.project.exception.EventNotFoundException;
import spring.boot.event.booking.project.exception.UnauthorizedAccessException;
import spring.boot.event.booking.project.exception.UserNotFoundException;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final UserRepository userRepository;

    @Transactional
    public EventDTO createEvent(EventDTO eventDTO) {

        String loggedUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User organizer = userRepository.findByEmail(loggedUserEmail)
                .orElseThrow(() -> new UserNotFoundException("Logged in user not found in database!"));

        Event event = eventMapper.toEntity(eventDTO);
        event.setOrganizer(organizer);

        if(event.getAvailableSeats() == null) {
            event.setAvailableSeats(event.getTotalSeats());
        }

        Event savedEvent = eventRepository.save(event);

        return eventMapper.toDTO(savedEvent);
    }

    @Cacheable(value = "events", key = "#pageable.pageNumber + '_' + #pageable.pageSize", sync = true)
    public PageResponse<EventDTO> getAllEvents(Pageable pageable) {
        // 1. Fetch from DB
        Page<Event> page = eventRepository.findAll(pageable);

        // 2. Convert Event to EventDTO
        Page<EventDTO> dtoPage = page.map(eventMapper::toDTO);

        // 3. Wrap in our custom Serializable DTO and return!
        return new PageResponse<>(dtoPage);
    }

    @Cacheable(value = "single_event", key = "#id", sync = true)
    public EventDTO findEventById(Long id) {

        Event event = eventRepository.findById(id)
                      .orElseThrow(() -> new EventNotFoundException("Event not found with id" + id));

        return eventMapper.toDTO(event);
    }

    @Transactional
    @CacheEvict(value = "single_event", key = "#id")
    public EventDTO updateEvent(Long id, EventDTO eventDTO) {

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found with id: " + id));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loggedUserEmail = authentication.getName();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if (!event.getOrganizer().getEmail().equals(loggedUserEmail) && !isAdmin) {
            throw new UnauthorizedAccessException("You do not have permission to update this event");
        }

        eventMapper.updateEntityFromDTO(eventDTO, event);
        Event updatedEvent = eventRepository.save(event);

        return eventMapper.toDTO(updatedEvent);
    }

    @Transactional
    @CacheEvict(value = "single_event", key = "#id")
    public void deleteEventById(Long id) {

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Cannot delete Event with id:" + id + "because Event does not exist!"));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loggedUserEmail = authentication.getName();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if(!event.getOrganizer().getEmail().equals(loggedUserEmail) && !isAdmin) {
            throw new UnauthorizedAccessException("You do not have permission to delete this event");
        }
        eventRepository.deleteById(id);
    }

    @Cacheable(value = "events", key = "'search-' + #query + '-' + #pageable.pageNumber + '-' + #pageable.pageSize", sync = true)
    public PageResponse<EventDTO> searchEvents(String query, Pageable pageable) {

        // 1. Fetch from DB and map to DTO in one clean chain
        Page<EventDTO> dtoPage = eventRepository
                .findByEventNameContainingIgnoreCaseOrLocationContainingIgnoreCase(query, query, pageable)
                .map(eventMapper::toDTO);

        // 2. Wrap it in our custom Serializable DTO to prevent Redis crashes
        return new PageResponse<>(dtoPage);
    }
}
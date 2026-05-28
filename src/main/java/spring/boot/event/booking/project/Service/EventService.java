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
import org.springframework.web.multipart.MultipartFile;
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
    private final SupabaseStorageService storageService;

    @Transactional
    public EventDTO createEvent(EventDTO eventDTO, MultipartFile image) throws Exception {
        String loggedUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User organizer = userRepository.findByEmail(loggedUserEmail)
                .orElseThrow(() -> new UserNotFoundException("Logged in user not found in database!"));

        if (image != null && !image.isEmpty()) {
            String imageUrl = storageService.uploadImage(image);
            eventDTO.setImageUrl(imageUrl);
        }

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

        Page<Event> page = eventRepository.findAll(pageable);

        Page<EventDTO> dtoPage = page.map(eventMapper::toDTO);

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
    public EventDTO updateEvent(Long id, EventDTO eventDTO, MultipartFile image) throws Exception {

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found with id: " + id));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loggedUserEmail = authentication.getName();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if (!event.getOrganizer().getEmail().equals(loggedUserEmail) && !isAdmin) {
            throw new UnauthorizedAccessException("You do not have permission to update this event");
        }

        if (image != null && !image.isEmpty()) {
            String imageUrl = storageService.uploadImage(image);
            eventDTO.setImageUrl(imageUrl);
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

        Page<EventDTO> dtoPage = eventRepository
                .findByEventNameContainingIgnoreCaseOrLocationContainingIgnoreCase(query, query, pageable)
                .map(eventMapper::toDTO);

        return new PageResponse<>(dtoPage);
    }
}
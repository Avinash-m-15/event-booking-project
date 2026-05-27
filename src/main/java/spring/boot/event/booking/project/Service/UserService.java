package spring.boot.event.booking.project.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import spring.boot.event.booking.project.DTO.UserRequest;
import spring.boot.event.booking.project.DTO.UserResponse;
import spring.boot.event.booking.project.Entity.User;
import spring.boot.event.booking.project.Mapper.UserMapper;
import spring.boot.event.booking.project.Repository.UserRepository;
import spring.boot.event.booking.project.exception.UserNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse registerUser(UserRequest userRequest) {

        User user = userMapper.toEntity(userRequest);

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);

    }

    public UserResponse findUserById(Long id) {

        User user = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException("User not found with id:" + id));
        return userMapper.toResponse(user);

    }

    public List<UserResponse> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());

    }

    public UserResponse getCurrentUser() {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userMapper.toResponse(userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found!")));
    }
}

package spring.boot.event.booking.project.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import spring.boot.event.booking.project.DTO.*;
import spring.boot.event.booking.project.Entity.PasswordResetToken;
import spring.boot.event.booking.project.Entity.User;
import spring.boot.event.booking.project.Repository.PasswordResetTokenRepository;
import spring.boot.event.booking.project.Repository.UserRepository;
import spring.boot.event.booking.project.Security.CustomUserDetails;
import spring.boot.event.booking.project.Security.JwtService;
import spring.boot.event.booking.project.exception.UserNotFoundException;

import java.security.SecureRandom;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final PasswordResetTokenRepository tokenRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse registerUser(UserRequest request) {

        userService.registerUser(request);
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found!"));

        log.info("SECURITY: New user registered successfully with email: {}", request.getEmail());

        var jwt = jwtService.generateToken(new CustomUserDetails(user));
        return new AuthResponse(jwt);

    }

    public AuthResponse login(AuthRequest request) {

        try{
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
                    request.getPassword()
            ));
        }catch (Exception e) {
            log.warn("SECURITY WARNING: Failed login attempt for email: {}", request.getEmail());
            throw e;
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found!"));
        var jwt = jwtService.generateToken(new CustomUserDetails(user));

        return new AuthResponse(jwt);
    }

    public void processForgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + request.getEmail()));

        SecureRandom secureRandom = new SecureRandom();
        String otp = String.format("%06d", secureRandom.nextInt(1000000));

        // Create and save the token entity
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(otp);
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(15)); // Expires in 15 mins

        tokenRepository.save(resetToken);

        log.info("SECURITY: Password reset OTP generated and emailed to: {}", request.getEmail());

        // Send the email asynchronously
        notificationService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), otp);
    }

    public void resetPassword(ResetPasswordRequest request) {
        // Find the token in the DB
        PasswordResetToken resetToken = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> {
                            log.warn("SECURITY WARNING: Attempt to reset password with INVALID token.");
                            return new RuntimeException("Invalid token!");
                });

        // Check if it's expired
        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken); // Clean up dead token
            log.warn("SECURITY WARNING: Attempt to reset password with EXPIRED token for user: {}", resetToken.getUser().getEmail());
            throw new RuntimeException("Token has expired. Please request a new one.");
        }

        // Update the user's password
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Delete the token so it can't be used again
        tokenRepository.delete(resetToken);

        log.info("SECURITY: Password successfully reset for user: {}", user.getEmail());
    }
}

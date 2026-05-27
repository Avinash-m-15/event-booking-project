package spring.boot.event.booking.project.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Email is required to reset password")
    @Email(message = "Please provide a valid email format")
    private String email;
}

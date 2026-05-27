package spring.boot.event.booking.project.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import spring.boot.event.booking.project.Entity.PasswordResetToken;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // We need this to find the token when the user submits their new password
    Optional<PasswordResetToken> findByToken(String token);
}

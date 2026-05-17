package lynx.team2.repository;

import lynx.team2.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailVerificationToken(String token);

    Optional<User> findByPasswordResetToken(String token);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByBotSubscribedUntilAfter(LocalDateTime dateTime);

    boolean existsByBotSubscribedUntilAfterAndBotRunningTrue(LocalDateTime dateTime);

    java.util.List<User> findByBotSubscribedUntilAfterAndBotRunningTrue(LocalDateTime dateTime);
}

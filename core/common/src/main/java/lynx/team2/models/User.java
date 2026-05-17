package lynx.team2.models;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.IdGeneratorType;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class User{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(unique = true, nullable=false)
    private String username;

    @Column(unique = true, nullable=false)
    private String email;

    @Column(nullable=false)
    private String password;

    @Column(name = "platform_user_id")
    private Long platformUserId;

    @Column(nullable = false)
    private boolean emailVerified = false;

    private String emailVerificationToken;

    private LocalDateTime emailVerificationTokenExpiresAt;

    private String passwordResetToken;

    private LocalDateTime passwordResetTokenExpiresAt;

    @Column(name = "bot_subscribed_until")
    private LocalDateTime botSubscribedUntil;

    @Column(name = "bot_running", nullable = false, columnDefinition = "boolean default false")
    private boolean botRunning = false;

}

package lynx.team2.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lynx.team2.models.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getUserId().toString())
                .claim("username", user.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + 1000 * 60 * 60 * 24))
                .signWith(key)
                .compact();
    }
}

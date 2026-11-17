package com.airlinetracker.llmsummary.service;

import com.airlinetracker.llmsummary.dto.AuthResponse;
import com.airlinetracker.llmsummary.dto.LoginRequest;
import com.airlinetracker.llmsummary.dto.RegisterRequest;
import com.airlinetracker.llmsummary.entity.User;
import com.airlinetracker.llmsummary.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Service for user authentication and authorization.
 * Handles registration, login, and JWT token generation.
 */
@Slf4j
@Service
@Profile("accounts")
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${jwt.secret:AirlineTrackerSecretKeyForJWTTokenSigning2024!@#$%^&*()}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400}")
    private long jwtExpiration;

    /**
     * Register a new user.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully: {}", user.getUsername());

        return buildAuthResponse(user);
    }

    /**
     * Authenticate a user and return a JWT token.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        log.info("User logged in successfully: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    /**
     * Build an AuthResponse with a JWT token.
     */
    private AuthResponse buildAuthResponse(User user) {
        String token = generateToken(user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .expiresIn(jwtExpiration)
                .build();
    }

    /**
     * Generate a JWT token for the given username.
     */
    private String generateToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(username)
                .claims(Map.of("username", username))
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(jwtExpiration)))
                .signWith(key)
                .compact();
    }
}

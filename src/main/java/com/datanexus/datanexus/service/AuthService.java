package com.datanexus.datanexus.service;

import com.datanexus.datanexus.dto.UserDto;
import com.datanexus.datanexus.dto.auth.*;
import com.datanexus.datanexus.entity.RefreshToken;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.exception.ApiException;
import com.datanexus.datanexus.repository.RefreshTokenRepository;
import com.datanexus.datanexus.repository.UserRepository;
import com.datanexus.datanexus.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername());

        if (user == null) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
        }

        user.setLastLogin(Instant.now());
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .user(toUserDto(user))
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isNewUser(false)
                .build();
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()) != null) {
            throw ApiException.conflict("Username already exists");
        }
        if (userRepository.findByEmail(request.getEmail()) != null) {
            throw ApiException.conflict("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        user = userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .user(toUserDto(user))
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isNewUser(true)
                .build();
    }

    public UserDto getUserDetails(User user) {
        UserDto dto = toUserDto(user);
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("theme", user.getPreferredTheme() != null ? user.getPreferredTheme() : "light");
        if (user.getDefaultConnectionId() != null) {
            preferences.put("defaultConnectionId", user.getDefaultConnectionId());
        }
        dto.setPreferences(preferences);
        return dto;
    }

    public TokenResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenAndNotRevoked(request.getRefreshToken());

        if (storedToken == null) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Invalid refresh token");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("TOKEN_EXPIRED", "Refresh token has expired");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String newRefreshToken = createRefreshToken(user);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    public void logout(User user) {
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    private String createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(Instant.now().plus(refreshTokenExpiration, ChronoUnit.MILLIS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }
}

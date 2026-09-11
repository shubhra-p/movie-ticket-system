package com.shubh.movietickets.users.service;

import com.shubh.movietickets.common.exception.ConflictException;
import com.shubh.movietickets.common.exception.ResourceNotFoundException;
import com.shubh.movietickets.users.dto.AuthResponse;
import com.shubh.movietickets.users.dto.LoginRequest;
import com.shubh.movietickets.users.dto.RegisterRequest;
import com.shubh.movietickets.users.dto.UserDto;
import com.shubh.movietickets.users.entity.Role;
import com.shubh.movietickets.users.entity.User;
import com.shubh.movietickets.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = User.builder()
                .id(testUserId)
                .name("Test User")
                .email("test@example.com")
                .passwordHash("hashed_password")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("Register - success")
    void register_success() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password123", null);

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any(UUID.class), anyString(), anyString())).thenReturn("jwt-token");

        AuthResponse response = userService.register(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Register - duplicate email throws ConflictException")
    void register_duplicateEmail_throws() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password123", null);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login - success")
    void login_success() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtService.generateToken(any(UUID.class), anyString(), anyString())).thenReturn("jwt-token");

        AuthResponse response = userService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Login - wrong password throws")
    void login_wrongPassword_throws() {
        LoginRequest request = new LoginRequest("test@example.com", "wrong");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "hashed_password")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid password");
    }

    @Test
    @DisplayName("Login - user not found throws")
    void login_userNotFound_throws() {
        LoginRequest request = new LoginRequest("missing@example.com", "password");

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get profile - success")
    void getProfile_success() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        UserDto profile = userService.getProfile(testUserId);

        assertThat(profile.getName()).isEqualTo("Test User");
        assertThat(profile.getEmail()).isEqualTo("test@example.com");
        assertThat(profile.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Get profile - not found throws")
    void getProfile_notFound_throws() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

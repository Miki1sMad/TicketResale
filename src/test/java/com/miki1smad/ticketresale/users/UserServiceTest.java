package com.miki1smad.ticketresale.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void shouldRegisterUserWithHashedPassword() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.registerUser("test@example.com", "secret123", "John", "Doe", Role.USER);

        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getPassword()).isEqualTo("hashed-secret");
        assertThat(user.getFirstName()).isEqualTo("John");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getRole()).isEqualTo(Role.USER);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed-secret");
    }

    @Test
    void shouldThrowExceptionWhenRegisteringExistingEmail() {
        when(userRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser("duplicate@example.com", "pass", "A", "B", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldFindUserByEmail() {
        User user = User.builder().email("find@example.com").build();
        when(userRepository.findByEmail("find@example.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByEmail("find@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("find@example.com");
    }
}

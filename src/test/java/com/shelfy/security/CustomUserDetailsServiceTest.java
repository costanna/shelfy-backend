package com.shelfy.security;

import com.shelfy.user.User;
import com.shelfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_returnsAPrincipalForAMatchingEmail() {
        when(userRepository.findByEmailIgnoreCase("Lectora@Shelfy.app"))
                .thenReturn(Optional.of(User.builder().id(1L).email("lectora@shelfy.app").build()));

        UserPrincipal principal = (UserPrincipal) service.loadUserByUsername("Lectora@Shelfy.app");

        assertThat(principal.getId()).isEqualTo(1L);
    }

    @Test
    void loadUserByUsername_throwsWhenNoAccountMatchesTheEmail() {
        when(userRepository.findByEmailIgnoreCase("ghost@shelfy.app")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost@shelfy.app"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserById_returnsAPrincipalForAMatchingId() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).build()));

        UserPrincipal principal = (UserPrincipal) service.loadUserById(7L);

        assertThat(principal.getId()).isEqualTo(7L);
    }

    @Test
    void loadUserById_throwsWhenNoAccountMatchesTheId() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserById(99L)).isInstanceOf(UsernameNotFoundException.class);
    }
}

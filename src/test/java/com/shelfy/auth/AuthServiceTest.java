package com.shelfy.auth;

import com.shelfy.auth.dto.AuthResponse;
import com.shelfy.auth.dto.EmailRequest;
import com.shelfy.auth.dto.LoginRequest;
import com.shelfy.auth.dto.RegisterRequest;
import com.shelfy.auth.dto.RegisterResponse;
import com.shelfy.auth.dto.ResetPasswordRequest;
import com.shelfy.category.CategoryService;
import com.shelfy.common.dto.MessageResponse;
import com.shelfy.common.exception.DuplicateResourceException;
import com.shelfy.common.exception.EmailNotVerifiedException;
import com.shelfy.common.exception.InvalidTokenException;
import com.shelfy.mail.EmailService;
import com.shelfy.security.JwtService;
import com.shelfy.security.UserPrincipal;
import com.shelfy.user.User;
import com.shelfy.user.UserMapper;
import com.shelfy.user.UserRepository;
import com.shelfy.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "lectora@shelfy.app";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private EmailService emailService;
    @Mock
    private CategoryService categoryService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, userMapper, passwordEncoder, jwtService,
                authenticationManager, emailService, categoryService);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userMapper.toResponse(any(User.class)))
                .thenReturn(new UserResponse(USER_ID, EMAIL, "Lectora", null, null, null, null, true, 9));
        lenient().when(jwtService.generateToken(any(), any(), anyInt())).thenReturn("jwt-token");
    }

    private void setRequireVerification(boolean value) {
        ReflectionTestUtils.setField(service, "requireEmailVerification", value);
    }

    private User savedUser(boolean verified) {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .name("Lectora")
                .password("hash")
                .emailVerified(verified)
                .tokenVersion(0)
                .build();
    }

    // --- register ---

    @Test
    void register_throwsWhenEmailAlreadyRegistered() {
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest(EMAIL, "password123", "Lectora")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withVerificationRequired_createsUnverifiedUserAndSendsVerificationEmail() {
        setRequireVerification(true);
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        RegisterResponse response = service.register(new RegisterRequest(EMAIL, "password123", "Lectora"));

        assertThat(response.requiresVerification()).isTrue();
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getVerificationToken()).isNotBlank();
        assertThat(saved.getVerificationTokenExpiresAt()).isAfter(Instant.now());
        verify(emailService).sendVerificationEmail(eq(EMAIL), eq("Lectora"), anyString());
        verify(categoryService).seedMissingDefaults(saved);
    }

    @Test
    void register_withoutVerificationRequired_createsVerifiedUserAndSendsNoEmail() {
        setRequireVerification(false);
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        RegisterResponse response = service.register(new RegisterRequest(EMAIL, "password123", "Lectora"));

        assertThat(response.requiresVerification()).isFalse();
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    // --- login ---

    private Authentication authenticationFor(User user) {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        UserPrincipal principal = UserPrincipal.from(user);
        lenient().when(authentication.getPrincipal()).thenReturn(principal);
        return authentication;
    }

    @Test
    void login_throwsWhenCredentialsAreInvalid() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("nope"));

        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_throwsWhenVerificationRequiredAndEmailNotVerified() {
        setRequireVerification(true);
        User user = savedUser(false);
        Authentication authentication = authenticationFor(user);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "password123")))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void login_succeedsWhenVerificationRequiredAndEmailIsVerified() {
        setRequireVerification(true);
        User user = savedUser(true);
        Authentication authentication = authenticationFor(user);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(jwtService.getExpirationMillis()).thenReturn(86_400_000L);

        AuthResponse response = service.login(new LoginRequest(EMAIL, "password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        verify(categoryService).seedMissingDefaults(user);
    }

    @Test
    void login_succeedsForUnverifiedUserWhenVerificationNotRequired() {
        setRequireVerification(false);
        User user = savedUser(false);
        Authentication authentication = authenticationFor(user);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(jwtService.getExpirationMillis()).thenReturn(86_400_000L);

        AuthResponse response = service.login(new LoginRequest(EMAIL, "password123"));

        assertThat(response).isNotNull();
    }

    // --- verifyEmail ---

    @Test
    void verifyEmail_throwsWhenTokenDoesNotExist() {
        when(userRepository.findByVerificationToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail("bad-token")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyEmail_throwsWhenTokenHasExpired() {
        User user = savedUser(false);
        user.setVerificationToken("token");
        user.setVerificationTokenExpiresAt(Instant.now().minusSeconds(60));
        when(userRepository.findByVerificationToken("token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verifyEmail("token")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyEmail_marksUserAsVerifiedAndClearsTheToken() {
        User user = savedUser(false);
        user.setVerificationToken("token");
        user.setVerificationTokenExpiresAt(Instant.now().plusSeconds(60));
        when(userRepository.findByVerificationToken("token")).thenReturn(Optional.of(user));
        when(jwtService.getExpirationMillis()).thenReturn(86_400_000L);

        service.verifyEmail("token");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getVerificationToken()).isNull();
        assertThat(user.getVerificationTokenExpiresAt()).isNull();
    }

    // --- resendVerification ---

    @Test
    void resendVerification_sendsNewTokenWhenUserExistsAndIsNotVerified() {
        User user = savedUser(false);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        MessageResponse response = service.resendVerification(new EmailRequest(EMAIL));

        assertThat(response.message()).isNotBlank();
        assertThat(user.getVerificationToken()).isNotBlank();
        verify(emailService).sendVerificationEmail(eq(EMAIL), eq("Lectora"), anyString());
    }

    @Test
    void resendVerification_doesNothingWhenUserIsAlreadyVerified() {
        User user = savedUser(true);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        service.resendVerification(new EmailRequest(EMAIL));

        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    @Test
    void resendVerification_doesNothingWhenNoAccountMatchesTheEmail_butDoesNotLeakThat() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        MessageResponse response = service.resendVerification(new EmailRequest(EMAIL));

        assertThat(response.message()).isNotBlank();
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    // --- forgotPassword ---

    @Test
    void forgotPassword_sendsResetEmailWhenAccountExists() {
        User user = savedUser(true);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        service.forgotPassword(new EmailRequest(EMAIL));

        assertThat(user.getPasswordResetToken()).isNotBlank();
        verify(emailService).sendPasswordResetEmail(eq(EMAIL), eq("Lectora"), anyString());
    }

    @Test
    void forgotPassword_doesNothingWhenNoAccountMatchesTheEmail() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        service.forgotPassword(new EmailRequest(EMAIL));

        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    // --- resetPassword ---

    @Test
    void resetPassword_throwsWhenTokenDoesNotExist() {
        when(userRepository.findByPasswordResetToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("bad", "newpassword1")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void resetPassword_throwsWhenTokenHasExpired() {
        User user = savedUser(true);
        user.setPasswordResetToken("token");
        user.setPasswordResetTokenExpiresAt(Instant.now().minusSeconds(60));
        when(userRepository.findByPasswordResetToken("token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("token", "newpassword1")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void resetPassword_updatesPasswordClearsTokenAndBumpsTokenVersion() {
        User user = savedUser(true);
        user.setPasswordResetToken("token");
        user.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(60));
        user.setTokenVersion(3);
        when(userRepository.findByPasswordResetToken("token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-hash");

        service.resetPassword(new ResetPasswordRequest("token", "newpassword1"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getPasswordResetToken()).isNull();
        assertThat(user.getPasswordResetTokenExpiresAt()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(4);
    }
}

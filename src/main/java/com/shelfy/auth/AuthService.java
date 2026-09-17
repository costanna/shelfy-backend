package com.shelfy.auth;

import com.shelfy.auth.dto.AuthResponse;
import com.shelfy.auth.dto.EmailRequest;
import com.shelfy.auth.dto.LoginRequest;
import com.shelfy.auth.dto.RegisterRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);

    private static final MessageResponse RESEND_ACK =
            new MessageResponse("Si la cuenta existe y no está verificada, te hemos enviado un nuevo email.");
    private static final MessageResponse FORGOT_PASSWORD_ACK =
            new MessageResponse("Si la cuenta existe, te hemos enviado un email para restablecer la contraseña.");

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final CategoryService categoryService;

    // ⚠️ VERIFICACIÓN POR EMAIL DESACTIVADA A PROPÓSITO — NO BORRAR ESTE BLOQUE.
    // El envío de emails todavía no funciona en producción, así que se ha forzado
    // a "false" directamente en el código (no solo con la variable de entorno
    // REQUIRE_EMAIL_VERIFICATION), para que no dependa de la configuración de Render.
    // Para reactivar la verificación en el futuro:
    //   1. Descomenta la línea @Value de abajo y borra la inicialización "= false".
    //   2. Descomenta los bloques marcados con el mismo aviso en register() y login().
    // No elimines este campo ni los métodos verifyEmail()/resendVerification():
    // AuthController y EmailService siguen dependiendo de ellos para compilar.
    // @Value("${shelfy.registration.require-email-verification}")
    private boolean requireEmailVerification = false;

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Ya existe una cuenta con ese email");
        }

        User.UserBuilder builder = User.builder()
                .email(request.email().toLowerCase())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .emailVerified(!requireEmailVerification);

        // ⚠️ NO BORRAR — rama de envío de email de verificación, desactivada mientras
        // requireEmailVerification esté forzado a false más arriba.
        // if (requireEmailVerification) {
        //     String token = UUID.randomUUID().toString();
        //     builder.verificationToken(token)
        //             .verificationTokenExpiresAt(Instant.now().plus(VERIFICATION_TOKEN_TTL));
        //
        //     User user = userRepository.save(builder.build());
        //     categoryService.seedMissingDefaults(user);
        //     emailService.sendVerificationEmail(user.getEmail(), user.getName(), token);
        //     return new MessageResponse("Te hemos enviado un email para verificar tu cuenta.");
        // }

        User user = userRepository.save(builder.build());
        categoryService.seedMissingDefaults(user);
        return new MessageResponse("Cuenta creada. Ya puedes iniciar sesión.");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.getReferenceById(principal.getId());

        // ⚠️ NO BORRAR — comprobación de cuenta verificada, desactivada mientras
        // requireEmailVerification esté forzado a false más arriba.
        // if (requireEmailVerification && !user.isEmailVerified()) {
        //     throw new EmailNotVerifiedException();
        // }

        categoryService.seedMissingDefaults(user);

        return buildResponse(user);
    }

    @Transactional
    public AuthResponse verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new InvalidTokenException("El enlace de verificación no es válido"));

        if (user.getVerificationTokenExpiresAt() == null
                || user.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("El enlace de verificación ha caducado");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);

        return buildResponse(user);
    }

    @Transactional
    public MessageResponse resendVerification(EmailRequest request) {
        userRepository.findByEmailIgnoreCase(request.email())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    String token = UUID.randomUUID().toString();
                    user.setVerificationToken(token);
                    user.setVerificationTokenExpiresAt(Instant.now().plus(VERIFICATION_TOKEN_TTL));
                    emailService.sendVerificationEmail(user.getEmail(), user.getName(), token);
                });

        return RESEND_ACK;
    }

    @Transactional
    public MessageResponse forgotPassword(EmailRequest request) {
        userRepository.findByEmailIgnoreCase(request.email())
                .ifPresent(user -> {
                    String token = UUID.randomUUID().toString();
                    user.setPasswordResetToken(token);
                    user.setPasswordResetTokenExpiresAt(Instant.now().plus(RESET_TOKEN_TTL));
                    emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), token);
                });

        return FORGOT_PASSWORD_ACK;
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.token())
                .orElseThrow(() -> new InvalidTokenException("El enlace para restablecer la contraseña no es válido"));

        if (user.getPasswordResetTokenExpiresAt() == null
                || user.getPasswordResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("El enlace para restablecer la contraseña ha caducado");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        user.setTokenVersion(user.getTokenVersion() + 1);

        return new MessageResponse("Contraseña actualizada. Ya puedes iniciar sesión.");
    }

    private AuthResponse buildResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getTokenVersion());
        return AuthResponse.of(token, jwtService.getExpirationMillis(), userMapper.toResponse(user));
    }
}

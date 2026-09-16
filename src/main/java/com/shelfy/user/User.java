package com.shelfy.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_preference", nullable = false, length = 10)
    @Builder.Default
    private ThemePreference themePreference = ThemePreference.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(name = "language_preference", nullable = false, length = 2)
    @Builder.Default
    private LanguagePreference languagePreference = LanguagePreference.ES;

    @Column(name = "email_verified", nullable = false)
    @ColumnDefault("true")
    private boolean emailVerified;

    @Column(name = "verification_token", unique = true)
    private String verificationToken;

    @Column(name = "verification_token_expires_at")
    private Instant verificationTokenExpiresAt;

    @Column(name = "password_reset_token", unique = true)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expires_at")
    private Instant passwordResetTokenExpiresAt;

    @Column(unique = true, length = 24)
    private String alias;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @Column(name = "token_version", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private int tokenVersion = 0;

    @Column(name = "reminders_enabled", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean remindersEnabled = true;
}

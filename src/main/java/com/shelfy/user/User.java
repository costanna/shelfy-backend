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

    /**
     * Sin {@code @Builder.Default}: una cuenta nueva creada por código
     * (registro) queda sin verificar por defecto (false). El
     * {@code @ColumnDefault("true")} solo afecta a la migración de las
     * columnas de cuentas ya existentes al añadir esta columna, no a los
     * INSERT que haga Hibernate a partir de ahora.
     */
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

    /**
     * Copia de la fecha de actualización del {@link Avatar} asociado (que
     * vive en su propia tabla). Se duplica aquí para poder incluirla en
     * cualquier respuesta que ya carga un {@code User} (perfil propio,
     * búsqueda, perfil público) sin una consulta extra a
     * {@code user_avatars} por cada usuario listado; el frontend la usa
     * solo como parámetro de caché en la URL de la imagen. {@code null}
     * significa "sin avatar".
     */
    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;
}

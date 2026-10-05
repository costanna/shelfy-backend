package com.shelfy.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // HS256 necesita al menos 256 bits (32 bytes); de sobra para un secreto de pruebas.
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-secret-de-prueba-de-32-bytes-o-mas".getBytes());

    private JwtService service(long expirationMillis) {
        return new JwtService(SECRET, expirationMillis);
    }

    @Test
    void generateToken_canBeReadBackWithTheSameUserIdAndVersion() {
        JwtService service = service(60_000);

        String token = service.generateToken(42L, "lectora@shelfy.app", 3);

        assertThat(service.extractUserId(token)).isEqualTo(42L);
        assertThat(service.extractTokenVersion(token)).isEqualTo(3);
    }

    @Test
    void getExpirationMillis_returnsTheConfiguredValue() {
        assertThat(service(123_456).getExpirationMillis()).isEqualTo(123_456);
    }

    @Test
    void extractUserId_throwsForAnExpiredToken() {
        // Expiración negativa: el "exp" queda claramente en el pasado (el claim se trunca a
        // segundos, así que un 0 justo podría caer en el mismo segundo que la verificación).
        JwtService service = service(-10_000);
        String token = service.generateToken(1L, "a@b.com", 0);

        assertThatThrownBy(() -> service.extractUserId(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void extractUserId_throwsWhenTheSignatureDoesNotMatch() {
        JwtService service = service(60_000);
        String token = service.generateToken(1L, "a@b.com", 0);
        String tamperedToken = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> service.extractUserId(tamperedToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void extractUserId_throwsForATokenSignedWithADifferentSecret() {
        SecretKey otherKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                Base64.getEncoder().encodeToString("otro-secreto-totalmente-distinto-32-bytes".getBytes())));
        String foreignToken = Jwts.builder()
                .subject("1")
                .claim("tv", 0)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        assertThatThrownBy(() -> service(60_000).extractUserId(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void extractUserId_throwsForAMalformedToken() {
        assertThatThrownBy(() -> service(60_000).extractUserId("esto-no-es-un-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractTokenVersion_defaultsToZeroWhenClaimIsMissing() {
        // Un token "antiguo" sin el claim "tv" no debería romper la lectura: se trata como versión 0.
        JwtService service = service(60_000);
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        String tokenWithoutVersion = Jwts.builder()
                .subject("1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertThat(service.extractTokenVersion(tokenWithoutVersion)).isZero();
    }

    @Test
    void generateToken_alsoWorksWithARawNonBase64Secret() {
        // buildKey() cae a UTF-8 en bruto si el secreto no es un Base64 válido — probar ese camino también.
        JwtService service = new JwtService("un-secreto-en-texto-plano-de-32-bytes-o-mas", 60_000);

        String token = service.generateToken(7L, "x@y.com", 1);

        assertThat(service.extractUserId(token)).isEqualTo(7L);
    }
}

package com.shelfy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita por IP las peticiones a los endpoints de auth sin autenticar
 * (login, registro, recuperación de contraseña...), para dificultar la
 * fuerza bruta de contraseñas y el spam de cuentas/emails.
 *
 * Deliberadamente simple: en memoria, por IP, sin mirar si el intento fue
 * válido o no. Render free corre una sola instancia, así que no hace falta
 * Redis ni nada distribuido; y no leer el cuerpo de la petición evita tener
 * que envolver el request para poder leerlo dos veces. No protege contra un
 * ataque distribuido desde muchas IPs distintas, pero ese es un umbral de
 * sofisticación que no hace falta cubrir aquí.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/resend-verification");

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && PROTECTED_PATHS.contains(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = clientIp(request) + ":" + request.getRequestURI();
        Window window = attempts.compute(key, (k, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.startedAt.plus(WINDOW).isBefore(now)) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (attempts.size() > MAX_TRACKED_KEYS) {
            pruneExpired();
        }

        if (window.count.get() > MAX_ATTEMPTS) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"message\":\"Demasiados intentos. Espera unos minutos antes de volver a intentarlo.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private void pruneExpired() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> entry.getValue().startedAt.plus(WINDOW).isBefore(now));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final Instant startedAt;
        private final AtomicInteger count;

        private Window(Instant startedAt, AtomicInteger count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}

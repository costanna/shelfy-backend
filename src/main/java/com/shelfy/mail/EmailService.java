package com.shelfy.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Envía los emails transaccionales de Shelfy a través de la API HTTP de Resend
 * (https://resend.com), en vez de SMTP directo: Render bloquea los puertos SMTP
 * salientes (25/465/587) en su plan free desde sep-2025, y el corte es silencioso
 * (la conexión se cuelga en vez de fallar), lo que bloqueaba la petición HTTP
 * entera de register/forgot-password durante minutos. La API de Resend solo
 * necesita el puerto 443 (HTTPS), que no está bloqueado.
 */
@Service
@Slf4j
public class EmailService {

    private static final String RESEND_API_URL = "https://api.resend.com";

    private final RestClient restClient;

    @Value("${shelfy.mail.enabled}")
    private boolean mailEnabled;

    @Value("${shelfy.mail.from}")
    private String fromAddress;

    @Value("${shelfy.frontend-url}")
    private String frontendUrl;

    public EmailService(@Value("${shelfy.mail.resend-api-key:}") String resendApiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(RESEND_API_URL)
                .defaultHeader("Authorization", "Bearer " + resendApiKey)
                .build();
    }

    public void sendVerificationEmail(String to, String name, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        String body = """
                Hola %s,

                Gracias por registrarte en Shelfy. Confirma tu cuenta en este enlace \
                (caduca en 24 horas):

                %s

                Si no has sido tú, puedes ignorar este email.
                """.formatted(name, link);

        send(to, "Verifica tu cuenta de Shelfy", body, link);
    }

    public void sendPasswordResetEmail(String to, String name, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String body = """
                Hola %s,

                Hemos recibido una solicitud para restablecer tu contraseña de Shelfy. \
                Este enlace caduca en 1 hora:

                %s

                Si no has sido tú, ignora este email: tu contraseña no cambiará.
                """.formatted(name, link);

        send(to, "Recupera tu contraseña de Shelfy", body, link);
    }

    public void sendStaleReadingReminder(String to, String name, List<String> bookTitles) {
        String titleList = bookTitles.stream().map(title -> "- " + title).reduce((a, b) -> a + "\n" + b).orElse("");
        String link = frontendUrl + "/books?status=READING";
        String body = """
                Hola %s,

                Hace tiempo que no marcas avances en estos libros que empezaste a leer:

                %s

                Retómalos cuando quieras, o actualiza su estado si ya no te interesan:

                %s
                """.formatted(name, titleList, link);

        send(to, "¿Sigues con estos libros?", body, link);
    }

    private void send(String to, String subject, String body, String link) {
        if (!mailEnabled) {
            log.info("[correo deshabilitado] destinatario={} asunto=\"{}\" enlace={}", to, subject, link);
            return;
        }

        try {
            restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", fromAddress,
                            "to", List.of(to),
                            "subject", subject,
                            "text", body
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("No se pudo enviar el correo a {} (\"{}\"): {}", to, subject, ex.getMessage());
        }
    }
}

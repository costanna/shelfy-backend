package com.shelfy.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Si {@code shelfy.mail.enabled} es false (por defecto en local), no manda
 * nada de verdad: deja el enlace en el log para poder probar el flujo
 * completo sin credenciales reales de correo.
 *
 * Un fallo real de envío (proveedor caído, credenciales incorrectas...) se
 * registra en el log pero nunca se propaga: registrarse o pedir un cambio de
 * contraseña no puede depender de que el proveedor de correo esté sano en
 * ese momento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${shelfy.mail.enabled}")
    private boolean mailEnabled;

    @Value("${shelfy.mail.from}")
    private String fromAddress;

    @Value("${shelfy.frontend-url}")
    private String frontendUrl;

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

    private void send(String to, String subject, String body, String link) {
        if (!mailEnabled) {
            log.info("[correo deshabilitado] destinatario={} asunto=\"{}\" enlace={}", to, subject, link);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("No se pudo enviar el correo a {} (\"{}\"): {}", to, subject, ex.getMessage());
        }
    }
}

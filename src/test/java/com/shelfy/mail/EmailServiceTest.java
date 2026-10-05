package com.shelfy.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec uriSpec;
    @Mock
    private RestClient.RequestBodySpec bodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Captor
    private ArgumentCaptor<Map<String, Object>> bodyCaptor;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService("ignored-api-key");
        ReflectionTestUtils.setField(service, "restClient", restClient);
        ReflectionTestUtils.setField(service, "fromAddress", "Shelfy <no-reply@shelfy.app>");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://shelfy.app");
    }

    private void enableMail() {
        ReflectionTestUtils.setField(service, "mailEnabled", true);
    }

    private void stubSuccessfulSend() {
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/emails")).thenReturn(bodySpec);
        when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
        when(bodySpec.body(any(Map.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
    }

    @Test
    void sendVerificationEmail_doesNotCallTheRestClientWhenMailIsDisabled() {
        // mailEnabled no se activa: queda en su valor por defecto (false).
        service.sendVerificationEmail("lectora@shelfy.app", "Lectora", "tok-123");

        verify(restClient, never()).post();
    }

    @Test
    void sendVerificationEmail_postsToTheEmailsEndpointWithTheVerificationLink() {
        enableMail();
        stubSuccessfulSend();

        service.sendVerificationEmail("lectora@shelfy.app", "Lectora", "tok-123");

        verify(bodySpec).body(bodyCaptor.capture());
        Map<String, Object> payload = bodyCaptor.getValue();
        assertThatCode(() -> payload.get("from")).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(payload.get("from")).isEqualTo("Shelfy <no-reply@shelfy.app>");
        org.assertj.core.api.Assertions.assertThat(payload.get("to")).isEqualTo(List.of("lectora@shelfy.app"));
        org.assertj.core.api.Assertions.assertThat(payload.get("subject")).isEqualTo("Verifica tu cuenta de Shelfy");
        org.assertj.core.api.Assertions.assertThat((String) payload.get("text"))
                .contains("https://shelfy.app/verify-email?token=tok-123");
    }

    @Test
    void sendPasswordResetEmail_includesTheResetLinkInTheBody() {
        enableMail();
        stubSuccessfulSend();

        service.sendPasswordResetEmail("lectora@shelfy.app", "Lectora", "reset-456");

        verify(bodySpec).body(bodyCaptor.capture());
        org.assertj.core.api.Assertions.assertThat((String) bodyCaptor.getValue().get("text"))
                .contains("https://shelfy.app/reset-password?token=reset-456");
    }

    @Test
    void sendStaleReadingReminder_listsEachBookTitleInTheBody() {
        enableMail();
        stubSuccessfulSend();

        service.sendStaleReadingReminder("lectora@shelfy.app", "Lectora", List.of("Dune", "1984"));

        verify(bodySpec).body(bodyCaptor.capture());
        String text = (String) bodyCaptor.getValue().get("text");
        org.assertj.core.api.Assertions.assertThat(text).contains("- Dune").contains("- 1984");
    }

    @Test
    void send_swallowsRestClientExceptionsInsteadOfPropagatingThem() {
        enableMail();
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/emails")).thenReturn(bodySpec);
        when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
        when(bodySpec.body(any(Map.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenThrow(new RestClientException("boom"));

        assertThatCode(() -> service.sendVerificationEmail("lectora@shelfy.app", "Lectora", "tok-123"))
                .doesNotThrowAnyException();
    }
}

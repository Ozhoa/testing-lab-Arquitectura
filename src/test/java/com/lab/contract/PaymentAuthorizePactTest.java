package com.lab.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.*;
import au.com.dius.pact.consumer.junit5.*;
import au.com.dius.pact.core.model.*;
import au.com.dius.pact.core.model.annotations.Pact;
import com.lab.client.PaymentClient;
import com.lab.client.dto.AuthorizeRequest;
import com.lab.client.dto.AuthorizeResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;

/**
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  EJERCICIO C – Contrato Pact para POST /payments/authorize       │
 * │                                                                  │
 * │  Consumer-Driven Contract Testing con Pact JVM + JUnit 5.        │
 * │                                                                  │
 * │  Flujo:                                                          │
 * │  1. El consumer (este módulo) define las interacciones           │
 * │     que espera del provider (servicio de pagos externo).         │
 * │  2. Pact genera un archivo JSON en target/pacts/                 │
 * │  3. El provider lo usa para verificar que cumple el contrato.    │
 * │                                                                  │
 * │  Interacciones definidas:                                        │
 * │   a) APPROVED  – pago autorizado exitosamente                    │
 * │   b) DECLINED  – pago rechazado por fondos insuficientes         │
 * │   c) ERROR     – error de validación (merchantId faltante)       │
 * └─────────────────────────────────────────────────────────────────┘
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "PaymentProviderService", port = "8090")
@DisplayName("Contrato Pact – POST /payments/authorize")
class PaymentAuthorizePactTest {

    // ══════════════════════════════════════════════════════════════
    //  PACT INTERACTIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Interacción 1: Pago aprobado.
     * El consumer espera que cuando envíe una solicitud válida con
     * monto > 0, el provider responda 200 con status APPROVED.
     */
    @Pact(consumer = "PaymentConsumerService")
    public RequestResponsePact authorizeApproved(PactDslWithProvider builder) {
        return builder
            .given("el merchantId M-001 está activo y tiene fondos suficientes")
            .uponReceiving("solicitud de autorización válida")
                .method("POST")
                .path("/payments/authorize")
                .matchHeader("Content-Type", "application/json.*", "application/json")
                .body(new PactDslJsonBody()
                    .stringType("transactionId", "TXN-2024-001")
                    .decimalType("amount", 150.00)
                    .stringValue("currency", "USD")
                    .stringType("merchantId", "M-001")
                )
            .willRespondWith()
                .status(200)
                .matchHeader("Content-Type", "application/json.*")
                .body(new PactDslJsonBody()
                    .stringMatcher("authorizationCode", "AUTH-[A-Z0-9]{8}", "AUTH-ABCD1234")
                    .stringValue("status", "APPROVED")
                    .stringType("message", "Transacción autorizada exitosamente")
                    .numberType("timestamp", 1704067200000L)
                )
            .toPact();
    }

    /**
     * Interacción 2: Pago rechazado.
     * El consumer espera 200 con status DECLINED cuando el merchant
     * no tiene fondos suficientes.
     */
    @Pact(consumer = "PaymentConsumerService")
    public RequestResponsePact authorizeDeclined(PactDslWithProvider builder) {
        return builder
            .given("el merchantId M-002 no tiene fondos suficientes")
            .uponReceiving("solicitud de autorización con fondos insuficientes")
                .method("POST")
                .path("/payments/authorize")
                .matchHeader("Content-Type", "application/json.*", "application/json")
                .body(new PactDslJsonBody()
                    .stringType("transactionId", "TXN-2024-002")
                    .decimalType("amount", 9999.99)
                    .stringValue("currency", "USD")
                    .stringType("merchantId", "M-002")
                )
            .willRespondWith()
                .status(200)
                .matchHeader("Content-Type", "application/json.*")
                .body(new PactDslJsonBody()
                    .nullValue("authorizationCode")
                    .stringValue("status", "DECLINED")
                    .stringType("message", "Fondos insuficientes")
                    .numberType("timestamp", 1704067200000L)
                )
            .toPact();
    }

    /**
     * Interacción 3: Error de validación.
     * El consumer espera 422 cuando el merchantId está ausente.
     */
    @Pact(consumer = "PaymentConsumerService")
    public RequestResponsePact authorizeValidationError(PactDslWithProvider builder) {
        return builder
            .given("se envía una solicitud con merchantId vacío")
            .uponReceiving("solicitud de autorización sin merchantId")
                .method("POST")
                .path("/payments/authorize")
                .matchHeader("Content-Type", "application/json.*", "application/json")
                .body(new PactDslJsonBody()
                    .stringType("transactionId", "TXN-2024-003")
                    .decimalType("amount", 50.00)
                    .stringValue("currency", "USD")
                    .stringValue("merchantId", "")
                )
            .willRespondWith()
                .status(422)
                .matchHeader("Content-Type", "application/json.*")
                .body(new PactDslJsonBody()
                    .nullValue("authorizationCode")
                    .stringValue("status", "ERROR")
                    .stringMatcher("message", ".*merchantId.*", "El campo merchantId es obligatorio")
                    .numberType("timestamp", 1704067200000L)
                )
            .toPact();
    }

    // ══════════════════════════════════════════════════════════════
    //  TESTS
    // ══════════════════════════════════════════════════════════════

    @Test
    @PactTestFor(pactMethod = "authorizeApproved")
    @DisplayName("POST /payments/authorize → APPROVED cuando los datos son válidos")
    void authorize_approved(MockServer mockServer) {
        PaymentClient client = buildClient(mockServer);

        AuthorizeRequest request = AuthorizeRequest.builder()
            .transactionId("TXN-2024-001")
            .amount(new java.math.BigDecimal("150.00"))
            .currency("USD")
            .merchantId("M-001")
            .build();

        AuthorizeResponse response = client.authorize(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getAuthorizationCode())
            .isNotNull()
            .matches("AUTH-[A-Z0-9]{8}");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @PactTestFor(pactMethod = "authorizeDeclined")
    @DisplayName("POST /payments/authorize → DECLINED cuando fondos insuficientes")
    void authorize_declined(MockServer mockServer) {
        PaymentClient client = buildClient(mockServer);

        AuthorizeRequest request = AuthorizeRequest.builder()
            .transactionId("TXN-2024-002")
            .amount(new java.math.BigDecimal("9999.99"))
            .currency("USD")
            .merchantId("M-002")
            .build();

        AuthorizeResponse response = client.authorize(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("DECLINED");
        assertThat(response.getAuthorizationCode()).isNull();
        assertThat(response.getMessage()).isNotBlank();
    }

    @Test
    @PactTestFor(pactMethod = "authorizeValidationError")
    @DisplayName("POST /payments/authorize → ERROR (422) cuando merchantId está vacío")
    void authorize_validationError(MockServer mockServer) {
        PaymentClient client = buildClient(mockServer);

        AuthorizeRequest request = AuthorizeRequest.builder()
            .transactionId("TXN-2024-003")
            .amount(new java.math.BigDecimal("50.00"))
            .currency("USD")
            .merchantId("")
            .build();

        // 422 → RestTemplate lanza HttpClientErrorException
        assertThatThrownBy(() -> client.authorize(request))
            .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class)
            .hasMessageContaining("422");
    }

    // ── helper ───────────────────────────────────────────────────────
    private PaymentClient buildClient(MockServer mockServer) {
        return new PaymentClient(new RestTemplate(), mockServer.getUrl());
    }
}

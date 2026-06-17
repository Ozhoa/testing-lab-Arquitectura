package com.lab.contract;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.lab.client.PaymentClient;
import com.lab.client.dto.AuthorizeRequest;
import com.lab.client.dto.AuthorizeResponse;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  EJERCICIO C – WireMock para simular el proveedor de pagos       │
 * │                                                                  │
 * │  WireMock levanta un servidor HTTP falso que imita al provider.  │
 * │  Permite probar el cliente HTTP de forma aislada, sin necesitar  │
 * │  un servidor real ni Docker.                                     │
 * │                                                                  │
 * │  Diferencia con Pact:                                            │
 * │   · WireMock: valida el comportamiento del CONSUMER únicamente.  │
 * │   · Pact: genera un contrato que el PROVIDER también verifica.   │
 * └─────────────────────────────────────────────────────────────────┘
 */
@DisplayName("PaymentClient – WireMock (simulación del provider)")
class PaymentClientWireMockTest {

    private static WireMockServer wireMock;
    private PaymentClient client;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        client = new PaymentClient(new RestTemplate(), "http://localhost:" + wireMock.port());
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. Escenarios de respuesta exitosa
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("WireMock: responde 200 APPROVED con código de autorización")
    void wiremock_approvedPayment() {
        wireMock.stubFor(post(urlEqualTo("/payments/authorize"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(matchingJsonPath("$.transactionId"))
            .withRequestBody(matchingJsonPath("$.amount"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "authorizationCode": "AUTH-XK9P2L7Q",
                      "status": "APPROVED",
                      "message": "Transacción autorizada",
                      "timestamp": 1704067200000
                    }
                    """)));

        AuthorizeResponse response = client.authorize(buildRequest("TXN-001", "500.00", "M-001"));

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getAuthorizationCode()).isEqualTo("AUTH-XK9P2L7Q");

        wireMock.verify(1, postRequestedFor(urlEqualTo("/payments/authorize")));
    }

    @Test
    @DisplayName("WireMock: responde 200 DECLINED cuando los fondos son insuficientes")
    void wiremock_declinedPayment() {
        wireMock.stubFor(post(urlEqualTo("/payments/authorize"))
            .withRequestBody(matchingJsonPath("$.merchantId", equalTo("M-BLOCKED")))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "authorizationCode": null,
                      "status": "DECLINED",
                      "message": "Comercio bloqueado por riesgo",
                      "timestamp": 1704067200000
                    }
                    """)));

        AuthorizeResponse response = client.authorize(
            buildRequest("TXN-002", "100.00", "M-BLOCKED"));

        assertThat(response.getStatus()).isEqualTo("DECLINED");
        assertThat(response.getAuthorizationCode()).isNull();
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. Escenarios de error del servidor
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("WireMock: 503 Service Unavailable lanza excepción HTTP")
    void wiremock_serviceUnavailable() {
        wireMock.stubFor(post(urlEqualTo("/payments/authorize"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("{\"error\": \"Service temporarily unavailable\"}")));

        assertThatThrownBy(() ->
            client.authorize(buildRequest("TXN-003", "200.00", "M-001")))
            .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
    }

    @Test
    @DisplayName("WireMock: timeout configurable – simula latencia alta")
    void wiremock_slowResponse() {
        wireMock.stubFor(post(urlEqualTo("/payments/authorize"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(100) // 100ms de latencia simulada
                .withBody("""
                    {
                      "authorizationCode": "AUTH-SLOW001",
                      "status": "APPROVED",
                      "message": "OK (lento)",
                      "timestamp": 1704067200000
                    }
                    """)));

        long start = System.currentTimeMillis();
        AuthorizeResponse response = client.authorize(buildRequest("TXN-004", "300.00", "M-001"));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(elapsed).isGreaterThanOrEqualTo(100);
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. Verificaciones de request (request matching)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("WireMock: verifica que el cliente envía el header Content-Type correcto")
    void wiremock_verifyContentTypeHeader() {
        wireMock.stubFor(post(urlEqualTo("/payments/authorize"))
            .willReturn(okJson("""
                {
                  "authorizationCode": "AUTH-HDR001",
                  "status": "APPROVED",
                  "message": "OK",
                  "timestamp": 1704067200000
                }
                """)));

        client.authorize(buildRequest("TXN-005", "75.00", "M-001"));

        wireMock.verify(postRequestedFor(urlEqualTo("/payments/authorize"))
            .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    @DisplayName("WireMock: verifica campos requeridos en el body del request")
    void wiremock_verifyRequestBody() {
        wireMock.stubFor(post(urlEqualTo("/payments/authorize"))
            .willReturn(okJson("""
                {
                  "authorizationCode": "AUTH-BODY01",
                  "status": "APPROVED",
                  "message": "OK",
                  "timestamp": 1704067200000
                }
                """)));

        client.authorize(AuthorizeRequest.builder()
            .transactionId("TXN-VERIFY")
            .amount(new BigDecimal("1234.56"))
            .currency("USD")
            .merchantId("M-VERIFY")
            .build());

        wireMock.verify(postRequestedFor(urlEqualTo("/payments/authorize"))
            .withRequestBody(matchingJsonPath("$.transactionId", equalTo("TXN-VERIFY")))
            .withRequestBody(matchingJsonPath("$.amount"))
            .withRequestBody(matchingJsonPath("$.currency", equalTo("USD")))
            .withRequestBody(matchingJsonPath("$.merchantId", equalTo("M-VERIFY"))));
    }

    @Test
    @DisplayName("WireMock: escenario stateful – primer call PENDING, segundo APPROVED")
    void wiremock_scenarioStateful() {
        final String SCENARIO = "payment-processing";
        final String STATE_PENDING  = "Procesando";
        final String STATE_APPROVED = "Aprobado";

        wireMock.stubFor(post(urlEqualTo("/payments/authorize"))
            .inScenario(SCENARIO)
            .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
            .willReturn(okJson("""
                {"authorizationCode":null,"status":"PENDING",
                 "message":"En proceso","timestamp":1704067200000}
                """))
            .willSetStateTo(STATE_PENDING));

        wireMock.stubFor(post(urlEqualTo("/payments/authorize"))
            .inScenario(SCENARIO)
            .whenScenarioStateIs(STATE_PENDING)
            .willReturn(okJson("""
                {"authorizationCode":"AUTH-FINAL1","status":"APPROVED",
                 "message":"Autorizado","timestamp":1704067201000}
                """))
            .willSetStateTo(STATE_APPROVED));

        AuthorizeRequest req = buildRequest("TXN-STATE", "500.00", "M-001");

        // Primera llamada → PENDING
        AuthorizeResponse r1 = client.authorize(req);
        assertThat(r1.getStatus()).isEqualTo("PENDING");

        // Segunda llamada → APPROVED
        AuthorizeResponse r2 = client.authorize(req);
        assertThat(r2.getStatus()).isEqualTo("APPROVED");
        assertThat(r2.getAuthorizationCode()).isEqualTo("AUTH-FINAL1");
    }

    // ── helper ───────────────────────────────────────────────────────
    private AuthorizeRequest buildRequest(String txId, String amount, String merchantId) {
        return AuthorizeRequest.builder()
            .transactionId(txId)
            .amount(new BigDecimal(amount))
            .currency("USD")
            .merchantId(merchantId)
            .build();
    }
}

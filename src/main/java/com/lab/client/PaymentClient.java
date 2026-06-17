package com.lab.client;

import com.lab.client.dto.AuthorizeRequest;
import com.lab.client.dto.AuthorizeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente HTTP para el servicio externo de pagos.
 * El contrato de /payments/authorize se verifica con Pact (Ejercicio C).
 */
@Component
public class PaymentClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PaymentClient(RestTemplate restTemplate,
                         @Value("${payment.service.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Llama a POST /payments/authorize con los datos de la transacción.
     */
    public AuthorizeResponse authorize(AuthorizeRequest request) {
        return restTemplate.postForObject(
            baseUrl + "/payments/authorize",
            request,
            AuthorizeResponse.class
        );
    }
}

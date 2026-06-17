package com.lab.client.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthorizeRequest {
    private String  transactionId;
    private BigDecimal amount;
    private String  currency;
    private String  merchantId;
}

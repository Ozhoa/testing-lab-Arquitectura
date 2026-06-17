package com.lab.client.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthorizeResponse {
    private String  authorizationCode;
    private String  status;          // APPROVED | DECLINED | ERROR
    private String  message;
    private Long    timestamp;
}

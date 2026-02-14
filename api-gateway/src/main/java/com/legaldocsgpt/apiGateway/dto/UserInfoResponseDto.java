package com.legaldocsgpt.apiGateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInfoResponseDto {
    private Long id;
    private String email;
    private String username;
    private String role;
}
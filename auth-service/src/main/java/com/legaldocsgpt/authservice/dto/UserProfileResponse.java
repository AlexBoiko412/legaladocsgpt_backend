package com.legaldocsgpt.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserProfileResponse {
    private String email;
    private String username;
    private String role;
    private String provider;
}
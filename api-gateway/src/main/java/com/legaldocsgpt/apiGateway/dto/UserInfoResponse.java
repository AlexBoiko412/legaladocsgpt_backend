package com.legaldocsgpt.apiGateway.dto;

public class UserInfoResponse {
    private Long id;
    private String username;
    private String role;

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }
    public String getUsername() {
        return username;
    }
}
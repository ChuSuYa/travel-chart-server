package com.travelchart.manageservice.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AdminLoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}

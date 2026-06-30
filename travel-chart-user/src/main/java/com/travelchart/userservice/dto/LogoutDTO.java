package com.travelchart.userservice.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class LogoutDTO {
    @NotBlank(message = "token不能为空")
    private String token;
}

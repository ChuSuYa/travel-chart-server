package com.travelchart.userservice.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class RefreshDTO {
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}

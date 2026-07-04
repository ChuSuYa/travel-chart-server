package com.travelchart.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenDTO {
    private String accessToken;
    private String refreshToken;
    private long expiresIn = 7200L;
    private Long userId;
    private String nickname;
    private String phone;
    private String avatar;
    private boolean isNewUser;
}

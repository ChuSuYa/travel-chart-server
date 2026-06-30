package com.travelchart.userservice.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDTO {
    private Long id;
    private String phone;
    private String nickname;
    private String avatar;
    private String email;
    private Integer inspiration;
    private String language;
    private String preferences;
    private LocalDateTime createTime;
}

package com.travelchart.userservice.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class TravelerDTO {
    private Long id;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotNull(message = "年龄不能为空")
    private Integer age;
    @NotBlank(message = "类型不能为空")
    private String type;
    private String relationTag;
}

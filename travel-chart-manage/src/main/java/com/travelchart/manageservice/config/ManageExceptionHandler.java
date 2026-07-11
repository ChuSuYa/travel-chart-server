package com.travelchart.manageservice.config;

import com.travelchart.common.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.travelchart.manageservice")
public class ManageExceptionHandler {
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> unauthorized(SecurityException exception) {
        return Result.error(401, exception.getMessage());
    }
}

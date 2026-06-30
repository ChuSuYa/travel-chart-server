package com.travelchart.common.model;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<T>(200, "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<T>(200, "success", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<T>(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<T>(500, message, null);
    }
}

package com.travelchart.common.result;

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

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success(T data, String message) {
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    // Compat aliases
    public static <T> Result<T> ok(T data) {
        return success(data);
    }

    public static <T> Result<T> ok() {
        return success();
    }

    public static <T> Result<T> fail(int code, String message) {
        return error(code, message);
    }

    public static <T> Result<T> fail(String message) {
        return error(message);
    }
}

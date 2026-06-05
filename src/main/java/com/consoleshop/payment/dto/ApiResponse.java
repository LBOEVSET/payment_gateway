package com.consoleshop.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private Integer statusCode;

    public static <T> ApiResponse<T> ok(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .statusCode(200)
                .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .statusCode(200)
                .build();
    }

    public static <T> ApiResponse<T> error(int status, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .statusCode(status)
                .build();
    }
}

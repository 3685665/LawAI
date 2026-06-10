package com.lawai.api.dto;

public record ActivityLogCreateRequest(
    String action,
    String screen,
    String detail,
    String path
) {
}

package com.lawai.api.dto;

import java.time.OffsetDateTime;

public record ActivityLogDto(
    String id,
    String userId,
    String userName,
    String userEmail,
    String role,
    String source,
    String action,
    String screen,
    String detail,
    String path,
    OffsetDateTime createdAt
) {
}

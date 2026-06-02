package com.lawai.auth.dto;

import java.time.OffsetDateTime;

public record AuthUserDto(
    String id,
    String name,
    String email,
    OffsetDateTime createdAt,
    OffsetDateTime lastLoginAt
) {
}

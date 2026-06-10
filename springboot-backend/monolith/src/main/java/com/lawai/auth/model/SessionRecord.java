package com.lawai.auth.model;

import java.time.OffsetDateTime;

public record SessionRecord(
    String tokenHash,
    String userId,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt
) {
}

package com.lawai.api.model;

import java.time.OffsetDateTime;

public record ActivityLogRecord(
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

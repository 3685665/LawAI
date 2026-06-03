package com.lawai.api.model;

import java.time.OffsetDateTime;

public record FeedbackRecord(
    String id,
    String userId,
    String userName,
    String userEmail,
    String type,
    String subject,
    String message,
    String status,
    OffsetDateTime createdAt
) {
}

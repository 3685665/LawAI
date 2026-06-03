package com.lawai.api.dto;

import java.time.OffsetDateTime;

public record FeedbackRecordDto(
    String id,
    String type,
    String subject,
    String message,
    String status,
    OffsetDateTime createdAt
) {
}

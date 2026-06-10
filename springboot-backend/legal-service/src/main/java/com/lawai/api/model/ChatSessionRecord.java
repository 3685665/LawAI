package com.lawai.api.model;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatSessionRecord(
    String id,
    String userId,
    String title,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<ChatMessageRecord> messages
) {
}

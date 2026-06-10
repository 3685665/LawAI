package com.lawai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatSessionDto(
    String id,
    String title,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<ChatMessageDto> messages
) {
}

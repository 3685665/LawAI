package com.lawai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatMessageDto(
    String id,
    String role,
    String text,
    List<PrecedentDto> citations,
    String disclaimer,
    OffsetDateTime createdAt
) {
}

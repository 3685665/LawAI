package com.lawai.api.model;

import com.lawai.api.dto.PrecedentDto;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatMessageRecord(
    String id,
    String role,
    String text,
    List<PrecedentDto> citations,
    String disclaimer,
    OffsetDateTime createdAt
) {
}

package com.lawai.api.dto;

public record PrecedentBatchItemDto(
    String sourceId,
    String court,
    String title,
    String date
) {
}

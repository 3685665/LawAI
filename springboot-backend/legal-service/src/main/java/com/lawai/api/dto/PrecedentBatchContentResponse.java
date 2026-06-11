package com.lawai.api.dto;

public record PrecedentBatchContentResponse(
    String sourceId,
    String court,
    String title,
    String plainText
) {
}

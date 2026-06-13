package com.lawai.api.dto;

import java.time.OffsetDateTime;

public record CaseUploadedDocumentDetailDto(
    String id,
    String filename,
    long size,
    String contentType,
    int extractedCharacters,
    int chunkCount,
    int indexed,
    String textPreview,
    String extractedText,
    boolean originalContentAvailable,
    OffsetDateTime createdAt
) {
}

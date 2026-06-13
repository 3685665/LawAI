package com.lawai.api.dto;

import java.time.OffsetDateTime;

public record CaseUploadedDocumentDto(
    String id,
    String filename,
    long size,
    String contentType,
    int extractedCharacters,
    int chunkCount,
    int indexed,
    String textPreview,
    OffsetDateTime createdAt
) {
}

package com.lawai.document.precedentsync.dto;

import java.time.Instant;

public record PrecedentSyncRunFileDto(
    long id,
    String filename,
    String storedPath,
    String status,
    Long documentId,
    Integer extractedChars,
    Integer chunkCount,
    String errorMessage,
    Instant processedAt
) {
}

package com.lawai.document.batch.dto;

import java.time.Instant;

public record BatchDocumentRunFileDto(
    long id,
    String filename,
    String filePath,
    Long fileSizeBytes,
    String status,
    Long documentId,
    Integer extractedChars,
    Integer chunkCount,
    String errorMessage,
    Instant processedAt
) {
}

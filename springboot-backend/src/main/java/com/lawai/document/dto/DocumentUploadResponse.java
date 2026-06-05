package com.lawai.document.dto;

public record DocumentUploadResponse(
    long documentId,
    String filename,
    String storedPath,
    int extractedCharacters,
    int chunkCount,
    int postgresChunks,
    int opensearchIndexed,
    int pgvectorEmbeddings,
    String message
) {
}

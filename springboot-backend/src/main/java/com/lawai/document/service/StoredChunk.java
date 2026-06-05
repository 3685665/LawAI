package com.lawai.document.service;

public record StoredChunk(
    long id,
    long documentId,
    int chunkIndex,
    String content,
    String embeddingLiteral
) {
}

package com.lawai.document.service;

public record DocumentChunk(
    int chunkIndex,
    String content,
    String embeddingLiteral
) {
}

package com.lawai.document.dto;

public record DocumentSearchResult(
    long documentId,
    long chunkId,
    String filename,
    int chunkIndex,
    String content,
    double score
) {
}

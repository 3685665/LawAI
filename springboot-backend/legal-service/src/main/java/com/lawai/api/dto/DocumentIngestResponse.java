package com.lawai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record DocumentIngestResponse(
    String filename,
    long size,
    String contentType,
    int extractedCharacters,
    int chunkCount,
    int indexed,
    String storage,
    String message,
    String textPreview,
    @JsonIgnore
    String extractedText,
    List<String> warnings
) {
}

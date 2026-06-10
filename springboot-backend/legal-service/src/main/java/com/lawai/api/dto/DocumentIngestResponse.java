package com.lawai.api.dto;

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
    List<String> warnings
) {
}

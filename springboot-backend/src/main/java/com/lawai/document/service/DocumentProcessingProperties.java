package com.lawai.document.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.document")
public record DocumentProcessingProperties(
    String uploadDir,
    String pythonCommand,
    String pdfExtractorScript,
    int chunkSize,
    int chunkOverlap,
    int embeddingDimensions
) {
}

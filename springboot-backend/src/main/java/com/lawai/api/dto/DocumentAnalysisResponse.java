package com.lawai.api.dto;

import java.util.List;

public record DocumentAnalysisResponse(
    String filename,
    long size,
    String contentType,
    List<String> detectedIssues,
    String summary
) {
}

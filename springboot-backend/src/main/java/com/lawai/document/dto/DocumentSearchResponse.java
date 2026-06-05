package com.lawai.document.dto;

import java.util.List;

public record DocumentSearchResponse(
    String query,
    String source,
    List<DocumentSearchResult> results
) {
}

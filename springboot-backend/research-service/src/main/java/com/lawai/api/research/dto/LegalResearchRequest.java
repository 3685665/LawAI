package com.lawai.api.research.dto;

import jakarta.validation.constraints.NotBlank;

public record LegalResearchRequest(@NotBlank(message = "{research.query-required}") String query, String sessionId) {
}

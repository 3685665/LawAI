package com.lawai.api.research.dto;

import jakarta.validation.constraints.NotBlank;

public record LegalResearchRequest(@NotBlank String query, String sessionId) {
}
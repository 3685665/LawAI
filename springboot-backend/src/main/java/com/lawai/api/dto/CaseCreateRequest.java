package com.lawai.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CaseCreateRequest(
    @NotBlank String caseType,
    @NotBlank String clientName,
    @NotBlank String opponentName,
    @NotBlank String courtName,
    @NotBlank String subject,
    @NotBlank String summary,
    List<String> completedDocumentIds
) {
}

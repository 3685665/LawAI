package com.lawai.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CaseCreateRequest(
    @NotBlank String caseType,
    @NotBlank String fileTitle,
    @NotBlank String caseNumber,
    @NotBlank String courtName,
    @NotBlank String city,
    @NotBlank String notes,
    List<String> completedDocumentIds,
    List<CasePartyDto> parties,
    List<CaseExpenseDto> expenses,
    List<CaseNoteDto> caseNotes
) {
}

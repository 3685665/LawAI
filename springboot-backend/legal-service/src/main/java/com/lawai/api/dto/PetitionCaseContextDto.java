package com.lawai.api.dto;

public record PetitionCaseContextDto(
    String caseId,
    String caseType,
    String caseLabel,
    String fileTitle,
    String caseNumber,
    String city,
    String notes,
    String clientName,
    String opponentName,
    String courtName,
    String subject,
    String summary,
    String petitionType,
    String petitionFacts,
    String petitionDemands
) {
}

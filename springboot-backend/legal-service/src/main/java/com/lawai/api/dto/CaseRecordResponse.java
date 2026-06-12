package com.lawai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CaseRecordResponse(
    String id,
    String caseType,
    String caseLabel,
    String fileTitle,
    String caseNumber,
    String courtName,
    String city,
    String notes,
    int requiredDocumentCount,
    int completedRequiredDocumentCount,
    int progress,
    List<CaseDocumentDto> documents,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

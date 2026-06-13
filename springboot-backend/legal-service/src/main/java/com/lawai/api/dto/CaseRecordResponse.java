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
    List<CaseDocumentDto> documents,
    List<CaseUploadedDocumentDto> uploadedDocuments,
    List<CasePartyDto> parties,
    List<CaseExpenseDto> expenses,
    List<CaseNoteDto> caseNotes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

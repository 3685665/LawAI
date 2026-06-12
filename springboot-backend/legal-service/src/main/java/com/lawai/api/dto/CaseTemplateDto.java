package com.lawai.api.dto;

import java.util.List;

public record CaseTemplateDto(
    String caseType,
    String label,
    String title,
    String courtHint,
    List<CaseDocumentDto> documents
) {
}

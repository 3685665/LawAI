package com.lawai.api.dto;

import java.util.List;

public record CaseDocumentPatchResponse(
    CaseRecordResponse caseRecord,
    List<CaseRecordResponse> cases
) {
}

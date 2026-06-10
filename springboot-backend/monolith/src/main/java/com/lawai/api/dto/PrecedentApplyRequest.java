package com.lawai.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PrecedentApplyRequest(
    @NotBlank String court,
    String chamber,
    String docketNo,
    String decisionNo,
    String date,
    @NotBlank String topic,
    String summary,
    @NotBlank @Size(min = 20) String content,
    String aiSummary,
    @NotNull @Valid PetitionCaseContextDto caseContext
) {
}

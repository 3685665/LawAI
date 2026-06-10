package com.lawai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrecedentSummarizeRequest(
    @NotBlank String court,
    String chamber,
    String docketNo,
    String decisionNo,
    String date,
    @NotBlank String topic,
    String summary,
    @NotBlank @Size(min = 20) String content
) {
}

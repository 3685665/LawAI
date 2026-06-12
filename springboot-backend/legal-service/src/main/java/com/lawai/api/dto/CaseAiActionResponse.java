package com.lawai.api.dto;

import java.util.List;

public record CaseAiActionResponse(
    String action,
    String title,
    String answer,
    List<String> nextSteps,
    String disclaimer
) {
}

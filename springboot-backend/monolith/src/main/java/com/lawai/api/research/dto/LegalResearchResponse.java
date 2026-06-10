package com.lawai.api.research.dto;

import java.util.List;

public record LegalResearchResponse(
    ResearchPlanDto plan,
    List<ResearchStepDto> steps,
    List<ResearchSourceResultDto> sourceResults,
    String answer,
    String disclaimer,
    String sessionId
) {
}
package com.lawai.api.research.dto;

import com.lawai.api.research.ResearchStepStatus;
import com.lawai.api.research.ResearchStepType;

public record ResearchProgressEvent(
    String event,
    ResearchStepType stepType,
    String label,
    ResearchStepStatus status,
    LegalResearchResponse response
) {
  public static ResearchProgressEvent step(ResearchStepType type, ResearchStepStatus status) {
    return step(type, type.messageCode(), status);
  }

  public static ResearchProgressEvent step(ResearchStepType type, String label, ResearchStepStatus status) {
    return new ResearchProgressEvent("step", type, label, status, null);
  }

  public static ResearchProgressEvent complete(LegalResearchResponse response) {
    return new ResearchProgressEvent("complete", null, null, null, response);
  }
}

package com.lawai.api.research.dto;

import com.lawai.api.research.ResearchStepStatus;
import com.lawai.api.research.ResearchStepType;

public record ResearchStepDto(ResearchStepType type, String label, ResearchStepStatus status) {
}

package com.lawai.api.dto;

import java.util.List;

public record PetitionTrainingResponse(
    String title,
    String subtitle,
    String summary,
    List<String> audience,
    List<PetitionTrainingModuleDto> modules,
    List<PetitionTrainingPromptDto> prompts,
    List<PetitionTrainingChecklistItemDto> checklist,
    List<String> commonMistakes,
    List<String> suggestedModes
) {
}

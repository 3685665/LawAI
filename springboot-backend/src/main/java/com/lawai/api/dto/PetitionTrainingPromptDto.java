package com.lawai.api.dto;

public record PetitionTrainingPromptDto(
    String label,
    String prompt,
    String expectedOutput
) {
}

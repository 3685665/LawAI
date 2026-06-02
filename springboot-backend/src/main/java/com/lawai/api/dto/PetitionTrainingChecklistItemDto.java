package com.lawai.api.dto;

public record PetitionTrainingChecklistItemDto(
    String label,
    String detail,
    boolean required
) {
}

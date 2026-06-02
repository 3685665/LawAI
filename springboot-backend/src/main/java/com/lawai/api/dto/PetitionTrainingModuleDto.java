package com.lawai.api.dto;

import java.util.List;

public record PetitionTrainingModuleDto(
    String id,
    String title,
    String summary,
    List<String> lessonPoints,
    List<String> exercises
) {
}

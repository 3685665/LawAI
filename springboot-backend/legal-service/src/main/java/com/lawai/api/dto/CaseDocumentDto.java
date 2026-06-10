package com.lawai.api.dto;

public record CaseDocumentDto(
    String id,
    String title,
    String detail,
    boolean required,
    String group,
    boolean completed
) {
}

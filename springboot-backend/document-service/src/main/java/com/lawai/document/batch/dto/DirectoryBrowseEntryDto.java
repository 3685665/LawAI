package com.lawai.document.batch.dto;

public record DirectoryBrowseEntryDto(
    String name,
    String path,
    boolean directory
) {
}

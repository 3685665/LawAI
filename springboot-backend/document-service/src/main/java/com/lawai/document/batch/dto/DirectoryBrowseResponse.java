package com.lawai.document.batch.dto;

import java.util.List;

public record DirectoryBrowseResponse(
    String currentPath,
    String parentPath,
    List<String> roots,
    List<DirectoryBrowseEntryDto> entries
) {
}

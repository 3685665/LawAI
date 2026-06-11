package com.lawai.document.precedentsync.dto;

import java.time.Instant;
import java.util.List;

public record PrecedentSyncRunDto(
    long id,
    long taskId,
    String taskName,
    String triggerType,
    String status,
    Instant startedAt,
    Instant finishedAt,
    int totalFiles,
    int successCount,
    int failedCount,
    int skippedCount,
    String summaryMessage,
    List<PrecedentSyncRunFileDto> files
) {
}

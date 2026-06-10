package com.lawai.document.batch.dto;

import java.time.Instant;
import java.util.List;

public record BatchDocumentRunDto(
    long id,
    long jobId,
    String jobName,
    String triggerType,
    String status,
    Instant startedAt,
    Instant finishedAt,
    int totalFiles,
    int successCount,
    int failedCount,
    int skippedCount,
    String summaryMessage,
    List<BatchDocumentRunFileDto> files
) {
}

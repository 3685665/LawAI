package com.lawai.document.precedentsync.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PrecedentSyncTaskDto(
    long id,
    String name,
    List<String> courts,
    LocalDate dateFrom,
    LocalDate dateTo,
    int maxDocumentsPerRun,
    int intervalMinutes,
    boolean enabled,
    String status,
    Instant lastRunAt,
    Instant nextRunAt,
    String createdByUserId,
    String createdByUserName,
    Instant createdAt,
    Instant updatedAt
) {
}

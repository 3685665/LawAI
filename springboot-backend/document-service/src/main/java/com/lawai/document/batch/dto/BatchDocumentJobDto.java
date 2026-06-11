package com.lawai.document.batch.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BatchDocumentJobDto(
    long id,
    String name,
    String sourceType,
    String directoryPath,
    List<String> precedentCourts,
    String precedentQuery,
    LocalDate precedentDateFrom,
    LocalDate precedentDateTo,
    Integer precedentMaxDocuments,
    String scheduleType,
    String scheduledTime,
    LocalDate scheduledDate,
    Integer dayOfWeek,
    Integer dayOfMonth,
    boolean enabled,
    String createdByUserId,
    String createdByUserName,
    Instant lastRunAt,
    Instant nextRunAt,
    Instant createdAt,
    Instant updatedAt
) {
}

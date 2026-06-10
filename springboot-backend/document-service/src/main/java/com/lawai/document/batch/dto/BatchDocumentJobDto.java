package com.lawai.document.batch.dto;

import java.time.Instant;
import java.time.LocalDate;

public record BatchDocumentJobDto(
    long id,
    String name,
    String directoryPath,
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

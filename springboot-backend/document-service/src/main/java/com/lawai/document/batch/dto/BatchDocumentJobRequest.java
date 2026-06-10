package com.lawai.document.batch.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BatchDocumentJobRequest(
    @Size(max = 120) String name,
    @NotBlank @Size(max = 500) String directoryPath,
    @NotBlank @Pattern(regexp = "ONCE|DAILY|WEEKLY|MONTHLY") String scheduleType,
    @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String scheduledTime,
    LocalDate scheduledDate,
    @Min(1) @Max(7) Integer dayOfWeek,
    @Min(1) @Max(31) Integer dayOfMonth,
    @NotNull Boolean enabled
) {
}

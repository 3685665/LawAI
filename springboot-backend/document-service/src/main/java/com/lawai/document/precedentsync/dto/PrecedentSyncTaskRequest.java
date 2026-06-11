package com.lawai.document.precedentsync.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PrecedentSyncTaskRequest(
    @Size(max = 120) String name,
    @NotNull List<@Pattern(regexp = "YARGITAY|DANISTAY|ANAYASA") String> courts,
    @NotNull LocalDate dateFrom,
    @NotNull LocalDate dateTo,
    @Min(1) @Max(5000) Integer maxDocumentsPerRun,
    @Min(5) @Max(1440) Integer intervalMinutes,
    @NotNull Boolean enabled
) {
}

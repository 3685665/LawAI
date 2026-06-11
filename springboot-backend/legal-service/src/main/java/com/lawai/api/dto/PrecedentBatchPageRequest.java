package com.lawai.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PrecedentBatchPageRequest(
    @NotBlank @Pattern(regexp = "YARGITAY|DANISTAY|ANAYASA") String court,
    String query,
    String dateFrom,
    String dateTo,
    @Min(1) int page,
    @Min(1) @Max(100) int pageSize
) {
}

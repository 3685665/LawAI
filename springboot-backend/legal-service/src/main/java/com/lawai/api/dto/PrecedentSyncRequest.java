package com.lawai.api.dto;

public record PrecedentSyncRequest(
    String court,
    String dateFrom,
    String dateTo,
    Integer minutesBack,
    Integer pageSize,
    Integer maxPages
) {
}

package com.lawai.api.dto;

import java.time.ZonedDateTime;

public record PrecedentSyncResponse(
    String court,
    ZonedDateTime from,
    ZonedDateTime to,
    int fetched,
    int indexed,
    String storage,
    String message
) {
}

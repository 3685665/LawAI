package com.lawai.api.model;

import java.util.List;

public record ActivityLogStorePayload(
    List<ActivityLogRecord> logs
) {
}

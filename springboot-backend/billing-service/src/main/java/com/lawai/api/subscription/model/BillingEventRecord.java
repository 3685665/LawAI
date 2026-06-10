package com.lawai.api.subscription.model;

import java.time.OffsetDateTime;

public record BillingEventRecord(
    String provider,
    String eventId,
    String eventType,
    OffsetDateTime processedAt
) {
}

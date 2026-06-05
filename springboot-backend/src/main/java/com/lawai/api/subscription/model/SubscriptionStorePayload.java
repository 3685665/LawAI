package com.lawai.api.subscription.model;

import java.util.List;

public record SubscriptionStorePayload(List<SubscriptionPlanRecord> plans) {
}

package com.lawai.api.model;

import java.util.List;

public record FeedbackStorePayload(
    List<FeedbackRecord> feedbackItems
) {
}

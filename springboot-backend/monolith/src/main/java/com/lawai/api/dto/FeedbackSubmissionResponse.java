package com.lawai.api.dto;

public record FeedbackSubmissionResponse(
    String message,
    FeedbackRecordDto feedback
) {
}

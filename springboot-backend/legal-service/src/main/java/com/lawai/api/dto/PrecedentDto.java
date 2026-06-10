package com.lawai.api.dto;

public record PrecedentDto(
    String sourceId,
    String court,
    String chamber,
    String docketNo,
    String decisionNo,
    String date,
    String topic,
    String summary,
    String content,
    String outcome
) {
}

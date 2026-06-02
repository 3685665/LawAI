package com.lawai.api.dto;

public record KnowledgeDocumentRequest(
    String sourceType,
    String court,
    String chamber,
    String docketNo,
    String decisionNo,
    String date,
    String topic,
    String summary,
    String content
) {
}

package com.lawai.api.dto;

public record PrecedentSearchRequest(
    String query,
    String court,
    String chamber,
    String docketNo,
    String decisionNo,
    String dateFrom,
    String dateTo,
    Integer limit
) {
}

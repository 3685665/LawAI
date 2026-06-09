package com.lawai.api.dto;

public record PrecedentApplyResponse(
    String applicationNote,
    String legalGroundsSnippet,
    String factsLinkSnippet,
    String citationLine,
    String disclaimer
) {
}

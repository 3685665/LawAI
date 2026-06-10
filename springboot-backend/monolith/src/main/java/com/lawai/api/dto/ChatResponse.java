package com.lawai.api.dto;

import java.util.List;

public record ChatResponse(String answer, List<PrecedentDto> citations, List<String> nextSteps, String disclaimer, String sessionId) {
}

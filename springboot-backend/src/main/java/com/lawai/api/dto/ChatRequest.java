package com.lawai.api.dto;

public record ChatRequest(String question, String mode, Boolean privateMode) {
}

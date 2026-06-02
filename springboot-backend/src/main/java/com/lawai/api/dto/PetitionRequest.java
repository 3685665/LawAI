package com.lawai.api.dto;

public record PetitionRequest(String petitionType, String court, String parties, String facts, String demands) {
}

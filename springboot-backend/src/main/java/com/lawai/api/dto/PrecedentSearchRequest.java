package com.lawai.api.dto;

public record PrecedentSearchRequest(String query, String court, String chamber, Integer limit) {
}

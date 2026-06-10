package com.lawai.api.dto;

import java.util.List;

public record PrecedentSearchResponse(String query, List<PrecedentDto> results) {
}

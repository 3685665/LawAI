package com.lawai.api.dto;

import java.util.List;

public record PetitionResponse(String title, String body, List<PrecedentDto> citedPrecedents) {
}

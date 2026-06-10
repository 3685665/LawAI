package com.lawai.api.research.dto;

import com.lawai.api.research.ResearchSource;

import java.util.List;

public record ResearchSourceResultDto(ResearchSource source, List<String> findings) {
}

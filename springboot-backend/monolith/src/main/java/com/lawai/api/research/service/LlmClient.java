package com.lawai.api.research.service;

import com.lawai.api.research.dto.ResearchSourceResultDto;

import java.util.List;

public interface LlmClient {

  String synthesizeAnswer(String query, List<ResearchSourceResultDto> sourceResults);
}

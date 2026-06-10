package com.lawai.api.research.dto;

import com.lawai.api.research.ResearchSource;

import java.util.List;

public record LegalResearchSynthesizeRequest(String query, List<SourceFinding> sourceResults) {

  public record SourceFinding(ResearchSource source, List<String> findings) {
  }
}

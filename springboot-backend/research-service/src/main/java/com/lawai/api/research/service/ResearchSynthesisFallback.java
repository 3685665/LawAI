package com.lawai.api.research.service;

import com.lawai.api.research.ResearchSource;
import com.lawai.api.research.dto.ResearchSourceResultDto;

import java.util.List;
import java.util.stream.Collectors;

final class ResearchSynthesisFallback {

  private ResearchSynthesisFallback() {
  }

  static String synthesize(String query, List<ResearchSourceResultDto> sourceResults) {
    String findingsSummary = sourceResults.stream()
        .map(ResearchSynthesisFallback::formatSourceBlock)
        .collect(Collectors.joining("\n\n"));

    return """
        ## Hukuki Arastirma Ozeti: %s

        ### Arastirma Bulgulari
        %s

        ### Not
        AI servisi su anda yanit veremedi; yukaridaki bulgular otomatik olarak derlendi. Tam sentez icin Python AI servisini ve LLM saglayicisini kontrol edin.
        """.formatted(query, findingsSummary);
  }

  private static String formatSourceBlock(ResearchSourceResultDto result) {
    String title = switch (result.source()) {
      case LEGISLATION -> "Mevzuat";
      case CASE_LAW -> "Ictihat";
      case WEB -> "Web";
    };
    if (result.findings() == null || result.findings().isEmpty()) {
      return "**" + title + "**\n- Sonuc bulunamadi.";
    }
    String items = result.findings().stream()
        .map(finding -> "- " + finding)
        .collect(Collectors.joining("\n"));
    return "**" + title + "**\n" + items;
  }
}

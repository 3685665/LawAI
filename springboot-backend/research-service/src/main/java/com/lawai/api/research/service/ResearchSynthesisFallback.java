package com.lawai.api.research.service;

import com.lawai.api.research.ResearchSource;
import com.lawai.api.research.dto.ResearchSourceResultDto;
import com.lawai.common.i18n.I18nMessages;

import java.util.List;
import java.util.stream.Collectors;

final class ResearchSynthesisFallback {

  private ResearchSynthesisFallback() {
  }

  static String synthesize(String query, List<ResearchSourceResultDto> sourceResults, I18nMessages i18n) {
    String findingsSummary = sourceResults.stream()
        .map(result -> formatSourceBlock(result, i18n))
        .collect(Collectors.joining("\n\n"));

    return """
        %s

        %s
        %s

        %s
        %s
        """.formatted(
            i18n.get("research.local-title", query),
            i18n.get("research.findings-title"),
            findingsSummary,
            i18n.get("research.note-title"),
            i18n.get("research.ai-fallback-note")
        );
  }

  private static String formatSourceBlock(ResearchSourceResultDto result, I18nMessages i18n) {
    String title = switch (result.source()) {
      case LEGISLATION -> i18n.get("research.source.legislation");
      case CASE_LAW -> i18n.get("research.source.case-law");
      case WEB -> i18n.get("research.source.web");
    };
    if (result.findings() == null || result.findings().isEmpty()) {
      return "**" + title + "**\n" + i18n.get("research.no-results");
    }
    String items = result.findings().stream()
        .map(finding -> "- " + finding)
        .collect(Collectors.joining("\n"));
    return "**" + title + "**\n" + items;
  }
}

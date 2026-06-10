package com.lawai.api.research.service;

import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.dto.PrecedentSearchRequest;
import com.lawai.api.service.PrecedentSearchService;
import com.lawai.config.ResearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(prefix = "app.research", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
public class PrecedentCaseLawSearchService implements CaseLawSearchService {

  private static final Logger log = LoggerFactory.getLogger(PrecedentCaseLawSearchService.class);

  private final PrecedentSearchService precedentSearchService;
  private final ResearchProperties researchProperties;

  public PrecedentCaseLawSearchService(
      PrecedentSearchService precedentSearchService,
      ResearchProperties researchProperties
  ) {
    this.precedentSearchService = precedentSearchService;
    this.researchProperties = researchProperties;
  }

  @Override
  public List<String> search(String query) {
    log.info("Ictihat aramasi (Yargitay/Danistay/AYM): query={}", query);
    try {
      PrecedentSearchRequest request = new PrecedentSearchRequest(
          query,
          null,
          null,
          null,
          null,
          null,
          null,
          researchProperties.caseLawSearchLimit()
      );
      return precedentSearchService.search(request).stream()
          .map(this::formatFinding)
          .filter(StringUtils::hasText)
          .toList();
    } catch (RuntimeException exception) {
      log.warn("Ictihat aramasi basarisiz, bos sonuc donuluyor: {}", exception.getMessage());
      return List.of();
    }
  }

  private String formatFinding(PrecedentDto precedent) {
    String reference = Stream.of(precedent.docketNo(), precedent.decisionNo(), precedent.date())
        .filter(StringUtils::hasText)
        .collect(Collectors.joining(" / "));
    String summary = StringUtils.hasText(precedent.summary()) ? precedent.summary() : precedent.topic();
    return precedent.court()
        + (StringUtils.hasText(precedent.chamber()) ? " " + precedent.chamber() : "")
        + " — "
        + precedent.topic()
        + (StringUtils.hasText(reference) ? " (" + reference + ")" : "")
        + ": "
        + preview(summary, 180);
  }

  private String preview(String content, int maxLength) {
    if (!StringUtils.hasText(content)) {
      return "";
    }
    String normalized = content.replaceAll("\\s+", " ").trim();
    return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim() + "...";
  }
}

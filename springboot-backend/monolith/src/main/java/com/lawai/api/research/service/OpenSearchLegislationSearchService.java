package com.lawai.api.research.service;

import com.lawai.config.ResearchProperties;
import com.lawai.document.dto.DocumentSearchResult;
import com.lawai.document.service.DocumentProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.research", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
public class OpenSearchLegislationSearchService implements LegislationSearchService {

  private static final Logger log = LoggerFactory.getLogger(OpenSearchLegislationSearchService.class);

  private final DocumentProcessingService documentProcessingService;
  private final ResearchProperties researchProperties;

  public OpenSearchLegislationSearchService(
      DocumentProcessingService documentProcessingService,
      ResearchProperties researchProperties
  ) {
    this.documentProcessingService = documentProcessingService;
    this.researchProperties = researchProperties;
  }

  @Override
  public List<String> search(String query) {
    log.info("Mevzuat aramasi (OpenSearch/pgvector): query={}", query);
    try {
      return documentProcessingService.search(query, researchProperties.legislationSearchLimit())
          .results()
          .stream()
          .map(this::formatFinding)
          .filter(StringUtils::hasText)
          .toList();
    } catch (RuntimeException exception) {
      log.warn("Mevzuat aramasi basarisiz, bos sonuc donuluyor: {}", exception.getMessage());
      return List.of();
    }
  }

  private String formatFinding(DocumentSearchResult result) {
    String preview = preview(result.content(), 220);
    return result.filename() + " — " + preview;
  }

  private String preview(String content, int maxLength) {
    if (!StringUtils.hasText(content)) {
      return "Icerik bulunamadi.";
    }
    String normalized = content.replaceAll("\\s+", " ").trim();
    return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim() + "...";
  }
}

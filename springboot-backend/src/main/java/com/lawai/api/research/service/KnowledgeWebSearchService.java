package com.lawai.api.research.service;

import com.lawai.api.dto.PrecedentSearchRequest;
import com.lawai.api.service.AiServiceClient;
import com.lawai.config.ResearchProperties;
import com.lawai.document.dto.DocumentSearchResult;
import com.lawai.document.service.DocumentProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "app.research", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
public class KnowledgeWebSearchService implements WebSearchService {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeWebSearchService.class);

  private final DocumentProcessingService documentProcessingService;
  private final AiServiceClient aiServiceClient;
  private final ResearchProperties researchProperties;

  public KnowledgeWebSearchService(
      DocumentProcessingService documentProcessingService,
      AiServiceClient aiServiceClient,
      ResearchProperties researchProperties
  ) {
    this.documentProcessingService = documentProcessingService;
    this.aiServiceClient = aiServiceClient;
    this.researchProperties = researchProperties;
  }

  @Override
  public List<String> search(String query) {
    log.info("Web/knowledge aramasi: query={}", query);
    Set<String> findings = new LinkedHashSet<>();
    collectIndexedDocuments(query, findings);
    collectKnowledgeBase(query, findings);
    if (findings.isEmpty()) {
      findings.add("Harici web arama kaynagi henuz bagli degil; indekslenmis belge bulunamadi.");
    }
    return List.copyOf(findings);
  }

  private void collectIndexedDocuments(String query, Set<String> findings) {
    try {
      for (DocumentSearchResult result : documentProcessingService.searchWholeDocuments(query, researchProperties.webSearchLimit()).results()) {
        findings.add("Indekslenmis kaynak — " + result.filename() + ": " + preview(result.content(), 200));
        if (findings.size() >= researchProperties.webSearchLimit()) {
          return;
        }
      }
    } catch (RuntimeException exception) {
      log.warn("Indekslenmis belge aramasi basarisiz: {}", exception.getMessage());
    }
  }

  private void collectKnowledgeBase(String query, Set<String> findings) {
    try {
      aiServiceClient.searchPrecedents(new PrecedentSearchRequest(
          query,
          null,
          null,
          null,
          null,
          null,
          null,
          researchProperties.webSearchLimit()
      )).results().forEach(precedent -> {
        if (findings.size() >= researchProperties.webSearchLimit()) {
          return;
        }
        findings.add("Bilgi bankasi — " + precedent.court() + ": " + precedent.topic() + " — " + preview(precedent.summary(), 160));
      });
    } catch (RuntimeException exception) {
      log.warn("Bilgi bankasi aramasi basarisiz: {}", exception.getMessage());
    }
  }

  private String preview(String content, int maxLength) {
    if (!StringUtils.hasText(content)) {
      return "";
    }
    String normalized = content.replaceAll("\\s+", " ").trim();
    return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim() + "...";
  }
}

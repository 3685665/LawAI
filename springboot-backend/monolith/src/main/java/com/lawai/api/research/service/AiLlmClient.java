package com.lawai.api.research.service;

import com.lawai.api.research.dto.LegalResearchSynthesizeRequest;
import com.lawai.api.research.dto.LegalResearchSynthesizeResponse;
import com.lawai.api.research.dto.ResearchSourceResultDto;
import com.lawai.api.service.AiServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.research", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
public class AiLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(AiLlmClient.class);

  private final AiServiceClient aiServiceClient;

  public AiLlmClient(AiServiceClient aiServiceClient) {
    this.aiServiceClient = aiServiceClient;
  }

  @Override
  public String synthesizeAnswer(String query, List<ResearchSourceResultDto> sourceResults) {
    log.info("LLM sentezi (Python AI servisi): query={}", query);
    LegalResearchSynthesizeRequest request = new LegalResearchSynthesizeRequest(
        query,
        sourceResults.stream()
            .map(result -> new LegalResearchSynthesizeRequest.SourceFinding(result.source(), result.findings()))
            .toList()
    );
    try {
      LegalResearchSynthesizeResponse response = aiServiceClient.synthesizeResearch(request);
      return response.answer();
    } catch (RuntimeException exception) {
      log.warn("AI sentez basarisiz, yerel ozet kullaniliyor: {}", exception.getMessage());
      return ResearchSynthesisFallback.synthesize(query, sourceResults);
    }
  }
}

package com.lawai.api.research.service.mock;

import com.lawai.api.research.ResearchSource;
import com.lawai.api.research.dto.ResearchSourceResultDto;
import com.lawai.api.research.service.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "app.research", name = "mock-enabled", havingValue = "true")
public class MockLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

  @Override
  public String synthesizeAnswer(String query, List<ResearchSourceResultDto> sourceResults) {
    log.info("Mock LLM sentezi: query={}, kaynakSayisi={}", query, sourceResults.size());
    simulateLatency();

    String findingsSummary = sourceResults.stream()
        .map(result -> formatSourceBlock(result))
        .collect(Collectors.joining("\n\n"));

    return """
        ## Hukuki Arastirma Ozeti: %s

        ### Genel Degerlendirme
        "%s" konusunda mevzuat, ictihat ve web kaynaklari incelenmistir. Asagida elde edilen bulgulara dayanan on analiz sunulmaktadir.

        ### Kaynak Bulgulari
        %s

        ### Sonuc ve Oneriler
        1. Ilgili kanun maddeleri ve nitelikli haller birlikte degerlendirilmelidir.
        2. Yargitay kararlari uygulamadaki yorum farkliliklarini gostermektedir.
        3. Guncel mevzuat degisiklikleri resmi kaynaklardan teyit edilmelidir.

        Bu yanit mock veri ile uretilmistir; gercek entegrasyon sonrasinda OpenAI, Claude veya Ollama uzerinden sentezlenecektir.
        """.formatted(query, query, findingsSummary);
  }

  private String formatSourceBlock(ResearchSourceResultDto result) {
    String title = switch (result.source()) {
      case LEGISLATION -> "Mevzuat";
      case CASE_LAW -> "Ictihat";
      case WEB -> "Web";
    };
    String items = result.findings().stream()
        .map(finding -> "- " + finding)
        .collect(Collectors.joining("\n"));
    return "**" + title + "**\n" + items;
  }

  private void simulateLatency() {
    try {
      Thread.sleep(600);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}

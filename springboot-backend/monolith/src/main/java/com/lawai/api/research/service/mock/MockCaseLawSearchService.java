package com.lawai.api.research.service.mock;

import com.lawai.api.research.service.CaseLawSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.research", name = "mock-enabled", havingValue = "true")
public class MockCaseLawSearchService implements CaseLawSearchService {

  private static final Logger log = LoggerFactory.getLogger(MockCaseLawSearchService.class);

  @Override
  public List<String> search(String query) {
    log.info("Mock ictihat aramasi: query={}", query);
    simulateLatency();
    return List.of(
        "Yargitay 1. Ceza Dairesi — Ornek karar (agir ceza)",
        "Yargitay CGK — Nitelikli hal uygulamasi ornegi",
        "Danistay — Idari yaptirim ile ceza hukuku iliskisi (ornek)"
    );
  }

  private void simulateLatency() {
    try {
      Thread.sleep(500);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}

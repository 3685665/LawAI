package com.lawai.api.research.service.mock;

import com.lawai.api.research.service.LegislationSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.research", name = "mock-enabled", havingValue = "true")
public class MockLegislationSearchService implements LegislationSearchService {

  private static final Logger log = LoggerFactory.getLogger(MockLegislationSearchService.class);

  @Override
  public List<String> search(String query) {
    log.info("Mock mevzuat aramasi: query={}", query);
    simulateLatency();
    return List.of(
        "TCK Madde 81 — Kasten öldürme",
        "TCK Madde 82 — Nitelikli haller (ağır ceza)",
        "5237 sayili Turk Ceza Kanunu — Agir ceza mahkemelerinin gorevi"
    );
  }

  private void simulateLatency() {
    try {
      Thread.sleep(400);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}

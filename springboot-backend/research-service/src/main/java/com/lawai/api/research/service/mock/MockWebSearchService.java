package com.lawai.api.research.service.mock;

import com.lawai.api.research.service.WebSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.research", name = "mock-enabled", havingValue = "true")
public class MockWebSearchService implements WebSearchService {

  private static final Logger log = LoggerFactory.getLogger(MockWebSearchService.class);

  @Override
  public List<String> search(String query) {
    log.info("Mock web aramasi: query={}", query);
    simulateLatency();
    return List.of(
        "Resmi Gazete — Guncel mevzuat degisikligi ozeti (ornek)",
        "Adalet Bakanligi — Agir ceza mahkemeleri bilgilendirme sayfasi (ornek)",
        "Hukuk portali — Konu bazli arastirma notu (ornek)"
    );
  }

  private void simulateLatency() {
    try {
      Thread.sleep(350);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}

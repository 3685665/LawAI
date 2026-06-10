package com.lawai.document.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BatchDocumentScheduler {

  private static final Logger log = LoggerFactory.getLogger(BatchDocumentScheduler.class);

  private final BatchDocumentProcessingService processingService;

  public BatchDocumentScheduler(BatchDocumentProcessingService processingService) {
    this.processingService = processingService;
  }

  @Scheduled(fixedRate = 60_000, initialDelay = 30_000)
  public void runDueJobs() {
    try {
      processingService.runDueJobs();
    } catch (Exception exception) {
      log.warn("Zamanlanmis belge isleme kontrolu basarisiz: {}", exception.getMessage());
    }
  }
}

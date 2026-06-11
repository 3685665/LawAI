package com.lawai.document.precedentsync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PrecedentSyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(PrecedentSyncScheduler.class);

  private final PrecedentSyncService precedentSyncService;

  public PrecedentSyncScheduler(PrecedentSyncService precedentSyncService) {
    this.precedentSyncService = precedentSyncService;
  }

  @Scheduled(fixedRate = 60_000, initialDelay = 45_000)
  public void runDueTasks() {
    try {
      precedentSyncService.runDueTasks();
    } catch (Exception exception) {
      log.warn("Mahkeme karari senkronizasyon kontrolu basarisiz: {}", exception.getMessage());
    }
  }
}

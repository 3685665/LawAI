package com.lawai.document.precedentsync;

import com.lawai.common.client.ActivityLogClient;
import com.lawai.common.model.AuthenticatedUser;
import com.lawai.document.batch.BatchPrecedentIngestService;
import com.lawai.document.batch.PrecedentCourt;
import com.lawai.document.dto.DocumentUploadResponse;
import com.lawai.document.precedentsync.dto.PrecedentSyncRunDto;
import com.lawai.document.precedentsync.dto.PrecedentSyncTaskDto;
import com.lawai.document.precedentsync.dto.PrecedentSyncTaskRequest;
import com.lawai.document.service.DocumentProcessingService;
import com.lawai.document.service.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PrecedentSyncService {

  private static final int DEFAULT_MAX_DOCUMENTS = 500;
  private static final int DEFAULT_INTERVAL_MINUTES = 60;

  private final PrecedentSyncRepository repository;
  private final BatchPrecedentIngestService batchPrecedentIngestService;
  private final DocumentProcessingService documentProcessingService;
  private final DocumentRepository documentRepository;
  private final ActivityLogClient activityLogClient;

  public PrecedentSyncService(
      PrecedentSyncRepository repository,
      BatchPrecedentIngestService batchPrecedentIngestService,
      DocumentProcessingService documentProcessingService,
      DocumentRepository documentRepository,
      ActivityLogClient activityLogClient
  ) {
    this.repository = repository;
    this.batchPrecedentIngestService = batchPrecedentIngestService;
    this.documentProcessingService = documentProcessingService;
    this.documentRepository = documentRepository;
    this.activityLogClient = activityLogClient;
  }

  public List<PrecedentSyncTaskDto> listTasks(AuthenticatedUser user) {
    requireAdmin(user);
    return repository.listTasks();
  }

  public PrecedentSyncTaskDto createTask(AuthenticatedUser user, PrecedentSyncTaskRequest request) {
    requireAdmin(user);
    validateRequest(request);
    List<PrecedentCourt> courts = parseCourts(request.courts());
    String name = resolveName(request);
    Instant nextRunAt = resolveNextRunAt(Boolean.TRUE.equals(request.enabled()), request.intervalMinutes(), Instant.now());
    long id = repository.createTask(
        name,
        PrecedentCourt.toCsv(courts),
        request.dateFrom(),
        request.dateTo(),
        normalizeMaxDocuments(request.maxDocumentsPerRun()),
        normalizeInterval(request.intervalMinutes()),
        Boolean.TRUE.equals(request.enabled()),
        user.id(),
        user.name(),
        nextRunAt
    );
    activityLogClient.logBackend(user, "precedent_sync_create", "admin-precedent-sync", name, "/api/precedent-sync/tasks");
    return repository.findTask(id).orElseThrow(() -> new IllegalStateException("Gorev olusturulamadi."));
  }

  public PrecedentSyncTaskDto updateTask(AuthenticatedUser user, long id, PrecedentSyncTaskRequest request) {
    requireAdmin(user);
    repository.findTask(id).orElseThrow(() -> new IllegalArgumentException("Gorev bulunamadi."));
    validateRequest(request);
    List<PrecedentCourt> courts = parseCourts(request.courts());
    String name = resolveName(request);
    Instant nextRunAt = resolveNextRunAt(Boolean.TRUE.equals(request.enabled()), request.intervalMinutes(), Instant.now());
    repository.updateTask(
        id,
        name,
        PrecedentCourt.toCsv(courts),
        request.dateFrom(),
        request.dateTo(),
        normalizeMaxDocuments(request.maxDocumentsPerRun()),
        normalizeInterval(request.intervalMinutes()),
        Boolean.TRUE.equals(request.enabled()),
        nextRunAt
    );
    activityLogClient.logBackend(user, "precedent_sync_update", "admin-precedent-sync", name, "/api/precedent-sync/tasks/" + id);
    return repository.findTask(id).orElseThrow(() -> new IllegalStateException("Gorev guncellenemedi."));
  }

  public void deleteTask(AuthenticatedUser user, long id) {
    requireAdmin(user);
    PrecedentSyncTaskDto task = repository.findTask(id).orElseThrow(() -> new IllegalArgumentException("Gorev bulunamadi."));
    repository.deleteTask(id);
    activityLogClient.logBackend(user, "precedent_sync_delete", "admin-precedent-sync", task.name(), "/api/precedent-sync/tasks/" + id);
  }

  public PrecedentSyncRunDto triggerTask(AuthenticatedUser user, long id) {
    requireAdmin(user);
    PrecedentSyncTaskDto task = repository.findTask(id).orElseThrow(() -> new IllegalArgumentException("Gorev bulunamadi."));
    PrecedentSyncRunDto run = executeTask(task, "MANUAL");
    activityLogClient.logBackend(
        user,
        "precedent_sync_trigger",
        "admin-precedent-sync",
        task.name() + " -> " + run.successCount() + "/" + run.totalFiles(),
        "/api/precedent-sync/tasks/" + id + "/run"
    );
    return run;
  }

  public List<PrecedentSyncRunDto> listRuns(AuthenticatedUser user, Long taskId, int limit) {
    requireAdmin(user);
    return repository.listRuns(taskId, Math.min(Math.max(limit, 1), 100));
  }

  public PrecedentSyncRunDto getRun(AuthenticatedUser user, long runId) {
    requireAdmin(user);
    return repository.findRun(runId).orElseThrow(() -> new IllegalArgumentException("Calistirma kaydi bulunamadi."));
  }

  public void runDueTasks() {
    Instant now = Instant.now();
    for (PrecedentSyncTaskDto task : repository.findDueTasks(now)) {
      executeTask(task, "SCHEDULED");
    }
  }

  private PrecedentSyncRunDto executeTask(PrecedentSyncTaskDto task, String triggerType) {
    repository.setTaskStatus(task.id(), PrecedentSyncTaskStatus.RUNNING);
    long runId = repository.createRun(task.id(), triggerType, PrecedentSyncRunStatus.RUNNING);
    List<BatchPrecedentIngestService.PrecedentWorkItem> workItems;
    try {
      List<PrecedentCourt> courts = task.courts().stream()
          .map(value -> PrecedentCourt.valueOf(value.trim().toUpperCase(Locale.ROOT)))
          .toList();
      workItems = batchPrecedentIngestService.collectWorkItems(
          courts,
          task.dateFrom(),
          task.dateTo(),
          task.maxDocumentsPerRun()
      );
    } catch (Exception exception) {
      repository.finishRun(runId, PrecedentSyncRunStatus.FAILED, 0, 0, 0, 0, "Karar listesi alinamadi: " + exception.getMessage());
      repository.updateTaskRunState(task.id(), Instant.now(), computeNextRun(task, Instant.now()));
      return repository.findRun(runId).orElseThrow(() -> new IllegalStateException("Calistirma kaydi olusturulamadi."));
    }

    int successCount = 0;
    int failedCount = 0;
    int skippedCount = 0;

    for (BatchPrecedentIngestService.PrecedentWorkItem item : workItems) {
      String filename = item.filename();
      try {
        if (documentRepository.existsByStoredPath(item.storedPath())) {
          skippedCount += 1;
          repository.createRunFile(
              runId,
              filename,
              item.storedPath(),
              "SKIPPED",
              null,
              null,
              null,
              "Karar daha once islendi."
          );
          continue;
        }
        String plainText = batchPrecedentIngestService.fetchPlainText(item.court(), item.sourceId());
        if (!StringUtils.hasText(plainText)) {
          skippedCount += 1;
          repository.createRunFile(
              runId,
              filename,
              item.storedPath(),
              "SKIPPED",
              null,
              null,
              null,
              "Karar metni bos."
          );
          continue;
        }
        DocumentUploadResponse response = documentProcessingService.processText(filename, plainText, item.storedPath());
        repository.createRunFile(
            runId,
            filename,
            item.storedPath(),
            "SUCCESS",
            response.documentId(),
            response.extractedCharacters(),
            response.chunkCount(),
            null
        );
        successCount += 1;
      } catch (Exception exception) {
        failedCount += 1;
        repository.createRunFile(
            runId,
            filename,
            item.storedPath(),
            "FAILED",
            null,
            null,
            null,
            exception.getMessage()
        );
      }
    }

    int totalFiles = workItems.size();
    PrecedentSyncRunStatus status = resolveStatus(totalFiles, successCount, failedCount);
    String summary = buildSummary(totalFiles, successCount, failedCount, skippedCount, task);
    repository.finishRun(runId, status, totalFiles, successCount, failedCount, skippedCount, summary);

    Instant finishedAt = Instant.now();
    Instant nextRunAt = task.enabled() ? computeNextRun(task, finishedAt) : null;
    repository.updateTaskRunState(task.id(), finishedAt, nextRunAt);

    return repository.findRun(runId).orElseThrow(() -> new IllegalStateException("Calistirma kaydi olusturulamadi."));
  }

  private PrecedentSyncRunStatus resolveStatus(int totalFiles, int successCount, int failedCount) {
    if (totalFiles == 0) {
      return PrecedentSyncRunStatus.FAILED;
    }
    if (failedCount == 0) {
      return PrecedentSyncRunStatus.COMPLETED;
    }
    if (successCount == 0) {
      return PrecedentSyncRunStatus.FAILED;
    }
    return PrecedentSyncRunStatus.PARTIAL;
  }

  private String buildSummary(int totalFiles, int successCount, int failedCount, int skippedCount, PrecedentSyncTaskDto task) {
    if (totalFiles == 0) {
      return "Secilen mahkeme ve tarih araliginda yeni karar bulunamadi.";
    }
    String courts = task.courts() == null || task.courts().isEmpty()
        ? "mahkeme"
        : String.join(", ", task.courts());
    return successCount + " karar islendi, " + failedCount + " basarisiz, " + skippedCount + " atlandi. "
        + courts + " kaynaklarindan toplam " + totalFiles + " karar tarandi.";
  }

  private void validateRequest(PrecedentSyncTaskRequest request) {
    if (parseCourts(request.courts()).isEmpty()) {
      throw new IllegalArgumentException("En az bir mahkeme secin.");
    }
    if (request.dateFrom() == null || request.dateTo() == null) {
      throw new IllegalArgumentException("Baslangic ve bitis tarihi gerekli.");
    }
    if (request.dateFrom().isAfter(request.dateTo())) {
      throw new IllegalArgumentException("Baslangic tarihi bitis tarihinden sonra olamaz.");
    }
  }

  private List<PrecedentCourt> parseCourts(List<String> courts) {
    if (courts == null || courts.isEmpty()) {
      return List.of();
    }
    List<PrecedentCourt> parsed = new ArrayList<>();
    for (String court : courts) {
      if (!StringUtils.hasText(court)) {
        continue;
      }
      parsed.add(PrecedentCourt.valueOf(court.trim().toUpperCase(Locale.ROOT)));
    }
    return parsed;
  }

  private String resolveName(PrecedentSyncTaskRequest request) {
    if (request.name() != null && !request.name().isBlank()) {
      return request.name().trim();
    }
    String courts = request.courts() == null ? "ictihat" : String.join("-", request.courts()).toLowerCase(Locale.ROOT);
    return courts + "-" + request.dateFrom() + "-" + request.dateTo();
  }

  private Instant resolveNextRunAt(boolean enabled, Integer intervalMinutes, Instant from) {
    if (!enabled) {
      return null;
    }
    return from.plusSeconds(normalizeInterval(intervalMinutes) * 60L);
  }

  private Instant computeNextRun(PrecedentSyncTaskDto task, Instant finishedAt) {
    return finishedAt.plusSeconds(task.intervalMinutes() * 60L);
  }

  private int normalizeMaxDocuments(Integer value) {
    if (value == null) {
      return DEFAULT_MAX_DOCUMENTS;
    }
    return Math.min(Math.max(value, 1), 5000);
  }

  private int normalizeInterval(Integer value) {
    if (value == null) {
      return DEFAULT_INTERVAL_MINUTES;
    }
    return Math.min(Math.max(value, 5), 1440);
  }

  private void requireAdmin(AuthenticatedUser user) {
    if (user == null || !user.isAdmin()) {
      throw new IllegalArgumentException("Bu islem icin yonetici yetkisi gerekli.");
    }
  }
}

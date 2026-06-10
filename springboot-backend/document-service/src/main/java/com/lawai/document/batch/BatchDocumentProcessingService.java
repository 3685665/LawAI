package com.lawai.document.batch;

import com.lawai.common.client.ActivityLogClient;
import com.lawai.common.model.AuthenticatedUser;
import com.lawai.document.batch.dto.BatchDocumentJobDto;
import com.lawai.document.batch.dto.BatchDocumentJobRequest;
import com.lawai.document.batch.dto.BatchDocumentRunDto;
import com.lawai.document.batch.dto.BatchDocumentRunFileDto;
import com.lawai.document.dto.DocumentUploadResponse;
import com.lawai.document.service.DocumentProcessingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class BatchDocumentProcessingService {

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf", ".txt", ".doc", ".docx");

  private final BatchDocumentJobRepository repository;
  private final DocumentProcessingService documentProcessingService;
  private final ActivityLogClient activityLogClient;

  public BatchDocumentProcessingService(
      BatchDocumentJobRepository repository,
      DocumentProcessingService documentProcessingService,
      ActivityLogClient activityLogClient
  ) {
    this.repository = repository;
    this.documentProcessingService = documentProcessingService;
    this.activityLogClient = activityLogClient;
  }

  public List<BatchDocumentJobDto> listJobs(AuthenticatedUser user) {
    requireAdmin(user);
    return repository.listJobs();
  }

  public BatchDocumentJobDto createJob(AuthenticatedUser user, BatchDocumentJobRequest request) {
    requireAdmin(user);
    BatchScheduleType scheduleType = parseScheduleType(request.scheduleType());
    LocalTime scheduledTime = LocalTime.parse(request.scheduledTime());
    validateSchedule(scheduleType, request.scheduledDate(), request.dayOfWeek(), request.dayOfMonth());
    Path directory = resolveDirectory(request.directoryPath());
    String jobName = BatchJobNameGenerator.resolveName(request, directory);
    Instant nextRunAt = resolveNextRunAt(scheduleType, scheduledTime, request);
    long id = repository.createJob(
        jobName,
        directory.toString(),
        scheduleType,
        scheduledTime,
        request.scheduledDate(),
        request.dayOfWeek(),
        request.dayOfMonth(),
        Boolean.TRUE.equals(request.enabled()),
        user.id(),
        user.name(),
        nextRunAt
    );
    activityLogClient.logBackend(user, "batch_job_create", "admin-batch-documents", jobName, "/api/batch-documents/jobs");
    return repository.findJob(id).orElseThrow(() -> new IllegalStateException("Gorev olusturulamadi."));
  }

  public BatchDocumentJobDto updateJob(AuthenticatedUser user, long id, BatchDocumentJobRequest request) {
    requireAdmin(user);
    repository.findJob(id).orElseThrow(() -> new IllegalArgumentException("Gorev bulunamadi."));
    BatchScheduleType scheduleType = parseScheduleType(request.scheduleType());
    LocalTime scheduledTime = LocalTime.parse(request.scheduledTime());
    validateSchedule(scheduleType, request.scheduledDate(), request.dayOfWeek(), request.dayOfMonth());
    Path directory = resolveDirectory(request.directoryPath());
    String jobName = BatchJobNameGenerator.resolveName(request, directory);
    Instant nextRunAt = resolveNextRunAt(scheduleType, scheduledTime, request);
    repository.updateJob(
        id,
        jobName,
        directory.toString(),
        scheduleType,
        scheduledTime,
        request.scheduledDate(),
        request.dayOfWeek(),
        request.dayOfMonth(),
        Boolean.TRUE.equals(request.enabled()),
        nextRunAt
    );
    activityLogClient.logBackend(user, "batch_job_update", "admin-batch-documents", jobName, "/api/batch-documents/jobs/" + id);
    return repository.findJob(id).orElseThrow(() -> new IllegalStateException("Gorev guncellenemedi."));
  }

  public void deleteJob(AuthenticatedUser user, long id) {
    requireAdmin(user);
    BatchDocumentJobDto job = repository.findJob(id).orElseThrow(() -> new IllegalArgumentException("Gorev bulunamadi."));
    repository.deleteJob(id);
    activityLogClient.logBackend(user, "batch_job_delete", "admin-batch-documents", job.name(), "/api/batch-documents/jobs/" + id);
  }

  public BatchDocumentRunDto triggerJob(AuthenticatedUser user, long id) {
    requireAdmin(user);
    BatchDocumentJobDto job = repository.findJob(id).orElseThrow(() -> new IllegalArgumentException("Gorev bulunamadi."));
    BatchDocumentRunDto run = executeJob(job, "MANUAL");
    activityLogClient.logBackend(
        user,
        "batch_job_trigger",
        "admin-batch-documents",
        job.name() + " -> " + run.successCount() + "/" + run.totalFiles(),
        "/api/batch-documents/jobs/" + id + "/run"
    );
    return run;
  }

  public List<BatchDocumentRunDto> listRuns(AuthenticatedUser user, Long jobId, int limit) {
    requireAdmin(user);
    return repository.listRuns(jobId, Math.min(Math.max(limit, 1), 100));
  }

  public BatchDocumentRunDto getRun(AuthenticatedUser user, long runId) {
    requireAdmin(user);
    return repository.findRun(runId).orElseThrow(() -> new IllegalArgumentException("Calistirma kaydi bulunamadi."));
  }

  public void runDueJobs() {
    Instant now = Instant.now();
    for (BatchDocumentJobDto job : repository.findDueJobs(now)) {
      executeJob(job, "SCHEDULED");
    }
  }

  private BatchDocumentRunDto executeJob(BatchDocumentJobDto job, String triggerType) {
    long runId = repository.createRun(job.id(), triggerType, BatchRunStatus.RUNNING);
    Path directory = Path.of(job.directoryPath());
    List<Path> files = listSupportedFiles(directory);
    int successCount = 0;
    int failedCount = 0;
    int skippedCount = 0;

    for (Path file : files) {
      String filename = file.getFileName().toString();
      try {
        if (!Files.isRegularFile(file)) {
          skippedCount += 1;
          repository.createRunFile(runId, filename, file.toString(), null, BatchFileStatus.SKIPPED, null, null, null, "Dosya okunamadi.");
          continue;
        }
        long size = Files.size(file);
        DocumentUploadResponse response = documentProcessingService.processFile(file);
        repository.createRunFile(
            runId,
            filename,
            file.toString(),
            size,
            BatchFileStatus.SUCCESS,
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
            file.toString(),
            safeSize(file),
            BatchFileStatus.FAILED,
            null,
            null,
            null,
            exception.getMessage()
        );
      }
    }

    int totalFiles = files.size();
    BatchRunStatus status = resolveStatus(totalFiles, successCount, failedCount);
    String summary = buildSummary(totalFiles, successCount, failedCount, skippedCount);
    repository.finishRun(runId, status, totalFiles, successCount, failedCount, skippedCount, summary);

    Instant finishedAt = Instant.now();
    boolean keepEnabled = !BatchScheduleType.ONCE.name().equals(job.scheduleType()) && job.enabled();
    Instant nextRunAt = keepEnabled ? BatchScheduleCalculator.computeNextRunAfterExecution(job, finishedAt) : null;
    repository.updateJobRunState(job.id(), finishedAt, nextRunAt, keepEnabled);

    return repository.findRun(runId).orElseThrow(() -> new IllegalStateException("Calistirma kaydi olusturulamadi."));
  }

  private BatchRunStatus resolveStatus(int totalFiles, int successCount, int failedCount) {
    if (totalFiles == 0) {
      return BatchRunStatus.FAILED;
    }
    if (failedCount == 0) {
      return BatchRunStatus.COMPLETED;
    }
    if (successCount == 0) {
      return BatchRunStatus.FAILED;
    }
    return BatchRunStatus.PARTIAL;
  }

  private String buildSummary(int totalFiles, int successCount, int failedCount, int skippedCount) {
    if (totalFiles == 0) {
      return "Dizinde islenecek desteklenen dosya bulunamadi.";
    }
    return successCount + " dosya basariyla islendi, " + failedCount + " basarisiz, " + skippedCount + " atlandi. Toplam " + totalFiles + " dosya tarandi.";
  }

  private List<Path> listSupportedFiles(Path directory) {
    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException("Dizin bulunamadi: " + directory);
    }
    try (Stream<Path> stream = Files.list(directory)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> isSupportedExtension(path.getFileName().toString()))
          .sorted((left, right) -> left.getFileName().toString().compareToIgnoreCase(right.getFileName().toString()))
          .toList();
    } catch (Exception exception) {
      throw new IllegalStateException("Dizin okunamadi: " + exception.getMessage(), exception);
    }
  }

  private Path resolveDirectory(String directoryPath) {
    if (!StringUtils.hasText(directoryPath)) {
      throw new IllegalArgumentException("Dizin yolu gerekli.");
    }
    Path path = Path.of(directoryPath.trim()).toAbsolutePath().normalize();
    if (!Files.exists(path)) {
      throw new IllegalArgumentException("Dizin mevcut degil: " + path);
    }
    if (!Files.isDirectory(path)) {
      throw new IllegalArgumentException("Yol bir dizin olmali: " + path);
    }
    return path;
  }

  private void validateSchedule(BatchScheduleType type, LocalDate scheduledDate, Integer dayOfWeek, Integer dayOfMonth) {
    switch (type) {
      case ONCE -> {
        if (scheduledDate == null) {
          throw new IllegalArgumentException("Tek seferlik gorev icin tarih secin.");
        }
      }
      case WEEKLY -> {
        if (dayOfWeek == null) {
          throw new IllegalArgumentException("Haftalik gorev icin haftanin gununu secin.");
        }
      }
      case MONTHLY -> {
        if (dayOfMonth == null) {
          throw new IllegalArgumentException("Aylik gorev icin ay gununu secin.");
        }
      }
      default -> {
      }
    }
  }

  private BatchScheduleType parseScheduleType(String value) {
    try {
      return BatchScheduleType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Gecersiz zamanlama tipi: " + value);
    }
  }

  private boolean isSupportedExtension(String filename) {
    String lower = filename.toLowerCase(Locale.ROOT);
    return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
  }

  private Long safeSize(Path file) {
    try {
      return Files.size(file);
    } catch (Exception exception) {
      return null;
    }
  }

  private Instant resolveNextRunAt(BatchScheduleType scheduleType, LocalTime scheduledTime, BatchDocumentJobRequest request) {
    if (!Boolean.TRUE.equals(request.enabled())) {
      return null;
    }
    Instant nextRunAt = BatchScheduleCalculator.computeNextRun(
        scheduleType,
        scheduledTime,
        request.scheduledDate(),
        request.dayOfWeek(),
        request.dayOfMonth(),
        Instant.now()
    );
    if (nextRunAt == null) {
      throw new IllegalArgumentException("Tek seferlik gorev icin gelecekte bir tarih ve saat secin.");
    }
    return nextRunAt;
  }

  private void requireAdmin(AuthenticatedUser user) {
    if (user == null || !user.isAdmin()) {
      throw new IllegalArgumentException("Bu islem icin yonetici yetkisi gerekli.");
    }
  }
}

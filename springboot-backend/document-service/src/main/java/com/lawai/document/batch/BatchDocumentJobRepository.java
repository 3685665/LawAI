package com.lawai.document.batch;

import com.lawai.document.batch.dto.BatchDocumentJobDto;
import com.lawai.document.batch.dto.BatchDocumentRunDto;
import com.lawai.document.batch.dto.BatchDocumentRunFileDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class BatchDocumentJobRepository {

  private static final String JOB_SELECT = """
      SELECT id, name, source_type, directory_path, precedent_courts, precedent_query,
             precedent_date_from, precedent_date_to, precedent_max_documents,
             schedule_type, scheduled_time::text, scheduled_date,
             day_of_week, day_of_month, enabled, created_by_user_id, created_by_user_name,
             last_run_at, next_run_at, created_at, updated_at
      FROM batch_document_jobs
      """;

  private final JdbcTemplate jdbcTemplate;
  private boolean ready;

  public BatchDocumentJobRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<BatchDocumentJobDto> listJobs() {
    ensureSchema();
    return jdbcTemplate.query(
        JOB_SELECT + " ORDER BY created_at DESC",
        this::mapJob
    );
  }

  public Optional<BatchDocumentJobDto> findJob(long id) {
    ensureSchema();
    List<BatchDocumentJobDto> rows = jdbcTemplate.query(
        JOB_SELECT + " WHERE id = ?",
        this::mapJob,
        id
    );
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public List<BatchDocumentJobDto> findDueJobs(Instant now) {
    ensureSchema();
    return jdbcTemplate.query(
        JOB_SELECT + """
        WHERE enabled = TRUE
          AND next_run_at IS NOT NULL
          AND next_run_at <= ?
        ORDER BY next_run_at ASC
        """,
        this::mapJob,
        Timestamp.from(now)
    );
  }

  public long createJob(
      String name,
      BatchSourceType sourceType,
      String directoryPath,
      String precedentCourts,
      String precedentQuery,
      LocalDate precedentDateFrom,
      LocalDate precedentDateTo,
      Integer precedentMaxDocuments,
      BatchScheduleType scheduleType,
      LocalTime scheduledTime,
      LocalDate scheduledDate,
      Integer dayOfWeek,
      Integer dayOfMonth,
      boolean enabled,
      String createdByUserId,
      String createdByUserName,
      Instant nextRunAt
  ) {
    ensureSchema();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO batch_document_jobs (
            name, source_type, directory_path, precedent_courts, precedent_query,
            precedent_date_from, precedent_date_to, precedent_max_documents,
            schedule_type, scheduled_time, scheduled_date,
            day_of_week, day_of_month, enabled, created_by_user_id, created_by_user_name, next_run_at
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          new String[] {"id"}
      );
      int index = 1;
      statement.setString(index++, name);
      statement.setString(index++, sourceType.name());
      statement.setString(index++, directoryPath);
      statement.setString(index++, precedentCourts);
      statement.setString(index++, precedentQuery);
      if (precedentDateFrom == null) {
        statement.setNull(index++, Types.DATE);
      } else {
        statement.setDate(index++, java.sql.Date.valueOf(precedentDateFrom));
      }
      if (precedentDateTo == null) {
        statement.setNull(index++, Types.DATE);
      } else {
        statement.setDate(index++, java.sql.Date.valueOf(precedentDateTo));
      }
      if (precedentMaxDocuments == null) {
        statement.setNull(index++, Types.INTEGER);
      } else {
        statement.setInt(index++, precedentMaxDocuments);
      }
      statement.setString(index++, scheduleType.name());
      statement.setTime(index++, java.sql.Time.valueOf(scheduledTime));
      if (scheduledDate == null) {
        statement.setNull(index++, Types.DATE);
      } else {
        statement.setDate(index++, java.sql.Date.valueOf(scheduledDate));
      }
      if (dayOfWeek == null) {
        statement.setNull(index++, Types.SMALLINT);
      } else {
        statement.setInt(index++, dayOfWeek);
      }
      if (dayOfMonth == null) {
        statement.setNull(index++, Types.SMALLINT);
      } else {
        statement.setInt(index++, dayOfMonth);
      }
      statement.setBoolean(index++, enabled);
      statement.setString(index++, createdByUserId);
      statement.setString(index++, createdByUserName);
      if (nextRunAt == null) {
        statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
      } else {
        statement.setTimestamp(index, Timestamp.from(nextRunAt));
      }
      return statement;
    }, keyHolder);
    return Objects.requireNonNull(keyHolder.getKey()).longValue();
  }

  public void updateJob(
      long id,
      String name,
      BatchSourceType sourceType,
      String directoryPath,
      String precedentCourts,
      String precedentQuery,
      LocalDate precedentDateFrom,
      LocalDate precedentDateTo,
      Integer precedentMaxDocuments,
      BatchScheduleType scheduleType,
      LocalTime scheduledTime,
      LocalDate scheduledDate,
      Integer dayOfWeek,
      Integer dayOfMonth,
      boolean enabled,
      Instant nextRunAt
  ) {
    ensureSchema();
    jdbcTemplate.update(
        """
        UPDATE batch_document_jobs
        SET name = ?, source_type = ?, directory_path = ?, precedent_courts = ?, precedent_query = ?,
            precedent_date_from = ?, precedent_date_to = ?, precedent_max_documents = ?,
            schedule_type = ?, scheduled_time = ?, scheduled_date = ?,
            day_of_week = ?, day_of_month = ?, enabled = ?, next_run_at = ?, updated_at = now()
        WHERE id = ?
        """,
        name,
        sourceType.name(),
        directoryPath,
        precedentCourts,
        precedentQuery,
        precedentDateFrom == null ? null : java.sql.Date.valueOf(precedentDateFrom),
        precedentDateTo == null ? null : java.sql.Date.valueOf(precedentDateTo),
        precedentMaxDocuments,
        scheduleType.name(),
        java.sql.Time.valueOf(scheduledTime),
        scheduledDate == null ? null : java.sql.Date.valueOf(scheduledDate),
        dayOfWeek,
        dayOfMonth,
        enabled,
        nextRunAt == null ? null : Timestamp.from(nextRunAt),
        id
    );
  }

  public void deleteJob(long id) {
    ensureSchema();
    jdbcTemplate.update("DELETE FROM batch_document_jobs WHERE id = ?", id);
  }

  public void updateJobRunState(long id, Instant lastRunAt, Instant nextRunAt, boolean enabled) {
    ensureSchema();
    jdbcTemplate.update(
        """
        UPDATE batch_document_jobs
        SET last_run_at = ?, next_run_at = ?, enabled = ?, updated_at = now()
        WHERE id = ?
        """,
        lastRunAt == null ? null : Timestamp.from(lastRunAt),
        nextRunAt == null ? null : Timestamp.from(nextRunAt),
        enabled,
        id
    );
  }

  public long createRun(long jobId, String triggerType, BatchRunStatus status) {
    ensureSchema();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO batch_document_runs (job_id, trigger_type, status)
          VALUES (?, ?, ?)
          """,
          new String[] {"id"}
      );
      statement.setLong(1, jobId);
      statement.setString(2, triggerType);
      statement.setString(3, status.name());
      return statement;
    }, keyHolder);
    return Objects.requireNonNull(keyHolder.getKey()).longValue();
  }

  public void finishRun(
      long runId,
      BatchRunStatus status,
      int totalFiles,
      int successCount,
      int failedCount,
      int skippedCount,
      String summaryMessage
  ) {
    ensureSchema();
    jdbcTemplate.update(
        """
        UPDATE batch_document_runs
        SET status = ?, finished_at = now(), total_files = ?, success_count = ?,
            failed_count = ?, skipped_count = ?, summary_message = ?
        WHERE id = ?
        """,
        status.name(),
        totalFiles,
        successCount,
        failedCount,
        skippedCount,
        summaryMessage,
        runId
    );
  }

  public long createRunFile(
      long runId,
      String filename,
      String filePath,
      Long fileSizeBytes,
      BatchFileStatus status,
      Long documentId,
      Integer extractedChars,
      Integer chunkCount,
      String errorMessage
  ) {
    ensureSchema();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO batch_document_run_files (
            run_id, filename, file_path, file_size_bytes, status,
            document_id, extracted_chars, chunk_count, error_message
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          new String[] {"id"}
      );
      statement.setLong(1, runId);
      statement.setString(2, filename);
      statement.setString(3, filePath);
      if (fileSizeBytes == null) {
        statement.setNull(4, Types.BIGINT);
      } else {
        statement.setLong(4, fileSizeBytes);
      }
      statement.setString(5, status.name());
      if (documentId == null) {
        statement.setNull(6, Types.BIGINT);
      } else {
        statement.setLong(6, documentId);
      }
      if (extractedChars == null) {
        statement.setNull(7, Types.INTEGER);
      } else {
        statement.setInt(7, extractedChars);
      }
      if (chunkCount == null) {
        statement.setNull(8, Types.INTEGER);
      } else {
        statement.setInt(8, chunkCount);
      }
      statement.setString(9, errorMessage);
      return statement;
    }, keyHolder);
    return Objects.requireNonNull(keyHolder.getKey()).longValue();
  }

  public List<BatchDocumentRunDto> listRuns(Long jobId, int limit) {
    ensureSchema();
    String sql = """
        SELECT r.id, r.job_id, j.name AS job_name, r.trigger_type, r.status, r.started_at, r.finished_at,
               r.total_files, r.success_count, r.failed_count, r.skipped_count, r.summary_message
        FROM batch_document_runs r
        JOIN batch_document_jobs j ON j.id = r.job_id
        """;
    if (jobId == null) {
      return jdbcTemplate.query(
          sql + " ORDER BY r.started_at DESC LIMIT ?",
          this::mapRunWithoutFiles,
          limit
      );
    }
    return jdbcTemplate.query(
        sql + " WHERE r.job_id = ? ORDER BY r.started_at DESC LIMIT ?",
        this::mapRunWithoutFiles,
        jobId,
        limit
    );
  }

  public Optional<BatchDocumentRunDto> findRun(long runId) {
    ensureSchema();
    List<BatchDocumentRunDto> runs = jdbcTemplate.query(
        """
        SELECT r.id, r.job_id, j.name AS job_name, r.trigger_type, r.status, r.started_at, r.finished_at,
               r.total_files, r.success_count, r.failed_count, r.skipped_count, r.summary_message
        FROM batch_document_runs r
        JOIN batch_document_jobs j ON j.id = r.job_id
        WHERE r.id = ?
        """,
        this::mapRunWithoutFiles,
        runId
    );
    if (runs.isEmpty()) {
      return Optional.empty();
    }
    BatchDocumentRunDto run = runs.get(0);
    List<BatchDocumentRunFileDto> files = listRunFiles(runId);
    return Optional.of(new BatchDocumentRunDto(
        run.id(),
        run.jobId(),
        run.jobName(),
        run.triggerType(),
        run.status(),
        run.startedAt(),
        run.finishedAt(),
        run.totalFiles(),
        run.successCount(),
        run.failedCount(),
        run.skippedCount(),
        run.summaryMessage(),
        files
    ));
  }

  public List<BatchDocumentRunFileDto> listRunFiles(long runId) {
    ensureSchema();
    return jdbcTemplate.query(
        """
        SELECT id, filename, file_path, file_size_bytes, status, document_id,
               extracted_chars, chunk_count, error_message, processed_at
        FROM batch_document_run_files
        WHERE run_id = ?
        ORDER BY id ASC
        """,
        this::mapRunFile,
        runId
    );
  }

  private BatchDocumentJobDto mapJob(ResultSet rs, int rowNum) throws SQLException {
    String courtsValue = rs.getString("precedent_courts");
    List<String> courts = courtsValue == null || courtsValue.isBlank()
        ? List.of()
        : Arrays.stream(courtsValue.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
    return new BatchDocumentJobDto(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("source_type"),
        rs.getString("directory_path"),
        courts,
        rs.getString("precedent_query"),
        rs.getDate("precedent_date_from") == null ? null : rs.getDate("precedent_date_from").toLocalDate(),
        rs.getDate("precedent_date_to") == null ? null : rs.getDate("precedent_date_to").toLocalDate(),
        (Integer) rs.getObject("precedent_max_documents"),
        rs.getString("schedule_type"),
        rs.getString("scheduled_time"),
        rs.getDate("scheduled_date") == null ? null : rs.getDate("scheduled_date").toLocalDate(),
        (Integer) rs.getObject("day_of_week"),
        (Integer) rs.getObject("day_of_month"),
        rs.getBoolean("enabled"),
        rs.getString("created_by_user_id"),
        rs.getString("created_by_user_name"),
        toInstant(rs.getTimestamp("last_run_at")),
        toInstant(rs.getTimestamp("next_run_at")),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at"))
    );
  }

  private BatchDocumentRunDto mapRunWithoutFiles(ResultSet rs, int rowNum) throws SQLException {
    return new BatchDocumentRunDto(
        rs.getLong("id"),
        rs.getLong("job_id"),
        rs.getString("job_name"),
        rs.getString("trigger_type"),
        rs.getString("status"),
        toInstant(rs.getTimestamp("started_at")),
        toInstant(rs.getTimestamp("finished_at")),
        rs.getInt("total_files"),
        rs.getInt("success_count"),
        rs.getInt("failed_count"),
        rs.getInt("skipped_count"),
        rs.getString("summary_message"),
        List.of()
    );
  }

  private BatchDocumentRunFileDto mapRunFile(ResultSet rs, int rowNum) throws SQLException {
    return new BatchDocumentRunFileDto(
        rs.getLong("id"),
        rs.getString("filename"),
        rs.getString("file_path"),
        (Long) rs.getObject("file_size_bytes"),
        rs.getString("status"),
        (Long) rs.getObject("document_id"),
        (Integer) rs.getObject("extracted_chars"),
        (Integer) rs.getObject("chunk_count"),
        rs.getString("error_message"),
        toInstant(rs.getTimestamp("processed_at"))
    );
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private synchronized void ensureSchema() {
    if (ready) {
      return;
    }
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS batch_document_jobs (
          id bigserial PRIMARY KEY,
          name text NOT NULL,
          source_type text NOT NULL DEFAULT 'DIRECTORY',
          directory_path text,
          precedent_courts text,
          precedent_query text,
          precedent_date_from date,
          precedent_date_to date,
          precedent_max_documents integer DEFAULT 500,
          schedule_type text NOT NULL,
          scheduled_time time NOT NULL,
          scheduled_date date,
          day_of_week smallint,
          day_of_month smallint,
          enabled boolean NOT NULL DEFAULT TRUE,
          created_by_user_id text,
          created_by_user_name text,
          last_run_at timestamptz,
          next_run_at timestamptz,
          created_at timestamptz NOT NULL DEFAULT now(),
          updated_at timestamptz NOT NULL DEFAULT now()
        )
        """
    );
    jdbcTemplate.execute("ALTER TABLE batch_document_jobs ADD COLUMN IF NOT EXISTS source_type text NOT NULL DEFAULT 'DIRECTORY'");
    jdbcTemplate.execute("ALTER TABLE batch_document_jobs ADD COLUMN IF NOT EXISTS precedent_courts text");
    jdbcTemplate.execute("ALTER TABLE batch_document_jobs ADD COLUMN IF NOT EXISTS precedent_query text");
    jdbcTemplate.execute("ALTER TABLE batch_document_jobs ADD COLUMN IF NOT EXISTS precedent_date_from date");
    jdbcTemplate.execute("ALTER TABLE batch_document_jobs ADD COLUMN IF NOT EXISTS precedent_date_to date");
    jdbcTemplate.execute("ALTER TABLE batch_document_jobs ADD COLUMN IF NOT EXISTS precedent_max_documents integer DEFAULT 500");
    jdbcTemplate.execute("ALTER TABLE batch_document_jobs ALTER COLUMN directory_path DROP NOT NULL");
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS batch_document_runs (
          id bigserial PRIMARY KEY,
          job_id bigint NOT NULL REFERENCES batch_document_jobs(id) ON DELETE CASCADE,
          trigger_type text NOT NULL,
          status text NOT NULL,
          started_at timestamptz NOT NULL DEFAULT now(),
          finished_at timestamptz,
          total_files integer NOT NULL DEFAULT 0,
          success_count integer NOT NULL DEFAULT 0,
          failed_count integer NOT NULL DEFAULT 0,
          skipped_count integer NOT NULL DEFAULT 0,
          summary_message text
        )
        """
    );
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS batch_document_run_files (
          id bigserial PRIMARY KEY,
          run_id bigint NOT NULL REFERENCES batch_document_runs(id) ON DELETE CASCADE,
          filename text NOT NULL,
          file_path text NOT NULL,
          file_size_bytes bigint,
          status text NOT NULL,
          document_id bigint,
          extracted_chars integer,
          chunk_count integer,
          error_message text,
          processed_at timestamptz NOT NULL DEFAULT now()
        )
        """
    );
    ready = true;
  }
}

package com.lawai.document.precedentsync;

import com.lawai.document.precedentsync.dto.PrecedentSyncRunDto;
import com.lawai.document.precedentsync.dto.PrecedentSyncRunFileDto;
import com.lawai.document.precedentsync.dto.PrecedentSyncTaskDto;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class PrecedentSyncRepository {

  private static final String TASK_SELECT = """
      SELECT id, name, courts, date_from, date_to, max_documents_per_run, interval_minutes,
             enabled, status, last_run_at, next_run_at, created_by_user_id, created_by_user_name,
             created_at, updated_at
      FROM precedent_sync_tasks
      """;

  private final JdbcTemplate jdbcTemplate;
  private boolean ready;

  public PrecedentSyncRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<PrecedentSyncTaskDto> listTasks() {
    ensureSchema();
    return jdbcTemplate.query(TASK_SELECT + " ORDER BY created_at DESC", this::mapTask);
  }

  public Optional<PrecedentSyncTaskDto> findTask(long id) {
    ensureSchema();
    List<PrecedentSyncTaskDto> rows = jdbcTemplate.query(TASK_SELECT + " WHERE id = ?", this::mapTask, id);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public List<PrecedentSyncTaskDto> findDueTasks(Instant now) {
    ensureSchema();
    return jdbcTemplate.query(
        TASK_SELECT + """
        WHERE enabled = TRUE
          AND status = 'IDLE'
          AND next_run_at IS NOT NULL
          AND next_run_at <= ?
        ORDER BY next_run_at ASC
        """,
        this::mapTask,
        Timestamp.from(now)
    );
  }

  public long createTask(
      String name,
      String courts,
      LocalDate dateFrom,
      LocalDate dateTo,
      int maxDocumentsPerRun,
      int intervalMinutes,
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
          INSERT INTO precedent_sync_tasks (
            name, courts, date_from, date_to, max_documents_per_run, interval_minutes,
            enabled, status, next_run_at, created_by_user_id, created_by_user_name
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, 'IDLE', ?, ?, ?)
          """,
          new String[] {"id"}
      );
      int index = 1;
      statement.setString(index++, name);
      statement.setString(index++, courts);
      statement.setDate(index++, java.sql.Date.valueOf(dateFrom));
      statement.setDate(index++, java.sql.Date.valueOf(dateTo));
      statement.setInt(index++, maxDocumentsPerRun);
      statement.setInt(index++, intervalMinutes);
      statement.setBoolean(index++, enabled);
      if (nextRunAt == null) {
        statement.setNull(index++, Types.TIMESTAMP_WITH_TIMEZONE);
      } else {
        statement.setTimestamp(index++, Timestamp.from(nextRunAt));
      }
      statement.setString(index++, createdByUserId);
      statement.setString(index, createdByUserName);
      return statement;
    }, keyHolder);
    return Objects.requireNonNull(keyHolder.getKey()).longValue();
  }

  public void updateTask(
      long id,
      String name,
      String courts,
      LocalDate dateFrom,
      LocalDate dateTo,
      int maxDocumentsPerRun,
      int intervalMinutes,
      boolean enabled,
      Instant nextRunAt
  ) {
    ensureSchema();
    jdbcTemplate.update(
        """
        UPDATE precedent_sync_tasks
        SET name = ?, courts = ?, date_from = ?, date_to = ?,
            max_documents_per_run = ?, interval_minutes = ?, enabled = ?, next_run_at = ?, updated_at = now()
        WHERE id = ?
        """,
        name,
        courts,
        java.sql.Date.valueOf(dateFrom),
        java.sql.Date.valueOf(dateTo),
        maxDocumentsPerRun,
        intervalMinutes,
        enabled,
        nextRunAt == null ? null : Timestamp.from(nextRunAt),
        id
    );
  }

  public void deleteTask(long id) {
    ensureSchema();
    jdbcTemplate.update("DELETE FROM precedent_sync_tasks WHERE id = ?", id);
  }

  public void setTaskStatus(long id, PrecedentSyncTaskStatus status) {
    ensureSchema();
    jdbcTemplate.update(
        "UPDATE precedent_sync_tasks SET status = ?, updated_at = now() WHERE id = ?",
        status.name(),
        id
    );
  }

  public void updateTaskRunState(long id, Instant lastRunAt, Instant nextRunAt) {
    ensureSchema();
    jdbcTemplate.update(
        """
        UPDATE precedent_sync_tasks
        SET last_run_at = ?, next_run_at = ?, status = 'IDLE', updated_at = now()
        WHERE id = ?
        """,
        lastRunAt == null ? null : Timestamp.from(lastRunAt),
        nextRunAt == null ? null : Timestamp.from(nextRunAt),
        id
    );
  }

  public long createRun(long taskId, String triggerType, PrecedentSyncRunStatus status) {
    ensureSchema();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          "INSERT INTO precedent_sync_runs (task_id, trigger_type, status) VALUES (?, ?, ?)",
          new String[] {"id"}
      );
      statement.setLong(1, taskId);
      statement.setString(2, triggerType);
      statement.setString(3, status.name());
      return statement;
    }, keyHolder);
    return Objects.requireNonNull(keyHolder.getKey()).longValue();
  }

  public void finishRun(
      long runId,
      PrecedentSyncRunStatus status,
      int totalFiles,
      int successCount,
      int failedCount,
      int skippedCount,
      String summaryMessage
  ) {
    ensureSchema();
    jdbcTemplate.update(
        """
        UPDATE precedent_sync_runs
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

  public void createRunFile(
      long runId,
      String filename,
      String storedPath,
      String status,
      Long documentId,
      Integer extractedChars,
      Integer chunkCount,
      String errorMessage
  ) {
    ensureSchema();
    jdbcTemplate.update(
        """
        INSERT INTO precedent_sync_run_files (
          run_id, filename, stored_path, status, document_id, extracted_chars, chunk_count, error_message
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        runId,
        filename,
        storedPath,
        status,
        documentId,
        extractedChars,
        chunkCount,
        errorMessage
    );
  }

  public List<PrecedentSyncRunDto> listRuns(Long taskId, int limit) {
    ensureSchema();
    String sql = """
        SELECT r.id, r.task_id, t.name AS task_name, r.trigger_type, r.status,
               r.started_at, r.finished_at, r.total_files, r.success_count,
               r.failed_count, r.skipped_count, r.summary_message
        FROM precedent_sync_runs r
        JOIN precedent_sync_tasks t ON t.id = r.task_id
        """;
    if (taskId != null) {
      return jdbcTemplate.query(
          sql + " WHERE r.task_id = ? ORDER BY r.started_at DESC LIMIT ?",
          this::mapRunWithoutFiles,
          taskId,
          limit
      );
    }
    return jdbcTemplate.query(sql + " ORDER BY r.started_at DESC LIMIT ?", this::mapRunWithoutFiles, limit);
  }

  public Optional<PrecedentSyncRunDto> findRun(long runId) {
    ensureSchema();
    List<PrecedentSyncRunDto> runs = jdbcTemplate.query(
        """
        SELECT r.id, r.task_id, t.name AS task_name, r.trigger_type, r.status,
               r.started_at, r.finished_at, r.total_files, r.success_count,
               r.failed_count, r.skipped_count, r.summary_message
        FROM precedent_sync_runs r
        JOIN precedent_sync_tasks t ON t.id = r.task_id
        WHERE r.id = ?
        """,
        this::mapRunWithoutFiles,
        runId
    );
    if (runs.isEmpty()) {
      return Optional.empty();
    }
    PrecedentSyncRunDto run = runs.get(0);
    List<PrecedentSyncRunFileDto> files = listRunFiles(runId);
    return Optional.of(new PrecedentSyncRunDto(
        run.id(),
        run.taskId(),
        run.taskName(),
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

  public List<PrecedentSyncRunFileDto> listRunFiles(long runId) {
    ensureSchema();
    return jdbcTemplate.query(
        """
        SELECT id, filename, stored_path, status, document_id, extracted_chars, chunk_count, error_message, processed_at
        FROM precedent_sync_run_files
        WHERE run_id = ?
        ORDER BY id ASC
        """,
        this::mapRunFile,
        runId
    );
  }

  private PrecedentSyncTaskDto mapTask(ResultSet rs, int rowNum) throws SQLException {
    String courts = rs.getString("courts");
    List<String> courtList = courts == null || courts.isBlank()
        ? List.of()
        : Arrays.stream(courts.split(",")).map(String::trim).filter(part -> !part.isBlank()).toList();
    return new PrecedentSyncTaskDto(
        rs.getLong("id"),
        rs.getString("name"),
        courtList,
        rs.getDate("date_from").toLocalDate(),
        rs.getDate("date_to").toLocalDate(),
        rs.getInt("max_documents_per_run"),
        rs.getInt("interval_minutes"),
        rs.getBoolean("enabled"),
        rs.getString("status"),
        toInstant(rs.getTimestamp("last_run_at")),
        toInstant(rs.getTimestamp("next_run_at")),
        rs.getString("created_by_user_id"),
        rs.getString("created_by_user_name"),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at"))
    );
  }

  private PrecedentSyncRunDto mapRunWithoutFiles(ResultSet rs, int rowNum) throws SQLException {
    return new PrecedentSyncRunDto(
        rs.getLong("id"),
        rs.getLong("task_id"),
        rs.getString("task_name"),
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

  private PrecedentSyncRunFileDto mapRunFile(ResultSet rs, int rowNum) throws SQLException {
    return new PrecedentSyncRunFileDto(
        rs.getLong("id"),
        rs.getString("filename"),
        rs.getString("stored_path"),
        rs.getString("status"),
        (Long) rs.getObject("document_id"),
        (Integer) rs.getObject("extracted_chars"),
        (Integer) rs.getObject("chunk_count"),
        rs.getString("error_message"),
        toInstant(rs.getTimestamp("processed_at"))
    );
  }

  private Instant toInstant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private synchronized void ensureSchema() {
    if (ready) {
      return;
    }
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS precedent_sync_tasks (
          id bigserial PRIMARY KEY,
          name text NOT NULL,
          courts text NOT NULL,
          date_from date NOT NULL,
          date_to date NOT NULL,
          max_documents_per_run integer NOT NULL DEFAULT 500,
          interval_minutes integer NOT NULL DEFAULT 60,
          enabled boolean NOT NULL DEFAULT TRUE,
          status text NOT NULL DEFAULT 'IDLE',
          last_run_at timestamptz,
          next_run_at timestamptz,
          created_by_user_id text,
          created_by_user_name text,
          created_at timestamptz NOT NULL DEFAULT now(),
          updated_at timestamptz NOT NULL DEFAULT now()
        )
        """
    );
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS precedent_sync_runs (
          id bigserial PRIMARY KEY,
          task_id bigint NOT NULL REFERENCES precedent_sync_tasks(id) ON DELETE CASCADE,
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
        CREATE TABLE IF NOT EXISTS precedent_sync_run_files (
          id bigserial PRIMARY KEY,
          run_id bigint NOT NULL REFERENCES precedent_sync_runs(id) ON DELETE CASCADE,
          filename text NOT NULL,
          stored_path text NOT NULL,
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

package com.lawai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LegalCaseSchemaMigration {

  private static final Logger log = LoggerFactory.getLogger(LegalCaseSchemaMigration.class);

  private final JdbcTemplate jdbcTemplate;

  public LegalCaseSchemaMigration(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void migrateLegalCaseColumns() {
    if (!tableExists("legal_cases")) {
      return;
    }

    if (!columnExists("legal_cases", "file_title")) {
      jdbcTemplate.execute("ALTER TABLE legal_cases ADD COLUMN file_title varchar(255)");
      jdbcTemplate.update("""
          UPDATE legal_cases
          SET file_title = COALESCE(NULLIF(subject, ''), NULLIF(client_name, ''), id)
          """);
      log.info("Added file_title column to legal_cases");
    }

    jdbcTemplate.update("""
        UPDATE legal_cases
        SET file_title = COALESCE(NULLIF(file_title, ''), NULLIF(subject, ''), NULLIF(client_name, ''), id)
        """);
    jdbcTemplate.execute("ALTER TABLE legal_cases ALTER COLUMN file_title SET NOT NULL");

    relaxLegacyNotNull("client_name");
    relaxLegacyNotNull("opponent_name");
    relaxLegacyNotNull("subject");
    relaxLegacyNotNull("summary");
  }

  private void relaxLegacyNotNull(String columnName) {
    if (!columnExists("legal_cases", columnName)) {
      return;
    }
    jdbcTemplate.execute("ALTER TABLE legal_cases ALTER COLUMN " + columnName + " DROP NOT NULL");
    jdbcTemplate.execute("ALTER TABLE legal_cases ALTER COLUMN " + columnName + " SET DEFAULT ''");
  }

  private boolean tableExists(String tableName) {
    Boolean exists = jdbcTemplate.queryForObject(
        """
            SELECT EXISTS (
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = 'public' AND table_name = ?
            )
            """,
        Boolean.class,
        tableName
    );
    return Boolean.TRUE.equals(exists);
  }

  private boolean columnExists(String tableName, String columnName) {
    Boolean exists = jdbcTemplate.queryForObject(
        """
            SELECT EXISTS (
              SELECT 1
              FROM information_schema.columns
              WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
            )
            """,
        Boolean.class,
        tableName,
        columnName
    );
    return Boolean.TRUE.equals(exists);
  }
}

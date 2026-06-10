package com.lawai.document.service;

import com.lawai.document.dto.DocumentSearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;

@Repository
public class DocumentRepository {

  private final JdbcTemplate jdbcTemplate;
  private final int dimensions;
  private boolean ready;

  public DocumentRepository(JdbcTemplate jdbcTemplate, DocumentProcessingProperties properties) {
    this.jdbcTemplate = jdbcTemplate;
    this.dimensions = properties.embeddingDimensions();
  }

  public long createDocument(String filename, String contentType, long sizeBytes, String storedPath, String text) {
    ensureSchema();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO legal_documents (filename, content_type, size_bytes, stored_path, extracted_text)
          VALUES (?, ?, ?, ?, ?)
          """,
          new String[] {"id"}
      );
      statement.setString(1, filename);
      statement.setString(2, contentType);
      statement.setLong(3, sizeBytes);
      statement.setString(4, storedPath);
      statement.setString(5, text);
      return statement;
    }, keyHolder);
    return Objects.requireNonNull(keyHolder.getKey()).longValue();
  }

  public List<StoredChunk> createChunks(long documentId, List<DocumentChunk> chunks) {
    ensureSchema();
    return chunks.stream()
        .map(chunk -> createChunk(documentId, chunk))
        .toList();
  }

  public List<DocumentSearchResult> searchByVector(String queryEmbedding, int limit) {
    ensureSchema();
    return jdbcTemplate.query(
        """
        SELECT d.id AS document_id,
               c.id AS chunk_id,
               d.filename,
               c.chunk_index,
               c.content,
               1 - (c.embedding <=> ?::vector) AS score
        FROM legal_document_chunks c
        JOIN legal_documents d ON d.id = c.document_id
        ORDER BY c.embedding <=> ?::vector
        LIMIT ?
        """,
        (rs, rowNum) -> new DocumentSearchResult(
            rs.getLong("document_id"),
            rs.getLong("chunk_id"),
            rs.getString("filename"),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getDouble("score")
        ),
        queryEmbedding,
        queryEmbedding,
        limit
    );
  }

  private StoredChunk createChunk(long documentId, DocumentChunk chunk) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO legal_document_chunks (document_id, chunk_index, content, embedding)
          VALUES (?, ?, ?, ?::vector)
          """,
          new String[] {"id"}
      );
      statement.setLong(1, documentId);
      statement.setInt(2, chunk.chunkIndex());
      statement.setString(3, chunk.content());
      statement.setString(4, chunk.embeddingLiteral());
      return statement;
    }, keyHolder);
    return new StoredChunk(
        Objects.requireNonNull(keyHolder.getKey()).longValue(),
        documentId,
        chunk.chunkIndex(),
        chunk.content(),
        chunk.embeddingLiteral()
    );
  }

  private synchronized void ensureSchema() {
    if (ready) {
      return;
    }
    jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS legal_documents (
          id bigserial PRIMARY KEY,
          filename text NOT NULL,
          content_type text NOT NULL,
          size_bytes bigint NOT NULL,
          stored_path text NOT NULL,
          extracted_text text NOT NULL,
          created_at timestamptz NOT NULL DEFAULT now()
        )
        """
    );
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS legal_document_chunks (
          id bigserial PRIMARY KEY,
          document_id bigint NOT NULL REFERENCES legal_documents(id) ON DELETE CASCADE,
          chunk_index integer NOT NULL,
          content text NOT NULL,
          embedding vector(%d) NOT NULL,
          created_at timestamptz NOT NULL DEFAULT now(),
          UNIQUE (document_id, chunk_index)
        )
        """.formatted(dimensions)
    );
    jdbcTemplate.execute(
        """
        CREATE INDEX IF NOT EXISTS legal_document_chunks_embedding_idx
        ON legal_document_chunks USING hnsw (embedding vector_cosine_ops)
        """
    );
    ready = true;
  }
}

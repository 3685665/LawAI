package com.lawai.persistence.entity;

import com.lawai.api.service.CaseService.CaseUploadedDocumentSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "case_uploaded_documents")
public class CaseUploadedDocumentEntity {

  @Id
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id", nullable = false)
  private LegalCaseEntity legalCase;

  @Column(nullable = false)
  private String filename;

  @Column(nullable = false)
  private long size;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "extracted_characters", nullable = false)
  private int extractedCharacters;

  @Column(name = "chunk_count", nullable = false)
  private int chunkCount;

  @Column(nullable = false)
  private int indexed;

  @Column(name = "text_preview", columnDefinition = "text")
  private String textPreview;

  @Column(name = "extracted_text", columnDefinition = "text")
  private String extractedText;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected CaseUploadedDocumentEntity() {
  }

  public CaseUploadedDocumentEntity(String id, LegalCaseEntity legalCase, String filename, long size,
      String contentType, int extractedCharacters, int chunkCount, int indexed, String textPreview,
      String extractedText, OffsetDateTime createdAt) {
    this.id = id;
    this.legalCase = legalCase;
    this.filename = filename;
    this.size = size;
    this.contentType = contentType;
    this.extractedCharacters = extractedCharacters;
    this.chunkCount = chunkCount;
    this.indexed = indexed;
    this.textPreview = textPreview;
    this.extractedText = extractedText;
    this.createdAt = createdAt;
  }

  public static CaseUploadedDocumentEntity fromSnapshot(CaseUploadedDocumentSnapshot snapshot, LegalCaseEntity legalCase) {
    return new CaseUploadedDocumentEntity(
        snapshot.id(), legalCase, snapshot.filename(), snapshot.size(), snapshot.contentType(),
        snapshot.extractedCharacters(), snapshot.chunkCount(), snapshot.indexed(), snapshot.textPreview(),
        snapshot.extractedText(), snapshot.createdAt()
    );
  }

  public CaseUploadedDocumentSnapshot toSnapshot() {
    return new CaseUploadedDocumentSnapshot(
        id, filename, size, contentType, extractedCharacters, chunkCount, indexed, textPreview,
        extractedText, createdAt
    );
  }
}

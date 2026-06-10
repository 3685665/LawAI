package com.lawai.persistence.entity;

import com.lawai.api.service.CaseService.CaseDocumentSnapshot;
import com.lawai.api.service.CaseService.CaseRecordSnapshot;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "legal_cases")
public class LegalCaseEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "case_type", nullable = false, length = 50)
  private String caseType;

  @Column(name = "client_name", nullable = false)
  private String clientName;

  @Column(name = "opponent_name", nullable = false)
  private String opponentName;

  @Column(name = "court_name", nullable = false)
  private String courtName;

  @Column(nullable = false)
  private String subject;

  @Column(nullable = false, columnDefinition = "text")
  private String summary;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @OneToMany(mappedBy = "legalCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @OrderBy("id ASC")
  private List<CaseDocumentEntity> documents = new ArrayList<>();

  protected LegalCaseEntity() {
  }

  public LegalCaseEntity(String id, String caseType, String clientName, String opponentName, String courtName,
      String subject, String summary, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    this.id = id;
    this.caseType = caseType;
    this.clientName = clientName;
    this.opponentName = opponentName;
    this.courtName = courtName;
    this.subject = subject;
    this.summary = summary;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static LegalCaseEntity fromSnapshot(CaseRecordSnapshot snapshot) {
    LegalCaseEntity entity = new LegalCaseEntity(
        snapshot.id(), snapshot.caseType(), snapshot.clientName(), snapshot.opponentName(),
        snapshot.courtName(), snapshot.subject(), snapshot.summary(), snapshot.createdAt(), snapshot.updatedAt()
    );
    if (snapshot.documents() != null) {
      for (var document : snapshot.documents()) {
        entity.documents.add(CaseDocumentEntity.fromSnapshot(document, entity));
      }
    }
    return entity;
  }

  public CaseRecordSnapshot toSnapshot() {
    List<CaseDocumentSnapshot> documentSnapshots = documents.stream()
        .map(CaseDocumentEntity::toSnapshot)
        .toList();
    return new CaseRecordSnapshot(
        id, caseType, clientName, opponentName, courtName, subject, summary, documentSnapshots, createdAt, updatedAt
    );
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getId() {
    return id;
  }

  public List<CaseDocumentEntity> getDocuments() {
    return documents;
  }
}

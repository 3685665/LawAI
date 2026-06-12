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

  @Column(name = "file_title", nullable = false)
  private String fileTitle;

  @Column(name = "case_number", nullable = false)
  private String caseNumber;

  @Column(name = "court_name", nullable = false)
  private String courtName;

  @Column(nullable = false)
  private String city;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @OneToMany(mappedBy = "legalCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @OrderBy("id ASC")
  private List<CaseDocumentEntity> documents = new ArrayList<>();

  protected LegalCaseEntity() {
  }

  public LegalCaseEntity(String id, String caseType, String fileTitle, String caseNumber, String courtName,
      String city, String notes, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    this.id = id;
    this.caseType = caseType;
    this.fileTitle = fileTitle;
    this.caseNumber = caseNumber;
    this.courtName = courtName;
    this.city = city;
    this.notes = notes;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static LegalCaseEntity fromSnapshot(CaseRecordSnapshot snapshot) {
    LegalCaseEntity entity = new LegalCaseEntity(
        snapshot.id(), snapshot.caseType(), snapshot.fileTitle(), snapshot.caseNumber(),
        snapshot.courtName(), snapshot.city(), snapshot.notes(), snapshot.createdAt(), snapshot.updatedAt()
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
        id, caseType, fileTitle, caseNumber, courtName, city, notes, documentSnapshots, createdAt, updatedAt
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

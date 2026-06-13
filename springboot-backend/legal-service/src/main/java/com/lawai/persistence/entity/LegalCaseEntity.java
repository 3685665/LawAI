package com.lawai.persistence.entity;

import com.lawai.api.service.CaseService.CaseDocumentSnapshot;
import com.lawai.api.service.CaseService.CaseExpenseSnapshot;
import com.lawai.api.service.CaseService.CaseNoteSnapshot;
import com.lawai.api.service.CaseService.CasePartySnapshot;
import com.lawai.api.service.CaseService.CaseRecordSnapshot;
import com.lawai.api.service.CaseService.CaseUploadedDocumentSnapshot;
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

  @Column(name = "case_number")
  private String caseNumber;

  @Column(name = "court_name")
  private String courtName;

  @Column
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

  @OneToMany(mappedBy = "legalCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @OrderBy("createdAt DESC")
  private List<CaseUploadedDocumentEntity> uploadedDocuments = new ArrayList<>();

  @OneToMany(mappedBy = "legalCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @OrderBy("id ASC")
  private List<CasePartyEntity> parties = new ArrayList<>();

  @OneToMany(mappedBy = "legalCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @OrderBy("id ASC")
  private List<CaseExpenseEntity> expenses = new ArrayList<>();

  @OneToMany(mappedBy = "legalCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @OrderBy("id ASC")
  private List<CaseNoteEntity> caseNotes = new ArrayList<>();

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
    if (snapshot.uploadedDocuments() != null) {
      for (var document : snapshot.uploadedDocuments()) {
        entity.uploadedDocuments.add(CaseUploadedDocumentEntity.fromSnapshot(document, entity));
      }
    }
    if (snapshot.parties() != null) {
      for (var party : snapshot.parties()) {
        entity.parties.add(CasePartyEntity.fromSnapshot(party, entity));
      }
    }
    if (snapshot.expenses() != null) {
      for (var expense : snapshot.expenses()) {
        entity.expenses.add(CaseExpenseEntity.fromSnapshot(expense, entity));
      }
    }
    if (snapshot.caseNotes() != null) {
      for (var caseNote : snapshot.caseNotes()) {
        entity.caseNotes.add(CaseNoteEntity.fromSnapshot(caseNote, entity));
      }
    }
    return entity;
  }

  public void replaceFromSnapshot(CaseRecordSnapshot snapshot) {
    this.caseType = snapshot.caseType();
    this.fileTitle = snapshot.fileTitle();
    this.caseNumber = snapshot.caseNumber();
    this.courtName = snapshot.courtName();
    this.city = snapshot.city();
    this.notes = snapshot.notes();
    this.updatedAt = snapshot.updatedAt();

    this.documents.clear();
    if (snapshot.documents() != null) {
      for (var document : snapshot.documents()) {
        this.documents.add(CaseDocumentEntity.fromSnapshot(document, this));
      }
    }
    this.uploadedDocuments.clear();
    if (snapshot.uploadedDocuments() != null) {
      for (var document : snapshot.uploadedDocuments()) {
        this.uploadedDocuments.add(CaseUploadedDocumentEntity.fromSnapshot(document, this));
      }
    }
    this.parties.clear();
    if (snapshot.parties() != null) {
      for (var party : snapshot.parties()) {
        this.parties.add(CasePartyEntity.fromSnapshot(party, this));
      }
    }
    this.expenses.clear();
    if (snapshot.expenses() != null) {
      for (var expense : snapshot.expenses()) {
        this.expenses.add(CaseExpenseEntity.fromSnapshot(expense, this));
      }
    }
    this.caseNotes.clear();
    if (snapshot.caseNotes() != null) {
      for (var caseNote : snapshot.caseNotes()) {
        this.caseNotes.add(CaseNoteEntity.fromSnapshot(caseNote, this));
      }
    }
  }

  public CaseRecordSnapshot toSnapshot() {
    List<CaseDocumentSnapshot> documentSnapshots = documents.stream()
        .map(CaseDocumentEntity::toSnapshot)
        .toList();
    List<CaseUploadedDocumentSnapshot> uploadedDocumentSnapshots = uploadedDocuments.stream()
        .map(CaseUploadedDocumentEntity::toSnapshot)
        .toList();
    List<CasePartySnapshot> partySnapshots = parties.stream()
        .map(CasePartyEntity::toSnapshot)
        .toList();
    List<CaseExpenseSnapshot> expenseSnapshots = expenses.stream()
        .map(CaseExpenseEntity::toSnapshot)
        .toList();
    List<CaseNoteSnapshot> caseNoteSnapshots = caseNotes.stream()
        .map(CaseNoteEntity::toSnapshot)
        .toList();
    return new CaseRecordSnapshot(
        id, caseType, fileTitle, caseNumber, courtName, city, notes, documentSnapshots, uploadedDocumentSnapshots,
        partySnapshots, expenseSnapshots, caseNoteSnapshots, createdAt, updatedAt
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

  public List<CaseUploadedDocumentEntity> getUploadedDocuments() {
    return uploadedDocuments;
  }
}

package com.lawai.persistence.entity;

import com.lawai.api.service.CaseService.CaseNoteSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "case_notes")
public class CaseNoteEntity {

  @Id
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id", nullable = false)
  private LegalCaseEntity legalCase;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String text;

  protected CaseNoteEntity() {
  }

  public CaseNoteEntity(String id, LegalCaseEntity legalCase, String title, String text) {
    this.id = id;
    this.legalCase = legalCase;
    this.title = title;
    this.text = text;
  }

  public static CaseNoteEntity fromSnapshot(CaseNoteSnapshot snapshot, LegalCaseEntity legalCase) {
    return new CaseNoteEntity(snapshot.id(), legalCase, snapshot.title(), snapshot.text());
  }

  public CaseNoteSnapshot toSnapshot() {
    return new CaseNoteSnapshot(id, title, text);
  }
}

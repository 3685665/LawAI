package com.lawai.persistence.entity;

import com.lawai.api.service.CaseService.CaseDocumentSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "case_documents")
public class CaseDocumentEntity {

  @Id
  @Column(length = 80)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id", nullable = false)
  private LegalCaseEntity legalCase;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String detail;

  @Column(nullable = false)
  private boolean required;

  @Column(name = "doc_group", nullable = false)
  private String group;

  @Column(nullable = false)
  private boolean completed;

  protected CaseDocumentEntity() {
  }

  public CaseDocumentEntity(String id, LegalCaseEntity legalCase, String title, String detail,
      boolean required, String group, boolean completed) {
    this.id = id;
    this.legalCase = legalCase;
    this.title = title;
    this.detail = detail;
    this.required = required;
    this.group = group;
    this.completed = completed;
  }

  public static CaseDocumentEntity fromSnapshot(CaseDocumentSnapshot snapshot, LegalCaseEntity legalCase) {
    return new CaseDocumentEntity(
        snapshot.id(), legalCase, snapshot.title(), snapshot.detail(),
        snapshot.required(), snapshot.group(), snapshot.completed()
    );
  }

  public CaseDocumentSnapshot toSnapshot() {
    return new CaseDocumentSnapshot(id, title, detail, required, group, completed);
  }

  public void setCompleted(boolean completed) {
    this.completed = completed;
  }

  public String getId() {
    return id;
  }
}

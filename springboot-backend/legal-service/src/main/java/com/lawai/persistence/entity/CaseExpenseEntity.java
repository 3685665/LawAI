package com.lawai.persistence.entity;

import com.lawai.api.service.CaseService.CaseExpenseSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "case_expenses")
public class CaseExpenseEntity {

  @Id
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id", nullable = false)
  private LegalCaseEntity legalCase;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(columnDefinition = "text")
  private String description;

  protected CaseExpenseEntity() {
  }

  public CaseExpenseEntity(String id, LegalCaseEntity legalCase, String title, BigDecimal amount, String description) {
    this.id = id;
    this.legalCase = legalCase;
    this.title = title;
    this.amount = amount;
    this.description = description;
  }

  public static CaseExpenseEntity fromSnapshot(CaseExpenseSnapshot snapshot, LegalCaseEntity legalCase) {
    return new CaseExpenseEntity(snapshot.id(), legalCase, snapshot.title(), snapshot.amount(), snapshot.description());
  }

  public CaseExpenseSnapshot toSnapshot() {
    return new CaseExpenseSnapshot(id, title, amount, description);
  }
}

package com.lawai.persistence.entity;

import com.lawai.api.service.CaseService.CasePartySnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "case_parties")
public class CasePartyEntity {

  @Id
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id", nullable = false)
  private LegalCaseEntity legalCase;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String role;

  private String contact;

  @Column(name = "identity_number")
  private String identityNumber;

  private String phone;

  private String email;

  @Column(name = "start_date")
  private String startDate;

  @Column(name = "end_date")
  private String endDate;

  protected CasePartyEntity() {
  }

  public CasePartyEntity(String id, LegalCaseEntity legalCase, String name, String role, String contact,
      String identityNumber, String phone, String email, String startDate, String endDate) {
    this.id = id;
    this.legalCase = legalCase;
    this.name = name;
    this.role = role;
    this.contact = contact;
    this.identityNumber = identityNumber;
    this.phone = phone;
    this.email = email;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public static CasePartyEntity fromSnapshot(CasePartySnapshot snapshot, LegalCaseEntity legalCase) {
    return new CasePartyEntity(
        snapshot.id(), legalCase, snapshot.name(), snapshot.role(), snapshot.contact(),
        snapshot.identityNumber(), snapshot.phone(), snapshot.email(), snapshot.startDate(), snapshot.endDate()
    );
  }

  public CasePartySnapshot toSnapshot() {
    return new CasePartySnapshot(id, name, role, contact, identityNumber, phone, email, startDate, endDate);
  }
}

package com.lawai.api.service;

import com.lawai.api.dto.CaseCreateRequest;
import com.lawai.api.dto.CaseDocumentDto;
import com.lawai.api.dto.CaseDocumentPatchResponse;
import com.lawai.api.dto.CaseDocumentUpdateRequest;
import com.lawai.api.dto.CaseExpenseDto;
import com.lawai.api.dto.CaseNoteDto;
import com.lawai.api.dto.CasePartyDto;
import com.lawai.api.dto.CaseRecordResponse;
import com.lawai.api.dto.CaseTemplateDto;
import com.lawai.api.dto.CaseTemplatesResponse;
import com.lawai.persistence.entity.LegalCaseEntity;
import com.lawai.persistence.repository.LegalCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CaseService {

  private static final Map<String, CaseTemplateDefinition> TEMPLATES = Map.of(
      "genel", new CaseTemplateDefinition(
          "genel",
          "Genel hukuk",
          "Genel hukuk dosyasi",
          "Nobetci Asliye Hukuk Mahkemesi",
          List.of()
      ),
      "is", new CaseTemplateDefinition(
          "is",
          "Is hukuku",
          "Is hukuku dosyasi",
          "Is Mahkemesi",
          List.of()
      ),
      "sozlesme", new CaseTemplateDefinition(
          "sozlesme",
          "Sozlesme / alacak",
          "Sozlesme / alacak dosyasi",
          "Nobetci Asliye Hukuk Mahkemesi",
          List.of()
      ),
      "icra", new CaseTemplateDefinition(
          "icra",
          "Icra takibi",
          "Icra takibi dosyasi",
          "Icra Mudurlugu / Icra Hukuk Mahkemesi",
          List.of()
      ),
      "aile", new CaseTemplateDefinition(
          "aile",
          "Aile hukuku",
          "Aile hukuku dosyasi",
          "Aile Mahkemesi",
          List.of()
      )
  );

  private final LegalCaseRepository legalCaseRepository;

  public CaseService(LegalCaseRepository legalCaseRepository) {
    this.legalCaseRepository = legalCaseRepository;
  }

  public CaseTemplatesResponse templates() {
    return new CaseTemplatesResponse(
        TEMPLATES.values().stream()
            .sorted(Comparator.comparing(CaseTemplateDefinition::label))
            .map(this::toTemplateDto)
            .toList()
    );
  }

  @Transactional(readOnly = true)
  public List<CaseRecordResponse> listCases() {
    return load().stream()
        .sorted(Comparator.comparing(CaseRecordSnapshot::updatedAt).reversed())
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public List<CaseRecordResponse> seedSamples() {
    List<CaseRecordSnapshot> existing = load();
    boolean alreadySeeded = existing.stream().anyMatch(item -> safe(item.fileTitle()).startsWith("Ornek "));
    if (alreadySeeded) {
      return listCasesFromSnapshots(existing);
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<CaseRecordSnapshot> samples = new ArrayList<>(existing);
    samples.add(sampleCase(
        "genel",
        "Ornek Ahmet Yilmaz - Is alacagi dosyasi",
        "2026/118",
        "Ankara 3. Is Mahkemesi",
        "Ankara",
        "Is sozlesmesi, bordro ve arabuluculuk evraklarinin kontrolu.",
        List.of("genel-vekalet", "genel-kimlik", "genel-dilekce", "genel-delil", "genel-harc"),
        now.minusDays(3)
    ));
    samples.add(sampleCase(
        "is",
        "Ornek Ayse Kaya - Fesih ve alacaklar",
        "2026/204",
        "Istanbul 8. Is Mahkemesi",
        "Istanbul",
        "Is akdinin feshi, fazla mesai ve kullanilmayan izinlerin takibi.",
        List.of("is-vekalet", "is-hizmet", "is-fesih", "is-bordro"),
        now.minusDays(2)
    ));
    samples.add(sampleCase(
        "icra",
        "Ornek Mehmet Demir - Ilamli icra takibi",
        "2026/77",
        "Bursa 1. Icra Mudurlugu",
        "Bursa",
        "Ilam ve hesap cetveli ile icra takibi baslatilmasi ve tebligat evrakinin kontrolu.",
        List.of("i-dayanak", "i-vekalet", "i-hesap", "i-tebligat", "i-adres"),
        now.minusDays(1)
    ));
    save(samples);
    return listCasesFromSnapshots(samples);
  }

  @Transactional
  public CaseRecordResponse createCase(CaseCreateRequest request) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    CaseRecordSnapshot snapshot = buildSnapshot(
        UUID.randomUUID().toString(),
        request,
        now,
        now
    );
    legalCaseRepository.save(LegalCaseEntity.fromSnapshot(snapshot));
    return toResponse(snapshot);
  }

  @Transactional
  public CaseRecordResponse updateCase(String id, CaseCreateRequest request) {
    LegalCaseEntity legalCase = legalCaseRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Dava bulunamadi."));
    CaseRecordSnapshot existing = legalCase.toSnapshot();
    CaseRecordSnapshot snapshot = buildSnapshot(
        id,
        request,
        existing.createdAt(),
        OffsetDateTime.now(ZoneOffset.UTC)
    );
    legalCase.replaceFromSnapshot(snapshot);
    LegalCaseEntity saved = legalCaseRepository.save(legalCase);
    return toResponse(saved.toSnapshot());
  }

  @Transactional(readOnly = true)
  public CaseRecordResponse getCase(String id) {
    return load().stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .map(this::toResponse)
        .orElseThrow(() -> new IllegalArgumentException("Dava bulunamadi."));
  }

  @Transactional
  public List<CaseRecordResponse> deleteCase(String id) {
    if (!legalCaseRepository.existsById(id)) {
      throw new IllegalArgumentException("Dava bulunamadi.");
    }
    legalCaseRepository.deleteById(id);
    return listCases();
  }

  @Transactional
  public CaseDocumentPatchResponse updateDocument(String caseId, String documentId, CaseDocumentUpdateRequest request) {
    LegalCaseEntity legalCase = legalCaseRepository.findById(caseId)
        .orElseThrow(() -> new IllegalArgumentException("Dava bulunamadi."));
    boolean documentFound = false;
    for (var document : legalCase.getDocuments()) {
      if (document.getId().equals(documentId)) {
        document.setCompleted(request.completed());
        documentFound = true;
        break;
      }
    }
    if (!documentFound) {
      throw new IllegalArgumentException("Belge bulunamadi.");
    }
    legalCase.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    LegalCaseEntity saved = legalCaseRepository.save(legalCase);
    return new CaseDocumentPatchResponse(toResponse(saved.toSnapshot()), listCases());
  }

  private List<CaseRecordResponse> listCasesFromSnapshots(List<CaseRecordSnapshot> snapshots) {
    return snapshots.stream()
        .sorted(Comparator.comparing(CaseRecordSnapshot::updatedAt).reversed())
        .map(this::toResponse)
        .toList();
  }

  private CaseTemplateDto toTemplateDto(CaseTemplateDefinition template) {
    return new CaseTemplateDto(
        template.caseType(),
        template.label(),
        template.title(),
        template.courtHint(),
        template.documents().stream()
            .map(document -> new CaseDocumentDto(document.id(), document.title(), document.detail(), document.required(), document.group(), false))
            .toList()
    );
  }

  private CaseRecordResponse toResponse(CaseRecordSnapshot snapshot) {
    List<CaseDocumentDto> documents = snapshot.documents().stream()
        .map(document -> new CaseDocumentDto(
            document.id(),
            document.title(),
            document.detail(),
            document.required(),
            document.group(),
            document.completed()
        ))
        .toList();
    int requiredDocumentCount = (int) documents.stream().filter(CaseDocumentDto::required).count();
    int completedRequiredDocumentCount = (int) documents.stream().filter(document -> document.required() && document.completed()).count();
    int progress = requiredDocumentCount == 0 ? 100 : Math.round((completedRequiredDocumentCount * 100.0f) / requiredDocumentCount);
    CaseTemplateDefinition template = TEMPLATES.get(snapshot.caseType());
    return new CaseRecordResponse(
        snapshot.id(),
        snapshot.caseType(),
        template == null ? snapshot.caseType() : template.label(),
        safe(snapshot.fileTitle()),
        safe(snapshot.caseNumber()),
        safe(snapshot.courtName()),
        safe(snapshot.city()),
        safe(snapshot.notes()),
        requiredDocumentCount,
        completedRequiredDocumentCount,
        progress,
        documents,
        snapshot.parties().stream()
            .map(party -> new CasePartyDto(
                party.id(),
                party.name(),
                party.role(),
                party.contact(),
                party.identityNumber(),
                party.phone(),
                party.email(),
                party.startDate(),
                party.endDate()
            ))
            .toList(),
        snapshot.expenses().stream()
            .map(expense -> new CaseExpenseDto(
                expense.id(),
                expense.title(),
                expense.amount(),
                expense.description(),
                expense.category(),
                expense.expenseDate(),
                expense.paid()
            ))
            .toList(),
        snapshot.caseNotes().stream()
            .map(caseNote -> new CaseNoteDto(caseNote.id(), caseNote.title(), caseNote.text()))
            .toList(),
        snapshot.createdAt(),
        snapshot.updatedAt()
    );
  }

  private List<CasePartySnapshot> normalizeParties(List<CasePartyDto> parties) {
    if (parties == null) {
      return List.of();
    }
    return parties.stream()
        .filter(party -> StringUtils.hasText(party.name()) || StringUtils.hasText(party.role()) || StringUtils.hasText(party.contact()))
        .map(party -> new CasePartySnapshot(
            newId(),
            safe(party.name()).trim(),
            safe(party.role()).trim(),
            safe(party.contact()).trim(),
            safe(party.identityNumber()).trim(),
            safe(party.phone()).trim(),
            safe(party.email()).trim(),
            safe(party.startDate()).trim(),
            safe(party.endDate()).trim()
        ))
        .toList();
  }

  private List<CaseExpenseSnapshot> normalizeExpenses(List<CaseExpenseDto> expenses) {
    if (expenses == null) {
      return List.of();
    }
    return expenses.stream()
        .filter(expense -> StringUtils.hasText(expense.title()) || expense.amount() != null || StringUtils.hasText(expense.description()))
        .map(expense -> new CaseExpenseSnapshot(
            newId(),
            safe(expense.title()).trim(),
            expense.amount() == null ? BigDecimal.ZERO : expense.amount(),
            safe(expense.description()).trim(),
            safe(expense.category()).trim(),
            safe(expense.expenseDate()).trim(),
            Boolean.TRUE.equals(expense.paid())
        ))
        .toList();
  }

  private List<CaseNoteSnapshot> normalizeCaseNotes(List<CaseNoteDto> caseNotes) {
    if (caseNotes == null) {
      return List.of();
    }
    return caseNotes.stream()
        .filter(caseNote -> StringUtils.hasText(caseNote.title()) || StringUtils.hasText(caseNote.text()))
        .map(caseNote -> new CaseNoteSnapshot(
            newId(),
            safe(caseNote.title()).trim(),
            safe(caseNote.text()).trim()
        ))
        .toList();
  }

  private CaseRecordSnapshot buildSnapshot(
      String id,
      CaseCreateRequest request,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
    CaseTemplateDefinition template = requireTemplate(request.caseType());
    Map<String, Boolean> completedDocumentIds = request.completedDocumentIds() == null
        ? Map.of()
        : request.completedDocumentIds().stream()
            .filter(StringUtils::hasText)
            .map(value -> value.trim())
            .collect(Collectors.toMap(Function.identity(), ignored -> true, (left, right) -> left, LinkedHashMap::new));
    return new CaseRecordSnapshot(
        id,
        template.caseType(),
        request.fileTitle().trim(),
        request.caseNumber().trim(),
        request.courtName().trim(),
        request.city().trim(),
        request.notes().trim(),
        template.documents().stream()
            .map(document -> new CaseDocumentSnapshot(
                document.id(),
                document.title(),
                document.detail(),
                document.required(),
                document.group(),
                completedDocumentIds.getOrDefault(document.id(), false)
            ))
            .toList(),
        normalizeParties(request.parties()),
        normalizeExpenses(request.expenses()),
        normalizeCaseNotes(request.caseNotes()),
        createdAt,
        updatedAt
    );
  }

  private CaseTemplateDefinition requireTemplate(String caseType) {
    CaseTemplateDefinition template = TEMPLATES.get(normalize(caseType));
    if (template == null) {
      throw new IllegalArgumentException("Gecersiz dava turu.");
    }
    return template;
  }

  private List<CaseRecordSnapshot> load() {
    return legalCaseRepository.findAll().stream()
        .map(LegalCaseEntity::toSnapshot)
        .toList();
  }

  private void save(List<CaseRecordSnapshot> cases) {
    legalCaseRepository.saveAll(cases.stream().map(LegalCaseEntity::fromSnapshot).toList());
  }

  private CaseRecordSnapshot sampleCase(
      String caseType,
      String fileTitle,
      String caseNumber,
      String courtName,
      String city,
      String notes,
      List<String> completedDocumentIds,
      OffsetDateTime updatedAt
  ) {
    CaseTemplateDefinition template = requireTemplate(caseType);
    Map<String, Boolean> completed = completedDocumentIds.stream()
        .filter(StringUtils::hasText)
        .map(value -> value.trim())
        .collect(Collectors.toMap(Function.identity(), ignored -> true, (left, right) -> left, LinkedHashMap::new));
    List<CaseDocumentSnapshot> documents = template.documents().stream()
        .map(document -> new CaseDocumentSnapshot(
            document.id(),
            document.title(),
            document.detail(),
            document.required(),
            document.group(),
            completed.getOrDefault(document.id(), false)
        ))
        .toList();
    return new CaseRecordSnapshot(
        UUID.randomUUID().toString(),
        template.caseType(),
        fileTitle,
        caseNumber,
        courtName,
        city,
        notes,
        documents,
        List.of(),
        List.of(),
        List.of(),
        updatedAt.minusHours(6),
        updatedAt
    );
  }

  private String normalize(String value) {
    return StringUtils.hasText(value) ? value.trim().toLowerCase() : "";
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String newId() {
    return UUID.randomUUID().toString();
  }

  public record CaseTemplateDefinition(
      String caseType,
      String label,
      String title,
      String courtHint,
      List<CaseDocumentSnapshot> documents
  ) {
  }

  public record CaseDocumentSnapshot(
      String id,
      String title,
      String detail,
      boolean required,
      String group,
      boolean completed
  ) {
  }

  public record CaseRecordSnapshot(
      String id,
      String caseType,
      String fileTitle,
      String caseNumber,
      String courtName,
      String city,
      String notes,
      List<CaseDocumentSnapshot> documents,
      List<CasePartySnapshot> parties,
      List<CaseExpenseSnapshot> expenses,
      List<CaseNoteSnapshot> caseNotes,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
    private CaseRecordSnapshot withDocuments(List<CaseDocumentSnapshot> documents) {
      return new CaseRecordSnapshot(id, caseType, fileTitle, caseNumber, courtName, city, notes, documents, parties, expenses, caseNotes, createdAt, updatedAt);
    }

    private CaseRecordSnapshot withUpdatedAt(OffsetDateTime updatedAt) {
      return new CaseRecordSnapshot(id, caseType, fileTitle, caseNumber, courtName, city, notes, documents, parties, expenses, caseNotes, createdAt, updatedAt);
    }
  }

  public record CasePartySnapshot(
      String id,
      String name,
      String role,
      String contact,
      String identityNumber,
      String phone,
      String email,
      String startDate,
      String endDate
  ) {
  }

  public record CaseExpenseSnapshot(
      String id,
      String title,
      BigDecimal amount,
      String description,
      String category,
      String expenseDate,
      Boolean paid
  ) {
  }

  public record CaseNoteSnapshot(
      String id,
      String title,
      String text
  ) {
  }
}

package com.lawai.api.service;

import com.lawai.api.dto.CaseCreateRequest;
import com.lawai.api.dto.CaseDocumentDto;
import com.lawai.api.dto.CaseDocumentPatchResponse;
import com.lawai.api.dto.CaseDocumentUpdateRequest;
import com.lawai.api.dto.CaseRecordResponse;
import com.lawai.api.dto.CaseTemplateDto;
import com.lawai.api.dto.CaseTemplatesResponse;
import com.lawai.persistence.entity.LegalCaseEntity;
import com.lawai.persistence.repository.LegalCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
          "Dava dilekcesi, delil listesi, vekaletname ve ekler klasoru ile baslanir.",
          List.of(
              doc("genel-vekalet", "Vekaletname / yetki belgesi", "Musteri vekaleti ve temsil yetkisini gosteren ana belge.", true, "Yetki"),
              doc("genel-kimlik", "Taraf kimlik ve iletisim bilgileri", "TCKN / VKN, adres, telefon ve tebligat bilgileri.", true, "Taraf bilgileri"),
              doc("genel-dilekce", "Dava dilekcesi", "Talep sonucu, vakialar ve hukuki nedenler.", true, "Dava evraki"),
              doc("genel-delil", "Delil listesi", "Belgeler, taniklar, bilirkiisi ve diger ispat vasitalari.", true, "Dava evraki"),
              doc("genel-ekler", "Ekler klasoru", "Belgelerin numarali ve duzenli sekilde dosyalanmis hali.", true, "Ekler"),
              doc("genel-harc", "Harc ve gider avansi makbuzlari", "Basvuru harci, pesin harc ve gider avansi kayitlari.", true, "Usul"),
              doc("genel-arabuluculuk", "Arabuluculuk son tutanagi", "Zorunlu dava sartina tabi dosyalarda eklenir.", false, "Usul"),
              doc("genel-tebligat", "Tebligat / ihtarname evraki", "Karsi tarafa gonderilen ihtar ve tebligat belgeleri.", false, "Ekler")
          )
      ),
      "is", new CaseTemplateDefinition(
          "is",
          "Is hukuku",
          "Is hukuku dosyasi",
          "Is Mahkemesi",
          "Hizmet dokumu, fesih bildirimi, bordrolar ve arabuluculuk son tutanagi onerilir.",
          List.of(
              doc("is-vekalet", "Vekaletname / yetki belgesi", "Avukatlik yetkisini ve temsil kapsamini gosterir.", true, "Yetki"),
              doc("is-hizmet", "Hizmet dokumu / SGK kayitlari", "Calisma suresi ve prim kayitlarini teyit eder.", true, "Calisma kaydi"),
              doc("is-fesih", "Fesih bildirimi / cikis evragi", "Fesih tarihini ve sebebini netlestirir.", true, "Fesih"),
              doc("is-bordro", "Bordro ve ucret belgeleri", "Maas, fazla mesai ve kesintilerin ispatinda kullanilir.", true, "Ucret"),
              doc("is-sozlesme", "Is sozlesmesi / gorev tanimi", "Gorev, unvan ve calisma duzenini ortaya koyar.", true, "Calisma kaydi"),
              doc("is-arabuluculuk", "Arabuluculuk son tutanagi", "Dava sartidir; sure ve taraf bilgileri kontrol edilmelidir.", true, "Usul"),
              doc("is-izin", "Izin ve puantaj kayitlari", "Yillik izin, devamsizlik ve fazla mesai icin destek belge.", false, "Ekler"),
              doc("is-tanik", "Tanik listesi", "Calisma kosullari ve alacaklar icin taniklar.", false, "Delil")
          )
      ),
      "sozlesme", new CaseTemplateDefinition(
          "sozlesme",
          "Sozlesme / alacak",
          "Sozlesme / alacak dosyasi",
          "Nobetci Asliye Hukuk Mahkemesi",
          "Sozlesme, fatura, dekont, ihtarname ve teslim / kabul belgeleri bir arada tutulur.",
          List.of(
              doc("s-vekalet", "Vekaletname / yetki belgesi", "Temsil yetkisini ve dava acma yetkisini gosterir.", true, "Yetki"),
              doc("s-sozlesme", "Sozlesme / siparis formu", "Taraflar arasindaki borc ve edimleri belirler.", true, "Sozlesme"),
              doc("s-fatura", "Fatura / irsaliye / teslim belgeleri", "Edimin yerine getirildigini veya alacagin dogdugunu destekler.", true, "Delil"),
              doc("s-dekont", "Odeme dekontlari / cari hesap", "Yapilan veya yapilmayan odemeleri gosteren belgeler.", true, "Delil"),
              doc("s-ihtar", "Ihtarname ve tebligat evraki", "Temerrut, ihtar ve bildirimin ispatinda kullanilir.", true, "Usul"),
              doc("s-delil", "Ek deliller klasoru", "Mail yazismalari, teslim tutanaklari, WhatsApp ciktilari vb.", true, "Ekler"),
              doc("s-arabuluculuk", "Arabuluculuk son tutanagi", "Konuya gore zorunlu olabilir.", false, "Usul"),
              doc("s-hesap", "Hesap cetveli", "Faiz ve ana para hesabini tablo halinde verir.", false, "Delil")
          )
      ),
      "icra", new CaseTemplateDefinition(
          "icra",
          "Icra takibi",
          "Icra takibi dosyasi",
          "Icra Mudurlugu / Icra Hukuk Mahkemesi",
          "Takip dayanagi belge, hesap cetveli, senet / cek ve tebligat evraki gerekir.",
          List.of(
              doc("i-dayanak", "Takip dayanagi belge", "Ilam, senet, sozlesme veya faturaya dayali evrak.", true, "Dayanak"),
              doc("i-vekalet", "Vekaletname / yetki belgesi", "Icra islemlerinde temsil yetkisi icin gerekir.", true, "Yetki"),
              doc("i-hesap", "Hesap cetveli", "Faiz, vekalet ucreti ve toplam alacak hesabi.", true, "Hesap"),
              doc("i-tebligat", "Tebligat / ihtar evraki", "Borc bildirimi ve temerrut icin kullanilir.", true, "Usul"),
              doc("i-senet", "Senet / cek / bono", "Ilamsiz takipte dayanak olarak kullanilir.", false, "Dayanak"),
              doc("i-kesinlesme", "Kesinlesme / ilam serhi", "Ilamli takipte gerektiginde eklenir.", false, "Dayanak"),
              doc("i-adres", "Alacakli / borclu adres bilgileri", "Tebligat ve takip islemleri icin gerekir.", true, "Taraf bilgileri")
          )
      ),
      "aile", new CaseTemplateDefinition(
          "aile",
          "Aile hukuku",
          "Aile hukuku dosyasi",
          "Aile Mahkemesi",
          "Nufus kayit ornegi, evlilik belgeleri, velayet / nafaka belgeleri ve sosyal inceleme destegi gerekir.",
          List.of(
              doc("a-vekalet", "Vekaletname / yetki belgesi", "Temsil yetkisini gosterir.", true, "Yetki"),
              doc("a-nufus", "Nufus kayit ornegi", "Taraflarin aile bagini ve baglantili kayitlarini ortaya koyar.", true, "Kimlik"),
              doc("a-evlilik", "Evlilik / bosanma belgeleri", "Nikah, bosanma, ayrilik veya aile birligi belgeleri.", true, "Aile kaydi"),
              doc("a-velayet", "Velayet / nafaka destek belgeleri", "Cocuklarin bakimi, egitimi ve giderlerine iliskin belgeler.", true, "Cocuk ve destek"),
              doc("a-gelir", "Gelir ve gider belgeleri", "Nafaka veya katilma alacagi taleplerinde kullanilir.", false, "Mali durum"),
              doc("a-sosyal", "Sosyal inceleme / adres belgeleri", "Gerekirse mahkemeye sunulacak destek evraki.", false, "Ekler"),
              doc("a-tedbir", "Tedbir / koruma talebi belgeleri", "Acil koruma, uzaklastirma veya gecici onlem icin.", false, "Usul")
          )
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
    CaseTemplateDefinition template = requireTemplate(request.caseType());
    Map<String, Boolean> completedDocumentIds = request.completedDocumentIds() == null
        ? Map.of()
        : request.completedDocumentIds().stream()
            .filter(StringUtils::hasText)
            .map(value -> value.trim())
            .collect(Collectors.toMap(Function.identity(), ignored -> true, (left, right) -> left, LinkedHashMap::new));
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    CaseRecordSnapshot snapshot = new CaseRecordSnapshot(
        UUID.randomUUID().toString(),
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
        now,
        now
    );
    legalCaseRepository.save(LegalCaseEntity.fromSnapshot(snapshot));
    return toResponse(snapshot);
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
        template.summary(),
        template.documents().stream()
            .map(document -> new CaseDocumentDto(document.id(), document.title(), document.detail(), document.required(), document.group(), false))
            .toList()
    );
  }

  private CaseRecordResponse toResponse(CaseRecordSnapshot snapshot) {
    List<CaseDocumentDto> documents = snapshot.documents().stream()
        .map(document -> new CaseDocumentDto(document.id(), document.title(), document.detail(), document.required(), document.group(), document.completed()))
        .toList();
    int requiredDocumentCount = (int) documents.stream().filter(CaseDocumentDto::required).count();
    int completedRequiredDocumentCount = (int) documents.stream().filter(document -> document.required() && document.completed()).count();
    int progress = requiredDocumentCount == 0 ? 100 : Math.round((completedRequiredDocumentCount * 100.0f) / requiredDocumentCount);
    CaseTemplateDefinition template = TEMPLATES.get(snapshot.caseType());
    return new CaseRecordResponse(
        snapshot.id(),
        snapshot.caseType(),
        template == null ? snapshot.caseType() : template.label(),
        snapshot.fileTitle(),
        snapshot.caseNumber(),
        snapshot.courtName(),
        snapshot.city(),
        snapshot.notes(),
        requiredDocumentCount,
        completedRequiredDocumentCount,
        progress,
        documents,
        snapshot.createdAt(),
        snapshot.updatedAt()
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

  private static CaseDocumentSnapshot doc(String id, String title, String detail, boolean required, String group) {
    return new CaseDocumentSnapshot(id, title, detail, required, group, false);
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

  public record CaseTemplateDefinition(
      String caseType,
      String label,
      String title,
      String courtHint,
      String summary,
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
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
    private CaseRecordSnapshot withDocuments(List<CaseDocumentSnapshot> documents) {
      return new CaseRecordSnapshot(id, caseType, fileTitle, caseNumber, courtName, city, notes, documents, createdAt, updatedAt);
    }

    private CaseRecordSnapshot withUpdatedAt(OffsetDateTime updatedAt) {
      return new CaseRecordSnapshot(id, caseType, fileTitle, caseNumber, courtName, city, notes, documents, createdAt, updatedAt);
    }
  }
}

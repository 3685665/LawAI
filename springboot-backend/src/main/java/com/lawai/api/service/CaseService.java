package com.lawai.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.dto.CaseCreateRequest;
import com.lawai.api.dto.CaseDocumentDto;
import com.lawai.api.dto.CaseDocumentPatchResponse;
import com.lawai.api.dto.CaseDocumentUpdateRequest;
import com.lawai.api.dto.CaseRecordResponse;
import com.lawai.api.dto.CaseTemplateDto;
import com.lawai.api.dto.CaseTemplatesResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private final Path storagePath;
  private final ObjectMapper objectMapper;

  public CaseService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.storagePath = Path.of(System.getProperty("user.dir"), "data", "cases.json");
  }

  public CaseTemplatesResponse templates() {
    return new CaseTemplatesResponse(
        TEMPLATES.values().stream()
            .sorted(Comparator.comparing(CaseTemplateDefinition::label))
            .map(this::toTemplateDto)
            .toList()
    );
  }

  public List<CaseRecordResponse> listCases() {
    return load().stream()
        .sorted(Comparator.comparing(CaseRecordSnapshot::updatedAt).reversed())
        .map(this::toResponse)
        .toList();
  }

  public List<CaseRecordResponse> seedSamples() {
    List<CaseRecordSnapshot> existing = load();
    boolean alreadySeeded = existing.stream().anyMatch(item -> item.clientName().startsWith("Ornek "));
    if (alreadySeeded) {
      return listCasesFromSnapshots(existing);
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<CaseRecordSnapshot> samples = new ArrayList<>(existing);
    samples.add(sampleCase(
        "genel",
        "Ornek Ahmet Yilmaz",
        "Ornek XYZ A.S.",
        "Ankara 3. Asliye Hukuk Mahkemesi",
        "Sözleşme bedelinin tahsili",
        "Sözleşme uyarinca dogan alacagin tahsili ve ihtarname surecinin kontrolu.",
        List.of("genel-vekalet", "genel-kimlik", "genel-dilekce", "genel-delil", "genel-harc"),
        now.minusDays(3)
    ));
    samples.add(sampleCase(
        "is",
        "Ornek Ayse Kaya",
        "Ornek Isveren Ltd.",
        "Istanbul 8. Is Mahkemesi",
        "Fesih ve alacaklar",
        "Is akdinin feshi, fazla mesai ve kullanilmayan izinlerin takibi.",
        List.of("is-vekalet", "is-hizmet", "is-fesih", "is-bordro"),
        now.minusDays(2)
    ));
    samples.add(sampleCase(
        "icra",
        "Ornek Mehmet Demir",
        "Ornek Borclu San. Ltd.",
        "Bursa 1. Icra Mudurlugu",
        "Ilamli icra takibi",
        "Ilam ve hesap cetveli ile icra takibi baslatilmasi ve tebligat evrakinin kontrolu.",
        List.of("i-dayanak", "i-vekalet", "i-hesap", "i-tebligat", "i-adres"),
        now.minusDays(1)
    ));
    save(samples);
    return listCasesFromSnapshots(samples);
  }

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
        request.clientName().trim(),
        request.opponentName().trim(),
        request.courtName().trim(),
        request.subject().trim(),
        request.summary().trim(),
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
    List<CaseRecordSnapshot> cases = new ArrayList<>(load());
    cases.add(snapshot);
    save(cases);
    return toResponse(snapshot);
  }

  public CaseRecordResponse getCase(String id) {
    return load().stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .map(this::toResponse)
        .orElseThrow(() -> new IllegalArgumentException("Dava bulunamadi."));
  }

  public List<CaseRecordResponse> deleteCase(String id) {
    List<CaseRecordSnapshot> cases = load();
    List<CaseRecordSnapshot> remaining = cases.stream()
        .filter(item -> !item.id().equals(id))
        .toList();
    if (remaining.size() == cases.size()) {
      throw new IllegalArgumentException("Dava bulunamadi.");
    }
    save(remaining);
    return listCasesFromSnapshots(remaining);
  }

  public CaseDocumentPatchResponse updateDocument(String caseId, String documentId, CaseDocumentUpdateRequest request) {
    List<CaseRecordSnapshot> cases = load();
    boolean updated = false;
    List<CaseRecordSnapshot> nextCases = new ArrayList<>();
    CaseRecordSnapshot updatedCase = null;

    for (CaseRecordSnapshot snapshot : cases) {
      if (!snapshot.id().equals(caseId)) {
        nextCases.add(snapshot);
        continue;
      }

      boolean documentFound = false;
      List<CaseDocumentSnapshot> documents = new ArrayList<>();
      for (CaseDocumentSnapshot document : snapshot.documents()) {
        if (document.id().equals(documentId)) {
          documentFound = true;
          documents.add(new CaseDocumentSnapshot(
              document.id(),
              document.title(),
              document.detail(),
              document.required(),
              document.group(),
              request.completed()
          ));
        } else {
          documents.add(document);
        }
      }

      if (!documentFound) {
        throw new IllegalArgumentException("Belge bulunamadi.");
      }

      updated = true;
      updatedCase = snapshot.withDocuments(documents).withUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
      nextCases.add(updatedCase);
      continue;
    }

    if (!updated || updatedCase == null) {
      throw new IllegalArgumentException("Dava bulunamadi.");
    }

    save(nextCases);
    return new CaseDocumentPatchResponse(toResponse(updatedCase), listCasesFromSnapshots(nextCases));
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
        snapshot.clientName(),
        snapshot.opponentName(),
        snapshot.courtName(),
        snapshot.subject(),
        snapshot.summary(),
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
    if (!Files.exists(storagePath)) {
      return new ArrayList<>();
    }
    try {
      CaseStorePayload payload = objectMapper.readValue(Files.readString(storagePath), CaseStorePayload.class);
      return payload.cases() == null ? new ArrayList<>() : new ArrayList<>(payload.cases());
    } catch (IOException exception) {
      throw new IllegalStateException("Dava kayitlari yuklenemedi: " + exception.getMessage(), exception);
    }
  }

  private void save(List<CaseRecordSnapshot> cases) {
    try {
      Files.createDirectories(storagePath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter()
          .writeValue(storagePath.toFile(), new CaseStorePayload(cases));
    } catch (IOException exception) {
      throw new IllegalStateException("Dava kayitlari kaydedilemedi: " + exception.getMessage(), exception);
    }
  }

  private static CaseDocumentSnapshot doc(String id, String title, String detail, boolean required, String group) {
    return new CaseDocumentSnapshot(id, title, detail, required, group, false);
  }

  private CaseRecordSnapshot sampleCase(
      String caseType,
      String clientName,
      String opponentName,
      String courtName,
      String subject,
      String summary,
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
        clientName,
        opponentName,
        courtName,
        subject,
        summary,
        documents,
        updatedAt.minusHours(6),
        updatedAt
    );
  }

  private String normalize(String value) {
    return StringUtils.hasText(value) ? value.trim().toLowerCase() : "";
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
      String clientName,
      String opponentName,
      String courtName,
      String subject,
      String summary,
      List<CaseDocumentSnapshot> documents,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
    private CaseRecordSnapshot withDocuments(List<CaseDocumentSnapshot> documents) {
      return new CaseRecordSnapshot(id, caseType, clientName, opponentName, courtName, subject, summary, documents, createdAt, updatedAt);
    }

    private CaseRecordSnapshot withUpdatedAt(OffsetDateTime updatedAt) {
      return new CaseRecordSnapshot(id, caseType, clientName, opponentName, courtName, subject, summary, documents, createdAt, updatedAt);
    }
  }

  public record CaseStorePayload(List<CaseRecordSnapshot> cases) {
  }
}

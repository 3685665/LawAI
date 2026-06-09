package com.lawai.persistence;

import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.subscription.model.SubscriptionPlanRecord;
import com.lawai.api.subscription.model.UserSubscriptionRecord;
import com.lawai.persistence.entity.ActivityLogEntity;
import com.lawai.persistence.entity.CaseDocumentEntity;
import com.lawai.persistence.entity.ChatMessageEntity;
import com.lawai.persistence.entity.ChatSessionEntity;
import com.lawai.persistence.entity.FeedbackEntity;
import com.lawai.persistence.entity.LegalCaseEntity;
import com.lawai.persistence.entity.SubscriptionPlanEntity;
import com.lawai.persistence.entity.UserEntity;
import com.lawai.persistence.entity.UserSubscriptionEntity;
import com.lawai.persistence.repository.ActivityLogRepository;
import com.lawai.persistence.repository.ChatSessionRepository;
import com.lawai.persistence.repository.FeedbackRepository;
import com.lawai.persistence.repository.LegalCaseRepository;
import com.lawai.persistence.repository.SubscriptionPlanRepository;
import com.lawai.persistence.repository.UserRepository;
import com.lawai.persistence.repository.UserSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.data.seed-enabled", havingValue = "true", matchIfMissing = true)
public class SampleDataSeeder {

  private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

  static final String DEMO_PASSWORD = "Demo1234!";
  static final String SEED_MARKER_EMAIL = "avukat@demo.lawai";

  private final UserRepository userRepository;
  private final SubscriptionPlanRepository subscriptionPlanRepository;
  private final UserSubscriptionRepository userSubscriptionRepository;
  private final LegalCaseRepository legalCaseRepository;
  private final ChatSessionRepository chatSessionRepository;
  private final ActivityLogRepository activityLogRepository;
  private final FeedbackRepository feedbackRepository;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final String bootstrapEmail;

  public SampleDataSeeder(
      UserRepository userRepository,
      SubscriptionPlanRepository subscriptionPlanRepository,
      UserSubscriptionRepository userSubscriptionRepository,
      LegalCaseRepository legalCaseRepository,
      ChatSessionRepository chatSessionRepository,
      ActivityLogRepository activityLogRepository,
      FeedbackRepository feedbackRepository,
      @Value("${app.auth.bootstrap-email:admin@lawai.local}") String bootstrapEmail
  ) {
    this.userRepository = userRepository;
    this.subscriptionPlanRepository = subscriptionPlanRepository;
    this.userSubscriptionRepository = userSubscriptionRepository;
    this.legalCaseRepository = legalCaseRepository;
    this.chatSessionRepository = chatSessionRepository;
    this.activityLogRepository = activityLogRepository;
    this.feedbackRepository = feedbackRepository;
    this.bootstrapEmail = normalizeEmail(bootstrapEmail);
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(100)
  @Transactional
  public void seedIfEmpty() {
    if (userRepository.findById("seed-user-avukat-001").isPresent()) {
      return;
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);

    UserEntity admin = ensureAdminVerified(now);
    UserEntity avukat = saveUser(
        "seed-user-avukat-001",
        "Av. Elif Demir",
        SEED_MARKER_EMAIL,
        passwordHash,
        "USER",
        now.minusDays(30),
        now.minusHours(2),
        true,
        now.minusDays(29)
    );
    UserEntity stajyer = saveUser(
        "seed-user-stajyer-001",
        "Stj. Can Ozkan",
        "stajyer@demo.lawai",
        passwordHash,
        "USER",
        now.minusDays(14),
        now.minusDays(1),
        true,
        now.minusDays(13)
    );

    seedSubscriptionPlans(now);
    seedUserSubscriptions(admin, avukat, now);
    seedLegalCases(now);
    seedChatSessions(avukat, stajyer, now);
    seedActivityLogs(admin, avukat, stajyer, now);
    seedFeedback(avukat, stajyer, now);

    log.info(
        "Ornek veriler yuklendi. Giris: {} / {} veya {} / {}",
        SEED_MARKER_EMAIL, DEMO_PASSWORD, normalizeEmail(bootstrapEmail), "ChangeMe123!"
    );
  }

  private UserEntity ensureAdminVerified(OffsetDateTime now) {
    Optional<UserEntity> adminOptional = userRepository.findByEmailIgnoreCase(bootstrapEmail);
    if (adminOptional.isEmpty()) {
      return saveUser(
          "seed-user-admin-001",
          "Yonetici",
          bootstrapEmail,
          passwordEncoder.encode("ChangeMe123!"),
          "ADMIN",
          now.minusDays(60),
          now.minusHours(1),
          true,
          now.minusDays(59)
      );
    }
    UserEntity admin = adminOptional.get();
    if (!Boolean.TRUE.equals(admin.toRecord().verified())) {
      admin.applyRecord(admin.toRecord().withVerified(true, now.minusDays(59)).withLastLoginAt(now.minusHours(1)));
      admin = userRepository.save(admin);
    }
    return admin;
  }

  private UserEntity saveUser(
      String id,
      String name,
      String email,
      String passwordHash,
      String role,
      OffsetDateTime createdAt,
      OffsetDateTime lastLoginAt,
      boolean verified,
      OffsetDateTime verifiedAt
  ) {
    if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
      return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
    return userRepository.save(new UserEntity(
        id, name, normalizeEmail(email), passwordHash, role, createdAt, lastLoginAt, verified, verifiedAt
    ));
  }

  private void seedSubscriptionPlans(OffsetDateTime now) {
    if (subscriptionPlanRepository.findById("profesyonel").isPresent()) {
      return;
    }
    List<SubscriptionPlanRecord> plans = List.of(
        plan("ucretsiz", "Ucretsiz", "UCRETSIZ", "Tanitim suresi boyunca", 0, 0, "3 gunde yenilenen", "20 kullanim hakki", false, 10,
            List.of("Gelismis dilekce olusturma", "Dokuman analiz destegi", "Hukuki soru-cevap", "Karar arama araci", "Kisisel avukat profili"),
            List.of("UYAP uyumlu cikti", "Hesaplama araclari", "Derin arastirma modu"), "Ucretsiz basla", now),
        plan("baslangic", "Baslangic", "", "KDV haric", 1900, 19000, "Gunde yenilenen", "20 kullanim hakki", false, 20,
            List.of("Ucretsiz paketteki her sey", "Gunluk yenilenen 20 kullanim hakki", "UYAP uyumlu cikti"),
            List.of("Infaz hesaplama", "Isci alacaklari hesaplama", "Sozlesme analiz modulu"), "Baslangic'i sec", now),
        plan("profesyonel", "Profesyonel", "ONERILEN", "KDV haric", 2900, 29000, "Gunde yenilenen", "50 kullanim hakki", true, 30,
            List.of("Baslangic paketindeki her sey", "Derin arastirma modu", "Sozlesme yazma araci", "Arac deger kaybi hesaplama", "Miras hukuku hesaplama", "Faiz hesaplama", "Dava harc ve masraf"),
            List.of("Sozlesme analiz modulu", "Dava dosyalarim modulu"), "Profesyonel'i sec", now),
        plan("super", "Super", "SUPER", "KDV haric", 3900, 39000, "Gunde yenilenen", "100 kullanim hakki", false, 40,
            List.of("Profesyonel paketindeki her sey", "Sozlesme analiz modulu", "Dava dosyalarim modulu", "Infaz hesaplama", "Isci alacaklari hesaplama", "Tum hesaplama araclari", "Derin arastirma modu"),
            List.of(), "Super'i sec", now),
        plan("sinirsiz", "Sinirsiz", "LIMITSIZ", "KDV haric", 5000, 50000, "Sinirsiz kullanim hakki", "Tum ozellikler acik", false, 50,
            List.of("Super paketindeki her sey", "Sinirsiz kullanim hakki", "7/24 Teknik destek", "Tum moduller ve araclar", "Limitsiz analiz ve dilekce"),
            List.of(), "Sinirsiz'i sec", now),
        plan("kurumsal", "Kurumsal", "", "Iletisime geciniz", 0, 0, "Ekip kullanimina uygun", "Ozel kullanim haklari", false, 60,
            List.of("Coklu kullanici yonetimi", "Kuruma ozel dilekce ve sozlesme sablonlari", "Kuruma ozel analiz ciktilari", "Talebe yonelik ozel cozumler", "Oncelikli destek"),
            List.of(), "Irtibat kurun", now)
    );
    subscriptionPlanRepository.saveAll(plans.stream().map(SubscriptionPlanEntity::fromRecord).toList());
  }

  private void seedUserSubscriptions(UserEntity admin, UserEntity avukat, OffsetDateTime now) {
    if (userSubscriptionRepository.findById("seed-sub-avukat-001").isPresent()) {
      return;
    }
    userSubscriptionRepository.saveAll(List.of(
        UserSubscriptionEntity.fromRecord(new UserSubscriptionRecord(
            "seed-sub-admin-001", admin.getId(), "Yonetici", admin.getEmail(),
            "sinirsiz", "Sinirsiz", "yearly", "ACTIVE", "manual",
            "", "", "", "", "paid", false,
            now.minusMonths(2), now.plusMonths(10), now.minusMonths(2), now
        )),
        UserSubscriptionEntity.fromRecord(new UserSubscriptionRecord(
            "seed-sub-avukat-001", avukat.getId(), "Av. Elif Demir", avukat.getEmail(),
            "profesyonel", "Profesyonel", "monthly", "ACTIVE", "manual",
            "", "", "", "", "paid", false,
            now.minusDays(20), now.plusDays(10), now.minusDays(20), now
        ))
    ));
  }

  private void seedLegalCases(OffsetDateTime now) {
    if (legalCaseRepository.findById("seed-case-001").isPresent()) {
      return;
    }
    legalCaseRepository.saveAll(List.of(
        buildCase(
            "seed-case-001", "genel", "Ahmet Yilmaz", "Beta Insaat A.S.",
            "Ankara 5. Asliye Hukuk Mahkemesi", "Sozlesme bedelinin tahsili",
            "Taseron sozlesmesinden dogan 420.000 TL alacagin ihtarname sonrasi tahsili.",
            now.minusDays(12), now.minusDays(1),
            List.of(
                doc("genel-vekalet", "Vekaletname / yetki belgesi", "Musteri vekaleti ve temsil yetkisini gosteren ana belge.", true, "Yetki", true),
                doc("genel-kimlik", "Taraf kimlik ve iletisim bilgileri", "TCKN / VKN, adres, telefon ve tebligat bilgileri.", true, "Taraf bilgileri", true),
                doc("genel-dilekce", "Dava dilekcesi", "Talep sonucu, vakialar ve hukuki nedenler.", true, "Dava evraki", true),
                doc("genel-delil", "Delil listesi", "Belgeler, taniklar, bilirkiisi ve diger ispat vasitalari.", true, "Dava evraki", false),
                doc("genel-harc", "Harc ve gider avansi makbuzlari", "Basvuru harci, pesin harc ve gider avansi kayitlari.", true, "Usul", true)
            )
        ),
        buildCase(
            "seed-case-002", "is", "Ayse Kaya", "Delta Lojistik Ltd.",
            "Istanbul Anadolu 12. Is Mahkemesi", "Fesih ve alacaklar",
            "Haksiz fesih, kullanilmayan izin, fazla mesai ve ucret alacaklarinin tahsili.",
            now.minusDays(8), now.minusHours(6),
            List.of(
                doc("is-vekalet", "Vekaletname / yetki belgesi", "Avukatlik yetkisini ve temsil kapsamini gosterir.", true, "Yetki", true),
                doc("is-hizmet", "Hizmet dokumu / SGK kayitlari", "Calisma suresi ve prim kayitlarini teyit eder.", true, "Calisma kaydi", true),
                doc("is-fesih", "Fesih bildirimi / cikis evragi", "Fesih tarihini ve sebebini netlestirir.", true, "Fesih", true),
                doc("is-bordro", "Bordro ve ucret belgeleri", "Maas, fazla mesai ve kesintilerin ispatinda kullanilir.", true, "Ucret", false),
                doc("is-arabuluculuk", "Arabuluculuk son tutanagi", "Dava sartidir; sure ve taraf bilgileri kontrol edilmelidir.", true, "Usul", true)
            )
        ),
        buildCase(
            "seed-case-003", "icra", "Mehmet Demir", "Gamma Ticaret San. Ltd.",
            "Bursa 2. Icra Mudurlugu", "Ilamli icra takibi",
            "Kesinlesmis ilam uyarinca ana alacak, faiz ve vekalet ucretinin takibi.",
            now.minusDays(5), now.minusDays(2),
            List.of(
                doc("i-dayanak", "Takip dayanagi belge", "Ilam, senet, sozlesme veya faturaya dayali evrak.", true, "Dayanak", true),
                doc("i-vekalet", "Vekaletname / yetki belgesi", "Icra islemlerinde temsil yetkisi icin gerekir.", true, "Yetki", true),
                doc("i-hesap", "Hesap cetveli", "Faiz, vekalet ucreti ve toplam alacak hesabi.", true, "Hesap", false),
                doc("i-tebligat", "Tebligat / ihtar evraki", "Borc bildirimi ve temerrut icin kullanilir.", true, "Usul", true),
                doc("i-adres", "Alacakli / borclu adres bilgileri", "Tebligat ve takip islemleri icin gerekir.", true, "Taraf bilgileri", true)
            )
        ),
        buildCase(
            "seed-case-004", "aile", "Zeynep Arslan", "Murat Arslan",
            "Izmir 3. Aile Mahkemesi", "Velayet ve iştirak nafakası",
            "Ortak cocuk lehine velayet duzenlemesi ve aylik iştirak nafakasi talebi.",
            now.minusDays(3), now.minusHours(12),
            List.of(
                doc("a-vekalet", "Vekaletname / yetki belgesi", "Temsil yetkisini gosterir.", true, "Yetki", true),
                doc("a-nufus", "Nufus kayit ornegi", "Taraflarin aile bagini ve baglantili kayitlarini ortaya koyar.", true, "Kimlik", true),
                doc("a-evlilik", "Evlilik / bosanma belgeleri", "Nikah, bosanma, ayrilik veya aile birligi belgeleri.", true, "Aile kaydi", false),
                doc("a-velayet", "Velayet / nafaka destek belgeleri", "Cocuklarin bakimi, egitimi ve giderlerine iliskin belgeler.", true, "Cocuk ve destek", false)
            )
        )
    ));
  }

  private void seedChatSessions(UserEntity avukat, UserEntity stajyer, OffsetDateTime now) {
    if (chatSessionRepository.findById("seed-chat-001").isPresent()) {
      return;
    }

    ChatSessionEntity kiraSession = new ChatSessionEntity(
        "seed-chat-001", avukat.getId(), "Kira alacagi davasi",
        now.minusDays(4), now.minusDays(4)
    );
    kiraSession.addMessage(new ChatMessageEntity(
        "seed-msg-001", kiraSession, "user",
        "Kira bedeli tahsili icin hangi belgeler gerekli?",
        List.of(), null, now.minusDays(4).minusMinutes(5)
    ));
    kiraSession.addMessage(new ChatMessageEntity(
        "seed-msg-002", kiraSession, "assistant",
        """
        Kira alacagi davasinda genellikle sozlesme, odeme dekontlari, ihtarname teblig evraki, \
        taraflarin kimlik/adres bilgileri ve varsa tahliye tutanagi istenir. \
        TBK m.315 ve devamı hükümleri çerçevesinde temerrut ve faiz talebi ayrıca değerlendirilmelidir.
        """.trim(),
        List.of(new PrecedentDto(
            null, "Yargitay HGK", "6. HD", "2021/1234", "2022/567", "15.03.2022",
            "Kira alacagi", "Kira bedelinin tahsiline iliskin temerrut ve ispat degerlendirmesi.",
            "Kira sozlesmesi ve ihtarname ile birlikte odeme yapilmadiginin ispatlanmasi gerekir."
        )),
        "Bu yanit genel bilgilendirme amaclidir; somut dosya icin vekalet gereklidir.",
        now.minusDays(4)
    ));

    ChatSessionEntity isSession = new ChatSessionEntity(
        "seed-chat-002", avukat.getId(), "Is akdi fesih sureci",
        now.minusDays(2), now.minusDays(1)
    );
    isSession.addMessage(new ChatMessageEntity(
        "seed-msg-003", isSession, "user",
        "Isveren fesih bildiriminde hangi usul hatalari dava acilmasina yol acar?",
        List.of(), null, now.minusDays(2)
    ));
    isSession.addMessage(new ChatMessageEntity(
        "seed-msg-004", isSession, "assistant",
        """
        Fesih bildiriminde sebep acikligi, imza, teblig tarihi ve SGK cikis islemlerinin uyumu onemlidir. \
        Zorunlu arabuluculuk dava sartina uyulmamasi da usulden reddedilme nedeni olabilir.
        """.trim(),
        List.of(), null, now.minusDays(2).plusMinutes(2)
    ));

    ChatSessionEntity stajSession = new ChatSessionEntity(
        "seed-chat-003", stajyer.getId(), "Icra takip dayanagi",
        now.minusDays(1), now.minusHours(8)
    );
    stajSession.addMessage(new ChatMessageEntity(
        "seed-msg-005", stajSession, "user",
        "Ilamli icra takibinde hesap cetvelinde neler bulunmali?",
        List.of(), null, now.minusDays(1)
    ));
    stajSession.addMessage(new ChatMessageEntity(
        "seed-msg-006", stajSession, "assistant",
        "Ana alacak, islemis faiz, takip sonrasi faiz, vekalet ucreti ve masraflar ayri kalemler halinde gosterilmelidir.",
        List.of(), null, now.minusHours(8)
    ));

    chatSessionRepository.saveAll(List.of(kiraSession, isSession, stajSession));
  }

  private void seedActivityLogs(UserEntity admin, UserEntity avukat, UserEntity stajyer, OffsetDateTime now) {
    if (activityLogRepository.findById("seed-log-001").isPresent()) {
      return;
    }
    activityLogRepository.saveAll(List.of(
        log("seed-log-001", admin, "backend", "login", "Giris", "Yonetici oturumu acildi", "/auth/login", now.minusHours(1)),
        log("seed-log-002", admin, "frontend", "screen-view", "Yonetim Paneli", "Kullanici listesi goruntulendi", "/admin/users", now.minusMinutes(50)),
        log("seed-log-003", avukat, "frontend", "screen-view", "Sohbet", "Hukuki soru-cevap ekrani", "/chat", now.minusDays(4)),
        log("seed-log-004", avukat, "backend", "chat-exchange", "Sohbet", "Kira alacagi sorusu yanitlandi", "/api/chat", now.minusDays(4)),
        log("seed-log-005", avukat, "frontend", "screen-view", "Dava Dosyalarim", "Is hukuku dosyasi acildi", "/cases/seed-case-002", now.minusDays(2)),
        log("seed-log-006", avukat, "backend", "case-document-update", "Dava Dosyalarim", "Delil listesi tamamlandi", "/api/cases/seed-case-002/documents", now.minusDays(1)),
        log("seed-log-007", stajyer, "frontend", "screen-view", "Abonelikler", "Plan karsilastirma", "/subscriptions", now.minusDays(1)),
        log("seed-log-008", stajyer, "frontend", "screen-view", "Geri Bildirim", "Oneri formu acildi", "/feedback", now.minusHours(6))
    ));
  }

  private void seedFeedback(UserEntity avukat, UserEntity stajyer, OffsetDateTime now) {
    if (feedbackRepository.findById("seed-feedback-001").isPresent()) {
      return;
    }
    feedbackRepository.saveAll(List.of(
        new FeedbackEntity(
            "seed-feedback-001", avukat.getId(), "Av. Elif Demir", avukat.getEmail(),
            "feature", "UYAP uyumlu Word ciktisi",
            "Dilekce ciktisinda paragraf numaralandirmasi UYAP aktarimina daha uygun olabilir.",
            "received", now.minusDays(3)
        ),
        new FeedbackEntity(
            "seed-feedback-002", stajyer.getId(), "Stj. Can Ozkan", stajyer.getEmail(),
            "bug", "Dava dosyasi ilerleme cubugu",
            "Zorunlu belgeler tamamlandiginda ilerleme yuzdesi bazen %100 gosterilmiyor.",
            "read", now.minusDays(1)
        ),
        new FeedbackEntity(
            "seed-feedback-003", avukat.getId(), "Av. Elif Demir", avukat.getEmail(),
            "general", "Karar arama hizi",
            "Yargitay karar aramasinda filtreler cok faydali, sonuc suresi de iyi.",
            "resolved", now.minusHours(10)
        )
    ));
  }

  private ActivityLogEntity log(
      String id, UserEntity user, String source, String action, String screen, String detail, String path, OffsetDateTime createdAt
  ) {
    return ActivityLogEntity.fromRecord(new com.lawai.api.model.ActivityLogRecord(
        id, user.getId(), user.toRecord().name(), user.getEmail(), user.toRecord().role(),
        source, action, screen, detail, path, createdAt
    ));
  }

  private LegalCaseEntity buildCase(
      String id,
      String caseType,
      String clientName,
      String opponentName,
      String courtName,
      String subject,
      String summary,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      List<DocSeed> documents
  ) {
    LegalCaseEntity legalCase = new LegalCaseEntity(
        id, caseType, clientName, opponentName, courtName, subject, summary, createdAt, updatedAt
    );
    for (DocSeed document : documents) {
      legalCase.getDocuments().add(new CaseDocumentEntity(
          document.id(), legalCase, document.title(), document.detail(),
          document.required(), document.group(), document.completed()
      ));
    }
    return legalCase;
  }

  private DocSeed doc(String id, String title, String detail, boolean required, String group, boolean completed) {
    return new DocSeed(id, title, detail, required, group, completed);
  }

  private record DocSeed(String id, String title, String detail, boolean required, String group, boolean completed) {
  }

  private SubscriptionPlanRecord plan(
      String id, String name, String badge, String description, int monthlyPrice, int yearlyPrice,
      String usagePeriod, String usageLimit, boolean highlighted, int sortOrder,
      List<String> features, List<String> lockedFeatures, String ctaLabel, OffsetDateTime now
  ) {
    return new SubscriptionPlanRecord(
        id, name, id, badge, description, monthlyPrice, yearlyPrice, "TRY", usageLimit, usagePeriod,
        highlighted, true, sortOrder, features, lockedFeatures, ctaLabel, "", "", "", now, now
    );
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }
}

package com.lawai.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.subscription.dto.SubscriptionPlanDto;
import com.lawai.api.subscription.dto.SubscriptionPlanRequest;
import com.lawai.api.subscription.model.SubscriptionPlanRecord;
import com.lawai.api.subscription.model.SubscriptionStorePayload;
import com.lawai.auth.model.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SubscriptionPlanService {

  private final ObjectMapper objectMapper;
  private final Path storagePath;

  public SubscriptionPlanService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.storagePath = Path.of(System.getProperty("user.dir"), "data", "subscription-store.json");
  }

  public List<SubscriptionPlanDto> listActive() {
    return sorted(load()).stream()
        .filter(SubscriptionPlanRecord::active)
        .map(this::toDto)
        .toList();
  }

  public List<SubscriptionPlanDto> listAll(AuthenticatedUser user) {
    requireAdmin(user);
    return sorted(load()).stream().map(this::toDto).toList();
  }

  public SubscriptionPlanDto create(AuthenticatedUser user, SubscriptionPlanRequest request) {
    requireAdmin(user);
    List<SubscriptionPlanRecord> plans = new ArrayList<>(load());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    SubscriptionPlanRecord record = fromRequest(UUID.randomUUID().toString(), request, now, now);
    plans.add(record);
    save(plans);
    return toDto(record);
  }

  public SubscriptionPlanDto update(AuthenticatedUser user, String id, SubscriptionPlanRequest request) {
    requireAdmin(user);
    List<SubscriptionPlanRecord> plans = new ArrayList<>(load());
    boolean updated = false;
    List<SubscriptionPlanRecord> rewritten = new ArrayList<>(plans.size());
    for (SubscriptionPlanRecord plan : plans) {
      if (plan.id().equals(id)) {
        rewritten.add(fromRequest(plan.id(), request, plan.createdAt(), OffsetDateTime.now(ZoneOffset.UTC)));
        updated = true;
      } else {
        rewritten.add(plan);
      }
    }
    if (!updated) {
      throw new IllegalArgumentException("Abonelik plani bulunamadi.");
    }
    save(rewritten);
    return rewritten.stream().filter(item -> item.id().equals(id)).findFirst().map(this::toDto).orElseThrow();
  }

  public void delete(AuthenticatedUser user, String id) {
    requireAdmin(user);
    List<SubscriptionPlanRecord> plans = new ArrayList<>(load());
    List<SubscriptionPlanRecord> rewritten = plans.stream().filter(item -> !item.id().equals(id)).toList();
    if (rewritten.size() == plans.size()) {
      throw new IllegalArgumentException("Abonelik plani bulunamadi.");
    }
    save(rewritten);
  }

  private SubscriptionPlanRecord fromRequest(String id, SubscriptionPlanRequest request, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    String name = cleanRequired(request.name(), "Plan adi gerekli.");
    String slug = StringUtils.hasText(request.slug()) ? slugify(request.slug()) : slugify(name);
    if (!StringUtils.hasText(slug)) {
      slug = id;
    }
    List<String> features = cleanList(request.features());
    if (features.isEmpty()) {
      throw new IllegalArgumentException("En az bir ozellik girilmeli.");
    }
    return new SubscriptionPlanRecord(
        id,
        name,
        slug,
        clean(request.badge()),
        clean(request.description()),
        request.monthlyPrice(),
        request.yearlyPrice(),
        StringUtils.hasText(request.currency()) ? request.currency().trim() : "TRY",
        clean(request.usageLimit()),
        clean(request.usagePeriod()),
        request.highlighted(),
        request.active(),
        request.sortOrder(),
        features,
        cleanList(request.lockedFeatures()),
        StringUtils.hasText(request.ctaLabel()) ? request.ctaLabel().trim() : name + " sec",
        createdAt,
        updatedAt
    );
  }

  private List<SubscriptionPlanRecord> load() {
    if (!Files.exists(storagePath)) {
      List<SubscriptionPlanRecord> defaults = defaults();
      save(defaults);
      return defaults;
    }
    try {
      SubscriptionStorePayload payload = objectMapper.readValue(Files.readString(storagePath), SubscriptionStorePayload.class);
      return payload.plans() == null ? new ArrayList<>() : new ArrayList<>(payload.plans());
    } catch (IOException exception) {
      throw new IllegalStateException("Abonelik verisi yuklenemedi: " + exception.getMessage(), exception);
    }
  }

  private void save(List<SubscriptionPlanRecord> plans) {
    try {
      Files.createDirectories(storagePath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), new SubscriptionStorePayload(plans));
    } catch (IOException exception) {
      throw new IllegalStateException("Abonelik verisi kaydedilemedi: " + exception.getMessage(), exception);
    }
  }

  private List<SubscriptionPlanRecord> defaults() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return List.of(
        plan("ucretsiz", "Ucretsiz", "UCRETSIZ", "Tanitim suresi boyunca", 0, 0, "3 gunde yenilenen", "20 kullanim hakki", false, 10, List.of("Gelismis dilekce olusturma", "Dokuman analiz destegi", "Hukuki soru-cevap", "Karar arama araci", "Kisisel avukat profili"), List.of("UYAP uyumlu cikti", "Hesaplama araclari", "Derin arastirma modu"), "Ucretsiz basla", now),
        plan("baslangic", "Baslangic", "", "KDV haric", 1900, 19000, "Gunde yenilenen", "20 kullanim hakki", false, 20, List.of("Ucretsiz paketteki her sey", "Gunluk yenilenen 20 kullanim hakki", "UYAP uyumlu cikti"), List.of("Infaz hesaplama", "Isci alacaklari hesaplama", "Sozlesme analiz modulu"), "Baslangic'i sec", now),
        plan("profesyonel", "Profesyonel", "ONERILEN", "KDV haric", 2900, 29000, "Gunde yenilenen", "50 kullanim hakki", true, 30, List.of("Baslangic paketindeki her sey", "Derin arastirma modu", "Sozlesme yazma araci", "Arac deger kaybi hesaplama", "Miras hukuku hesaplama", "Faiz hesaplama", "Dava harc ve masraf"), List.of("Sozlesme analiz modulu", "Dava dosyalarim modulu"), "Profesyonel'i sec", now),
        plan("super", "Super", "SUPER", "KDV haric", 3900, 39000, "Gunde yenilenen", "100 kullanim hakki", false, 40, List.of("Profesyonel paketindeki her sey", "Sozlesme analiz modulu", "Dava dosyalarim modulu", "Infaz hesaplama", "Isci alacaklari hesaplama", "Tum hesaplama araclari", "Derin arastirma modu"), List.of(), "Super'i sec", now),
        plan("sinirsiz", "Sinirsiz", "LIMITSIZ", "KDV haric", 5000, 50000, "Sinirsiz kullanim hakki", "Tum ozellikler acik", false, 50, List.of("Super paketindeki her sey", "Sinirsiz kullanim hakki", "7/24 Teknik destek", "Tum moduller ve araclar", "Limitsiz analiz ve dilekce"), List.of(), "Sinirsiz'i sec", now),
        plan("kurumsal", "Kurumsal", "", "Iletisime geciniz", 0, 0, "Ekip kullanimina uygun", "Ozel kullanim haklari", false, 60, List.of("Coklu kullanici yonetimi", "Kuruma ozel dilekce ve sozlesme sablonlari", "Kuruma ozel analiz ciktilari", "Talebe yonelik ozel cozumler", "Oncelikli destek"), List.of(), "Irtibat kurun", now)
    );
  }

  private SubscriptionPlanRecord plan(String id, String name, String badge, String description, int monthlyPrice, int yearlyPrice, String usagePeriod, String usageLimit, boolean highlighted, int sortOrder, List<String> features, List<String> lockedFeatures, String ctaLabel, OffsetDateTime now) {
    return new SubscriptionPlanRecord(id, name, id, badge, description, monthlyPrice, yearlyPrice, "TRY", usageLimit, usagePeriod, highlighted, true, sortOrder, features, lockedFeatures, ctaLabel, now, now);
  }

  private List<SubscriptionPlanRecord> sorted(List<SubscriptionPlanRecord> plans) {
    return plans.stream().sorted(Comparator.comparingInt(SubscriptionPlanRecord::sortOrder).thenComparing(SubscriptionPlanRecord::name)).toList();
  }

  private SubscriptionPlanDto toDto(SubscriptionPlanRecord record) {
    return new SubscriptionPlanDto(record.id(), record.name(), record.slug(), record.badge(), record.description(), record.monthlyPrice(), record.yearlyPrice(), record.currency(), record.usageLimit(), record.usagePeriod(), record.highlighted(), record.active(), record.sortOrder(), record.features(), record.lockedFeatures(), record.ctaLabel(), record.createdAt(), record.updatedAt());
  }

  private void requireAdmin(AuthenticatedUser user) {
    if (user == null || !user.isAdmin()) {
      throw new AccessDeniedException("Yalnizca yonetici islem yapabilir.");
    }
  }

  private String cleanRequired(String value, String message) {
    String cleaned = clean(value);
    if (!StringUtils.hasText(cleaned)) {
      throw new IllegalArgumentException(message);
    }
    return cleaned;
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private List<String> cleanList(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream().map(this::clean).filter(StringUtils::hasText).toList();
  }

  private String slugify(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }
}

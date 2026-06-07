package com.lawai.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.subscription.dto.SubscriptionPlanDto;
import com.lawai.api.subscription.dto.SubscriptionPlanRequest;
import com.lawai.api.subscription.dto.UserSubscriptionDto;
import com.lawai.api.subscription.dto.UserSubscriptionRequest;
import com.lawai.api.subscription.model.SubscriptionPlanRecord;
import com.lawai.api.subscription.model.SubscriptionStorePayload;
import com.lawai.api.subscription.model.UserSubscriptionRecord;
import com.lawai.api.subscription.model.BillingEventRecord;
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
    return sorted(loadStore().plans()).stream()
        .filter(SubscriptionPlanRecord::active)
        .map(this::toDto)
        .toList();
  }

  public List<SubscriptionPlanDto> listAll(AuthenticatedUser user) {
    requireAdmin(user);
    return sorted(loadStore().plans()).stream().map(this::toDto).toList();
  }

  public SubscriptionPlanDto create(AuthenticatedUser user, SubscriptionPlanRequest request) {
    requireAdmin(user);
    SubscriptionStorePayload store = loadStore();
    List<SubscriptionPlanRecord> plans = new ArrayList<>(store.plans());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    SubscriptionPlanRecord record = fromRequest(UUID.randomUUID().toString(), request, now, now);
    plans.add(record);
    saveStore(new SubscriptionStorePayload(plans, store.userSubscriptions(), store.billingEvents()));
    return toDto(record);
  }

  public SubscriptionPlanDto update(AuthenticatedUser user, String id, SubscriptionPlanRequest request) {
    requireAdmin(user);
    SubscriptionStorePayload store = loadStore();
    List<SubscriptionPlanRecord> plans = new ArrayList<>(store.plans());
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
    List<UserSubscriptionRecord> subscriptions = store.userSubscriptions().stream()
        .map(item -> item.planId().equals(id)
            ? item.withPlan(id, rewritten.stream().filter(plan -> plan.id().equals(id)).findFirst().map(SubscriptionPlanRecord::name).orElse(item.planName()), item.billingCycle(), item.startsAt(), item.endsAt(), OffsetDateTime.now(ZoneOffset.UTC))
            : item)
        .toList();
    saveStore(new SubscriptionStorePayload(rewritten, subscriptions, store.billingEvents()));
    return rewritten.stream().filter(item -> item.id().equals(id)).findFirst().map(this::toDto).orElseThrow();
  }

  public void delete(AuthenticatedUser user, String id) {
    requireAdmin(user);
    SubscriptionStorePayload store = loadStore();
    List<SubscriptionPlanRecord> plans = new ArrayList<>(store.plans());
    List<SubscriptionPlanRecord> rewritten = plans.stream().filter(item -> !item.id().equals(id)).toList();
    if (rewritten.size() == plans.size()) {
      throw new IllegalArgumentException("Abonelik plani bulunamadi.");
    }
    saveStore(new SubscriptionStorePayload(rewritten, store.userSubscriptions(), store.billingEvents()));
  }

  public UserSubscriptionDto mySubscription(AuthenticatedUser user) {
    UserSubscriptionRecord record = activeSubscriptionFor(user);
    return record == null ? null : toDto(record);
  }

  public UserSubscriptionDto subscribe(AuthenticatedUser user, UserSubscriptionRequest request) {
    SubscriptionStorePayload store = loadStore();
    SubscriptionPlanRecord plan = store.plans().stream()
        .filter(item -> item.id().equals(request.planId()) && item.active())
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Aktif abonelik plani bulunamadi."));
    String billingCycle = normalizeBillingCycle(request.billingCycle());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime endsAt = "yearly".equals(billingCycle) ? now.plusYears(1) : now.plusMonths(1);
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    boolean updated = false;
    UserSubscriptionRecord result = null;
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      if (item.userId().equals(user.id()) && !"CANCELLED".equalsIgnoreCase(item.status())) {
        UserSubscriptionRecord rewritten = item
            .withUser(user.name(), user.email(), now)
            .withPlan(plan.id(), plan.name(), billingCycle, now, endsAt, now)
            .withStatus("ACTIVE", now);
        subscriptions.add(rewritten);
        result = rewritten;
        updated = true;
      } else {
        subscriptions.add(item);
      }
    }
    if (!updated) {
      result = new UserSubscriptionRecord(UUID.randomUUID().toString(), user.id(), user.name(), user.email(), plan.id(), plan.name(), billingCycle, "ACTIVE", "manual", "", "", "", "", "manual", false, now, endsAt, now, now);
      subscriptions.add(result);
    }
    saveStore(new SubscriptionStorePayload(store.plans(), subscriptions, store.billingEvents()));
    return toDto(result);
  }

  public UserSubscriptionDto cancelMine(AuthenticatedUser user) {
    SubscriptionStorePayload store = loadStore();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UserSubscriptionRecord result = null;
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      if (item.userId().equals(user.id()) && "ACTIVE".equalsIgnoreCase(item.status())) {
        UserSubscriptionRecord rewritten = item.withStatus("CANCELLED", now);
        subscriptions.add(rewritten);
        result = rewritten;
      } else {
        subscriptions.add(item);
      }
    }
    if (result == null) {
      throw new IllegalArgumentException("Aktif abonelik bulunamadi.");
    }
    saveStore(new SubscriptionStorePayload(store.plans(), subscriptions, store.billingEvents()));
    return toDto(result);
  }

  public List<UserSubscriptionDto> listUserSubscriptions(AuthenticatedUser user) {
    requireAdmin(user);
    return loadStore().userSubscriptions().stream()
        .sorted(Comparator.comparing(UserSubscriptionRecord::updatedAt).reversed())
        .map(this::toDto)
        .toList();
  }

  public UserSubscriptionDto updateUserSubscriptionStatus(AuthenticatedUser user, String id, String status) {
    requireAdmin(user);
    String nextStatus = normalizeStatus(status);
    SubscriptionStorePayload store = loadStore();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UserSubscriptionRecord result = null;
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      if (item.id().equals(id)) {
        UserSubscriptionRecord rewritten = item.withStatus(nextStatus, now);
        subscriptions.add(rewritten);
        result = rewritten;
      } else {
        subscriptions.add(item);
      }
    }
    if (result == null) {
      throw new IllegalArgumentException("Kullanici aboneligi bulunamadi.");
    }
    saveStore(new SubscriptionStorePayload(store.plans(), subscriptions, store.billingEvents()));
    return toDto(result);
  }

  public SubscriptionPlanRecord requireActivePlan(String planId) {
    return loadStore().plans().stream()
        .filter(item -> item.id().equals(planId) && item.active())
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Aktif abonelik plani bulunamadi."));
  }

  public String requireStripePriceId(SubscriptionPlanRecord plan, String billingCycle) {
    String normalizedCycle = normalizeBillingCycle(billingCycle);
    String priceId = "yearly".equals(normalizedCycle) ? clean(plan.stripeYearlyPriceId()) : clean(plan.stripeMonthlyPriceId());
    if (!StringUtils.hasText(priceId)) {
      throw new IllegalArgumentException("Bu plan icin Stripe Price ID ayarlanmadi.");
    }
    return priceId;
  }

  public UserSubscriptionDto markStripeCheckoutPending(AuthenticatedUser user, SubscriptionPlanRecord plan, String billingCycle, String checkoutSessionId, String customerId, String priceId) {
    SubscriptionStorePayload store = loadStore();
    String normalizedCycle = normalizeBillingCycle(billingCycle);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    UserSubscriptionRecord result = null;
    boolean updated = false;
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      if (item.userId().equals(user.id()) && !"CANCELLED".equalsIgnoreCase(item.status())) {
        UserSubscriptionRecord rewritten = item
            .withUser(user.name(), user.email(), now)
            .withPlan(plan.id(), plan.name(), normalizedCycle, now, now, now)
            .withStatus("PENDING_PAYMENT", now)
            .withProvider("stripe", customerId, item.providerSubscriptionId(), checkoutSessionId, priceId, "pending", false, now);
        subscriptions.add(rewritten);
        result = rewritten;
        updated = true;
      } else {
        subscriptions.add(item);
      }
    }
    if (!updated) {
      result = new UserSubscriptionRecord(UUID.randomUUID().toString(), user.id(), user.name(), user.email(), plan.id(), plan.name(), normalizedCycle, "PENDING_PAYMENT", "stripe", customerId, "", checkoutSessionId, priceId, "pending", false, now, now, now, now);
      subscriptions.add(result);
    }
    saveStore(new SubscriptionStorePayload(store.plans(), subscriptions, store.billingEvents()));
    return toDto(result);
  }

  public boolean hasProcessedBillingEvent(String provider, String eventId) {
    return loadStore().billingEvents().stream()
        .anyMatch(item -> item.provider().equalsIgnoreCase(provider) && item.eventId().equals(eventId));
  }

  public void recordBillingEvent(String provider, String eventId, String eventType) {
    SubscriptionStorePayload store = loadStore();
    List<BillingEventRecord> events = new ArrayList<>(store.billingEvents());
    if (events.stream().noneMatch(item -> item.provider().equalsIgnoreCase(provider) && item.eventId().equals(eventId))) {
      events.add(new BillingEventRecord(provider, eventId, eventType, OffsetDateTime.now(ZoneOffset.UTC)));
    }
    saveStore(new SubscriptionStorePayload(store.plans(), store.userSubscriptions(), events));
  }

  public void activateStripeSubscription(String checkoutSessionId, String customerId, String subscriptionId, String paymentStatus) {
    updateStripeSubscription(checkoutSessionId, subscriptionId, "ACTIVE", customerId, subscriptionId, paymentStatus, false, null, null);
  }

  public void updateStripeSubscriptionByProviderId(String subscriptionId, String status, String customerId, String paymentStatus, boolean cancelAtPeriodEnd, Long currentPeriodStart, Long currentPeriodEnd) {
    updateStripeSubscription(null, subscriptionId, status, customerId, subscriptionId, paymentStatus, cancelAtPeriodEnd, currentPeriodStart, currentPeriodEnd);
  }

  public void updateStripeSubscriptionByMetadata(String userId, String planId, String subscriptionId, String status, String customerId, String paymentStatus, boolean cancelAtPeriodEnd, Long currentPeriodStart, Long currentPeriodEnd) {
    SubscriptionStorePayload store = loadStore();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    boolean updated = false;
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      if (item.userId().equals(userId) && item.planId().equals(planId) && ("PENDING_PAYMENT".equalsIgnoreCase(item.status()) || !StringUtils.hasText(item.providerSubscriptionId()))) {
        OffsetDateTime startsAt = currentPeriodStart == null ? item.startsAt() : fromUnix(currentPeriodStart);
        OffsetDateTime endsAt = currentPeriodEnd == null ? item.endsAt() : fromUnix(currentPeriodEnd);
        UserSubscriptionRecord rewritten = item
            .withStatus(status, now)
            .withProvider("stripe", cleanOrExisting(customerId, item.providerCustomerId()), cleanOrExisting(subscriptionId, item.providerSubscriptionId()), item.providerCheckoutSessionId(), item.providerPriceId(), cleanOrExisting(paymentStatus, item.lastPaymentStatus()), cancelAtPeriodEnd, now)
            .withPeriod(startsAt, endsAt, now);
        subscriptions.add(rewritten);
        updated = true;
      } else {
        subscriptions.add(item);
      }
    }
    if (updated) {
      saveStore(new SubscriptionStorePayload(store.plans(), subscriptions, store.billingEvents()));
    }
  }

  private void updateStripeSubscription(String checkoutSessionId, String lookupSubscriptionId, String status, String customerId, String subscriptionId, String paymentStatus, boolean cancelAtPeriodEnd, Long currentPeriodStart, Long currentPeriodEnd) {
    SubscriptionStorePayload store = loadStore();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    boolean updated = false;
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      boolean matchesSession = StringUtils.hasText(checkoutSessionId) && checkoutSessionId.equals(item.providerCheckoutSessionId());
      boolean matchesSubscription = StringUtils.hasText(lookupSubscriptionId) && lookupSubscriptionId.equals(item.providerSubscriptionId());
      if (matchesSession || matchesSubscription) {
        OffsetDateTime startsAt = currentPeriodStart == null ? item.startsAt() : fromUnix(currentPeriodStart);
        OffsetDateTime endsAt = currentPeriodEnd == null ? item.endsAt() : fromUnix(currentPeriodEnd);
        UserSubscriptionRecord rewritten = item
            .withStatus(status, now)
            .withProvider("stripe", cleanOrExisting(customerId, item.providerCustomerId()), cleanOrExisting(subscriptionId, item.providerSubscriptionId()), item.providerCheckoutSessionId(), item.providerPriceId(), cleanOrExisting(paymentStatus, item.lastPaymentStatus()), cancelAtPeriodEnd, now)
            .withPeriod(startsAt, endsAt, now);
        subscriptions.add(rewritten);
        updated = true;
      } else {
        subscriptions.add(item);
      }
    }
    if (updated) {
      saveStore(new SubscriptionStorePayload(store.plans(), subscriptions, store.billingEvents()));
    }
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
        clean(request.stripeProductId()),
        clean(request.stripeMonthlyPriceId()),
        clean(request.stripeYearlyPriceId()),
        createdAt,
        updatedAt
    );
  }

  private SubscriptionStorePayload loadStore() {
    if (!Files.exists(storagePath)) {
      List<SubscriptionPlanRecord> defaults = defaults();
      SubscriptionStorePayload store = new SubscriptionStorePayload(defaults, List.of(), List.of());
      saveStore(store);
      return store;
    }
    try {
      SubscriptionStorePayload payload = objectMapper.readValue(Files.readString(storagePath), SubscriptionStorePayload.class);
      return new SubscriptionStorePayload(
          payload.plans() == null ? new ArrayList<>() : new ArrayList<>(payload.plans()),
          payload.userSubscriptions() == null ? new ArrayList<>() : new ArrayList<>(payload.userSubscriptions()),
          payload.billingEvents() == null ? new ArrayList<>() : new ArrayList<>(payload.billingEvents())
      );
    } catch (IOException exception) {
      throw new IllegalStateException("Abonelik verisi yuklenemedi: " + exception.getMessage(), exception);
    }
  }

  private void saveStore(SubscriptionStorePayload store) {
    try {
      Files.createDirectories(storagePath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), store);
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
    return new SubscriptionPlanRecord(id, name, id, badge, description, monthlyPrice, yearlyPrice, "TRY", usageLimit, usagePeriod, highlighted, true, sortOrder, features, lockedFeatures, ctaLabel, "", "", "", now, now);
  }

  private List<SubscriptionPlanRecord> sorted(List<SubscriptionPlanRecord> plans) {
    return plans.stream().sorted(Comparator.comparingInt(SubscriptionPlanRecord::sortOrder).thenComparing(SubscriptionPlanRecord::name)).toList();
  }

  private SubscriptionPlanDto toDto(SubscriptionPlanRecord record) {
    return new SubscriptionPlanDto(record.id(), record.name(), record.slug(), record.badge(), record.description(), record.monthlyPrice(), record.yearlyPrice(), record.currency(), record.usageLimit(), record.usagePeriod(), record.highlighted(), record.active(), record.sortOrder(), record.features(), record.lockedFeatures(), record.ctaLabel(), record.stripeProductId(), record.stripeMonthlyPriceId(), record.stripeYearlyPriceId(), record.createdAt(), record.updatedAt());
  }

  private UserSubscriptionDto toDto(UserSubscriptionRecord record) {
    return new UserSubscriptionDto(record.id(), record.userId(), record.userName(), record.userEmail(), record.planId(), record.planName(), record.billingCycle(), record.status(), record.provider(), record.providerCustomerId(), record.providerSubscriptionId(), record.providerCheckoutSessionId(), record.providerPriceId(), record.lastPaymentStatus(), record.cancelAtPeriodEnd(), record.startsAt(), record.endsAt(), record.createdAt(), record.updatedAt());
  }

  private UserSubscriptionRecord activeSubscriptionFor(AuthenticatedUser user) {
    if (user == null) {
      return null;
    }
    return loadStore().userSubscriptions().stream()
        .filter(item -> item.userId().equals(user.id()))
        .filter(item -> "ACTIVE".equalsIgnoreCase(item.status()))
        .max(Comparator.comparing(UserSubscriptionRecord::updatedAt))
        .orElse(null);
  }

  private String normalizeBillingCycle(String value) {
    String cleaned = clean(value).toLowerCase(Locale.ROOT);
    if ("monthly".equals(cleaned) || "yearly".equals(cleaned)) {
      return cleaned;
    }
    throw new IllegalArgumentException("Odeme donemi monthly veya yearly olmali.");
  }

  private String normalizeStatus(String value) {
    String cleaned = clean(value).toUpperCase(Locale.ROOT);
    if ("PENDING_PAYMENT".equals(cleaned) || "ACTIVE".equals(cleaned) || "PAUSED".equals(cleaned) || "PAST_DUE".equals(cleaned) || "CANCELLED".equals(cleaned) || "EXPIRED".equals(cleaned)) {
      return cleaned;
    }
    throw new IllegalArgumentException("Abonelik durumu gecersiz.");
  }

  private OffsetDateTime fromUnix(Long value) {
    return OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(value), ZoneOffset.UTC);
  }

  private String cleanOrExisting(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
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

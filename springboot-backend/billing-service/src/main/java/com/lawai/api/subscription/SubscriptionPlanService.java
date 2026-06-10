package com.lawai.api.subscription;

import com.iyzipay.model.subscription.resource.CreatedSubscriptionData;
import com.lawai.api.subscription.dto.SubscriptionPlanDto;
import com.lawai.api.subscription.dto.SubscriptionPlanRequest;
import com.lawai.api.subscription.dto.UserSubscriptionDto;
import com.lawai.api.subscription.model.BillingEventRecord;
import com.lawai.api.subscription.model.SubscriptionPlanRecord;
import com.lawai.api.subscription.model.SubscriptionStorePayload;
import com.lawai.api.subscription.model.UserSubscriptionRecord;
import com.lawai.common.model.AuthenticatedUser;
import com.lawai.persistence.entity.BillingEventEntity;
import com.lawai.persistence.entity.SubscriptionPlanEntity;
import com.lawai.persistence.entity.UserSubscriptionEntity;
import com.lawai.persistence.repository.BillingEventRepository;
import com.lawai.persistence.repository.SubscriptionPlanRepository;
import com.lawai.persistence.repository.UserSubscriptionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

  private final SubscriptionPlanRepository subscriptionPlanRepository;
  private final UserSubscriptionRepository userSubscriptionRepository;
  private final BillingEventRepository billingEventRepository;

  public SubscriptionPlanService(
      SubscriptionPlanRepository subscriptionPlanRepository,
      UserSubscriptionRepository userSubscriptionRepository,
      BillingEventRepository billingEventRepository
  ) {
    this.subscriptionPlanRepository = subscriptionPlanRepository;
    this.userSubscriptionRepository = userSubscriptionRepository;
    this.billingEventRepository = billingEventRepository;
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
    if (!subscriptionPlanRepository.existsById(id)) {
      throw new IllegalArgumentException("Abonelik plani bulunamadi.");
    }
    subscriptionPlanRepository.deleteById(id);
  }

  public UserSubscriptionDto mySubscription(AuthenticatedUser user) {
    UserSubscriptionRecord record = currentSubscriptionFor(user);
    return record == null ? null : toDto(record);
  }

  public UserSubscriptionDto activateFreePlan(AuthenticatedUser user, SubscriptionPlanRecord plan, String billingCycle) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime endsAt = periodEnd(now, billingCycle);
    return toDto(upsertSubscription(user, plan, billingCycle, "ACTIVE", "free", "", "", "", "", false, now, endsAt, now, "success"));
  }

  public UserSubscriptionDto cancelAtPeriodEnd(AuthenticatedUser user) {
    SubscriptionStorePayload store = loadStore();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UserSubscriptionRecord result = null;
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      if (item.userId().equals(user.id()) && isAccessibleStatus(item.status())) {
        UserSubscriptionRecord rewritten = item
            .withProvider(item.provider(), item.providerCustomerId(), item.providerSubscriptionId(), item.providerCheckoutSessionId(), item.providerPriceId(), item.lastPaymentStatus(), true, now);
        subscriptions.add(rewritten);
        result = rewritten;
      } else {
        subscriptions.add(item);
      }
    }
    if (result == null) {
      throw new IllegalArgumentException("Iptal edilebilir abonelik bulunamadi.");
    }
    saveStore(new SubscriptionStorePayload(store.plans(), subscriptions, store.billingEvents()));
    return toDto(result);
  }

  public UserSubscriptionRecord requireCancellableSubscription(AuthenticatedUser user) {
    UserSubscriptionRecord record = currentSubscriptionFor(user);
    if (record == null || !isAccessibleStatus(record.status())) {
      throw new IllegalArgumentException("Iptal edilebilir abonelik bulunamadi.");
    }
    return record;
  }

  public void expireEndedSubscriptions() {
    SubscriptionStorePayload store = loadStore();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    boolean updated = false;
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      if (shouldExpire(item, now)) {
        subscriptions.add(item.withStatus("EXPIRED", now));
        updated = true;
      } else {
        subscriptions.add(item);
      }
    }
    if (updated) {
      saveStore(new SubscriptionStorePayload(store.plans(), subscriptions, store.billingEvents()));
    }
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

  public String normalizeBillingCyclePublic(String billingCycle) {
    return normalizeBillingCycle(billingCycle);
  }

  public String requireIyzicoPricingPlanRef(SubscriptionPlanRecord plan, String billingCycle) {
    String normalizedCycle = normalizeBillingCycle(billingCycle);
    String planRef = "yearly".equals(normalizedCycle) ? clean(plan.iyzicoYearlyPlanRef()) : clean(plan.iyzicoMonthlyPlanRef());
    if (!StringUtils.hasText(planRef)) {
      throw new IllegalArgumentException("Bu plan icin iyzico odeme plani referansi ayarlanmadi.");
    }
    return planRef;
  }

  public UserSubscriptionDto markIyzicoCheckoutPending(
      AuthenticatedUser user,
      SubscriptionPlanRecord plan,
      String billingCycle,
      String checkoutToken,
      String pricingPlanRef,
      String conversationId
  ) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UserSubscriptionRecord result = upsertSubscription(
        user,
        plan,
        billingCycle,
        "PENDING_PAYMENT",
        "iyzico",
        conversationId,
        "",
        checkoutToken,
        pricingPlanRef,
        false,
        now,
        now,
        now,
        "pending"
    );
    return toDto(result);
  }

  @Transactional(readOnly = true)
  public boolean hasProcessedBillingEvent(String provider, String eventId) {
    return billingEventRepository.existsByProviderAndEventId(provider, eventId);
  }

  @Transactional
  public void recordBillingEvent(String provider, String eventId, String eventType) {
    if (!billingEventRepository.existsByProviderAndEventId(provider, eventId)) {
      billingEventRepository.save(new BillingEventEntity(provider, eventId, eventType, OffsetDateTime.now(ZoneOffset.UTC)));
    }
  }

  public UserSubscriptionDto activateIyzicoSubscription(String checkoutToken, CreatedSubscriptionData data) {
    String mappedStatus = mapIyzicoSubscriptionStatus(data.getSubscriptionStatus());
    OffsetDateTime startsAt = fromIyzicoDate(data.getStartDate());
    OffsetDateTime endsAt = fromIyzicoDate(data.getEndDate());
    if (endsAt == null) {
      endsAt = startsAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : periodEnd(startsAt, findBillingCycleByToken(checkoutToken));
    }
    if (startsAt == null) {
      startsAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
    SubscriptionStorePayload store = loadStore();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    UserSubscriptionRecord result = null;
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      if (checkoutToken.equals(item.providerCheckoutSessionId())) {
        UserSubscriptionRecord rewritten = item
            .withStatus(mappedStatus, now)
            .withProvider(
                "iyzico",
                cleanOrExisting(data.getCustomerReferenceCode(), item.providerCustomerId()),
                cleanOrExisting(data.getReferenceCode(), item.providerSubscriptionId()),
                item.providerCheckoutSessionId(),
                cleanOrExisting(data.getPricingPlanReferenceCode(), item.providerPriceId()),
                cleanOrExisting(data.getSubscriptionStatus(), item.lastPaymentStatus()),
                false,
                now
            )
            .withPeriod(startsAt, endsAt, now);
        subscriptions.add(rewritten);
        result = rewritten;
      } else {
        subscriptions.add(item);
      }
    }
    if (result == null) {
      throw new IllegalArgumentException("Bekleyen abonelik kaydi bulunamadi.");
    }
    saveStore(new SubscriptionStorePayload(store.plans(), subscriptions, store.billingEvents()));
    return toDto(result);
  }

  public void renewIyzicoSubscription(String subscriptionReferenceCode, String customerReferenceCode, String paymentStatus) {
    updateIyzicoSubscription(subscriptionReferenceCode, "ACTIVE", customerReferenceCode, paymentStatus, true);
  }

  public void updateIyzicoSubscriptionStatus(String subscriptionReferenceCode, String status, String customerReferenceCode, String paymentStatus) {
    updateIyzicoSubscription(subscriptionReferenceCode, status, customerReferenceCode, paymentStatus, false);
  }

  private void updateIyzicoSubscription(String subscriptionReferenceCode, String status, String customerReferenceCode, String paymentStatus, boolean extendPeriod) {
    if (!StringUtils.hasText(subscriptionReferenceCode)) {
      return;
    }
    SubscriptionStorePayload store = loadStore();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    boolean updated = false;
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      if (subscriptionReferenceCode.equals(item.providerSubscriptionId())) {
        OffsetDateTime startsAt = item.startsAt();
        OffsetDateTime endsAt = item.endsAt();
        if (extendPeriod && "ACTIVE".equalsIgnoreCase(status)) {
          OffsetDateTime base = endsAt != null && endsAt.isAfter(now) ? endsAt : now;
          endsAt = periodEnd(base, item.billingCycle());
        }
        UserSubscriptionRecord rewritten = item
            .withStatus(status, now)
            .withProvider(
                item.provider(),
                cleanOrExisting(customerReferenceCode, item.providerCustomerId()),
                item.providerSubscriptionId(),
                item.providerCheckoutSessionId(),
                item.providerPriceId(),
                cleanOrExisting(paymentStatus, item.lastPaymentStatus()),
                item.cancelAtPeriodEnd(),
                now
            )
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
        clean(request.iyzicoProductRef()),
        clean(request.iyzicoMonthlyPlanRef()),
        clean(request.iyzicoYearlyPlanRef()),
        createdAt,
        updatedAt
    );
  }

  private SubscriptionStorePayload loadStore() {
    List<SubscriptionPlanRecord> plans = subscriptionPlanRepository.findAll().stream()
        .map(SubscriptionPlanEntity::toRecord)
        .toList();
    if (plans.isEmpty()) {
      plans = defaults();
      subscriptionPlanRepository.saveAll(plans.stream().map(SubscriptionPlanEntity::fromRecord).toList());
    }
    List<UserSubscriptionRecord> subscriptions = userSubscriptionRepository.findAll().stream()
        .map(UserSubscriptionEntity::toRecord)
        .toList();
    List<BillingEventRecord> events = billingEventRepository.findAll().stream()
        .map(BillingEventEntity::toRecord)
        .toList();
    return new SubscriptionStorePayload(new ArrayList<>(plans), new ArrayList<>(subscriptions), new ArrayList<>(events));
  }

  private void saveStore(SubscriptionStorePayload store) {
    subscriptionPlanRepository.saveAll(store.plans().stream().map(SubscriptionPlanEntity::fromRecord).toList());
    userSubscriptionRepository.saveAll(store.userSubscriptions().stream().map(UserSubscriptionEntity::fromRecord).toList());
    billingEventRepository.saveAll(store.billingEvents().stream().map(BillingEventEntity::fromRecord).toList());
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
    return new SubscriptionPlanDto(record.id(), record.name(), record.slug(), record.badge(), record.description(), record.monthlyPrice(), record.yearlyPrice(), record.currency(), record.usageLimit(), record.usagePeriod(), record.highlighted(), record.active(), record.sortOrder(), record.features(), record.lockedFeatures(), record.ctaLabel(), record.iyzicoProductRef(), record.iyzicoMonthlyPlanRef(), record.iyzicoYearlyPlanRef(), record.createdAt(), record.updatedAt());
  }

  private UserSubscriptionDto toDto(UserSubscriptionRecord record) {
    return new UserSubscriptionDto(record.id(), record.userId(), record.userName(), record.userEmail(), record.planId(), record.planName(), record.billingCycle(), record.status(), record.provider(), record.providerCustomerId(), record.providerSubscriptionId(), record.providerCheckoutSessionId(), record.providerPriceId(), record.lastPaymentStatus(), record.cancelAtPeriodEnd(), record.startsAt(), record.endsAt(), record.createdAt(), record.updatedAt());
  }

  private UserSubscriptionRecord currentSubscriptionFor(AuthenticatedUser user) {
    if (user == null) {
      return null;
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return loadStore().userSubscriptions().stream()
        .filter(item -> item.userId().equals(user.id()))
        .filter(item -> isAccessibleStatus(item.status()))
        .filter(item -> item.endsAt() == null || !item.endsAt().isBefore(now))
        .max(Comparator.comparing(UserSubscriptionRecord::updatedAt))
        .orElse(null);
  }

  private UserSubscriptionRecord upsertSubscription(
      AuthenticatedUser user,
      SubscriptionPlanRecord plan,
      String billingCycle,
      String status,
      String provider,
      String customerId,
      String subscriptionId,
      String checkoutToken,
      String pricingPlanRef,
      boolean cancelAtPeriodEnd,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      OffsetDateTime now,
      String lastPaymentStatus
  ) {
    SubscriptionStorePayload store = loadStore();
    List<UserSubscriptionRecord> subscriptions = new ArrayList<>();
    UserSubscriptionRecord result = null;
    boolean updated = false;
    for (UserSubscriptionRecord item : store.userSubscriptions()) {
      if (item.userId().equals(user.id()) && !isTerminalStatus(item.status())) {
        UserSubscriptionRecord rewritten = item
            .withUser(user.name(), user.email(), now)
            .withPlan(plan.id(), plan.name(), billingCycle, startsAt, endsAt, now)
            .withStatus(status, now)
            .withProvider(provider, customerId, subscriptionId, checkoutToken, pricingPlanRef, lastPaymentStatus, cancelAtPeriodEnd, now);
        subscriptions.add(rewritten);
        result = rewritten;
        updated = true;
      } else {
        subscriptions.add(item);
      }
    }
    if (!updated) {
      result = new UserSubscriptionRecord(
          UUID.randomUUID().toString(),
          user.id(),
          user.name(),
          user.email(),
          plan.id(),
          plan.name(),
          billingCycle,
          status,
          provider,
          customerId,
          subscriptionId,
          checkoutToken,
          pricingPlanRef,
          lastPaymentStatus,
          cancelAtPeriodEnd,
          startsAt,
          endsAt,
          now,
          now
      );
      subscriptions.add(result);
    }
    saveStore(new SubscriptionStorePayload(store.plans(), subscriptions, store.billingEvents()));
    return result;
  }

  private boolean isAccessibleStatus(String status) {
    return "ACTIVE".equalsIgnoreCase(status)
        || "PAST_DUE".equalsIgnoreCase(status)
        || "PENDING_PAYMENT".equalsIgnoreCase(status);
  }

  private boolean isTerminalStatus(String status) {
    return "CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status);
  }

  private boolean shouldExpire(UserSubscriptionRecord item, OffsetDateTime now) {
    if (item.endsAt() == null || item.endsAt().isAfter(now)) {
      return false;
    }
    return isAccessibleStatus(item.status()) || "CANCELLED".equalsIgnoreCase(item.status());
  }

  private OffsetDateTime periodEnd(OffsetDateTime startsAt, String billingCycle) {
    return "yearly".equalsIgnoreCase(billingCycle) ? startsAt.plusYears(1) : startsAt.plusMonths(1);
  }

  private String findBillingCycleByToken(String checkoutToken) {
    return loadStore().userSubscriptions().stream()
        .filter(item -> checkoutToken.equals(item.providerCheckoutSessionId()))
        .map(UserSubscriptionRecord::billingCycle)
        .findFirst()
        .orElse("monthly");
  }

  private String mapIyzicoSubscriptionStatus(String status) {
    return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
      case "ACTIVE" -> "ACTIVE";
      case "PENDING" -> "PENDING_PAYMENT";
      case "UNPAID" -> "PAST_DUE";
      case "CANCELED", "CANCELLED" -> "CANCELLED";
      case "EXPIRED" -> "EXPIRED";
      default -> "PENDING_PAYMENT";
    };
  }

  private OffsetDateTime fromIyzicoDate(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      long epoch = Long.parseLong(value.trim());
      if (value.trim().length() <= 10) {
        epoch *= 1000L;
      }
      return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
    } catch (NumberFormatException exception) {
      return null;
    }
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

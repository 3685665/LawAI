package com.lawai.api.subscription;

import com.iyzipay.IyzipayResource;
import com.iyzipay.Options;
import com.iyzipay.PagingRequest;
import com.iyzipay.model.Currency;
import com.iyzipay.model.Locale;
import com.iyzipay.model.Status;
import com.iyzipay.model.subscription.SubscriptionPricingPlan;
import com.iyzipay.model.subscription.SubscriptionProduct;
import com.iyzipay.model.subscription.SubscriptionProductList;
import com.iyzipay.model.subscription.enumtype.SubscriptionPaymentInterval;
import com.iyzipay.model.subscription.enumtype.SubscriptionPaymentType;
import com.iyzipay.request.subscription.CreateSubscriptionPricingPlanRequest;
import com.iyzipay.request.subscription.CreateSubscriptionProductRequest;
import com.lawai.api.subscription.model.SubscriptionPlanRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class IyzicoCatalogService {

  private static final Logger log = LoggerFactory.getLogger(IyzicoCatalogService.class);
  private static final String SUBSCRIPTION_DISABLED_ERROR_CODE = "100001";
  private static final Pattern IYZICO_REF_PATTERN = Pattern.compile(
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
  );

  private final Options options;
  private final boolean autoSyncEnabled;

  public IyzicoCatalogService(
      @Value("${app.billing.iyzico.auto-sync:false}") boolean autoSyncEnabled,
      @Value("${app.billing.iyzico.api-key:}") String apiKey,
      @Value("${app.billing.iyzico.secret-key:}") String secretKey,
      @Value("${app.billing.iyzico.base-url:https://sandbox-api.iyzipay.com}") String baseUrl
  ) {
    this.autoSyncEnabled = autoSyncEnabled;
    this.options = new Options();
    this.options.setApiKey(apiKey == null ? "" : apiKey.trim());
    this.options.setSecretKey(secretKey == null ? "" : secretKey.trim());
    this.options.setBaseUrl(baseUrl);
  }

  public boolean isAutoSyncEnabled() {
    return autoSyncEnabled;
  }

  public boolean isConfigured() {
    return StringUtils.hasText(options.getApiKey()) && StringUtils.hasText(options.getSecretKey());
  }

  public boolean isValidReference(String reference) {
    return StringUtils.hasText(reference) && IYZICO_REF_PATTERN.matcher(reference.trim()).matches();
  }

  public boolean isSubscriptionApiAvailable() {
    if (!isConfigured()) {
      return false;
    }
    try {
      PagingRequest request = new PagingRequest();
      request.setPage(1);
      request.setCount(1);
      SubscriptionProductList response = SubscriptionProductList.retrieve(request, options);
      if (Status.SUCCESS.getValue().equals(response.getStatus())) {
        return true;
      }
      log.warn(
          "iyzico abonelik API kullanilamiyor errorCode={} error={}",
          response.getErrorCode(),
          response.getErrorMessage()
      );
      return false;
    } catch (Exception exception) {
      log.warn("iyzico abonelik API kontrolu basarisiz: {}", exception.getMessage());
      return false;
    }
  }

  public String formatFailure(String action, IyzipayResource response) {
    String errorCode = response == null ? "" : clean(response.getErrorCode());
    String errorMessage = response == null ? "" : safeError(response.getErrorMessage());
    if (SUBSCRIPTION_DISABLED_ERROR_CODE.equals(errorCode)) {
      return action + ": iyzico abonelik modulu bu API anahtarinda aktif degil. "
          + "Iyzico panelinden urun ve odeme plani olusturup referans kodlarini admin abonelik sayfasina girin "
          + "veya destek@iyzico.com uzerinden merchant hesabinizda abonelik servisini acin.";
    }
    return action + ": " + errorMessage + (StringUtils.hasText(errorCode) ? " (" + errorCode + ")" : "");
  }

  public SubscriptionPlanRecord syncPlan(SubscriptionPlanRecord plan) {
    if (!autoSyncEnabled) {
      return plan;
    }
    if (!isConfigured()) {
      return plan;
    }
    if (plan.monthlyPrice() <= 0 && plan.yearlyPrice() <= 0) {
      return plan;
    }
    if (!isSubscriptionApiAvailable()) {
      throw new IllegalStateException(
          "iyzico abonelik API'si kullanilamiyor. "
              + "Merchant hesabinizda abonelik modulu acik olmayabilir; destek@iyzico.com ile iletisime gecin "
              + "veya IYZICO_AUTO_SYNC=false birakip referans kodlarini admin panelinden manuel girin."
      );
    }

    String productRef = resolveProductRef(plan);
    String monthlyRef = plan.monthlyPrice() > 0
        ? resolvePricingPlanRef(plan, productRef, plan.iyzicoMonthlyPlanRef(), plan.monthlyPrice(), SubscriptionPaymentInterval.MONTHLY, "aylik")
        : plan.iyzicoMonthlyPlanRef();
    String yearlyRef = plan.yearlyPrice() > 0
        ? resolvePricingPlanRef(plan, productRef, plan.iyzicoYearlyPlanRef(), plan.yearlyPrice(), SubscriptionPaymentInterval.YEARLY, "yillik")
        : plan.iyzicoYearlyPlanRef();

    return new SubscriptionPlanRecord(
        plan.id(),
        plan.name(),
        plan.slug(),
        plan.badge(),
        plan.description(),
        plan.monthlyPrice(),
        plan.yearlyPrice(),
        plan.currency(),
        plan.usageLimit(),
        plan.usagePeriod(),
        plan.highlighted(),
        plan.active(),
        plan.sortOrder(),
        plan.features(),
        plan.lockedFeatures(),
        plan.ctaLabel(),
        productRef,
        monthlyRef == null ? "" : monthlyRef,
        yearlyRef == null ? "" : yearlyRef,
        plan.createdAt(),
        plan.updatedAt()
    );
  }

  private String resolveProductRef(SubscriptionPlanRecord plan) {
    if (isValidReference(plan.iyzicoProductRef()) && productExists(plan.iyzicoProductRef())) {
      return plan.iyzicoProductRef();
    }
    CreateSubscriptionProductRequest request = new CreateSubscriptionProductRequest();
    request.setName(plan.name());
    request.setDescription(StringUtils.hasText(plan.description()) ? plan.description() : plan.name() + " abonelik plani");
    request.setLocale(Locale.TR.name());
    request.setConversationId(conversationId("product-" + plan.id()));
    SubscriptionProduct response = SubscriptionProduct.create(request, options);
    if (!Status.SUCCESS.getValue().equals(response.getStatus())) {
      log.warn(
          "iyzico urun olusturulamadi planId={} errorCode={} error={}",
          plan.id(),
          response.getErrorCode(),
          response.getErrorMessage()
      );
      throw new IllegalStateException(formatFailure("iyzico urun olusturulamadi", response));
    }
    String reference = response.getSubscriptionProductData().getReferenceCode();
    log.info("iyzico urun olusturuldu plan={} ref={}", plan.id(), reference);
    return reference;
  }

  private String resolvePricingPlanRef(
      SubscriptionPlanRecord plan,
      String productRef,
      String existingRef,
      int price,
      SubscriptionPaymentInterval interval,
      String suffix
  ) {
    if (isValidReference(existingRef) && pricingPlanExists(existingRef)) {
      return existingRef;
    }
    CreateSubscriptionPricingPlanRequest request = new CreateSubscriptionPricingPlanRequest();
    request.setPlanPaymentType(SubscriptionPaymentType.RECURRING.name());
    request.setName(plan.slug() + "-" + suffix);
    request.setPrice(toIyzicoPrice(price));
    request.setCurrencyCode(resolveCurrency(plan.currency()));
    request.setPaymentInterval(interval.name());
    request.setPaymentIntervalCount(1);
    request.setTrialPeriodDays(0);
    request.setLocale(Locale.TR.name());
    request.setConversationId(conversationId("pricing-" + plan.id() + "-" + suffix));
    SubscriptionPricingPlan response = SubscriptionPricingPlan.create(productRef, request, options);
    if (!Status.SUCCESS.getValue().equals(response.getStatus())) {
      log.warn(
          "iyzico odeme plani olusturulamadi planId={} interval={} errorCode={} error={}",
          plan.id(),
          interval,
          response.getErrorCode(),
          response.getErrorMessage()
      );
      throw new IllegalStateException(formatFailure("iyzico odeme plani olusturulamadi", response));
    }
    String reference = response.getSubscriptionPricingPlanData().getReferenceCode();
    log.info("iyzico odeme plani olusturuldu plan={} interval={} ref={}", plan.id(), interval, reference);
    return reference;
  }

  private boolean productExists(String reference) {
    try {
      SubscriptionProduct response = SubscriptionProduct.retrieve(reference, options);
      return Status.SUCCESS.getValue().equals(response.getStatus());
    } catch (Exception exception) {
      return false;
    }
  }

  private boolean pricingPlanExists(String reference) {
    try {
      SubscriptionPricingPlan response = SubscriptionPricingPlan.retrieve(reference, options);
      return Status.SUCCESS.getValue().equals(response.getStatus());
    } catch (Exception exception) {
      return false;
    }
  }

  private BigDecimal toIyzicoPrice(int price) {
    return BigDecimal.valueOf(price).setScale(2, RoundingMode.UNNECESSARY);
  }

  private String resolveCurrency(String currency) {
    return StringUtils.hasText(currency) ? currency.trim().toUpperCase() : Currency.TRY.name();
  }

  private String conversationId(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private String safeError(String value) {
    return StringUtils.hasText(value) ? value : "bilinmeyen hata";
  }
}

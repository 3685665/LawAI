package com.lawai.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iyzipay.Options;
import com.iyzipay.model.Locale;
import com.iyzipay.model.Status;
import com.iyzipay.model.SubscriptionAddress;
import com.iyzipay.model.subscription.SubscriptionCheckoutForm;
import com.iyzipay.model.subscription.SubscriptionCheckoutFormInitialize;
import com.iyzipay.model.subscription.SubscriptionOperation;
import com.iyzipay.model.subscription.enumtype.SubscriptionInitialStatus;
import com.iyzipay.model.subscription.resource.CreatedSubscriptionData;
import com.iyzipay.model.subscription.resource.SubscriptionCustomer;
import com.iyzipay.request.subscription.InitializeSubscriptionCheckoutFormRequest;
import com.lawai.api.subscription.dto.BillingCheckoutResponse;
import com.lawai.api.subscription.dto.UserSubscriptionDto;
import com.lawai.api.subscription.model.SubscriptionPlanRecord;
import com.lawai.api.subscription.model.UserSubscriptionRecord;
import com.lawai.common.model.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class IyzicoBillingService {

  private static final Logger log = LoggerFactory.getLogger(IyzicoBillingService.class);

  private final ObjectMapper objectMapper;
  private final SubscriptionPlanService subscriptionPlanService;
  private final IyzicoCatalogService iyzicoCatalogService;
  private final Options options;
  private final String merchantId;
  private final String callbackUrl;
  private final String checkoutPageUrl;
  private final String successUrl;
  private final String cancelUrl;
  private final String defaultGsmNumber;
  private final String defaultIdentityNumber;
  private final String defaultAddress;
  private final String defaultCity;
  private final String defaultCountry;
  private final String defaultZipCode;

  public IyzicoBillingService(
      ObjectMapper objectMapper,
      SubscriptionPlanService subscriptionPlanService,
      IyzicoCatalogService iyzicoCatalogService,
      @Value("${app.billing.iyzico.api-key:}") String apiKey,
      @Value("${app.billing.iyzico.secret-key:}") String secretKey,
      @Value("${app.billing.iyzico.base-url:https://sandbox-api.iyzipay.com}") String baseUrl,
      @Value("${app.billing.iyzico.merchant-id:}") String merchantId,
      @Value("${app.billing.iyzico.callback-url:http://localhost:8080/api/billing/iyzico/callback}") String callbackUrl,
      @Value("${app.billing.checkout-page-url:http://localhost:3000/subscriptions/checkout}") String checkoutPageUrl,
      @Value("${app.billing.success-url:http://localhost:3000/subscriptions?checkout=success}") String successUrl,
      @Value("${app.billing.cancel-url:http://localhost:3000/subscriptions?checkout=cancel}") String cancelUrl,
      @Value("${app.billing.iyzico.default-gsm:+905555555555}") String defaultGsmNumber,
      @Value("${app.billing.iyzico.default-identity-number:11111111111}") String defaultIdentityNumber,
      @Value("${app.billing.iyzico.default-address:Istanbul}") String defaultAddress,
      @Value("${app.billing.iyzico.default-city:Istanbul}") String defaultCity,
      @Value("${app.billing.iyzico.default-country:Turkey}") String defaultCountry,
      @Value("${app.billing.iyzico.default-zip-code:34000}") String defaultZipCode
  ) {
    this.objectMapper = objectMapper;
    this.subscriptionPlanService = subscriptionPlanService;
    this.iyzicoCatalogService = iyzicoCatalogService;
    this.merchantId = merchantId == null ? "" : merchantId.trim();
    this.callbackUrl = callbackUrl;
    this.checkoutPageUrl = checkoutPageUrl;
    this.successUrl = successUrl;
    this.cancelUrl = cancelUrl;
    this.defaultGsmNumber = defaultGsmNumber;
    this.defaultIdentityNumber = defaultIdentityNumber;
    this.defaultAddress = defaultAddress;
    this.defaultCity = defaultCity;
    this.defaultCountry = defaultCountry;
    this.defaultZipCode = defaultZipCode;
    this.options = new Options();
    this.options.setApiKey(apiKey == null ? "" : apiKey.trim());
    this.options.setSecretKey(secretKey == null ? "" : secretKey.trim());
    this.options.setBaseUrl(baseUrl);
  }

  public BillingCheckoutResponse createCheckout(AuthenticatedUser user, String planId, String billingCycle) {
    SubscriptionPlanRecord plan = subscriptionPlanService.requireActivePlan(planId);
    String normalizedCycle = subscriptionPlanService.normalizeBillingCyclePublic(billingCycle);
    int price = "yearly".equals(normalizedCycle) ? plan.yearlyPrice() : plan.monthlyPrice();
    if (price <= 0) {
      UserSubscriptionDto subscription = subscriptionPlanService.activateFreePlan(user, plan, normalizedCycle);
      return new BillingCheckoutResponse(successUrl, "free-" + subscription.id(), subscription, "");
    }
    requireIyzicoConfigured();
    plan = subscriptionPlanService.ensureIyzicoSynced(plan);
    String pricingPlanRef = subscriptionPlanService.requireIyzicoPricingPlanRef(plan, normalizedCycle);
    String conversationId = UUID.randomUUID().toString();
    InitializeSubscriptionCheckoutFormRequest request = new InitializeSubscriptionCheckoutFormRequest();
    request.setCustomer(buildCustomer(user));
    request.setCallbackUrl(callbackUrl);
    request.setPricingPlanReferenceCode(pricingPlanRef);
    request.setSubscriptionInitialStatus(SubscriptionInitialStatus.ACTIVE.name());
    request.setConversationId(conversationId);
    request.setLocale(Locale.TR.name());
    SubscriptionCheckoutFormInitialize response = SubscriptionCheckoutFormInitialize.create(request, options);
    if (!Status.SUCCESS.getValue().equals(response.getStatus())) {
      log.warn(
          "iyzico checkout basarisiz planId={} cycle={} pricingPlanRef={} errorCode={} error={}",
          plan.id(),
          normalizedCycle,
          pricingPlanRef,
          response.getErrorCode(),
          response.getErrorMessage()
      );
      throw new IllegalStateException(iyzicoCatalogService.formatFailure("iyzico odeme formu olusturulamadi", response));
    }
    UserSubscriptionDto subscription = subscriptionPlanService.markIyzicoCheckoutPending(
        user,
        plan,
        normalizedCycle,
        response.getToken(),
        pricingPlanRef,
        conversationId
    );
    String checkoutUrl = checkoutPageUrl + (checkoutPageUrl.contains("?") ? "&" : "?") + "token=" + response.getToken();
    return new BillingCheckoutResponse(checkoutUrl, response.getToken(), subscription, response.getCheckoutFormContent());
  }

  public UserSubscriptionDto completeCallback(String token) {
    requireIyzicoConfigured();
    if (!StringUtils.hasText(token)) {
      throw new IllegalArgumentException("iyzico odeme tokeni eksik.");
    }
    SubscriptionCheckoutForm response = SubscriptionCheckoutForm.retrieve(token, options);
    if (!Status.SUCCESS.getValue().equals(response.getStatus())) {
      throw new IllegalStateException("iyzico odeme sonucu alinamadi: " + safeError(response.getErrorMessage()));
    }
    CreatedSubscriptionData data = response.getCreatedSubscriptionData();
    if (data == null) {
      throw new IllegalStateException("iyzico abonelik verisi bulunamadi.");
    }
    return subscriptionPlanService.activateIyzicoSubscription(token, data);
  }

  public void handleWebhook(String payload, String signatureHeader) {
    requireIyzicoConfigured();
    if (!StringUtils.hasText(payload)) {
      throw new IllegalArgumentException("iyzico webhook govdesi bos.");
    }
    try {
      JsonNode body = objectMapper.readTree(payload);
      String eventType = text(body, "iyziEventType");
      String eventId = text(body, "iyziReferenceCode");
      if (!StringUtils.hasText(eventId)) {
        eventId = text(body, "orderReferenceCode");
      }
      if (subscriptionPlanService.hasProcessedBillingEvent("iyzico", eventId)) {
        return;
      }
      if (StringUtils.hasText(signatureHeader) && StringUtils.hasText(merchantId)) {
        String expected = buildWebhookSignature(
            eventType,
            text(body, "subscriptionReferenceCode"),
            text(body, "orderReferenceCode"),
            text(body, "customerReferenceCode")
        );
        if (!expected.equalsIgnoreCase(signatureHeader.trim())) {
          throw new IllegalArgumentException("iyzico webhook imzasi gecersiz.");
        }
      }
      switch (eventType) {
        case "subscription.order.success" -> subscriptionPlanService.renewIyzicoSubscription(
            text(body, "subscriptionReferenceCode"),
            text(body, "customerReferenceCode"),
            "success"
        );
        case "subscription.order.failure" -> subscriptionPlanService.updateIyzicoSubscriptionStatus(
            text(body, "subscriptionReferenceCode"),
            "PAST_DUE",
            text(body, "customerReferenceCode"),
            "failure"
        );
        default -> {
          // Event is verified but not relevant for subscription provisioning.
        }
      }
      subscriptionPlanService.recordBillingEvent("iyzico", eventId, eventType);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("iyzico webhook islenemedi: " + exception.getMessage(), exception);
    }
  }

  public UserSubscriptionDto cancelSubscription(AuthenticatedUser user) {
    UserSubscriptionRecord current = subscriptionPlanService.requireCancellableSubscription(user);
    if ("iyzico".equalsIgnoreCase(current.provider()) && StringUtils.hasText(current.providerSubscriptionId())) {
      requireIyzicoConfigured();
      SubscriptionOperation response = SubscriptionOperation.cancel(current.providerSubscriptionId(), options);
      if (!Status.SUCCESS.getValue().equals(response.getStatus())) {
        throw new IllegalStateException("iyzico abonelik iptali basarisiz: " + safeError(response.getErrorMessage()));
      }
    }
    return subscriptionPlanService.cancelAtPeriodEnd(user);
  }

  public String successRedirectUrl() {
    return successUrl;
  }

  public String cancelRedirectUrl() {
    return cancelUrl;
  }

  private SubscriptionCustomer buildCustomer(AuthenticatedUser user) {
    NameParts nameParts = splitName(user.name());
    SubscriptionAddress address = new SubscriptionAddress();
    address.setContactName(user.name());
    address.setCity(defaultCity);
    address.setCountry(defaultCountry);
    address.setAddress(defaultAddress);
    address.setZipCode(defaultZipCode);
    SubscriptionCustomer customer = new SubscriptionCustomer();
    customer.setName(nameParts.firstName());
    customer.setSurname(nameParts.lastName());
    customer.setEmail(user.email());
    customer.setGsmNumber(defaultGsmNumber);
    customer.setIdentityNumber(defaultIdentityNumber);
    customer.setBillingAddress(address);
    customer.setShippingAddress(address);
    return customer;
  }

  private String buildWebhookSignature(String eventType, String subscriptionReferenceCode, String orderReferenceCode, String customerReferenceCode) {
    String payload = merchantId + eventType + subscriptionReferenceCode + orderReferenceCode + customerReferenceCode;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(options.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("iyzico webhook imzasi hesaplanamadi.", exception);
    }
  }

  private void requireIyzicoConfigured() {
    if (!StringUtils.hasText(options.getApiKey()) || !StringUtils.hasText(options.getSecretKey())) {
      throw new IllegalStateException("iyzico API anahtarlari ayarlanmadi.");
    }
  }

  private String text(JsonNode node, String fieldName) {
    JsonNode value = node == null ? null : node.get(fieldName);
    return value == null || value.isNull() ? "" : value.asText("");
  }

  private String safeError(String value) {
    return StringUtils.hasText(value) ? value : "bilinmeyen hata";
  }

  private NameParts splitName(String fullName) {
    String cleaned = StringUtils.hasText(fullName) ? fullName.trim() : "LawAI Kullanici";
    int spaceIndex = cleaned.indexOf(' ');
    if (spaceIndex < 0) {
      return new NameParts(cleaned, "Kullanici");
    }
    return new NameParts(cleaned.substring(0, spaceIndex), cleaned.substring(spaceIndex + 1).trim());
  }

  private record NameParts(String firstName, String lastName) {
  }
}

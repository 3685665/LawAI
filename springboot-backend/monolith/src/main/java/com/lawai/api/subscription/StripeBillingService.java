package com.lawai.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.subscription.dto.BillingCheckoutResponse;
import com.lawai.api.subscription.dto.UserSubscriptionDto;
import com.lawai.api.subscription.model.SubscriptionPlanRecord;
import com.lawai.auth.model.AuthenticatedUser;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StripeBillingService {

  private final ObjectMapper objectMapper;
  private final SubscriptionPlanService subscriptionPlanService;
  private final String secretKey;
  private final String webhookSecret;
  private final String successUrl;
  private final String cancelUrl;

  public StripeBillingService(
      ObjectMapper objectMapper,
      SubscriptionPlanService subscriptionPlanService,
      @Value("${app.billing.stripe.secret-key:}") String secretKey,
      @Value("${app.billing.stripe.webhook-secret:}") String webhookSecret,
      @Value("${app.billing.success-url:http://localhost:3000/subscriptions?checkout=success}") String successUrl,
      @Value("${app.billing.cancel-url:http://localhost:3000/subscriptions?checkout=cancel}") String cancelUrl
  ) {
    this.objectMapper = objectMapper;
    this.subscriptionPlanService = subscriptionPlanService;
    this.secretKey = secretKey == null ? "" : secretKey.trim();
    this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    this.successUrl = successUrl;
    this.cancelUrl = cancelUrl;
  }

  public BillingCheckoutResponse createCheckoutSession(AuthenticatedUser user, String planId, String billingCycle) {
    requireStripeConfigured();
    SubscriptionPlanRecord plan = subscriptionPlanService.requireActivePlan(planId);
    String priceId = subscriptionPlanService.requireStripePriceId(plan, billingCycle);
    try {
      Stripe.apiKey = secretKey;
      SessionCreateParams params = SessionCreateParams.builder()
          .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
          .setSuccessUrl(withSessionPlaceholder(successUrl))
          .setCancelUrl(cancelUrl)
          .setCustomerEmail(user.email())
          .putMetadata("userId", user.id())
          .putMetadata("planId", plan.id())
          .putMetadata("billingCycle", billingCycle)
          .addLineItem(SessionCreateParams.LineItem.builder()
              .setPrice(priceId)
              .setQuantity(1L)
              .build())
          .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
              .putMetadata("userId", user.id())
              .putMetadata("planId", plan.id())
              .putMetadata("billingCycle", billingCycle)
              .build())
          .build();
      Session session = Session.create(params);
      UserSubscriptionDto subscription = subscriptionPlanService.markStripeCheckoutPending(user, plan, billingCycle, session.getId(), session.getCustomer(), priceId);
      return new BillingCheckoutResponse(session.getUrl(), session.getId(), subscription);
    } catch (Exception exception) {
      throw new IllegalStateException("Stripe checkout oturumu olusturulamadi: " + exception.getMessage(), exception);
    }
  }

  public void handleWebhook(String payload, String signatureHeader) {
    if (!StringUtils.hasText(webhookSecret)) {
      throw new IllegalStateException("Stripe webhook secret ayarlanmadi.");
    }
    if (!StringUtils.hasText(signatureHeader)) {
      throw new IllegalArgumentException("Stripe webhook imzasi eksik.");
    }
    try {
      Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
      if (subscriptionPlanService.hasProcessedBillingEvent("stripe", event.getId())) {
        return;
      }
      JsonNode object = objectMapper.readTree(payload).path("data").path("object");
      switch (event.getType()) {
        case "checkout.session.completed" -> handleCheckoutCompleted(object);
        case "invoice.paid" -> handleInvoicePaid(object);
        case "invoice.payment_failed" -> handleInvoicePaymentFailed(object);
        case "customer.subscription.created", "customer.subscription.updated" -> handleSubscriptionUpdated(object);
        case "customer.subscription.deleted" -> handleSubscriptionDeleted(object);
        default -> {
          // Event is verified but not relevant for subscription provisioning.
        }
      }
      subscriptionPlanService.recordBillingEvent("stripe", event.getId(), event.getType());
    } catch (SignatureVerificationException exception) {
      throw new IllegalArgumentException("Stripe webhook imzasi gecersiz.");
    } catch (Exception exception) {
      throw new IllegalStateException("Stripe webhook islenemedi: " + exception.getMessage(), exception);
    }
  }

  private void handleCheckoutCompleted(JsonNode object) {
    subscriptionPlanService.activateStripeSubscription(
        text(object, "id"),
        text(object, "customer"),
        text(object, "subscription"),
        text(object, "payment_status")
    );
  }

  private void handleInvoicePaid(JsonNode object) {
    String subscriptionId = text(object, "subscription");
    if (StringUtils.hasText(subscriptionId)) {
      subscriptionPlanService.updateStripeSubscriptionByProviderId(subscriptionId, "ACTIVE", text(object, "customer"), text(object, "status"), false, null, null);
    }
  }

  private void handleInvoicePaymentFailed(JsonNode object) {
    String subscriptionId = text(object, "subscription");
    if (StringUtils.hasText(subscriptionId)) {
      subscriptionPlanService.updateStripeSubscriptionByProviderId(subscriptionId, "PAST_DUE", text(object, "customer"), text(object, "status"), false, null, null);
    }
  }

  private void handleSubscriptionUpdated(JsonNode object) {
    String status = mapStripeSubscriptionStatus(text(object, "status"));
    subscriptionPlanService.updateStripeSubscriptionByProviderId(
        text(object, "id"),
        status,
        text(object, "customer"),
        text(object, "status"),
        object.path("cancel_at_period_end").asBoolean(false),
        longValue(object, "current_period_start"),
        longValue(object, "current_period_end")
    );
    String userId = text(object.path("metadata"), "userId");
    String planId = text(object.path("metadata"), "planId");
    if (StringUtils.hasText(userId) && StringUtils.hasText(planId)) {
      subscriptionPlanService.updateStripeSubscriptionByMetadata(
          userId,
          planId,
          text(object, "id"),
          status,
          text(object, "customer"),
          text(object, "status"),
          object.path("cancel_at_period_end").asBoolean(false),
          longValue(object, "current_period_start"),
          longValue(object, "current_period_end")
      );
    }
  }

  private void handleSubscriptionDeleted(JsonNode object) {
    subscriptionPlanService.updateStripeSubscriptionByProviderId(
        text(object, "id"),
        "CANCELLED",
        text(object, "customer"),
        text(object, "status"),
        true,
        longValue(object, "current_period_start"),
        longValue(object, "current_period_end")
    );
  }

  private String mapStripeSubscriptionStatus(String status) {
    return switch (status == null ? "" : status) {
      case "active", "trialing" -> "ACTIVE";
      case "past_due", "unpaid" -> "PAST_DUE";
      case "canceled" -> "CANCELLED";
      case "incomplete", "incomplete_expired" -> "PENDING_PAYMENT";
      default -> "PENDING_PAYMENT";
    };
  }

  private void requireStripeConfigured() {
    if (!StringUtils.hasText(secretKey)) {
      throw new IllegalStateException("Stripe secret key ayarlanmadi.");
    }
  }

  private String withSessionPlaceholder(String url) {
    String separator = url.contains("?") ? "&" : "?";
    return url.contains("{CHECKOUT_SESSION_ID}") ? url : url + separator + "session_id={CHECKOUT_SESSION_ID}";
  }

  private String text(JsonNode node, String fieldName) {
    JsonNode value = node == null ? null : node.get(fieldName);
    return value == null || value.isNull() ? "" : value.asText("");
  }

  private Long longValue(JsonNode node, String fieldName) {
    JsonNode value = node == null ? null : node.get(fieldName);
    return value == null || value.isNull() ? null : value.asLong();
  }
}

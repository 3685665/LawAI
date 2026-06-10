package com.lawai.persistence.entity;

import com.lawai.api.subscription.model.UserSubscriptionRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_subscriptions")
public class UserSubscriptionEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Column(name = "user_name", nullable = false)
  private String userName;

  @Column(name = "user_email", nullable = false)
  private String userEmail;

  @Column(name = "plan_id", nullable = false, length = 36)
  private String planId;

  @Column(name = "plan_name", nullable = false)
  private String planName;

  @Column(name = "billing_cycle", nullable = false, length = 20)
  private String billingCycle;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(nullable = false, length = 30)
  private String provider;

  @Column(name = "provider_customer_id", nullable = false, length = 128)
  private String providerCustomerId;

  @Column(name = "provider_subscription_id", nullable = false, length = 128)
  private String providerSubscriptionId;

  @Column(name = "provider_checkout_session_id", nullable = false, length = 128)
  private String providerCheckoutSessionId;

  @Column(name = "provider_price_id", nullable = false, length = 128)
  private String providerPriceId;

  @Column(name = "last_payment_status", nullable = false, length = 50)
  private String lastPaymentStatus;

  @Column(name = "cancel_at_period_end", nullable = false)
  private boolean cancelAtPeriodEnd;

  @Column(name = "starts_at", nullable = false)
  private OffsetDateTime startsAt;

  @Column(name = "ends_at", nullable = false)
  private OffsetDateTime endsAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected UserSubscriptionEntity() {
  }

  public static UserSubscriptionEntity fromRecord(UserSubscriptionRecord record) {
    UserSubscriptionEntity entity = new UserSubscriptionEntity();
    entity.id = record.id();
    entity.userId = record.userId();
    entity.userName = record.userName();
    entity.userEmail = record.userEmail();
    entity.planId = record.planId();
    entity.planName = record.planName();
    entity.billingCycle = record.billingCycle();
    entity.status = record.status();
    entity.provider = record.provider();
    entity.providerCustomerId = record.providerCustomerId();
    entity.providerSubscriptionId = record.providerSubscriptionId();
    entity.providerCheckoutSessionId = record.providerCheckoutSessionId();
    entity.providerPriceId = record.providerPriceId();
    entity.lastPaymentStatus = record.lastPaymentStatus();
    entity.cancelAtPeriodEnd = record.cancelAtPeriodEnd();
    entity.startsAt = record.startsAt();
    entity.endsAt = record.endsAt();
    entity.createdAt = record.createdAt();
    entity.updatedAt = record.updatedAt();
    return entity;
  }

  public UserSubscriptionRecord toRecord() {
    return new UserSubscriptionRecord(
        id, userId, userName, userEmail, planId, planName, billingCycle, status, provider,
        providerCustomerId, providerSubscriptionId, providerCheckoutSessionId, providerPriceId,
        lastPaymentStatus, cancelAtPeriodEnd, startsAt, endsAt, createdAt, updatedAt
    );
  }

  public void applyRecord(UserSubscriptionRecord record) {
    userName = record.userName();
    userEmail = record.userEmail();
    planId = record.planId();
    planName = record.planName();
    billingCycle = record.billingCycle();
    status = record.status();
    provider = record.provider();
    providerCustomerId = record.providerCustomerId();
    providerSubscriptionId = record.providerSubscriptionId();
    providerCheckoutSessionId = record.providerCheckoutSessionId();
    providerPriceId = record.providerPriceId();
    lastPaymentStatus = record.lastPaymentStatus();
    cancelAtPeriodEnd = record.cancelAtPeriodEnd();
    startsAt = record.startsAt();
    endsAt = record.endsAt();
    updatedAt = record.updatedAt();
  }

  public String getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public String getPlanId() {
    return planId;
  }

  public String getStatus() {
    return status;
  }

  public String getProviderCheckoutSessionId() {
    return providerCheckoutSessionId;
  }

  public String getProviderSubscriptionId() {
    return providerSubscriptionId;
  }
}

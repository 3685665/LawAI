package com.lawai.persistence.entity;

import com.lawai.api.subscription.model.SubscriptionPlanRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlanEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String slug;

  @Column(nullable = false, length = 100)
  private String badge;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(name = "monthly_price", nullable = false)
  private int monthlyPrice;

  @Column(name = "yearly_price", nullable = false)
  private int yearlyPrice;

  @Column(nullable = false, length = 10)
  private String currency;

  @Column(name = "usage_limit", nullable = false)
  private String usageLimit;

  @Column(name = "usage_period", nullable = false)
  private String usagePeriod;

  @Column(nullable = false)
  private boolean highlighted;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private List<String> features = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "locked_features", columnDefinition = "jsonb")
  private List<String> lockedFeatures = new ArrayList<>();

  @Column(name = "cta_label", nullable = false)
  private String ctaLabel;

  @Column(name = "iyzico_product_ref", nullable = false, length = 128)
  private String iyzicoProductRef;

  @Column(name = "iyzico_monthly_plan_ref", nullable = false, length = 128)
  private String iyzicoMonthlyPlanRef;

  @Column(name = "iyzico_yearly_plan_ref", nullable = false, length = 128)
  private String iyzicoYearlyPlanRef;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected SubscriptionPlanEntity() {
  }

  public static SubscriptionPlanEntity fromRecord(SubscriptionPlanRecord record) {
    SubscriptionPlanEntity entity = new SubscriptionPlanEntity();
    entity.id = record.id();
    entity.name = record.name();
    entity.slug = record.slug();
    entity.badge = record.badge();
    entity.description = record.description();
    entity.monthlyPrice = record.monthlyPrice();
    entity.yearlyPrice = record.yearlyPrice();
    entity.currency = record.currency();
    entity.usageLimit = record.usageLimit();
    entity.usagePeriod = record.usagePeriod();
    entity.highlighted = record.highlighted();
    entity.active = record.active();
    entity.sortOrder = record.sortOrder();
    entity.features = record.features() == null ? new ArrayList<>() : new ArrayList<>(record.features());
    entity.lockedFeatures = record.lockedFeatures() == null ? new ArrayList<>() : new ArrayList<>(record.lockedFeatures());
    entity.ctaLabel = record.ctaLabel();
    entity.iyzicoProductRef = record.iyzicoProductRef();
    entity.iyzicoMonthlyPlanRef = record.iyzicoMonthlyPlanRef();
    entity.iyzicoYearlyPlanRef = record.iyzicoYearlyPlanRef();
    entity.createdAt = record.createdAt();
    entity.updatedAt = record.updatedAt();
    return entity;
  }

  public SubscriptionPlanRecord toRecord() {
    return new SubscriptionPlanRecord(
        id, name, slug, badge, description, monthlyPrice, yearlyPrice, currency, usageLimit, usagePeriod,
        highlighted, active, sortOrder, features, lockedFeatures, ctaLabel,
        iyzicoProductRef, iyzicoMonthlyPlanRef, iyzicoYearlyPlanRef, createdAt, updatedAt
    );
  }

  public void applyRecord(SubscriptionPlanRecord record) {
    name = record.name();
    slug = record.slug();
    badge = record.badge();
    description = record.description();
    monthlyPrice = record.monthlyPrice();
    yearlyPrice = record.yearlyPrice();
    currency = record.currency();
    usageLimit = record.usageLimit();
    usagePeriod = record.usagePeriod();
    highlighted = record.highlighted();
    active = record.active();
    sortOrder = record.sortOrder();
    features = record.features() == null ? new ArrayList<>() : new ArrayList<>(record.features());
    lockedFeatures = record.lockedFeatures() == null ? new ArrayList<>() : new ArrayList<>(record.lockedFeatures());
    ctaLabel = record.ctaLabel();
    iyzicoProductRef = record.iyzicoProductRef();
    iyzicoMonthlyPlanRef = record.iyzicoMonthlyPlanRef();
    iyzicoYearlyPlanRef = record.iyzicoYearlyPlanRef();
    updatedAt = record.updatedAt();
  }

  public String getId() {
    return id;
  }

  public boolean isActive() {
    return active;
  }
}

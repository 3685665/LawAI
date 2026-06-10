package com.lawai.persistence.entity;

import com.lawai.api.subscription.model.BillingEventRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "billing_events")
public class BillingEventEntity {

  @EmbeddedId
  private BillingEventId id;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private OffsetDateTime processedAt;

  protected BillingEventEntity() {
  }

  public BillingEventEntity(String provider, String eventId, String eventType, OffsetDateTime processedAt) {
    this.id = new BillingEventId(provider, eventId);
    this.eventType = eventType;
    this.processedAt = processedAt;
  }

  public static BillingEventEntity fromRecord(BillingEventRecord record) {
    return new BillingEventEntity(record.provider(), record.eventId(), record.eventType(), record.processedAt());
  }

  public BillingEventRecord toRecord() {
    return new BillingEventRecord(id.provider, id.eventId, eventType, processedAt);
  }

  @Embeddable
  public static class BillingEventId implements Serializable {

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    protected BillingEventId() {
    }

    public BillingEventId(String provider, String eventId) {
      this.provider = provider;
      this.eventId = eventId;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof BillingEventId that)) {
        return false;
      }
      return Objects.equals(provider, that.provider) && Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(provider, eventId);
    }
  }
}

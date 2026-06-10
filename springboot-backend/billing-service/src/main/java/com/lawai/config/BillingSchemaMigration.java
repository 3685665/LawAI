package com.lawai.config;

import com.lawai.api.subscription.SubscriptionPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BillingSchemaMigration {

  private static final Logger log = LoggerFactory.getLogger(BillingSchemaMigration.class);

  private final JdbcTemplate jdbcTemplate;
  private final SubscriptionPlanService subscriptionPlanService;

  public BillingSchemaMigration(JdbcTemplate jdbcTemplate, SubscriptionPlanService subscriptionPlanService) {
    this.jdbcTemplate = jdbcTemplate;
    this.subscriptionPlanService = subscriptionPlanService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void migrateSubscriptionPlanColumns() {
    if (!tableExists("subscription_plans")) {
      return;
    }

    boolean hasStripeColumns = columnExists("subscription_plans", "stripe_product_id");
    boolean hasIyzicoColumns = columnExists("subscription_plans", "iyzico_product_ref");

    if (!hasIyzicoColumns) {
      jdbcTemplate.execute("ALTER TABLE subscription_plans ADD COLUMN iyzico_product_ref varchar(128)");
      jdbcTemplate.execute("ALTER TABLE subscription_plans ADD COLUMN iyzico_monthly_plan_ref varchar(128)");
      jdbcTemplate.execute("ALTER TABLE subscription_plans ADD COLUMN iyzico_yearly_plan_ref varchar(128)");
      log.info("Added iyzico billing columns to subscription_plans");
    }

    if (hasStripeColumns) {
      jdbcTemplate.update("""
          UPDATE subscription_plans
          SET iyzico_product_ref = COALESCE(NULLIF(iyzico_product_ref, ''), stripe_product_id, ''),
              iyzico_monthly_plan_ref = COALESCE(NULLIF(iyzico_monthly_plan_ref, ''), stripe_monthly_price_id, ''),
              iyzico_yearly_plan_ref = COALESCE(NULLIF(iyzico_yearly_plan_ref, ''), stripe_yearly_price_id, '')
          """);
      jdbcTemplate.execute("ALTER TABLE subscription_plans DROP COLUMN stripe_product_id");
      jdbcTemplate.execute("ALTER TABLE subscription_plans DROP COLUMN stripe_monthly_price_id");
      jdbcTemplate.execute("ALTER TABLE subscription_plans DROP COLUMN stripe_yearly_price_id");
      log.info("Migrated subscription_plans from stripe to iyzico column names");
    }

    jdbcTemplate.update("""
        UPDATE subscription_plans
        SET iyzico_product_ref = COALESCE(iyzico_product_ref, ''),
            iyzico_monthly_plan_ref = COALESCE(iyzico_monthly_plan_ref, ''),
            iyzico_yearly_plan_ref = COALESCE(iyzico_yearly_plan_ref, '')
        """);

    jdbcTemplate.execute("ALTER TABLE subscription_plans ALTER COLUMN iyzico_product_ref SET DEFAULT ''");
    jdbcTemplate.execute("ALTER TABLE subscription_plans ALTER COLUMN iyzico_monthly_plan_ref SET DEFAULT ''");
    jdbcTemplate.execute("ALTER TABLE subscription_plans ALTER COLUMN iyzico_yearly_plan_ref SET DEFAULT ''");
    jdbcTemplate.execute("ALTER TABLE subscription_plans ALTER COLUMN iyzico_product_ref SET NOT NULL");
    jdbcTemplate.execute("ALTER TABLE subscription_plans ALTER COLUMN iyzico_monthly_plan_ref SET NOT NULL");
    jdbcTemplate.execute("ALTER TABLE subscription_plans ALTER COLUMN iyzico_yearly_plan_ref SET NOT NULL");

    clearInvalidIyzicoReferences();
    if (subscriptionPlanService.isIyzicoAutoSyncEnabled()) {
      try {
        subscriptionPlanService.syncAllPaidPlansWithIyzico();
        log.info("Synced paid subscription plans with iyzico catalog");
      } catch (Exception exception) {
        log.warn("iyzico plan senkronizasyonu atlandi: {}", exception.getMessage());
      }
    }
  }

  private void clearInvalidIyzicoReferences() {
    int cleared = jdbcTemplate.update("""
        UPDATE subscription_plans
        SET iyzico_product_ref = CASE
              WHEN iyzico_product_ref ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN iyzico_product_ref
              ELSE ''
            END,
            iyzico_monthly_plan_ref = CASE
              WHEN iyzico_monthly_plan_ref ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN iyzico_monthly_plan_ref
              ELSE ''
            END,
            iyzico_yearly_plan_ref = CASE
              WHEN iyzico_yearly_plan_ref ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN iyzico_yearly_plan_ref
              ELSE ''
            END
        """);
    if (cleared > 0) {
      log.info("Cleared invalid iyzico reference codes from {} subscription plan(s)", cleared);
    }
  }

  private boolean tableExists(String tableName) {
    Boolean exists = jdbcTemplate.queryForObject(
        """
            SELECT EXISTS (
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = 'public' AND table_name = ?
            )
            """,
        Boolean.class,
        tableName
    );
    return Boolean.TRUE.equals(exists);
  }

  private boolean columnExists(String tableName, String columnName) {
    Boolean exists = jdbcTemplate.queryForObject(
        """
            SELECT EXISTS (
              SELECT 1
              FROM information_schema.columns
              WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
            )
            """,
        Boolean.class,
        tableName,
        columnName
    );
    return Boolean.TRUE.equals(exists);
  }
}

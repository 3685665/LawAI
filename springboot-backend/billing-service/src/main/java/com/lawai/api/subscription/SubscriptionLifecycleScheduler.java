package com.lawai.api.subscription;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionLifecycleScheduler {

  private final SubscriptionPlanService subscriptionPlanService;

  public SubscriptionLifecycleScheduler(SubscriptionPlanService subscriptionPlanService) {
    this.subscriptionPlanService = subscriptionPlanService;
  }

  @Scheduled(cron = "0 0 * * * *")
  public void expireEndedSubscriptions() {
    subscriptionPlanService.expireEndedSubscriptions();
  }
}

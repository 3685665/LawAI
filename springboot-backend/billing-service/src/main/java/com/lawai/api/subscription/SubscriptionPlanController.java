package com.lawai.api.subscription;

import com.lawai.common.client.ActivityLogClient;
import com.lawai.api.subscription.dto.SubscriptionPlanDto;
import com.lawai.api.subscription.dto.SubscriptionPlanRequest;
import com.lawai.api.subscription.dto.UserSubscriptionDto;
import com.lawai.api.subscription.dto.UserSubscriptionStatusRequest;
import com.lawai.common.model.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionPlanController {

  private final SubscriptionPlanService subscriptionPlanService;
  private final IyzicoBillingService iyzicoBillingService;
  private final ActivityLogClient activityLogClient;

  public SubscriptionPlanController(
      SubscriptionPlanService subscriptionPlanService,
      IyzicoBillingService iyzicoBillingService,
      ActivityLogClient activityLogClient
  ) {
    this.subscriptionPlanService = subscriptionPlanService;
    this.iyzicoBillingService = iyzicoBillingService;
    this.activityLogClient = activityLogClient;
  }

  @GetMapping
  public List<SubscriptionPlanDto> list() {
    return subscriptionPlanService.listActive();
  }

  @GetMapping("/admin")
  public List<SubscriptionPlanDto> listAdmin(Authentication authentication) {
    return subscriptionPlanService.listAll(requireUser(authentication));
  }

  @GetMapping("/me")
  public UserSubscriptionDto mySubscription(Authentication authentication) {
    return subscriptionPlanService.mySubscription(requireUser(authentication));
  }

  @PostMapping("/me")
  public UserSubscriptionDto subscribe(Authentication authentication) {
    throw new IllegalArgumentException("Abonelik baslatmak icin /api/billing/checkout akisini kullanin.");
  }

  @PostMapping("/me/cancel")
  public UserSubscriptionDto cancelMine(Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    UserSubscriptionDto response = iyzicoBillingService.cancelSubscription(user);
    activityLogClient.logBackend(user, "subscription-cancel", "Abonelik", "Abonelik donem sonunda iptal edilecek: " + response.planName(), "/api/subscriptions/me/cancel");
    return response;
  }

  @GetMapping("/admin/users")
  public List<UserSubscriptionDto> listUserSubscriptions(Authentication authentication) {
    return subscriptionPlanService.listUserSubscriptions(requireUser(authentication));
  }

  @PutMapping("/admin/users/{id}/status")
  public UserSubscriptionDto updateUserSubscriptionStatus(@PathVariable String id, @Valid @RequestBody UserSubscriptionStatusRequest request, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    UserSubscriptionDto response = subscriptionPlanService.updateUserSubscriptionStatus(user, id, request.status());
    activityLogClient.logBackend(user, "subscription-user-status", "Abonelik Yonetimi", "Kullanici aboneligi guncellendi: " + response.userEmail(), "/api/subscriptions/admin/users/" + id + "/status");
    return response;
  }

  @PostMapping("/admin")
  public SubscriptionPlanDto create(@Valid @RequestBody SubscriptionPlanRequest request, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    SubscriptionPlanDto response = subscriptionPlanService.create(user, request);
    activityLogClient.logBackend(user, "subscription-create", "Abonelik Yonetimi", "Abonelik plani olusturuldu: " + response.name(), "/api/subscriptions/admin");
    return response;
  }

  @PutMapping("/admin/{id}")
  public SubscriptionPlanDto update(@PathVariable String id, @Valid @RequestBody SubscriptionPlanRequest request, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    SubscriptionPlanDto response = subscriptionPlanService.update(user, id, request);
    activityLogClient.logBackend(user, "subscription-update", "Abonelik Yonetimi", "Abonelik plani guncellendi: " + response.name(), "/api/subscriptions/admin/" + id);
    return response;
  }

  @DeleteMapping("/admin/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    subscriptionPlanService.delete(user, id);
    activityLogClient.logBackend(user, "subscription-delete", "Abonelik Yonetimi", "Abonelik plani silindi: " + id, "/api/subscriptions/admin/" + id);
    return ResponseEntity.noContent().build();
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("Oturum gerekli.");
  }
}

package com.lawai.api.subscription;

import com.lawai.api.service.ActivityLogService;
import com.lawai.api.subscription.dto.SubscriptionPlanDto;
import com.lawai.api.subscription.dto.SubscriptionPlanRequest;
import com.lawai.auth.model.AuthenticatedUser;
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
  private final ActivityLogService activityLogService;

  public SubscriptionPlanController(SubscriptionPlanService subscriptionPlanService, ActivityLogService activityLogService) {
    this.subscriptionPlanService = subscriptionPlanService;
    this.activityLogService = activityLogService;
  }

  @GetMapping
  public List<SubscriptionPlanDto> list() {
    return subscriptionPlanService.listActive();
  }

  @GetMapping("/admin")
  public List<SubscriptionPlanDto> listAdmin(Authentication authentication) {
    return subscriptionPlanService.listAll(requireUser(authentication));
  }

  @PostMapping("/admin")
  public SubscriptionPlanDto create(@Valid @RequestBody SubscriptionPlanRequest request, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    SubscriptionPlanDto response = subscriptionPlanService.create(user, request);
    activityLogService.logBackend(user, "subscription-create", "Abonelik Yonetimi", "Abonelik plani olusturuldu: " + response.name(), "/api/subscriptions/admin");
    return response;
  }

  @PutMapping("/admin/{id}")
  public SubscriptionPlanDto update(@PathVariable String id, @Valid @RequestBody SubscriptionPlanRequest request, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    SubscriptionPlanDto response = subscriptionPlanService.update(user, id, request);
    activityLogService.logBackend(user, "subscription-update", "Abonelik Yonetimi", "Abonelik plani guncellendi: " + response.name(), "/api/subscriptions/admin/" + id);
    return response;
  }

  @DeleteMapping("/admin/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    subscriptionPlanService.delete(user, id);
    activityLogService.logBackend(user, "subscription-delete", "Abonelik Yonetimi", "Abonelik plani silindi: " + id, "/api/subscriptions/admin/" + id);
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

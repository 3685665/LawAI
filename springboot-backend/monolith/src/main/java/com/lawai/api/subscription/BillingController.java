package com.lawai.api.subscription;

import com.lawai.api.subscription.dto.BillingCheckoutRequest;
import com.lawai.api.subscription.dto.BillingCheckoutResponse;
import com.lawai.auth.model.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

  private final StripeBillingService stripeBillingService;

  public BillingController(StripeBillingService stripeBillingService) {
    this.stripeBillingService = stripeBillingService;
  }

  @PostMapping("/checkout")
  public BillingCheckoutResponse checkout(@Valid @RequestBody BillingCheckoutRequest request, Authentication authentication) {
    return stripeBillingService.createCheckoutSession(requireUser(authentication), request.planId(), request.billingCycle());
  }

  @PostMapping("/stripe/webhook")
  public ResponseEntity<Map<String, Boolean>> stripeWebhook(@RequestBody String payload, @RequestHeader(name = "Stripe-Signature", required = false) String signatureHeader) {
    stripeBillingService.handleWebhook(payload, signatureHeader);
    return ResponseEntity.ok(Map.of("received", true));
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("Oturum gerekli.");
  }
}

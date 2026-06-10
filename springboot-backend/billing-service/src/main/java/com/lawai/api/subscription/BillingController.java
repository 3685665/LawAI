package com.lawai.api.subscription;

import com.lawai.api.subscription.dto.BillingCheckoutRequest;
import com.lawai.api.subscription.dto.BillingCheckoutResponse;
import com.lawai.common.model.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

  private final IyzicoBillingService iyzicoBillingService;

  public BillingController(IyzicoBillingService iyzicoBillingService) {
    this.iyzicoBillingService = iyzicoBillingService;
  }

  @PostMapping("/checkout")
  public BillingCheckoutResponse checkout(@Valid @RequestBody BillingCheckoutRequest request, Authentication authentication) {
    return iyzicoBillingService.createCheckout(requireUser(authentication), request.planId(), request.billingCycle());
  }

  @PostMapping("/iyzico/callback")
  public void iyzicoCallbackPost(@RequestParam(name = "token", required = false) String token, HttpServletResponse response) throws IOException {
    handleCallback(token, response);
  }

  @GetMapping("/iyzico/callback")
  public void iyzicoCallbackGet(@RequestParam(name = "token", required = false) String token, HttpServletResponse response) throws IOException {
    handleCallback(token, response);
  }

  @PostMapping("/iyzico/webhook")
  public ResponseEntity<Map<String, Boolean>> iyzicoWebhook(
      @RequestBody String payload,
      @RequestHeader(name = "X-IYZ-SIGNATURE-V3", required = false) String signatureHeader
  ) {
    iyzicoBillingService.handleWebhook(payload, signatureHeader);
    return ResponseEntity.ok(Map.of("received", true));
  }

  private void handleCallback(String token, HttpServletResponse response) throws IOException {
    try {
      iyzicoBillingService.completeCallback(token);
      response.sendRedirect(iyzicoBillingService.successRedirectUrl());
    } catch (Exception exception) {
      String message = URLEncoder.encode(exception.getMessage() == null ? "Odeme tamamlanamadi." : exception.getMessage(), StandardCharsets.UTF_8);
      response.sendRedirect(iyzicoBillingService.cancelRedirectUrl() + "&error=" + message);
    }
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("Oturum gerekli.");
  }
}

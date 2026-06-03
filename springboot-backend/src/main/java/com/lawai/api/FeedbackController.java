package com.lawai.api;

import com.lawai.api.dto.FeedbackCreateRequest;
import com.lawai.api.dto.FeedbackRecordDto;
import com.lawai.api.dto.FeedbackSubmissionResponse;
import com.lawai.api.dto.FeedbackUpdateRequest;
import com.lawai.api.dto.FeedbackStatusUpdateRequest;
import com.lawai.api.service.FeedbackService;
import com.lawai.auth.model.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

  private final FeedbackService feedbackService;

  public FeedbackController(FeedbackService feedbackService) {
    this.feedbackService = feedbackService;
  }

  @GetMapping
  public List<FeedbackRecordDto> list(Authentication authentication) {
    return feedbackService.listVisible(requireUser(authentication));
  }

  @PostMapping
  public FeedbackSubmissionResponse submit(@Valid @RequestBody FeedbackCreateRequest request, Authentication authentication) {
    FeedbackRecordDto feedback = feedbackService.submit(requireUser(authentication), request);
    return new FeedbackSubmissionResponse("Geri bildiriminiz alindi.", feedback);
  }

  @PatchMapping("/{id}/status")
  public FeedbackRecordDto updateStatus(
      @PathVariable String id,
      @Valid @RequestBody FeedbackStatusUpdateRequest request,
      Authentication authentication
  ) {
    return feedbackService.updateStatus(requireUser(authentication), id, request);
  }

  @PatchMapping("/{id}")
  public FeedbackRecordDto update(
      @PathVariable String id,
      @Valid @RequestBody FeedbackUpdateRequest request,
      Authentication authentication
  ) {
    return feedbackService.update(requireUser(authentication), id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id, Authentication authentication) {
    feedbackService.delete(requireUser(authentication), id);
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

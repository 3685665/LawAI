package com.lawai.api;

import com.lawai.api.dto.PetitionTrainingResponse;
import com.lawai.api.service.ActivityLogService;
import com.lawai.api.service.TrainingService;
import com.lawai.auth.model.AuthenticatedUser;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/training")
public class TrainingController {

  private final TrainingService trainingService;
  private final ActivityLogService activityLogService;

  public TrainingController(TrainingService trainingService, ActivityLogService activityLogService) {
    this.trainingService = trainingService;
    this.activityLogService = activityLogService;
  }

  @GetMapping("/petition-drafting")
  public PetitionTrainingResponse petitionDrafting(Authentication authentication) {
    PetitionTrainingResponse response = trainingService.petitionDrafting();
    activityLogService.logBackend(requireUser(authentication), "training-open", "Egitim", "Dilekce egitimi goruntulendi.", "/api/training/petition-drafting");
    return response;
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("Oturum gerekli.");
  }
}

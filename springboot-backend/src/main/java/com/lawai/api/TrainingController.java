package com.lawai.api;

import com.lawai.api.dto.PetitionTrainingResponse;
import com.lawai.api.service.TrainingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/training")
public class TrainingController {

  private final TrainingService trainingService;

  public TrainingController(TrainingService trainingService) {
    this.trainingService = trainingService;
  }

  @GetMapping("/petition-drafting")
  public PetitionTrainingResponse petitionDrafting() {
    return trainingService.petitionDrafting();
  }
}

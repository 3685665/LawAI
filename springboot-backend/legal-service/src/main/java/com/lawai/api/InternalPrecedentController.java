package com.lawai.api;

import com.lawai.api.dto.PrecedentBatchContentResponse;
import com.lawai.api.dto.PrecedentBatchPageRequest;
import com.lawai.api.dto.PrecedentBatchPageResponse;
import com.lawai.api.service.PrecedentBatchFetchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/precedents")
public class InternalPrecedentController {

  private final PrecedentBatchFetchService precedentBatchFetchService;

  public InternalPrecedentController(PrecedentBatchFetchService precedentBatchFetchService) {
    this.precedentBatchFetchService = precedentBatchFetchService;
  }

  @PostMapping("/batch/page")
  public PrecedentBatchPageResponse fetchBatchPage(@Valid @RequestBody PrecedentBatchPageRequest request) {
    return precedentBatchFetchService.fetchPage(request);
  }

  @GetMapping("/batch/content")
  public PrecedentBatchContentResponse fetchBatchContent(
      @RequestParam String court,
      @RequestParam String sourceId
  ) {
    return precedentBatchFetchService.fetchContent(court, sourceId);
  }
}

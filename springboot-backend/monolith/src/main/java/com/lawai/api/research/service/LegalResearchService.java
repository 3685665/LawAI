package com.lawai.api.research.service;

import com.lawai.api.research.LegalResearchException;
import com.lawai.api.research.ResearchSource;
import com.lawai.api.research.ResearchStepStatus;
import com.lawai.api.research.ResearchStepType;
import com.lawai.api.research.dto.LegalResearchResponse;
import com.lawai.api.research.dto.ResearchPlanDto;
import com.lawai.api.research.dto.ResearchSourceResultDto;
import com.lawai.api.research.dto.ResearchStepDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

@Service
public class LegalResearchService {

  private static final Logger log = LoggerFactory.getLogger(LegalResearchService.class);
  private static final String DISCLAIMER =
      "Bu yanit hukuki arastirma asistani tarafindan uretilmistir; profesyonel hukuk danismanligi yerine gecmez.";

  private final QueryPlannerService queryPlannerService;
  private final LegislationSearchService legislationSearchService;
  private final CaseLawSearchService caseLawSearchService;
  private final WebSearchService webSearchService;
  private final LlmClient llmClient;

  public LegalResearchService(
      QueryPlannerService queryPlannerService,
      LegislationSearchService legislationSearchService,
      CaseLawSearchService caseLawSearchService,
      WebSearchService webSearchService,
      LlmClient llmClient
  ) {
    this.queryPlannerService = queryPlannerService;
    this.legislationSearchService = legislationSearchService;
    this.caseLawSearchService = caseLawSearchService;
    this.webSearchService = webSearchService;
    this.llmClient = llmClient;
  }

  public LegalResearchResponse run(String query) {
    return run(query, null);
  }

  public LegalResearchResponse run(String query, Consumer<ResearchStepDto> progressListener) {
    ResearchPlanDto plan = queryPlannerService.createPlan(query);
    log.info("Hukuki arastirma baslatildi: query={}", plan.query());

    emit(progressListener, ResearchStepType.PLAN_CREATED, ResearchStepStatus.COMPLETED);

    emit(progressListener, ResearchStepType.LEGISLATION_IN_PROGRESS, ResearchStepStatus.IN_PROGRESS);
    List<String> legislationFindings = legislationSearchService.search(plan.query());
    emit(progressListener, ResearchStepType.LEGISLATION_COMPLETED, ResearchStepStatus.COMPLETED);

    emit(progressListener, ResearchStepType.CASE_LAW_IN_PROGRESS, ResearchStepStatus.IN_PROGRESS);
    emit(progressListener, ResearchStepType.WEB_IN_PROGRESS, ResearchStepStatus.IN_PROGRESS);

    CompletableFuture<List<String>> caseLawFuture =
        CompletableFuture.supplyAsync(() -> caseLawSearchService.search(plan.query()));
    CompletableFuture<List<String>> webFuture =
        CompletableFuture.supplyAsync(() -> webSearchService.search(plan.query()));

    try {
      CompletableFuture.allOf(caseLawFuture, webFuture).join();
    } catch (CompletionException exception) {
      throw new LegalResearchException("Arastirma kaynaklarindan biri basarisiz oldu.", exception.getCause());
    }

    emit(progressListener, ResearchStepType.CASE_LAW_COMPLETED, ResearchStepStatus.COMPLETED);
    emit(progressListener, ResearchStepType.WEB_COMPLETED, ResearchStepStatus.COMPLETED);

    List<ResearchSourceResultDto> sourceResults = List.of(
        new ResearchSourceResultDto(ResearchSource.LEGISLATION, legislationFindings),
        new ResearchSourceResultDto(ResearchSource.CASE_LAW, caseLawFuture.join()),
        new ResearchSourceResultDto(ResearchSource.WEB, webFuture.join())
    );

    emit(progressListener, ResearchStepType.FINAL_ANSWER, ResearchStepStatus.IN_PROGRESS);
    String answer = llmClient.synthesizeAnswer(plan.query(), sourceResults);
    emit(progressListener, ResearchStepType.FINAL_ANSWER, ResearchStepStatus.COMPLETED);

    List<ResearchStepDto> steps = buildCompletedSteps();
    log.info("Hukuki arastirma tamamlandi: query={}", plan.query());
    return new LegalResearchResponse(plan, steps, sourceResults, answer, DISCLAIMER, null);
  }

  private void emit(Consumer<ResearchStepDto> progressListener, ResearchStepType type, ResearchStepStatus status) {
    if (progressListener == null) {
      return;
    }
    progressListener.accept(new ResearchStepDto(type, type.label(), status));
  }

  private List<ResearchStepDto> buildCompletedSteps() {
    List<ResearchStepDto> steps = new ArrayList<>();
    for (ResearchStepType type : ResearchStepType.values()) {
      steps.add(new ResearchStepDto(type, type.label(), ResearchStepStatus.COMPLETED));
    }
    return List.copyOf(steps);
  }
}

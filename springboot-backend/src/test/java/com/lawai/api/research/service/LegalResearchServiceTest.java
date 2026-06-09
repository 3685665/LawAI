package com.lawai.api.research.service;

import com.lawai.api.research.ResearchSource;
import com.lawai.api.research.ResearchStepStatus;
import com.lawai.api.research.ResearchStepType;
import com.lawai.api.research.dto.LegalResearchResponse;
import com.lawai.api.research.dto.ResearchStepDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalResearchServiceTest {

  @Test
  void run_collectsFindingsAndEmitsProgressSteps() {
    List<ResearchStepDto> progress = new CopyOnWriteArrayList<>();
    LegalResearchService service = new LegalResearchService(
        new QueryPlannerService(),
        query -> List.of("TCK Madde 81"),
        query -> List.of("Yargitay ornek karar"),
        query -> List.of("Resmi kaynak ornegi"),
        (query, results) -> "Sentez: " + query + " / " + results.size()
    );

    LegalResearchResponse response = service.run("agir cezalar", progress::add);

    assertEquals("agir cezalar", response.plan().query());
    assertEquals(3, response.sourceResults().size());
    assertTrue(response.answer().contains("Sentez"));
    assertFalse(progress.isEmpty());
    assertEquals(ResearchStepStatus.COMPLETED, progress.get(progress.size() - 1).status());
    assertTrue(progress.stream().anyMatch(step -> step.type() == ResearchStepType.LEGISLATION_IN_PROGRESS));
    assertTrue(progress.stream().anyMatch(step -> step.type() == ResearchStepType.FINAL_ANSWER));
  }

  @Test
  void run_returnsCompletedStepsInResponse() {
    LegalResearchService service = new LegalResearchService(
        new QueryPlannerService(),
        query -> List.of(),
        query -> List.of(),
        query -> List.of(),
        (query, results) -> "Cevap"
    );

    LegalResearchResponse response = service.run("kira artis orani");

    assertEquals(ResearchStepType.values().length, response.steps().size());
    assertTrue(response.steps().stream().allMatch(step -> step.status() == ResearchStepStatus.COMPLETED));
    assertEquals(
        List.of(ResearchSource.LEGISLATION, ResearchSource.CASE_LAW, ResearchSource.WEB),
        response.plan().sources()
    );
  }
}

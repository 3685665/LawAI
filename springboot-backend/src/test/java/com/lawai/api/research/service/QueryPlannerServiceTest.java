package com.lawai.api.research.service;

import com.lawai.api.research.ResearchSource;
import com.lawai.api.research.dto.ResearchPlanDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryPlannerServiceTest {

  private final QueryPlannerService queryPlannerService = new QueryPlannerService();

  @Test
  void createPlan_returnsAllSourcesForValidQuery() {
    ResearchPlanDto plan = queryPlannerService.createPlan("agir cezalar");

    assertEquals("agir cezalar", plan.query());
    assertEquals(List.of(ResearchSource.LEGISLATION, ResearchSource.CASE_LAW, ResearchSource.WEB), plan.sources());
  }

  @Test
  void createPlan_trimsQuery() {
    ResearchPlanDto plan = queryPlannerService.createPlan("  ise iade davasi  ");

    assertEquals("ise iade davasi", plan.query());
  }

  @Test
  void createPlan_rejectsBlankQuery() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> queryPlannerService.createPlan("   "));
    assertTrue(exception.getMessage().contains("bos"));
  }
}

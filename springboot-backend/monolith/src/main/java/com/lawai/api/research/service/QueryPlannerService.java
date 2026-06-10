package com.lawai.api.research.service;

import com.lawai.api.research.ResearchSource;
import com.lawai.api.research.dto.ResearchPlanDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.List;

@Service
public class QueryPlannerService {

  private static final Logger log = LoggerFactory.getLogger(QueryPlannerService.class);

  public ResearchPlanDto createPlan(String query) {
    if (!StringUtils.hasText(query)) {
      throw new IllegalArgumentException("Arastirma sorgusu bos olamaz.");
    }

    String normalized = query.trim();
    List<ResearchSource> sources = List.copyOf(EnumSet.allOf(ResearchSource.class));
    log.info("Arastirma plani olusturuldu: query={}, sources={}", normalized, sources);
    return new ResearchPlanDto(normalized, sources);
  }
}

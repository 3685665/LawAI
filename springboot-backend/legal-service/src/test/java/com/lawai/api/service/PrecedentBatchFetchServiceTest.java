package com.lawai.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.dto.PrecedentBatchPageRequest;
import com.lawai.api.dto.PrecedentBatchPageResponse;
import com.lawai.api.dto.PrecedentDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrecedentBatchFetchServiceTest {

  @Test
  void fetchPageUsesSourceHasMoreFlag() {
    YargitayPrecedentService yargitay = new YargitayPrecedentService(new ObjectMapper()) {
      @Override
      public PrecedentBatchPageResult searchBatchPage(com.lawai.api.dto.PrecedentSearchRequest request, int pageNumber, int pageSize) {
        return new PrecedentBatchPageResult(
            List.of(new PrecedentDto(
                "123",
                "Yargitay",
                "Ceza",
                "2024/1",
                "2024/2",
                "01/01/2024",
                "Karar",
                "Ozet",
                "Icerik",
                "Sonuc"
            )),
            true
        );
      }
    };
    DanistayPrecedentService danistay = new DanistayPrecedentService(new ObjectMapper()) {
      @Override
      public PrecedentBatchPageResult searchBatchPage(com.lawai.api.dto.PrecedentSearchRequest request, int pageNumber, int pageSize) {
        return new PrecedentBatchPageResult(List.of(), false);
      }
    };
    AnayasaPrecedentService anayasa = new AnayasaPrecedentService(new ObjectMapper()) {
      @Override
      public PrecedentBatchPageResult searchBatchPage(com.lawai.api.dto.PrecedentSearchRequest request, int pageNumber, int pageSize) {
        return new PrecedentBatchPageResult(List.of(), false);
      }
    };

    PrecedentBatchFetchService service = new PrecedentBatchFetchService(yargitay, danistay, anayasa);
    PrecedentBatchPageResponse response = service.fetchPage(new PrecedentBatchPageRequest(
        "YARGITAY",
        "",
        "2024-01-01",
        "2024-01-31",
        1,
        50
    ));

    assertThat(response.items()).hasSize(1);
    assertThat(response.hasMore()).isTrue();
  }
}

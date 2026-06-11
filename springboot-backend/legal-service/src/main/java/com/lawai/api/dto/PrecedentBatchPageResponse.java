package com.lawai.api.dto;

import java.util.List;

public record PrecedentBatchPageResponse(
    List<PrecedentBatchItemDto> items,
    boolean hasMore
) {
}

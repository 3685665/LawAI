package com.lawai.api.service;

import com.lawai.api.dto.PrecedentDto;

import java.util.List;

record PrecedentBatchPageResult(List<PrecedentDto> items, boolean hasMore) {
}

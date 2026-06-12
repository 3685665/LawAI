package com.lawai.api.dto;

import java.math.BigDecimal;

public record CaseExpenseDto(
    String id,
    String title,
    BigDecimal amount,
    String description
) {
}

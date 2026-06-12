package com.lawai.api.dto;

public record CasePartyDto(
    String id,
    String name,
    String role,
    String contact,
    String identityNumber,
    String phone,
    String email,
    String startDate,
    String endDate
) {
}

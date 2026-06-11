package com.lawai.document.batch;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public enum PrecedentCourt {
  YARGITAY,
  DANISTAY,
  ANAYASA;

  public static List<PrecedentCourt> parseCsv(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(part -> !part.isBlank())
        .map(part -> PrecedentCourt.valueOf(part.toUpperCase(Locale.ROOT)))
        .toList();
  }

  public static String toCsv(List<PrecedentCourt> courts) {
    return courts.stream().map(Enum::name).collect(Collectors.joining(","));
  }
}

package com.lawai.document.batch;

import com.lawai.document.batch.dto.BatchDocumentJobDto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public final class BatchScheduleCalculator {

  private static final ZoneId ZONE = ZoneId.systemDefault();

  private BatchScheduleCalculator() {
  }

  public static Instant computeNextRun(BatchScheduleType type, LocalTime time, LocalDate date, Integer dayOfWeek, Integer dayOfMonth, Instant from) {
    LocalDateTime anchor = LocalDateTime.ofInstant(from, ZONE);
    return switch (type) {
      case ONCE -> {
        if (date == null) {
          throw new IllegalArgumentException("Tek seferlik gorev icin tarih gerekli.");
        }
        LocalDateTime runAt = LocalDateTime.of(date, time);
        yield runAt.isAfter(anchor) ? runAt.atZone(ZONE).toInstant() : null;
      }
      case DAILY -> {
        LocalDateTime runAt = LocalDateTime.of(anchor.toLocalDate(), time);
        if (!runAt.isAfter(anchor)) {
          runAt = runAt.plusDays(1);
        }
        yield runAt.atZone(ZONE).toInstant();
      }
      case WEEKLY -> {
        if (dayOfWeek == null) {
          throw new IllegalArgumentException("Haftalik gorev icin gun secimi gerekli.");
        }
        DayOfWeek target = DayOfWeek.of(dayOfWeek);
        LocalDate nextDate = anchor.toLocalDate().with(TemporalAdjusters.nextOrSame(target));
        LocalDateTime runAt = LocalDateTime.of(nextDate, time);
        if (!runAt.isAfter(anchor)) {
          runAt = runAt.plusWeeks(1);
        }
        yield runAt.atZone(ZONE).toInstant();
      }
      case MONTHLY -> {
        if (dayOfMonth == null) {
          throw new IllegalArgumentException("Aylik gorev icin ay gunu gerekli.");
        }
        LocalDate candidate = anchor.toLocalDate().withDayOfMonth(Math.min(dayOfMonth, anchor.toLocalDate().lengthOfMonth()));
        LocalDateTime runAt = LocalDateTime.of(candidate, time);
        if (!runAt.isAfter(anchor)) {
          LocalDate nextMonth = anchor.toLocalDate().plusMonths(1);
          int day = Math.min(dayOfMonth, nextMonth.lengthOfMonth());
          runAt = LocalDateTime.of(nextMonth.withDayOfMonth(day), time);
        }
        yield runAt.atZone(ZONE).toInstant();
      }
    };
  }

  public static Instant computeNextRunAfterExecution(BatchDocumentJobDto job, Instant executedAt) {
    BatchScheduleType type = BatchScheduleType.valueOf(job.scheduleType());
    LocalTime time = LocalTime.parse(job.scheduledTime());
    if (type == BatchScheduleType.ONCE) {
      return null;
    }
    return computeNextRun(type, time, job.scheduledDate(), job.dayOfWeek(), job.dayOfMonth(), executedAt.plusSeconds(60));
  }
}

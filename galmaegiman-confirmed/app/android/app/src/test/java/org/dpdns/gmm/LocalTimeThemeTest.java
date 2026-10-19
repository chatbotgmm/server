package org.dpdns.gmm;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

/** 기기의 기본 시간대에 기대지 않고 현지 경계와 특수 시간대를 검증한다. */
public class LocalTimeThemeTest {
  static long utc(int year, int month, int day, int hour, int minute, int second) {
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.clear();
    calendar.set(year, month - 1, day, hour, minute, second);
    return calendar.getTimeInMillis();
  }

  void phase(long instant, String zone, LocalTimeTheme.Phase expected) {
    assertEquals(expected, LocalTimeTheme.resolve(instant, TimeZone.getTimeZone(zone)).phase);
  }

  @Test
  public void allFourBoundariesAndMidnightUseTheLocalCalendar() {
    String zone = "Asia/Seoul";
    phase(utc(2026, 10, 14, 19, 59, 59), zone, LocalTimeTheme.Phase.NIGHT);
    phase(utc(2026, 10, 14, 20, 0, 0), zone, LocalTimeTheme.Phase.MORNING);
    phase(utc(2026, 10, 15, 0, 59, 59), zone, LocalTimeTheme.Phase.MORNING);
    phase(utc(2026, 10, 15, 1, 0, 0), zone, LocalTimeTheme.Phase.DAY);
    phase(utc(2026, 10, 15, 7, 59, 59), zone, LocalTimeTheme.Phase.DAY);
    phase(utc(2026, 10, 15, 8, 0, 0), zone, LocalTimeTheme.Phase.EVENING);
    phase(utc(2026, 10, 15, 10, 59, 59), zone, LocalTimeTheme.Phase.EVENING);
    phase(utc(2026, 10, 15, 11, 0, 0), zone, LocalTimeTheme.Phase.NIGHT);
    phase(utc(2026, 10, 15, 14, 59, 59), zone, LocalTimeTheme.Phase.NIGHT);
    phase(utc(2026, 10, 15, 15, 0, 0), zone, LocalTimeTheme.Phase.NIGHT);
  }

  @Test
  public void seoulAndNewYorkResolveTheSameInstantDifferently() {
    long instant = utc(2026, 10, 15, 8, 30, 0);
    phase(instant, "Asia/Seoul", LocalTimeTheme.Phase.EVENING);
    phase(instant, "America/New_York", LocalTimeTheme.Phase.NIGHT);
    assertEquals("night", LocalTimeTheme.Phase.NIGHT.assetKey);
  }

  @Test
  public void dstJumpAndRepeatedHourUseTheActualTimezoneOffset() {
    TimeZone zone = TimeZone.getTimeZone("America/New_York");
    assertEquals(1, LocalTimeTheme.resolve(utc(2026, 3, 8, 6, 59, 59), zone).localHour);
    assertEquals(3, LocalTimeTheme.resolve(utc(2026, 3, 8, 7, 0, 0), zone).localHour);
    assertEquals(1, LocalTimeTheme.resolve(utc(2026, 11, 1, 5, 30, 0), zone).localHour);
    assertEquals(1, LocalTimeTheme.resolve(utc(2026, 11, 1, 6, 30, 0), zone).localHour);
    phase(utc(2026, 3, 8, 6, 59, 59), zone.getID(), LocalTimeTheme.Phase.NIGHT);
    phase(utc(2026, 3, 8, 7, 0, 0), zone.getID(), LocalTimeTheme.Phase.NIGHT);
    phase(utc(2026, 11, 1, 9, 59, 59), zone.getID(), LocalTimeTheme.Phase.NIGHT);
    phase(utc(2026, 11, 1, 10, 0, 0), zone.getID(), LocalTimeTheme.Phase.MORNING);
  }

  @Test
  public void halfAndQuarterHourOffsetsKeepExactTenOClockBoundaries() {
    phase(utc(2026, 10, 15, 4, 29, 59), "Asia/Kolkata", LocalTimeTheme.Phase.MORNING);
    phase(utc(2026, 10, 15, 4, 30, 0), "Asia/Kolkata", LocalTimeTheme.Phase.DAY);
    phase(utc(2026, 10, 15, 4, 14, 59), "Asia/Kathmandu", LocalTimeTheme.Phase.MORNING);
    phase(utc(2026, 10, 15, 4, 15, 0), "Asia/Kathmandu", LocalTimeTheme.Phase.DAY);
  }

  @Test
  public void defaultTimezoneIsReadAgainAfterDeviceSettingsChange() {
    TimeZone original = TimeZone.getDefault();
    long instant = utc(2026, 10, 15, 8, 30, 0);
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
      assertEquals(LocalTimeTheme.Phase.EVENING, LocalTimeTheme.resolve(instant).phase);
      assertEquals("Asia/Seoul", LocalTimeTheme.resolve(instant).zoneId);
      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
      assertEquals(LocalTimeTheme.Phase.NIGHT, LocalTimeTheme.resolve(instant).phase);
      assertEquals("America/New_York", LocalTimeTheme.resolve(instant, null).zoneId);
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  public void nextMinuteSchedulingHandlesExactAndNegativeEpochs() {
    assertEquals(1, LocalTimeTheme.millisUntilNextMinute(59_999));
    assertEquals(60_000, LocalTimeTheme.millisUntilNextMinute(60_000));
    assertEquals(1, LocalTimeTheme.millisUntilNextMinute(-1));
    assertEquals(60_000, LocalTimeTheme.millisUntilNextMinute(-60_000));
  }
}

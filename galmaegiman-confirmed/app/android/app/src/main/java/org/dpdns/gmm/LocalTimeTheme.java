package org.dpdns.gmm;

import java.util.Calendar;
import java.util.TimeZone;

/** 기기의 현지 시각으로 배경만 고른다. 서버의 출석·보상 시간과는 무관하다. */
public final class LocalTimeTheme {
  private LocalTimeTheme() {}

  public enum Phase {
    MORNING("morning"),
    DAY("day"),
    EVENING("evening"),
    NIGHT("night");

    public final String assetKey;

    Phase(String assetKey) {
      this.assetKey = assetKey;
    }
  }

  public static final class State {
    public final Phase phase;
    public final String zoneId;
    public final int localHour;

    private State(Phase phase, String zoneId, int localHour) {
      this.phase = phase;
      this.zoneId = zoneId;
      this.localHour = localHour;
    }
  }

  /** 시간대를 캐시하지 않아 여행이나 설정 변경을 다음 판정에 바로 반영한다. */
  public static State resolve(long epochMillis) {
    return resolve(epochMillis, TimeZone.getDefault());
  }

  public static State resolve(long epochMillis, TimeZone zone) {
    TimeZone effective = zone == null ? TimeZone.getDefault() : zone;
    Calendar local = Calendar.getInstance(effective);
    local.setTimeInMillis(epochMillis);
    int hour = local.get(Calendar.HOUR_OF_DAY);
    Phase phase =
        hour >= 5 && hour < 10
            ? Phase.MORNING
            : hour >= 10 && hour < 17
                ? Phase.DAY
                : hour >= 17 && hour < 20 ? Phase.EVENING : Phase.NIGHT;
    return new State(phase, effective.getID(), hour);
  }

  /** Handler의 상대 지연만 계산한다. 실제 판정은 매번 현재 벽시계를 다시 읽는다. */
  public static long millisUntilNextMinute(long epochMillis) {
    return 60_000L - Math.floorMod(epochMillis, 60_000L);
  }
}

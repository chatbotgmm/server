package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import java.util.List;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NativeBattleLogTest {
  JSONObject ref(int side) {
    return MainActivity.obj("side", side, "slot", 0, "key", "team" + side, "id", "D1");
  }

  @Test
  public void identicalNamesUseEventTargetForDamageAndActorForSkill() {
    String raw = "같은 갈매미가 20 피해를 입었어요.";
    JSONArray events =
        new JSONArray()
            .put(
                MainActivity.obj("logIndex", 8, "kind", "skill", "actor", ref(1), "target", ref(0)))
            .put(
                MainActivity.obj(
                    "logIndex", 9, "kind", "damage", "actor", ref(1), "target", ref(0)));
    Spannable result = NativeBattleLog.format(null, List.of("같은 갈매미의 기술!", raw), events, 9);
    assertEquals("[상대] 같은 갈매미의 기술!\n[나] " + raw, result.toString());
    assertEquals(2, result.getSpans(0, result.length(), BackgroundColorSpan.class).length);
  }

  @Test
  public void explicitPrefixIsPreservedAndUntaggedTextIsNeverGuessed() {
    String raw = "[상대] 같은 갈매미의 공격!";
    JSONArray events =
        new JSONArray().put(MainActivity.obj("logIndex", 2, "kind", "skill", "actor", ref(0)));
    assertEquals(raw, NativeBattleLog.format(null, List.of(raw), events, 2).toString());
    String unknown = "같은 갈매미의 공격!";
    assertEquals(
        unknown, NativeBattleLog.format(null, List.of(unknown), new JSONArray(), 3).toString());
  }

  @Test
  public void onlyTwoLatestLinesAreShownAndMixedSideLineHasNoInventedTag() {
    JSONArray events =
        new JSONArray()
            .put(MainActivity.obj("logIndex", 5, "kind", "heal", "target", ref(0)))
            .put(MainActivity.obj("logIndex", 5, "kind", "heal", "target", ref(1)));
    assertEquals(
        "이전 기록\n두 팀의 체력이 회복됐어요.",
        NativeBattleLog.format(null, List.of("오래된 기록", "이전 기록", "두 팀의 체력이 회복됐어요."), events, 5)
            .toString());
  }

  boolean hasColor(Spannable text, String token, int color) {
    int from = text.toString().indexOf(token), to = from + token.length();
    for (ForegroundColorSpan span : text.getSpans(from, to, ForegroundColorSpan.class)) {
      if (span.getForegroundColor() == color
          && text.getSpanStart(span) <= from
          && text.getSpanEnd(span) >= to) return true;
    }
    return false;
  }

  @Test
  public void previousEventKeepsBodySizeAndReadableInk() {
    Spannable text =
        NativeBattleLog.format(null, List.of("공격을 준비했어요.", "방어가 올랐어요."), new JSONArray(), 1);
    assertEquals(0, text.getSpans(0, text.length(), RelativeSizeSpan.class).length);
    assertTrue(hasColor(text, "공격을 준비했어요.", MainActivity.INK));
    assertTrue(hasColor(text, "방어가 올랐어요.", MainActivity.INK));
  }

  @Test
  public void damageHealingNumbersAndStatesAreEmphasizedInLiveAndHistoryLogs() {
    String damage = "갈매미가 화상으로 27 피해를 입었어요.", healed = "갈매미의 체력이 18 회복됐어요.";
    JSONArray events =
        new JSONArray()
            .put(MainActivity.obj("logIndex", 1, "kind", "damage", "target", ref(0)))
            .put(MainActivity.obj("logIndex", 2, "kind", "heal", "target", ref(0)));
    Spannable live = NativeBattleLog.format(null, List.of(damage, healed), events, 2);
    assertTrue(live.toString().contains(damage));
    assertTrue(live.toString().contains(healed));
    assertTrue(hasColor(live, "27", NativeBattleLog.DAMAGE));
    assertTrue(hasColor(live, "화상", NativeBattleLog.DAMAGE));
    assertTrue(hasColor(live, "18", NativeBattleLog.HEAL));
    assertTrue(hasColor(live, "회복", NativeBattleLog.HEAL));
    String raw = "빙결 · 잠 · 묶임 · 땅콩 폭주, 공격 +12%, 3턴";
    Spannable history = NativeBattleLog.historyLine(null, raw, new JSONArray(), 3);
    assertEquals(raw, history.toString());
    for (String state : List.of("빙결", "잠", "묶임", "땅콩 폭주"))
      assertTrue(hasColor(history, state, NativeBattleLog.statusColor(state)));
    assertTrue(hasColor(history, "+12%", NativeBattleLog.BUFF));
    assertTrue(hasColor(history, "3", NativeBattleLog.NUMBER));
    assertTrue(history.getSpans(0, history.length(), StyleSpan.class).length > 0);
  }
}

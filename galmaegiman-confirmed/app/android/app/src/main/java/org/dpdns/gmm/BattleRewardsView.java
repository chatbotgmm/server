package org.dpdns.gmm;

import android.view.*;
import android.widget.*;
import java.util.function.Consumer;
import org.json.*;

/** 서버에서 확정된 성장·체력·랭크와 다음 행동을 함께 안내한다. */
final class BattleRewardsView {
  static ScrollView create(MainActivity h, JSONObject battle) {
    return create(h, battle, null);
  }

  static ScrollView create(MainActivity h, JSONObject battle, Consumer<String> openGrowth) {
    return create(h, battle, openGrowth, null);
  }

  static ScrollView create(
      MainActivity h, JSONObject battle, Consumer<String> openGrowth, Runnable openTeam) {
    MenuKit k = new MenuKit(h);
    LinearLayout body = k.column();
    body.setPadding(h.dp(12), h.dp(6), h.dp(12), h.dp(6));
    JSONObject result = battle.optJSONObject("result");
    if (result == null) result = new JSONObject();
    boolean won = battle.optInt("winner", 1) == 0,
        practice = battle.optString("mode").equals("practice"),
        fled = result.optBoolean("left", result.optBoolean("fled", battle.optBoolean("fled")));
    TextView title = h.text(fled ? "전투에서 물러났어요" : won ? "승리!" : "다시 준비해요", 28);
    title.setTypeface(h.headline);
    title.setTextColor(JournalTheme.TEAL);
    title.setGravity(Gravity.CENTER);
    body.addView(title);
    TextView summary =
        k.note(
            practice
                ? "연습 대결 · 성장과 아이템은 저장되지 않아요."
                : result.has("point")
                    ? "대전 결과"
                    : "경험치 +"
                        + result.optInt("xp")
                        + (won ? " · 스테이지 클리어" : " · 체력을 회복하고 다시 도전해요"));
    summary.setGravity(Gravity.CENTER);
    body.addView(summary);
    if (fled && battle.optString("mode").equals("story"))
      body.addView(k.notice("스토리 체력은 유지돼요. 치료약이나 자연 회복으로 준비할 수 있어요."));
    if (result.has("point")) {
      TextView rank =
          h.text(
              result.optString("tier")
                  + " · "
                  + result.optInt("point")
                  + "점 ("
                  + (result.optInt("change") >= 0 ? "+" : "")
                  + result.optInt("change")
                  + ")",
              20);
      rank.setGravity(Gravity.CENTER);
      rank.setTextColor(
          result.optInt("change") >= 0 ? NativeBattleLog.HEAL : NativeBattleLog.DAMAGE);
      body.addView(rank);
    }
    body.addView(debrief(h, k, NativeBattleDebrief.from(battle), openTeam));
    JSONArray growth = h.list(result, "growth");
    for (int i = 0; i < growth.length(); i++) {
      JSONObject u = growth.optJSONObject(i);
      if (u == null) continue;
      LinearLayout row = k.paper(), head = k.row();
      head.addView(k.image(u.optString("image"), 56, 56));
      LinearLayout words = k.column();
      TextView name = h.text(u.optString("name"), 18);
      name.setTypeface(h.headline);
      words.addView(name);
      boolean up = u.optInt("level") > u.optInt("fromLevel");
      words.addView(
          k.note(
              up
                  ? "Lv" + u.optInt("fromLevel") + " → " + u.optInt("level") + " · 체력 완전 회복"
                  : "Lv" + u.optInt("level") + " · 경험치 +" + u.optInt("gained")));
      head.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(head);
      k.progress(
          row,
          "경험치 " + u.optInt("xp") + " / " + u.optInt("xpNext"),
          u.optInt("xp"),
          u.optInt("xpNext"),
          JournalTheme.BRASS);
      k.progress(
          row,
          "체력 " + u.optInt("hp") + " / " + u.optInt("maxHp"),
          u.optInt("hp"),
          u.optInt("maxHp"),
          0xff4b947e);
      boolean learn = u.optBoolean("canLearn") || h.list(u, "learn").length() > 0;
      boolean promo = u.optBoolean("canPromote") || h.list(u, "promo").length() > 0;
      boolean evolve = u.optBoolean("canEvolve") || h.list(u, "evolve").length() > 0;
      if (learn || promo || evolve) {
        String label = evolve ? " 승진하기" : promo ? "전용 기술 배우기" : "새 기술 배우기";
        if (openGrowth == null) row.addView(k.notice(label));
        else {
          String key = u.optString("key");
          Button button =
              k.action(evolve ? "u-emblem" : "learn", label.trim(), () -> openGrowth.accept(key));
          button.setBackground(new GameButtonDrawable(h, JournalTheme.PRIMARY));
          button.setTextColor(JournalTheme.controlText(JournalTheme.PRIMARY));
          button.setTag("reward-growth-" + key);
          row.addView(button, new LinearLayout.LayoutParams(-1, h.dp(48)));
        }
      }
      body.addView(row);
    }
    return k.scroll(body);
  }

  /** 기록은 접어 두어 성장·보상과 다음 행동을 밀어내지 않는다. */
  private static LinearLayout debrief(
      MainActivity h, MenuKit k, NativeBattleDebrief report, Runnable openTeam) {
    LinearLayout card = k.paper();
    card.setTag("reward-debrief");
    TextView title = h.text("전투 돌아보기", 19);
    title.setTypeface(h.headline);
    card.addView(title);
    card.addView(k.note(report.preview()));
    LinearLayout details = k.column();
    details.setTag("reward-debrief-details");
    details.setVisibility(View.GONE);
    Button toggle = k.action(null, "기록과 전략 펼치기 ▾", () -> {});
    toggle.setTag("reward-debrief-toggle");
    toggle.setMinHeight(h.dp(48));
    toggle.setOnClickListener(
        v -> {
          boolean show = details.getVisibility() != View.VISIBLE;
          details.setVisibility(show ? View.VISIBLE : View.GONE);
          toggle.setText(show ? "전투 돌아보기 접기 ▴" : "기록과 전략 펼치기 ▾");
          toggle.setContentDescription("전투 돌아보기 · " + (show ? "펼쳐짐 · 접기" : "접힘 · 펼치기"));
        });
    toggle.setContentDescription("전투 돌아보기 · 접힘 · 펼치기");
    card.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
    details.addView(k.note(report.coverage()));
    for (int side = 0; side < 2; side++) {
      TextView heading = h.text(side == 0 ? "내 팀의 기록" : "상대 팀의 기록", 17);
      heading.setTypeface(h.headline);
      heading.setPadding(0, h.dp(8), 0, h.dp(3));
      details.addView(heading);
      for (String line : report.facts(side)) {
        TextView fact = NativeBattleSheets.prose(h, "• " + line, 15);
        fact.setText(NativeBattleLog.historyLine(h, "• " + line, null, -1));
        details.addView(fact);
      }
      String names =
          report.augments[side].isEmpty()
              ? "선택한 증강체 기록이 없어요."
              : "선택한 증강체 · " + String.join(" · ", report.augments[side]);
      TextView augs = NativeBattleSheets.prose(h, names, 15);
      augs.setPadding(0, h.dp(4), 0, h.dp(3));
      details.addView(augs);
    }
    details.addView(k.note("선택한 증강체가 모두 수치로 기록되지는 않아요. 직접 확인된 발동만 합산했어요."));
    TextView next = h.text("다음 전투 준비", 17);
    next.setTypeface(h.headline);
    next.setPadding(0, h.dp(8), 0, h.dp(3));
    details.addView(next);
    for (String suggestion : report.suggestions())
      details.addView(NativeBattleSheets.prose(h, "• " + suggestion, 15));
    if (openTeam != null) {
      Button team = k.action("m-party", "전투 편성 확인", openTeam);
      team.setTag("reward-team");
      team.setMinHeight(h.dp(48));
      details.addView(team, new LinearLayout.LayoutParams(-1, -2));
    }
    card.addView(details);
    return card;
  }
}

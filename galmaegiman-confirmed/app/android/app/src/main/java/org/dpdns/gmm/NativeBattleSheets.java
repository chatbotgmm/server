package org.dpdns.gmm;

import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 전투 기록·상대 능력치·기술·증강체를 현장 기록부의 정보면으로 제공한다. */
final class NativeBattleSheets {
  static void option(MainActivity h, LinearLayout body, Button button) {
    button.setBackground(new GameButtonDrawable(h, JournalTheme.PAPER));
    button.setTextColor(JournalTheme.controlText(JournalTheme.PAPER));
    button.setMinHeight(h.dp(48));
    button.setMinimumHeight(h.dp(48));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, h.dp(3), 0, h.dp(3));
    body.addView(button, lp);
  }

  /** 보통은 기본 2×2 카드로 선택한다. 큰 글꼴·긴 서버 데이터만 넓은 선택지를 사용한다. */
  static void skills(BattleView v) {
    MainActivity h = v.host;
    LinearLayout body = column(h);
    body.setTag("battle-skills-modal");
    AlertDialog[] dialog = {null};
    Runnable close =
        () -> {
          if (dialog[0] != null) dialog[0].dismiss();
        };
    JSONArray skills = h.list(v.active(0), "skills");
    int columns = h.getResources().getConfiguration().fontScale >= 1.15f ? 1 : 2;
    LinearLayout row = null;
    int normal = 0;
    ArrayList<JSONObject> combos = new ArrayList<>();
    for (int i = 0; i < skills.length(); i++) {
      JSONObject skill = skills.optJSONObject(i);
      if (skill == null) continue;
      if (skill.optString("id").startsWith("combo-")) {
        combos.add(skill);
        continue;
      }
      if (normal++ % columns == 0) {
        row = new LinearLayout(h);
        body.addView(row);
      }
      SkillCardView card = new SkillCardView(v, skill, false, false, close);
      card.showCompleteEffect();
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
      row.addView(card, lp);
    }
    for (JSONObject skill : combos) {
      SkillCardView card = new SkillCardView(v, skill, false, false, close);
      card.showCompleteEffect();
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.setMargins(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
      body.addView(card, lp);
    }
    JSONArray legal = h.list(v.side(0), "legal");
    for (int i = 0; i < legal.length(); i++) {
      JSONObject action = legal.optJSONObject(i);
      if (action.optString("skillId").equals("struggle"))
        option(
            h,
            body,
            h.button(
                "몸통 박치기 · 위력 30",
                () -> {
                  close.run();
                  h.battleTurnFrom(v.battle, MainActivity.obj("action", action));
                }));
    }
    dialog[0] =
        new GameDialog(h)
            .setMaxWidthDp(760)
            .setTitle("기술 선택")
            .setScrollBody(body)
            .setNegativeButton("돌아가기", null)
            .show();
  }

  static void switches(BattleView v, boolean required, String augment) {
    MainActivity h = v.host;
    LinearLayout body = column(h);
    body.setTag("battle-switch-modal");
    AlertDialog[] dialog = {null};
    JSONArray fighters = h.list(v.side(0), "fighters"), legal = h.list(v.side(0), "legal");
    for (int i = 0; i < fighters.length(); i++) {
      int to = i;
      JSONObject fighter = fighters.optJSONObject(i);
      if (i == v.side(0).optInt("active")) continue;
      boolean can = required;
      for (int j = 0; j < legal.length(); j++) {
        JSONObject action = legal.optJSONObject(j);
        if (action.optString("kind").equals("switch") && action.optInt("to") == i) can = true;
      }
      boolean allowed = can && fighter.optInt("hp") > 0;
      option(
          h,
          body,
          h.button(
              fighter.optString("name")
                  + " · "
                  + fighter.optString("attr")
                  + "\n체력 "
                  + fighter.optInt("hp")
                  + " / "
                  + fighter.optInt("maxHp")
                  + (fighter.optInt("hp") <= 0
                      ? " · 전투불가"
                      : "\n"
                          + matchupLabel(fighter)
                          + " · 공격 "
                          + effectiveness(h, fighter, v.active(1))
                          + " · 방어 "
                          + defense(h, v.active(1), fighter)),
              () -> {
                if (!allowed) {
                  h.message(
                      fighter.optInt("hp") <= 0
                          ? "전투불가 상태예요. 부활 아이템을 사용해 주세요."
                          : "묶임 또는 기술 효과로 지금은 교체할 수 없어요.");
                  return;
                }
                if (dialog[0] != null) dialog[0].dismiss();
                if (required) {
                  JSONObject pick = MainActivity.obj("switchTo", to);
                  if (augment != null)
                    try {
                      pick.put("augment", augment);
                    } catch (JSONException ignored) {
                    }
                  h.battleTurnFrom(v.battle, MainActivity.obj("choose", pick));
                } else
                  h.battleTurnFrom(v.battle, 
                      MainActivity.obj("action", MainActivity.obj("kind", "switch", "to", to)));
              }));
    }
    if (body.getChildCount() == 0) body.addView(prose(h, "교체할 갈매미가 없어요.", 17));
    GameDialog builder =
        new GameDialog(h).setMaxWidthDp(680).setTitle("교체할 갈매미").setScrollBody(body);
    if (!required) builder.setNegativeButton("돌아가기", null);
    else builder.setCancelable(false);
    dialog[0] = builder.show();
  }

  static void items(BattleView v) {
    MainActivity h = v.host;
    LinearLayout body = column(h);
    body.setTag("battle-items-modal");
    AlertDialog[] dialog = {null};
    if (!v.battle.optString("mode").equals("story") && !NativeRealtime.isBattle(v.battle)) body.addView(prose(h, "스토리에서 사용할 수 있어요.", 18));
    else {
      int limit = v.battle.optInt("itemLimit", 0);
      body.addView(
          prose(
              h,
              (limit > 0
                      ? "이번 전투에 "
                          + Math.max(0, limit - v.side(0).optInt("itemsUsed"))
                          + "개 더 사용 가능 · "
                      : "")
                  + "사용하면 이번 턴 행동이 끝나요.",
              14));
      JSONArray items = h.list(v.battle, "items"), inventory = h.list(v.battle, "inventory");
      if (inventory.length() == 0 && !NativeRealtime.isBattle(v.battle)) inventory = h.list(h.state, "items");
      for (int i = 0; i < items.length(); i++) {
        JSONObject item = items.optJSONObject(i);
        int count = 0;
        for (int j = 0; j < inventory.length(); j++)
          if (inventory.optJSONObject(j).optString("id").equals(item.optString("id")))
            count = inventory.optJSONObject(j).optInt("count");
        int have = count;
        String why = item.isNull("reason") ? "" : item.optString("reason");
        option(
            h,
            body,
            h.button(
                item.optString("name")
                    + " ×"
                    + have
                    + "\n"
                    + itemEffect(item)
                    + (have <= 0 ? "\n가진 게 없어요." : why.isEmpty() ? "" : "\n" + why),
                () -> {
                  if (have <= 0) {
                    h.message("아이템이 없어요.");
                    return;
                  }
                  if (!why.isEmpty()) {
                    h.message(why);
                    return;
                  }
                  if (dialog[0] != null) dialog[0].dismiss();
                  JSONObject action =
                      MainActivity.obj("kind", "item", "item", item.optString("id"));
                  if (item.optInt("revive") > 0) {
                    JSONArray fighters = h.list(v.side(0), "fighters");
                    ArrayList<Integer> ids = new ArrayList<>();
                    ArrayList<String> names = new ArrayList<>();
                    for (int j = 0; j < fighters.length(); j++)
                      if (fighters.optJSONObject(j).optInt("hp") <= 0) {
                        ids.add(j);
                        names.add(fighters.optJSONObject(j).optString("name"));
                      }
                    new GameDialog(h)
                        .setTitle("회복할 갈매미")
                        .setItems(
                            names.toArray(new String[0]),
                            (d, which) -> {
                              try {
                                action.put("target", ids.get(which));
                              } catch (JSONException ignored) {
                              }
                              h.battleTurnFrom(v.battle, MainActivity.obj("action", action));
                            })
                        .show();
                  } else h.battleTurnFrom(v.battle, MainActivity.obj("action", action));
                }));
      }
    }
    dialog[0] =
        new GameDialog(h)
            .setMaxWidthDp(680)
            .setTitle("아이템")
            .setScrollBody(body)
            .setNegativeButton("돌아가기", null)
            .show();
  }

  static LinearLayout column(MainActivity h) {
    LinearLayout col = new LinearLayout(h);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setPadding(h.dp(12), h.dp(6), h.dp(12), h.dp(8));
    return col;
  }

  static TextView prose(MainActivity h, String text, int size) {
    TextView t = h.text(text, size);
    t.setTypeface(h.prose);
    t.setTextLocale(Locale.KOREAN);
    t.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
    t.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      t.setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
    return t;
  }

  static TextView heading(MainActivity h, String text, int size) {
    TextView t = h.text(text, size);
    t.setTypeface(h.headline);
    return t;
  }

  static void semanticColor(TextView text, int color) {
    android.text.SpannableString styled = new android.text.SpannableString(text.getText());
    styled.setSpan(
        new android.text.style.ForegroundColorSpan(color),
        0,
        styled.length(),
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    text.setText(styled);
    text.setTextColor(color);
  }

  static AlertDialog show(BattleView v, String title, LinearLayout content) {
    MainActivity h = v.host;
    content.setTag("battle-sheet-body");
    // 제목과 닫기는 고정하고 실제 본문만 공통 가용 높이 안에서 스크롤한다.
    return new GameDialog(h)
        .setMaxWidthDp(760)
        .setTitle(title)
        .setScrollBody(content)
        .setPositiveButton("닫기", null)
        .show();
  }

  static void history(BattleView v) {
    MainActivity h = v.host;
    LinearLayout col = column(h);
    JSONArray lines = h.list(v.battle, "history");
    int start = 0;
    if (lines.length() == 0) {
      lines = h.list(v.battle, "log");
      start = v.battle.optInt("displayLogStart");
    }
    JSONArray events = h.list(v.battle, "displayEvents");
    if (lines.length() == 0) col.addView(prose(h, "아직 전투 기록이 없어요.", 16));
    for (int i = 0; i < lines.length(); i++) {
      TextView line = prose(h, "", 17);
      line.setText(NativeBattleLog.historyLine(h, lines.optString(i), events, start + i));
      // 본문은 밝게 유지하고 기록지의 옅은 잉크 줄로만 읽는 행을 구분한다.
      line.setBackgroundColor(i % 2 == 0 ? 0x128e6b40 : android.graphics.Color.TRANSPARENT);
      col.addView(line);
    }
    show(v, "전투 기록 · " + v.battle.optInt("turn") + "턴", col);
  }

  static String role(JSONObject f) {
    String role = f.optString("role");
    return role.equals("ATTACK")
        ? "공격"
        : role.equals("DEFENSE") ? "수비" : role.equals("UTILITY") ? "유틸" : "";
  }

  static void fighter(BattleView v, int side) {
    MainActivity h = v.host;
    JSONObject f = v.active(side);
    LinearLayout col = column(h);
    JSONObject match = v.battle.optJSONObject("matchInfo");
    if (side == 1 && match != null)
      col.addView(
          prose(
              h,
              match.optString("foeName")
                  + (match.optString("foeTier").isEmpty() ? "" : " · " + match.optString("foeTier"))
                  + (match.optBoolean("bot") ? " · AI 상대" : ""),
              16));
    col.addView(
        prose(
            h,
            "Lv"
                + f.optInt("level")
                + " · "
                + f.optString("attr")
                + (f.isNull("extraAttr") ? "" : " + " + f.optString("extraAttr"))
                + (role(f).isEmpty() ? "" : " · " + role(f)),
            16));
    col.addView(prose(h, "체력 " + f.optInt("hp") + " / " + f.optInt("maxHp"), 17));
    JSONArray effects = BattleEffects.current(f);
    for (int i = 0; i < effects.length(); i++) {
      TextView effect = prose(h, BattleEffects.label(effects.optJSONObject(i)), 16);
      semanticColor(effect, BattleEffects.color(effects.optJSONObject(i)));
      col.addView(effect);
    }
    JSONObject stats = f.optJSONObject("base");
    JSONObject own = v.active(0).optJSONObject("base");
    if (side == 1 && stats != null && own != null) {
      int foeSpeed = (int) Math.round(stats.optDouble("spd")),
          ownSpeed = (int) Math.round(own.optDouble("spd"));
      col.addView(
          prose(
              h,
              "기본 속도 · 내 "
                  + ownSpeed
                  + " / 상대 "
                  + foeSpeed
                  + (foeSpeed == ownSpeed ? " · 속도가 같아요." : ""),
              16));
      col.addView(prose(h, "행동 순서는 속도 효과와 우선권에 따라 달라져요.", 15));
    }
    if (stats != null) {
      col.addView(heading(h, "기본 능력치", 18));
      String[] ids = {"patk", "pdef", "matk", "mdef", "spd"},
          names = {"물리 공격", "물리 방어", "마법 공격", "마법 방어", "속도"};
      for (int i = 0; i < ids.length; i++)
        col.addView(prose(h, names[i] + " " + Math.round(stats.optDouble(ids[i])), 16));
    }
    col.addView(heading(h, "기술", 20));
    JSONArray skills = h.list(f, "skills");
    for (int i = 0; i < skills.length(); i++) {
      JSONObject sk = skills.optJSONObject(i);
      LinearLayout card = column(h);
      card.setBackground(new GamePaperDrawable(h));
      card.addView(heading(h, sk.optString("name"), 18));
      card.addView(prose(h, SkillCardView.meta(sk) + "\n" + sk.optString("desc"), 15));
      col.addView(card);
    }
    appendAugments(v, col, side);
    show(v, (side == 0 ? "내 정보 · " : "상대 정보 · ") + f.optString("name"), col);
  }

  static void effects(BattleView v, int side) {
    MainActivity h = v.host;
    LinearLayout col = column(h);
    JSONArray current = BattleEffects.current(v.active(side));
    if (current.length() == 0) col.addView(prose(h, "현재 적용 중인 효과가 없어요.", 17));
    for (int i = 0; i < current.length(); i++) {
      JSONObject effect = current.optJSONObject(i);
      TextView text = prose(h, BattleEffects.label(effect), 18);
      semanticColor(text, BattleEffects.color(effect));
      text.setBackground(new GamePaperDrawable(h));
      col.addView(text, new LinearLayout.LayoutParams(-1, -2));
    }
    col.addView(prose(h, "남은 턴은 전투 판정이 끝날 때 갱신돼요. 지속 효과는 조건이 끝날 때 해제돼요.", 16));
    show(v, (side == 0 ? "내 " : "상대 ") + "현재 효과 · " + v.active(side).optString("name"), col);
  }

  static void appendAugments(BattleView v, LinearLayout col, int side) {
    MainActivity h = v.host;
    JSONArray augs = h.list(v.side(side), "augments");
    if (augs.length() == 0) return;
    col.addView(heading(h, "증강체", 20));
    for (int i = 0; i < augs.length(); i++) {
      JSONObject aug = augs.optJSONObject(i);
      LinearLayout card = column(h);
      card.setBackground(new GamePaperDrawable(h));
      TextView name =
          heading(
              h,
              aug.optString("name")
                  + " · "
                  + NativeAugmentView.kindName(NativeAugmentView.kind(aug.optString("id"))),
              18);
      semanticColor(name, NativeAugmentView.color(NativeAugmentView.kind(aug.optString("id"))));
      card.addView(name);
      card.addView(prose(h, aug.optString("desc"), 16));
      col.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }
  }

  static void augments(BattleView v) {
    MainActivity h = v.host;
    LinearLayout col = column(h);
    for (int side = 0; side < 2; side++) {
      col.addView(heading(h, side == 0 ? "우리 팀" : "상대 팀", 20));
      if (h.list(v.side(side), "augments").length() == 0)
        col.addView(prose(h, "장착한 증강체가 없어요.", 16));
      else appendAugments(v, col, side);
    }
    show(v, "장착 증강체", col);
  }

  static String effectiveness(MainActivity h, JSONObject from, JSONObject to) {
    JSONObject matchup = from.optJSONObject("matchup");
    JSONObject attack = matchup == null ? null : matchup.optJSONObject("attack");
    if (attack != null) return relation(attack.optDouble("multiplier", 1));
    JSONObject chart = h.state.optJSONObject("typeChart");
    if (chart == null) return "보통";
    String a = from.optString("attrCode"), b = to.optString("attrCode");
    JSONArray strong = h.list(chart.optJSONObject(a), "strong"),
        weak = h.list(chart.optJSONObject(b), "strong");
    for (int i = 0; i < strong.length(); i++) if (b.equals(strong.optString(i))) return "유리";
    for (int i = 0; i < weak.length(); i++) if (a.equals(weak.optString(i))) return "불리";
    return "보통";
  }

  static String relation(double multiplier) {
    return multiplier > 1 ? "유리" : multiplier < 1 ? "불리" : "보통";
  }

  static String defense(MainActivity h, JSONObject from, JSONObject to) {
    JSONObject matchup = to.optJSONObject("matchup");
    JSONObject received = matchup == null ? null : matchup.optJSONObject("defense");
    String attack =
        received == null
            ? effectiveness(h, from, to)
            : relation(received.optDouble("multiplier", 1));
    return attack.equals("유리") ? "불리" : attack.equals("불리") ? "유리" : "보통";
  }

  static String matchupLabel(JSONObject f) {
    return f.optJSONObject("matchup") == null ? "기본 상성" : "현재 상성";
  }

  static String itemEffect(JSONObject item) {
    return item.optInt("revive") > 0
        ? "쓰러진 갈매미를 체력 " + item.optInt("revive") + "%로 부활"
        : item.optInt("heal") > 0
            ? "체력 "
                + item.optInt("heal")
                + "% 회복"
                + (item.optBoolean("cleanse") ? " · 상태 이상 해제" : "")
            : item.optBoolean("cleanse") ? "상태 이상 해제" : item.optString("desc");
  }
}

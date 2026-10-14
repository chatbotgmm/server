package org.dpdns.gmm;

import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 웹 전투의 기록·상대 능력치·기술·증강체 안내를 네이티브 종이 시트로 제공한다. */
final class NativeBattleSheets {
  static LinearLayout column(MainActivity h) {
    LinearLayout col = new LinearLayout(h);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setPadding(h.dp(12), h.dp(6), h.dp(12), h.dp(8));
    return col;
  }

  static TextView prose(MainActivity h, String text, int size) {
    TextView t = h.text(text, size);
    t.setTypeface(h.prose);
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
    ScrollView scroll = new ScrollView(h);
    scroll.addView(content);
    int maxHeight =
        Math.max(
            h.dp(120),
            (int) h.safeBounds(h.root.getWidth(), h.root.getHeight()).height() - h.dp(150));
    scroll.setLayoutParams(new ViewGroup.LayoutParams(-1, maxHeight));
    FrameLayout wrap = new FrameLayout(h);
    wrap.addView(scroll, new FrameLayout.LayoutParams(-1, maxHeight));
    AlertDialog dialog =
        new GameDialog(h).setTitle(title).setView(wrap).setPositiveButton("닫기", null).show();
    if (dialog.getWindow() != null)
      dialog
          .getWindow()
          .setLayout(
              Math.min(
                  h.dp(760),
                  (int) (h.safeBounds(h.root.getWidth(), h.root.getHeight()).width() * .94f)),
              -2);
    return dialog;
  }

  static void skill(BattleView v, JSONObject sk) {
    MainActivity h = v.host;
    LinearLayout col = column(h);
    col.addView(prose(h, SkillCardView.kind(sk) + " · " + SkillCardView.meta(sk), 16));
    col.addView(prose(h, sk.optString("desc"), 18));
    String reuse =
        sk.optString("id").startsWith("combo-")
            ? "전투당 1회"
            : sk.optInt("power") > 0 ? "연속 사용 가능" : "효과가 적용 중일 때는 사용이 제한돼요.";
    col.addView(prose(h, "재사용 · " + reuse, 16));
    String why = SkillCardView.reason(v, sk);
    TextView state = prose(h, why.isEmpty() ? "지금 사용할 수 있어요." : "사용 불가 · " + why, 16);
    semanticColor(state, why.isEmpty() ? 0xff397953 : 0xffad4839);
    col.addView(state);
    if (sk.optString("id").startsWith("combo-"))
      col.addView(prose(h, "같은 시너지의 살아 있는 동료가 필요해요. 우리 팀은 이 전투에서 한 번만 사용할 수 있어요.", 15));
    show(v, sk.optString("name"), col);
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
      line.setBackground(i % 2 == 0 ? new GamePaperDrawable(h) : null);
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
              (foeSpeed > ownSpeed ? "상대가 더 빨라요." : foeSpeed < ownSpeed ? "내가 더 빨라요." : "속도가 같아요.")
                  + " · 내 속도 "
                  + ownSpeed,
              16));
    }
    if (stats != null) {
      String[] ids = {"patk", "pdef", "matk", "mdef", "spd"},
          names = {"물리 공격", "물리 방어", "마법 공격", "마법 방어", "속도"};
      for (int i = 0; i < ids.length; i++)
        col.addView(prose(h, names[i] + " " + Math.round(stats.optDouble(ids[i])), 16));
    }
    col.addView(h.text("기술", 20));
    JSONArray skills = h.list(f, "skills");
    for (int i = 0; i < skills.length(); i++) {
      JSONObject sk = skills.optJSONObject(i);
      LinearLayout card = column(h);
      card.setBackground(new GamePaperDrawable(h));
      card.addView(h.text(sk.optString("name"), 18));
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
    col.addView(h.text("증강체", 20));
    for (int i = 0; i < augs.length(); i++) {
      JSONObject aug = augs.optJSONObject(i);
      TextView name =
          h.text(
              aug.optString("name")
                  + " · "
                  + NativeAugmentView.kindName(NativeAugmentView.kind(aug.optString("id"))),
              18);
      semanticColor(name, NativeAugmentView.color(NativeAugmentView.kind(aug.optString("id"))));
      col.addView(name);
      col.addView(prose(h, aug.optString("desc"), 16));
    }
  }

  static void augments(BattleView v) {
    MainActivity h = v.host;
    LinearLayout col = column(h);
    for (int side = 0; side < 2; side++) {
      col.addView(h.text(side == 0 ? "우리 팀" : "상대 팀", 20));
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

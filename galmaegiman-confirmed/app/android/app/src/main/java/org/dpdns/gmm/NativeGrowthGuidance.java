package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 성장 상태를 스토리·편성·상세에서 같은 말과 표시로 안내한다. 수치는 서버 상태를 사용한다. */
final class NativeGrowthGuidance {
  final MainActivity h;
  final MenuKit k;

  NativeGrowthGuidance(MainActivity host, MenuKit kit) {
    h = host;
    k = kit;
  }

  static String badge(JSONObject u) {
    if (u.optJSONArray("evolve") != null && u.optJSONArray("evolve").length() > 0) return "↑ 승진 가능";
    if (u.optJSONArray("promo") != null && u.optJSONArray("promo").length() > 0) return "전용 기술 배우기";
    if (u.optJSONArray("learn") != null && u.optJSONArray("learn").length() > 0) {
      int left = u.optInt("learnLeft", 0);
      return "새 기술" + (left > 0 ? " · " + left + "개 더" : " 배우기");
    }
    return "기술·승진 보기 ›";
  }

  /** 서버가 제공한 실제 선택지만 안내한다. 회복은 보유 체력으로 판단한다. */
  static String actionSummary(JSONObject unit) {
    ArrayList<String> available = new ArrayList<>();
    if (unit.optJSONArray("evolve") != null && unit.optJSONArray("evolve").length() > 0)
      available.add("승진 가능");
    if (unit.optJSONArray("promo") != null && unit.optJSONArray("promo").length() > 0)
      available.add("전용 기술 배우기");
    if (unit.optJSONArray("learn") != null && unit.optJSONArray("learn").length() > 0)
      available.add("새 기술 " + Math.max(1, unit.optInt("learnLeft", 1)) + "개 배우기");
    if (unit.optInt("hp") < unit.optInt("maxHp")) available.add("회복 필요");
    if (!available.isEmpty()) return android.text.TextUtils.join(" · ", available);
    int next = unit.optInt("nextLearnLevel");
    return next > 0 ? "다음 새 기술 · Lv" + next : "지금은 기술과 체력이 준비됐어요.";
  }

  void meter(LinearLayout parent, int value, int max, int color, int height) {
    ProgressBar bar = new ProgressBar(h, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(Math.max(1, max));
    bar.setProgress(Math.min(Math.max(0, value), Math.max(1, max)));
    bar.setProgressTintList(ColorStateList.valueOf(color));
    bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xffe5d9c0));
    parent.addView(bar, new LinearLayout.LayoutParams(-1, h.dp(height)));
  }

  LinearLayout storyUnit(JSONObject u, int order, Runnable open, Runnable reorder) {
    boolean compact = h.compact();
    LinearLayout card = k.paper(), row = k.row(), info = k.column();
    card.setTag("story-team-unit-" + u.optString("key"));
    card.setPadding(h.dp(7), h.dp(4), h.dp(7), h.dp(4));
    row.addView(k.image(u.optString("image"), compact ? 43 : 70, compact ? 48 : 76));
    LinearLayout name = k.row();
    TextView title = h.text(u.optString("name"), compact ? 13 : 18);
    title.setTypeface(h.headline);
    title.setMaxLines(1);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    name.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView index = h.text(order == 0 ? "선봉" : (order + 1) + "번", compact ? 12 : 13);
    index.setTextColor(0xff8b5520);
    name.addView(index);
    info.addView(name);
    LinearLayout meta = k.row();
    TextView level =
        k.note(
            "Lv"
                + u.optInt("level")
                + (u.optInt("stage") > 0 ? " · " + "★".repeat(u.optInt("stage")) : ""));
    level.setTextSize(compact ? 12 : 14);
    meta.addView(level);
    TextView attr = k.chip(u.optString("attr"), u.optString("attrCode"));
    attr.setTextSize(compact ? 12 : 13);
    meta.addView(attr);
    TextView hp = k.note(u.optInt("hp") + "/" + u.optInt("maxHp"));
    hp.setTextSize(compact ? 12 : 13);
    hp.setGravity(Gravity.RIGHT);
    meta.addView(hp, new LinearLayout.LayoutParams(0, -2, 1));
    info.addView(meta);
    meter(info, u.optInt("hp"), u.optInt("maxHp"), 0xff51c98b, compact ? 5 : 8);
    LinearLayout growth = k.row();
    TextView xp = k.note("경험치 " + u.optInt("xp") + "/" + u.optInt("xpNext"));
    xp.setTextSize(compact ? 12 : 13);
    growth.addView(xp, new LinearLayout.LayoutParams(0, -2, 1));
    TextView notice = k.note(badge(u));
    notice.setTextSize(compact ? 12 : 13);
    notice.setTextColor(
        h.list(u, "evolve").length() > 0 || h.list(u, "promo").length() > 0
            ? 0xff9d5914
            : 0xff1d7165);
    growth.addView(notice);
    info.addView(growth);
    meter(info, u.optInt("xp"), u.optInt("xpNext"), 0xffe9a630, 3);
    row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
    card.addView(row);
    card.setContentDescription(
        (order == 0 ? "선봉 " : order + 1 + "번 ")
            + u.optString("name")
            + " "
            + badge(u)
            + ". 길게 눌러 순서를 바꿀 수 있어요.");
    card.setOnClickListener(v -> open.run());
    card.setOnLongClickListener(
        v -> {
          reorder.run();
          return true;
        });
    return card;
  }

  /** 같은 종류 두 마리는 시너지 한 마리로 계산한다. 웹·전투 엔진과 같은 규칙이다. */
  static List<String> comboLines(JSONObject definitions, Collection<JSONObject> units) {
    ArrayList<String> out = new ArrayList<>();
    if (definitions == null) return out;
    LinkedHashMap<String, String> unique = new LinkedHashMap<>();
    for (JSONObject u : units) unique.putIfAbsent(u.optString("id"), u.optString("name"));
    Iterator<String> keys = definitions.keys();
    while (keys.hasNext()) {
      JSONObject def = definitions.optJSONObject(keys.next());
      if (def == null) continue;
      JSONArray members = def.optJSONArray("members");
      if (members == null) continue;
      ArrayList<String> names = new ArrayList<>();
      for (int i = 0; i < members.length(); i++)
        if (unique.containsKey(members.optString(i))) names.add(unique.get(members.optString(i)));
      JSONObject skill = def.optJSONObject("skill");
      if (names.size() >= 2 && skill != null)
        out.add(
            "합동기 「" + skill.optString("name") + "」 · " + android.text.TextUtils.join(" + ", names));
    }
    return out;
  }

  TextView combos(Collection<JSONObject> units) {
    List<String> lines = comboLines(h.state.optJSONObject("synergies"), units);
    TextView hint =
        k.notice(
            lines.isEmpty()
                ? "합동기 · 같은 시너지의 다른 원형 2마리를 넣어 보세요."
                : android.text.TextUtils.join("\n", lines));
    hint.setTextSize(h.compact() ? 12 : 14);
    hint.setTag("team-combo-guidance");
    hint.setTextColor(lines.isEmpty() ? 0xff7d5f40 : 0xff785211);
    return hint;
  }

  String promotionNeed(JSONObject unit) {
    if (h.list(unit, "evolve").length() > 0) return "승진 조건 충족 · 재화 소모 없음";
    if (unit.optInt("stage") >= 3) return "최고 승진 단계";
    JSONObject need = unit.optJSONObject("promotionNeed");
    if (need == null) return "다음 승진: 해당 부 완료와 레벨 조건 필요";
    return "다음 승진: Lv"
        + need.optInt("level")
        + " + "
        + need.optInt("needParts", unit.optInt("stage") + 1)
        + "부 완료";
  }
}

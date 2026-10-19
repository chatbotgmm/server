package org.dpdns.gmm;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 성장 결정을 누르기 전에 서버의 기술·승진 값을 같은 종이 구성으로 비교한다. */
final class NativeGrowthComparison {
  final MainActivity h;
  final MenuKit k;

  NativeGrowthComparison(MainActivity host, MenuKit kit) {
    h = host;
    k = kit;
  }

  TextView heading(String text, int size) {
    TextView title = h.text(text, size);
    title.setTypeface(h.headline);
    return title;
  }

  TextView body(String text) {
    TextView label = k.note(text);
    label.setTextSize(h.compact() ? 14 : 16);
    label.setTextColor(MainActivity.INK);
    return label;
  }

  static boolean hasPreview(JSONObject award) {
    JSONObject preview = award.optJSONObject("preview");
    if (preview == null
        || !(preview.opt("fromStage") instanceof Number)
        || !(preview.opt("toStage") instanceof Number)
        || !(preview.opt("cost") instanceof Number)
        || !(preview.opt("restoresHp") instanceof Boolean)) return false;
    for (String field : new String[] {"beforeStats", "afterStats"}) {
      JSONObject stats = preview.optJSONObject(field);
      if (stats == null) return false;
      for (String key : new String[] {"hp", "patk", "pdef", "matk", "mdef", "spd"})
        if (!(stats.opt(key) instanceof Number)) return false;
    }
    for (String field : new String[] {"beforeSkills", "afterSkills", "beforePromo", "afterPromo"})
      if (preview.optJSONArray(field) == null) return false;
    return true;
  }

  LinearLayout skills(JSONObject old, JSONObject next) {
    LinearLayout content = k.column();
    content.setTag("growth-skill-comparison");
    content.addView(body("기술 4칸 중 하나를 바꿔요. 아래 두 기술을 비교한 뒤 확정해 주세요."));
    LinearLayout columns = k.row();
    columns.setGravity(Gravity.TOP);
    h.cell(columns, skill(old, "잊을 기술", "growth-skill-before"));
    h.cell(columns, skill(next, "배울 기술", "growth-skill-after"));
    content.addView(columns);
    content.addView(body("위력은 기술의 기본값이에요. 실제 피해는 상대·상성·증강체에 따라 달라져요."));
    return content;
  }

  LinearLayout skill(JSONObject skill, String role, String tag) {
    LinearLayout card = k.paper();
    card.setTag(tag);
    card.addView(body(role));
    TextView name = heading(skill.optString("name"), h.compact() ? 17 : 20);
    name.setTag(tag + "-name");
    card.addView(name);
    String attr = skill.optString("attr");
    String attrName =
        attr.isEmpty() ? "사용자 속성" : h.menus.attrName(h.state.optJSONObject("attributes"), attr);
    String cat =
        skill.optInt("power") <= 0
            ? "보조 기술"
            : skill.optString("cat").equals("phys")
                ? "물리"
                : skill.optString("cat").equals("mag") ? "마법" : "높은 공격 능력치 사용";
    card.addView(body("속성 · " + attrName));
    card.addView(body("분류 · " + cat));
    TextView power =
        body(
            skill.optInt("power") > 0
                ? "위력 · "
                    + skill.optInt("power")
                    + (skill.optInt("hits") > 1 ? " × " + skill.optInt("hits") + "회" : "")
                : "위력 · 직접 공격 없음");
    power.setTag(tag + "-power");
    card.addView(power);
    card.addView(heading("효과", h.compact() ? 14 : 16));
    String effect = SkillCardView.effect(skill);
    TextView description = body(effect.isEmpty() ? "추가 효과 없음" : effect);
    description.setTag(tag + "-effect");
    card.addView(description);
    card.addView(
        body(
            skill.optString("id").startsWith("combo-")
                ? "전투당 한 번 사용"
                : skill.optInt("power") > 0 ? "연속 사용 가능" : "같은 효과가 이미 적용 중이면 사용이 제한돼요."));
    return card;
  }

  LinearLayout promotion(JSONObject unit, JSONObject award) {
    JSONObject preview = award.optJSONObject("preview");
    LinearLayout content = k.column();
    content.setTag("growth-promotion-comparison");
    content.addView(heading(award.optString("name"), h.compact() ? 19 : 23));
    content.addView(body(award.optString("desc")));
    if (!hasPreview(award)) {
      content.addView(body("승진 미리보기를 받지 못했어요. 화면을 다시 열어 확인해 주세요."));
      return content;
    }
    content.addView(
        body("승진 " + preview.optInt("fromStage") + "단계 → " + preview.optInt("toStage") + "단계"));
    LinearLayout stats = k.paper();
    stats.setTag("growth-promotion-stats");
    stats.addView(heading("능력치 · 현재 → 승진 후", h.compact() ? 16 : 19));
    JSONObject before = preview.optJSONObject("beforeStats"),
        after = preview.optJSONObject("afterStats");
    String[] keys = {"hp", "patk", "pdef", "matk", "mdef", "spd"};
    String[] labels = {"최대 체력", "물리 공격", "물리 방어", "마법 공격", "마법 방어", "속도"};
    if (before != null && after != null) {
      for (int i = 0; i < keys.length; i++) {
        LinearLayout row = k.row();
        row.setGravity(Gravity.TOP);
        row.addView(body(labels[i]), new LinearLayout.LayoutParams(0, -2, .38f));
        int from = before.optInt(keys[i]), to = after.optInt(keys[i]);
        String text =
            String.format(Locale.KOREA, "%,d → %,d", from, to)
                + (to == from ? " · 유지" : String.format(Locale.KOREA, " (%+d)", to - from));
        SpannableString emphasized = new SpannableString(text);
        int arrow = text.indexOf('→');
        emphasized.setSpan(
            new StyleSpan(android.graphics.Typeface.BOLD),
            arrow + 1,
            text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView numbers = body(text);
        numbers.setText(emphasized);
        numbers.setTag("growth-promotion-stat-" + keys[i]);
        row.addView(numbers, new LinearLayout.LayoutParams(0, -2, .62f));
        stats.addView(row);
      }
    } else stats.addView(body("능력치 비교를 받지 못했어요."));
    content.addView(stats);
    content.addView(heading("승진 후 전용 기술", h.compact() ? 16 : 19));
    JSONArray beforePromo = h.list(preview, "beforePromo"),
        afterPromo = h.list(preview, "afterPromo");
    Set<String> known = new HashSet<>();
    for (int i = 0; i < beforePromo.length(); i++)
      known.add(beforePromo.optJSONObject(i).optString("id"));
    if (afterPromo.length() == 0) content.addView(body("새로 배울 전용 기술은 없어요. 배운 기술은 그대로 유지돼요."));
    else {
      content.addView(body("자동으로 배우지 않아요. 승진 후 기술 칸을 골라 별도로 배울 수 있어요."));
      for (int i = 0; i < afterPromo.length(); i++) {
        JSONObject skill = afterPromo.optJSONObject(i);
        content.addView(
            skill(
                skill,
                known.contains(skill.optString("id")) ? "계속 배울 수 있음" : "새로 배울 수 있음",
                "growth-promotion-skill-" + i));
      }
    }
    content.addView(body("현재 배운 기술 " + h.list(preview, "beforeSkills").length() + "/4칸은 유지돼요."));
    content.addView(
        body(preview.optInt("cost") == 0 ? "재화 소모 없음" : "재화 소모 " + preview.optInt("cost")));
    if (preview.optBoolean("restoresHp")) content.addView(body("승진하면 체력이 가득 회복돼요."));
    content.addView(body("직무 표창은 확정 후 바꿀 수 없어요."));
    return content;
  }
}

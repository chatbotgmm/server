package org.dpdns.gmm;

import android.view.Gravity;
import android.widget.*;
import java.util.function.Consumer;
import org.json.*;

/** 서버에서 확정된 성장·체력·랭크와 다음 행동을 함께 안내한다. */
final class BattleRewardsView {
  static ScrollView create(MainActivity h, JSONObject battle) {
    return create(h, battle, null);
  }

  static ScrollView create(MainActivity h, JSONObject battle, Consumer<String> openGrowth) {
    MenuKit k = new MenuKit(h);
    LinearLayout body = k.column();
    body.setPadding(h.dp(12), h.dp(6), h.dp(12), h.dp(6));
    JSONObject result = battle.optJSONObject("result");
    if (result == null) result = new JSONObject();
    boolean won = battle.optInt("winner", 1) == 0,
        practice = battle.optString("mode").equals("practice"),
        fled = result.optBoolean("left", result.optBoolean("fled", battle.optBoolean("fled")));
    TextView title = h.text(fled ? "전투에서 물러났어요" : won ? "승리!" : "다시 준비해요", 28);
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
      body.addView(rank);
    }
    JSONArray growth = h.list(result, "growth");
    for (int i = 0; i < growth.length(); i++) {
      JSONObject u = growth.optJSONObject(i);
      if (u == null) continue;
      LinearLayout row = k.paper(), head = k.row();
      head.addView(k.image(u.optString("image"), 56, 56));
      LinearLayout words = k.column();
      words.addView(h.text(u.optString("name"), 18));
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
          0xffe9a630);
      k.progress(
          row,
          "체력 " + u.optInt("hp") + " / " + u.optInt("maxHp"),
          u.optInt("hp"),
          u.optInt("maxHp"),
          0xff51c98b);
      boolean learn = u.optBoolean("canLearn") || h.list(u, "learn").length() > 0;
      boolean promo = u.optBoolean("canPromote") || h.list(u, "promo").length() > 0;
      boolean evolve = u.optBoolean("canEvolve") || h.list(u, "evolve").length() > 0;
      if (learn || promo || evolve) {
        String label = evolve ? " 승진하기" : promo ? "전용 기술 배우기" : "새 기술 배우기";
        if (openGrowth == null) row.addView(k.notice(label));
        else {
          String key = u.optString("key");
          Button button =
              k.action(evolve ? "u-defense" : "u-book", label.trim(), () -> openGrowth.accept(key));
          button.setTag("reward-growth-" + key);
          row.addView(button, new LinearLayout.LayoutParams(-1, h.dp(48)));
        }
      }
      body.addView(row);
    }
    return k.scroll(body);
  }
}

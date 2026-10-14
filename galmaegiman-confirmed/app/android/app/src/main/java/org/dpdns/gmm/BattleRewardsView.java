package org.dpdns.gmm;

import android.view.Gravity;
import android.widget.*;
import org.json.*;

/** 서버에서 확정된 보상을 성장·체력·랭크로 구분한다. */
final class BattleRewardsView {
  static ScrollView create(MainActivity h, JSONObject battle) {
    MenuKit k = new MenuKit(h);
    LinearLayout body = k.column();
    body.setPadding(h.dp(14), h.dp(8), h.dp(14), h.dp(8));
    JSONObject result = battle.optJSONObject("result");
    if (result == null) result = new JSONObject();
    boolean won = battle.optInt("winner", 1) == 0,
        practice = battle.optString("mode").equals("practice");
    TextView title = h.text(won ? "승리!" : "전투 종료", 30);
    title.setGravity(Gravity.CENTER);
    body.addView(title);
    TextView summary =
        k.note(
            practice
                ? "연습 대결 · 성장과 아이템은 저장되지 않아요."
                : result.has("point")
                    ? "대전 결과"
                    : "경험치 +" + result.optInt("xp") + (won ? " · 스테이지 클리어" : " · 다음 대결을 준비해요"));
    summary.setGravity(Gravity.CENTER);
    body.addView(summary);
    if (result.has("point"))
      body.addView(
          h.text(
              result.optString("tier")
                  + " · "
                  + result.optInt("point")
                  + "점 ("
                  + (result.optInt("change") >= 0 ? "+" : "")
                  + result.optInt("change")
                  + ")",
              20));
    JSONArray growth = h.list(result, "growth");
    for (int i = 0; i < growth.length(); i++) {
      JSONObject u = growth.optJSONObject(i);
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
      body.addView(row);
    }
    return k.scroll(body);
  }
}

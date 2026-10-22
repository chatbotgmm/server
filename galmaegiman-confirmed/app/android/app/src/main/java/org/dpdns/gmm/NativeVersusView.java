package org.dpdns.gmm;

import android.app.AlertDialog;
import android.view.Gravity;
import android.widget.*;
import org.json.*;

/** 서버가 정한 상대와 양쪽 편성을 대전 전에 보여 준다. 선택 제한 시간은 서버 기준이다. */
final class NativeVersusView {
  static void show(MainActivity h, JSONObject battle) {
    JSONObject match = battle.optJSONObject("matchInfo");
    if (match == null) return;
    MenuKit k = new MenuKit(h);
    LinearLayout body = k.column();
    body.setPadding(h.dp(12), h.dp(8), h.dp(12), h.dp(8));
    String mode = battle.optString("mode");
    TextView title = h.text(mode.equals("rank") ? "랭크게임" : "일반게임", 24);
    title.setTypeface(h.headline);
    title.setGravity(Gravity.CENTER);
    body.addView(title);
    LinearLayout teams = k.row();
    teams.setGravity(Gravity.TOP);
    JSONArray sides = h.list(battle, "teams");
    for (int side = 0; side < 2; side++) {
      if (side == 1) {
        TextView versus = h.text("VS", 25);
        versus.setTypeface(h.headline);
        versus.setTextColor(JournalTheme.TEAL);
        teams.addView(versus);
      }
      LinearLayout team = k.paper();
      TextView name =
          h.text(
              side == 0
                  ? h.me().optString("nickname", "내 수비대")
                  : match.optString("foeName", "상대 수비대"),
              18);
      name.setGravity(Gravity.CENTER);
      name.setTypeface(h.headline);
      team.addView(name);
      if (side == 1 && (!match.optString("foeTier").isEmpty() || match.optBoolean("bot"))) {
        TextView hint =
            k.note(match.optString("foeTier").isEmpty() ? "연습 팀과 겨뤄요" : match.optString("foeTier"));
        hint.setGravity(Gravity.CENTER);
        team.addView(hint);
      }
      LinearLayout portraits = k.row();
      team.addView(portraits);
      JSONArray fighters =
          sides.optJSONObject(side) == null
              ? new JSONArray()
              : h.list(sides.optJSONObject(side), "fighters");
      for (int i = 0; i < fighters.length(); i++) {
        JSONObject unit = fighters.optJSONObject(i);
        LinearLayout fighter = k.column();
        fighter.setGravity(Gravity.CENTER);
        ImageView portrait = k.image(unit.optString("image"), h.compact() ? 56 : 86, h.compact() ? 64 : 98);
        TemporaryUnitPresentation.apply(portrait, unit);
        portrait.setContentDescription(unit.optString("name") + TemporaryUnitPresentation.suffix(unit));
        fighter.addView(portrait);
        if (TemporaryUnitPresentation.temporary(unit)) fighter.addView(k.note(TemporaryUnitPresentation.label(unit)));
        TextView label = k.note("Lv" + unit.optInt("level"));
        label.setGravity(Gravity.CENTER);
        fighter.addView(label);
        portraits.addView(fighter, new LinearLayout.LayoutParams(0, -2, 1));
      }
      teams.addView(team, new LinearLayout.LayoutParams(0, -2, 1));
    }
    body.addView(teams);
    TextView hint =
        k.note(
            (mode.equals("rank") ? "중간에 나가면 패배로 처리돼요.\n" : "")
                + "3초 뒤 시작해요. 증강체 선택은 30초 안에 마쳐 주세요.");
    hint.setGravity(Gravity.CENTER);
    body.addView(hint);
    AlertDialog dialog =
        new GameDialog(h)
            .setView(k.scroll(body))
            .setCancelable(false)
            .setPositiveButton("대전 시작", null)
            .show();
    h.root.postDelayed(
        () -> {
          if (dialog.isShowing()) dialog.dismiss();
        },
        3000);
  }
}

package org.dpdns.gmm;

import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 출석·미션·순위·연습·설정·계정·안내를 각각의 원래 구성으로 표시한다. */
final class NativeExtras {
  final NativeMenus m;
  final MainActivity h;
  final MenuKit k;
  int guide;

  NativeExtras(NativeMenus m) {
    this.m = m;
    h = m.h;
    k = m.k;
  }

  TextView heading(String text, float size) {
    TextView title = h.text(text, size);
    title.setTypeface(h.headline);
    return title;
  }

  void daily() {
    h.base("출석체크", "daily");
    LinearLayout[] cols = k.split(.52f);
    m.fetch(
        "daily",
        r -> {
          JSONObject a = r.optJSONObject("attend");
          if (a != null) {
            LinearLayout panel = k.paper();
            cols[0].addView(panel);
            TextView heading =
                h.text(
                    a.optInt("week")
                        + "주째"
                        + (a.optInt("streak") > 0 ? " · " + a.optInt("streak") + "일 연속" : ""),
                    18);
            heading.setTypeface(h.headline);
            panel.addView(heading);
            TextView note = k.note("날마다·주마다 보상이 커져요 · 하루 빠지면 1일째부터");
            note.setTextSize(12);
            panel.addView(note);
            JSONArray rewards = h.list(a, "rewards");
            int current = a.optInt("dayInWeek", 1);
            boolean today = a.optBoolean("today");
            LinearLayout week = k.row();
            week.setGravity(Gravity.TOP);
            for (int i = 0; i < rewards.length(); i++) {
              int day = i + 1;
              boolean got = day < current || day == current && today;
              boolean active = day == current;
              LinearLayout card = k.column();
              card.setGravity(Gravity.CENTER_HORIZONTAL);
              card.setPadding(h.dp(2), h.dp(5), h.dp(2), h.dp(5));
              card.setBackground(active ? new GamePanelDrawable(h) : new GamePaperDrawable(h));
              if (day > current) card.setAlpha(.62f);
              TextView dayLabel = k.note(day + "일");
              dayLabel.setTextSize(12);
              card.addView(dayLabel);
              card.addView(
                  k.image("/duel/icons/" + (day == 7 ? "u-gift" : "u-ticket") + ".png", 25, 25));
              TextView reward = h.text(rewards.optInt(i) + "", 17);
              reward.setPadding(0, h.dp(4), 0, h.dp(4));
              reward.setSingleLine(true);
              card.addView(reward);
              TextView status = k.note(got ? active ? "✓ 받음" : "받음" : active ? "오늘" : "잠김");
              status.setTextSize(11);
              status.setTextColor(
                  got ? 0xff397953 : active ? JournalTheme.INK : JournalTheme.MUTED);
              card.addView(status);
              card.setContentDescription(
                  day + "일 " + status.getText() + " · 뽑기권 " + rewards.optInt(i) + "장");
              LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
              lp.setMargins(h.dp(2), h.dp(5), h.dp(2), h.dp(5));
              week.addView(card, lp);
            }
            panel.addView(week);
            String next =
                !today
                    ? "뽑기권 " + a.optInt("next") + "장"
                    : current < rewards.length()
                        ? "뽑기권 " + rewards.optInt(current) + "장"
                        : "다음 주 1일째 · 더 크게";
            LinearLayout preview = k.row();
            preview.addView(k.image("/duel/icons/u-ticket.png", 28, 28));
            LinearLayout words = k.column();
            TextView caption = k.note(today ? "내일 받을 보상" : "오늘 받을 보상");
            caption.setTextSize(12);
            words.addView(caption);
            words.addView(h.text(next, 17));
            preview.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            panel.addView(preview);
            Button attend =
                k.action(
                    "u-attend",
                    today ? "✓ 출석 완료 · 내일 또 와요" : "출석 · " + a.optInt("next") + "장 받기",
                    () -> h.mutate("attend", MainActivity.obj(), "daily"));
            NativeTutorialController.anchor(attend, "attend");
            attend.setEnabled(!today);
            panel.addView(attend, new LinearLayout.LayoutParams(-1, h.dp(48)));
          }
          JSONArray missions = h.list(r, "missions");
          int done = 0;
          for (int i = 0; i < missions.length(); i++)
            if (missions.optJSONObject(i).optBoolean("done")) done++;
          LinearLayout board = k.paper();
          cols[1].addView(board);
          board.addView(heading("오늘의 미션 · " + done + "/" + missions.length(), 18));
          TextView note = k.note("달성하면 자동 지급 · 한국 시간 밤 12시 초기화");
          note.setTextSize(12);
          board.addView(note);
          for (int i = 0; i < missions.length(); i++) {
            LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, -2);
            slot.topMargin = h.dp(5);
            board.addView(missionCard(missions.optJSONObject(i)), slot);
          }
        });
  }

  /** 보상은 숫자와 단위를 한 덩어리로 측정해 큰 글자에서도 세로로 갈라지지 않는다. */
  LinearLayout missionCard(JSONObject mission) {
    LinearLayout card = k.column();
    card.setTag("daily-mission-" + mission.optString("id", mission.optString("label")));
    card.setPadding(h.dp(8), h.dp(7), h.dp(8), h.dp(7));
    card.setBackground(new GamePaperDrawable(h));
    LinearLayout heading = k.row();
    TextView label = h.text(mission.optString("label"), 15);
    label.setTypeface(h.headline);
    label.setPadding(0, 0, h.dp(8), 0);
    heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(k.image("/duel/icons/u-ticket.png", 22, 22));
    TextView reward = h.text(String.format(Locale.KOREA, "%,d장", mission.optInt("tickets")), 15);
    reward.setTypeface(h.prose, android.graphics.Typeface.BOLD);
    reward.setTag("native-mission-reward");
    reward.setPadding(h.dp(4), 0, 0, 0);
    reward.setSingleLine(true);
    reward.setContentDescription("뽑기권 " + reward.getText());
    heading.addView(reward, new LinearLayout.LayoutParams(-2, -2));
    card.addView(heading);
    int goal = Math.max(1, mission.optInt("goal"));
    int value = Math.min(goal, Math.max(0, mission.optInt("value")));
    LinearLayout detail = k.row();
    TextView progress = k.note("진행 " + value + "/" + goal);
    progress.setPadding(0, h.dp(3), h.dp(6), h.dp(3));
    progress.setSingleLine(true);
    progress.setTextSize(12);
    detail.addView(progress, new LinearLayout.LayoutParams(0, -2, 1));
    TextView status =
        k.note(mission.optBoolean("done") ? "✓ 지급 완료" : value >= goal ? "지급 중" : "진행 중");
    status.setPadding(0, h.dp(3), 0, h.dp(3));
    status.setSingleLine(true);
    status.setTextSize(12);
    if (mission.optBoolean("done")) status.setTextColor(0xff397953);
    detail.addView(status, new LinearLayout.LayoutParams(-2, -2));
    card.addView(detail);
    ProgressBar bar = new ProgressBar(h, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(goal);
    bar.setProgress(value);
    bar.setProgressTintList(
        android.content.res.ColorStateList.valueOf(
            mission.optBoolean("done") ? 0xff51b985 : 0xffe9a630));
    card.addView(bar, new LinearLayout.LayoutParams(-1, h.dp(7)));
    return card;
  }

  void pvp() {
    h.base("랭크 · 순위표", "pvp");
    m.fetch(
        "pvp",
        r -> {
          JSONObject me = r.optJSONObject("me");
          if (me != null) {
            LinearLayout card = k.paper();
            m.intro(
                card,
                "mode-rank",
                me.optString("tier") + " · " + me.optInt("point") + "점",
                (me.isNull("rank") || me.optInt("rank") <= 0
                        ? "아직 랭크게임을 안 했어요"
                        : "전체 " + me.optInt("rank") + "위")
                    + " · "
                    + me.optInt("wins")
                    + "승 "
                    + me.optInt("losses")
                    + "패 · 양쪽 Lv"
                    + r.optInt("level"));
            h.body.addView(card);
          }
          LinearLayout links = k.row();
          h.cell(links, k.action("mode-normal", "일반게임", () -> h.startFight("normal", null)));
          h.cell(
              links,
              k.action(
                  "mode-rank",
                  "랭크게임",
                  () -> h.confirm("중간에 나가면 패배로 처리돼요. 시작할까요?", () -> h.startFight("rank", null))));
          h.cell(
              links,
              k.action(
                  "m-units",
                  "전투 편성",
                  () -> {
                    h.selectionMode = "pvp";
                    m.teamDraft = null;
                    h.team();
                  }));
          h.body.addView(links);
          LinearLayout table = k.paper();
          h.body.addView(table);
          LinearLayout header = k.row();
          for (String label : new String[] {"순위", "수비대", "티어", "점수", "승 / 패"})
            h.cell(header, heading(label, 17));
          table.addView(header);
          JSONArray top = h.list(r, "top");
          if (top.length() == 0) table.addView(k.note("아직 랭크게임을 한 사람이 없어요. 첫 번째가 되어 보세요!"));
          for (int i = 0; i < top.length(); i++) {
            JSONObject u = top.optJSONObject(i);
            LinearLayout row = k.row();
            if (u.optBoolean("me")) row.setBackground(new GamePanelDrawable(h));
            for (String value :
                new String[] {
                  u.optInt("rank") + "위",
                  u.optString("name"),
                  u.optString("tier"),
                  String.valueOf(u.optInt("point")),
                  u.optInt("wins") + " / " + u.optInt("losses")
                }) h.cell(row, h.text(value, 16));
            table.addView(row);
          }
        });
  }

  void practice() {
    h.base("연습 · 자유 대결", "practice");
    LinearLayout[] cols = k.split(.5f);
    practiceTeam(cols[0], false);
    practiceTeam(cols[1], true);
    LinearLayout options = k.paper();
    LinearLayout sliders = k.row();
    options.addView(sliders);
    slider(sliders, "레벨", h.practiceLevel, 1, 60, v -> h.practiceLevel = v);
    slider(sliders, "승진 단계", h.practicePromotion, 0, 3, v -> h.practicePromotion = v);
    options.addView(k.note("경험치·체력·아이템·대전 점수는 저장되지 않아요. 33종을 자유롭게 골라요."));
    options.addView(k.action("mode-pvp", "연습 시작", () -> h.startFight("practice", "1-6")));
    k.footer(options);
  }

  void practiceTeam(LinearLayout parent, boolean enemy) {
    parent.addView(k.section(enemy ? "연습 상대" : "우리 수비대"));
    ArrayList<String> ids = enemy ? h.practiceEnemy : h.practiceRoster;
    ArrayList<View> cards = new ArrayList<>();
    JSONArray all = h.list(h.state, "practiceUnits");
    for (String id : ids)
      for (int i = 0; i < all.length(); i++) {
        JSONObject u = all.optJSONObject(i);
        if (id.equals(u.optString("id")))
          cards.add(k.tile(u, u.optString("image"), u.optString("attr"), () -> picker(enemy)));
      }
    k.tiles(parent, cards, 3);
    if (ids.isEmpty()) parent.addView(k.notice("선택하지 않으면 이야기 상대와 연습해요."));
    parent.addView(k.action("m-units", enemy ? "상대 갈매미 선택" : "우리 갈매미 선택", () -> picker(enemy)));
  }

  void picker(boolean enemy) {
    ArrayList<String> draft = new ArrayList<>(enemy ? h.practiceEnemy : h.practiceRoster);
    JSONArray all = h.list(h.state, "practiceUnits");
    LinearLayout contents = k.column();
    android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          contents.removeAllViews();
          ArrayList<View> cards = new ArrayList<>();
          for (int i = 0; i < all.length(); i++) {
            JSONObject u = all.optJSONObject(i);
            String id = u.optString("id");
            LinearLayout tile =
                k.tile(
                    u,
                    u.optString("image"),
                    (draft.contains(id) ? "✓ " : "") + u.optString("attr"),
                    () -> {
                      if (draft.contains(id)) draft.remove(id);
                      else if (draft.size() < 3) draft.add(id);
                      else {
                        h.message("3마리까지 선택해 주세요.");
                        return;
                      }
                      paint[0].run();
                    });
            if (draft.contains(id)) tile.setBackground(new GamePanelDrawable(h));
            cards.add(tile);
          }
          k.tiles(contents, cards, 3);
        };
    paint[0].run();
    dialog[0] =
        new GameDialog(h)
            .setTitle(enemy ? "연습 상대 1~3마리" : "연습 수비대 1~3마리")
            .setView(k.scroll(contents))
            .setNegativeButton("취소", null)
            .setPositiveButton("선택", null)
            .show();
    dialog[0]
        .getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(
            v -> {
              if (draft.isEmpty()) {
                h.message("1마리 이상 골라 주세요.");
                return;
              }
              if (enemy) h.practiceEnemy = draft;
              else h.practiceRoster = draft;
              dialog[0].dismiss();
              practice();
            });
  }

  void slider(
      LinearLayout parent,
      String caption,
      int value,
      int min,
      int max,
      java.util.function.IntConsumer change) {
    LinearLayout card = k.column();
    TextView label = h.text(caption + " " + value, 17);
    card.addView(label);
    SeekBar slider = new SeekBar(h);
    slider.setMax(max - min);
    slider.setProgress(value - min);
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int n, boolean user) {
            if (user) {
              label.setText(caption + " " + (n + min));
              change.accept(n + min);
            }
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    card.addView(slider, new LinearLayout.LayoutParams(-1, h.dp(48)));
    if (parent.getOrientation() == LinearLayout.HORIZONTAL) h.cell(parent, card);
    else parent.addView(card, new LinearLayout.LayoutParams(-1, -2));
  }

  void settings() {
    NativeSettingsSheet.show(h);
  }

  void profile(View anchor) {
    LinearLayout panel = k.paper();
    panel.setPadding(h.dp(12), h.dp(10), h.dp(12), h.dp(10));
    panel.setBackground(new GamePanelDrawable(h));
    panel.addView(heading(h.me().optString("nickname", "수비조장"), 20));
    TextView rank = k.note("랭크를 불러오고 있어요…");
    panel.addView(rank);
    int width =
        Math.min(
            h.dp(292),
            (int) h.safeBounds(h.root.getWidth(), h.root.getHeight()).width() - h.dp(16));
    PopupWindow popup = new PopupWindow(panel, width, -2, true);
    popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    popup.setOutsideTouchable(true);
    popup.setElevation(h.dp(8));
    for (String[] entry :
        new String[][] {
          {"m-account", "계정 관리", "account"},
          {"mode-rank", "랭크 순위", "pvp"},
          {"u-settings", "설정", "settings"}
        }) {
      Button button =
          k.action(
              entry[0],
              entry[1],
              () -> {
                popup.dismiss();
                if (entry[2].equals("settings")) settings();
                else h.load(entry[2]);
              });
      button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, h.dp(48));
      row.topMargin = h.dp(5);
      panel.addView(button, row);
    }
    popup.showAsDropDown(anchor, 0, h.dp(8));
    h.api.call(
        "pvp",
        null,
        (r, e) -> {
          if (!popup.isShowing()) return;
          JSONObject me = r == null ? null : r.optJSONObject("me");
          if (e != null || me == null) rank.setText("랭크 정보를 불러오지 못했어요.");
          else
            rank.setText(
                me.optString("tier")
                    + " · "
                    + me.optInt("point")
                    + "점 · "
                    + me.optInt("wins")
                    + "승 "
                    + me.optInt("losses")
                    + "패");
        });
  }

  void account() {
    h.base("계정 관리", "account");
    LinearLayout[] cols = k.split(.52f);
    LinearLayout profile = k.paper();
    profile.addView(heading(h.me().optString("nickname", "수비조장"), 22));
    profile.addView(
        k.note(
            "도감 "
                + h.me().optInt("dex")
                + " / "
                + h.me().optInt("dexTotal")
                + " · 뽑기권 "
                + h.me().optInt("credits")
                + " · 새우gang "
                + h.me().optInt("snack")));
    TextView version = k.note("Android " + BuildConfig.VERSION_NAME);
    version.setTextSize(12);
    profile.addView(version);
    cols[0].addView(profile);
    m.fetch(
        "me",
        r -> {
          if (r.optBoolean("kakaoLogin")) {
            LinearLayout link = k.paper();
            TextView title = heading("카카오 계정", 18);
            link.addView(title);
            boolean linked = r.optBoolean("kakao");
            boolean linking =
                h.getSharedPreferences("native_account", 0).getBoolean("linking", false);
            link.addView(
                k.note(
                    linking
                        ? "연결 확인 중 · 현재 계정과 진행은 그대로 유지돼요."
                        : linked
                            ? "✓ 카카오 계정 연결됨"
                            : "기존 갈매미와 스토리를 유지한 채 연결해요. 다음부터 코드 없이 로그인할 수 있어요."));
            LinearLayout actions = k.row();
            Button connect =
                k.action(
                    "m-account",
                    linking ? "연결 다시 열기" : linked ? "연결 해제" : "카카오 연결",
                    () -> {
                      if (linked)
                        h.confirm(
                            "카카오 연결을 끊을까요? 다음부터 카톡 코드로 로그인해요. 진행은 유지돼요.",
                            () -> h.mutate("kakao/unlink", MainActivity.obj(), "account"));
                      else h.confirm("지금 계정에 카카오를 연결해요. 기존 갈매미·재화·스토리 진행이 유지됩니다.", h::kakaoLink);
                    });
            h.cell(actions, connect);
            if (linking)
              h.cell(
                  actions,
                  k.action(
                      null,
                      "연결 취소",
                      () -> {
                        h.getSharedPreferences("native_account", 0)
                            .edit()
                            .remove("verifier")
                            .remove("linking")
                            .remove("linkCode")
                            .apply();
                        h.load("account");
                      }));
            link.addView(actions);
            cols[0].addView(link);
          }
          LinearLayout session = k.paper();
          session.addView(k.note("이 기기에서 로그아웃해요. 서버에 저장된 진행은 유지돼요."));
          session.addView(k.action(null, "로그아웃", () -> h.confirm("이 기기에서 로그아웃할까요?", this::logout)));
          cols[0].addView(session);
          LinearLayout links = k.row();
          h.cell(links, k.action("u-settings", "설정", this::settings));
          h.cell(links, k.action("mode-rank", "랭크 순위", () -> h.load("pvp")));
          cols[0].addView(links);
          LinearLayout privacy = k.paper();
          privacy.addView(k.note("어떤 정보를 왜 저장하는지 확인할 수 있어요."));
          privacy.addView(
              k.action(
                  null,
                  "개인정보처리방침",
                  () ->
                      h.startActivity(
                          new Intent(
                              Intent.ACTION_VIEW,
                              Uri.parse(BuildConfig.SERVER_URL + "/privacy")))));
          cols[1].addView(privacy);
          LinearLayout danger = k.paper();
          danger.addView(heading("계정 삭제(탈퇴)", 19));
          TextView warning =
              k.note(
                  "갈매미·도감·재화·아이템·탐험·스토리 진행·댓글이 모두 지워지고 되돌릴 수 없어요.\n"
                      + "카톡 채널에 다시 말을 걸면 새 계정으로 시작해요. 서버 백업에는 최대 7일 동안 남아 있다가 사라져요.");
          warning.setTextColor(0xff9b4034);
          danger.addView(warning);
          Button remove =
              k.action(
                  null,
                  "계정 삭제",
                  () ->
                      h.confirm(
                          "정말 계정을 삭제할까요? 되돌릴 수 없어요.",
                          () ->
                              h.request(
                                  "account/delete",
                                  MainActivity.obj("confirm", "탈퇴"),
                                  (done, error) -> finishLogout())));
          remove.setBackground(new GameButtonDrawable(h, 0xffe8b4aa));
          remove.setEnabled(false);
          k.search(
              danger,
              "확인을 위해 ‘탈퇴’라고 적어 주세요",
              "",
              value -> remove.setEnabled(value.trim().equals("탈퇴")));
          danger.addView(remove);
          cols[1].addView(danger);
        });
  }

  void logout() {
    if (h.busy) return;
    h.epoch++;
    NativeTutorialController.get(h).close();
    h.busy = true;
    h.api.call(
        "logout",
        MainActivity.obj(),
        (r, error) -> {
          h.busy = false;
          finishLogout();
        });
  }

  void finishLogout() {
    h.epoch++;
    h.busy = false;
    NativeTutorialController.get(h).close();
    h.api.token("");
    h.art.clear();
    h.state = new JSONObject();
    m.reset();
    h.getSharedPreferences("native_account", 0)
        .edit()
        .remove("verifier")
        .remove("linking")
        .remove("linkCode")
        .apply();
    NativeAlerts.schedule(h, new JSONObject());
    if (!h.isFinishing() && !h.isDestroyed()) h.titleScreen();
  }

  void tutorial() {
    NativeTutorialController.get(h).startReplay();
  }
}

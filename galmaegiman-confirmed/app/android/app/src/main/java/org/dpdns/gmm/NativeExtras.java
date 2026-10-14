package org.dpdns.gmm;

import android.content.*;
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

  void daily() {
    h.base("출석체크 · 오늘의 미션", "daily");
    LinearLayout[] cols = k.split(.52f);
    m.fetch(
        "daily",
        r -> {
          JSONObject a = r.optJSONObject("attend");
          if (a != null) {
            LinearLayout panel = k.paper();
            cols[0].addView(panel);
            m.intro(
                panel,
                "u-attend",
                a.optInt("week") + "주째 · " + a.optInt("streak") + "일 연속",
                "하루 빠지면 1일째부터 · 날마다 보상이 커져요.");
            HorizontalScrollView week = new HorizontalScrollView(h);
            LinearLayout line = k.row();
            week.addView(line);
            panel.addView(week);
            JSONArray rewards = h.list(a, "rewards");
            int current = a.optInt("dayInWeek", 1);
            for (int i = 0; i < rewards.length(); i++) {
              int day = i + 1;
              boolean got = day < current || day == current && a.optBoolean("today");
              LinearLayout card = k.paper();
              card.addView(k.note(day + "일"));
              card.addView(
                  k.image("/duel/icons/" + (day == 7 ? "u-gift" : "u-ticket") + ".png", 32, 32));
              card.addView(h.text(rewards.optInt(i) + "장", 19));
              card.addView(k.note(got ? "✓ 받음" : day == current ? "오늘" : "잠김"));
              if (day == current) card.setBackground(h.material(0xfffbe8d3));
              LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(h.dp(78), -2);
              lp.setMargins(h.dp(3), 0, h.dp(3), 0);
              line.addView(card, lp);
            }
            Button attend =
                k.action(
                    "u-ticket",
                    a.optBoolean("today") ? "✓ 오늘 출석 완료" : "출석하고 뽑기권 " + a.optInt("next") + "장 받기",
                    () -> h.mutate("attend", MainActivity.obj(), "daily"));
            attend.setEnabled(!a.optBoolean("today"));
            panel.addView(attend);
          }
          cols[1].addView(k.section("오늘의 미션"));
          cols[1].addView(k.notice("달성하면 보상이 바로 들어와요 · 한국 시간 밤 12시 초기화"));
          JSONArray missions = h.list(r, "missions");
          for (int i = 0; i < missions.length(); i++) {
            JSONObject mission = missions.optJSONObject(i);
            LinearLayout card = k.paper();
            card.addView(h.text(mission.optString("label"), 18));
            k.progress(
                card,
                mission.optInt("value") + " / " + mission.optInt("goal"),
                mission.optInt("value"),
                mission.optInt("goal"),
                0xffe9a630);
            card.addView(
                k.note(
                    "뽑기권 "
                        + mission.optInt("tickets")
                        + "장 · "
                        + (mission.optBoolean("done")
                            ? "✓ 받음"
                            : mission.optInt("value") >= mission.optInt("goal")
                                ? "지급 중"
                                : "진행 중")));
            cols[1].addView(card);
          }
        });
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
                me.optInt("wins") + "승 " + me.optInt("losses") + "패 · 양쪽 Lv" + r.optInt("level"));
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
            h.cell(header, h.text(label, 17));
          table.addView(header);
          JSONArray top = h.list(r, "top");
          if (top.length() == 0) table.addView(k.note("아직 순위가 없어요."));
          for (int i = 0; i < top.length(); i++) {
            JSONObject u = top.optJSONObject(i);
            LinearLayout row = k.row();
            if (u.optBoolean("me")) row.setBackground(h.material(0xfffbe8d3));
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
            if (draft.contains(id)) tile.setBackground(h.material(0xfffbe8d3));
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

  void account() {
    h.base("계정 관리", "account");
    LinearLayout info = k.paper();
    info.addView(h.text(h.me().optString("nickname", "수비조장"), 22));
    info.addView(
        k.note(
            "도감 "
                + h.me().optInt("dex")
                + " / "
                + h.me().optInt("dexTotal")
                + " · 앱 버전 "
                + BuildConfig.VERSION_NAME));
    h.body.addView(info);
    m.fetch(
        "me",
        r -> {
          if (r.optBoolean("kakaoLogin")) {
            LinearLayout link = k.paper();
            link.addView(
                k.note(
                    r.optBoolean("kakao") ? "카카오 계정 연결됨" : "카카오 계정을 연결하면 다음부터 코드 없이 로그인할 수 있어요."));
            link.addView(
                k.action(
                    "m-account",
                    r.optBoolean("kakao") ? "연결 해제" : "카카오 연결",
                    () -> {
                      if (r.optBoolean("kakao"))
                        h.confirm(
                            "카카오 연결을 끊을까요? 다음부터 카톡 코드로 로그인해요.",
                            () -> h.mutate("kakao/unlink", MainActivity.obj(), "account"));
                      else h.kakaoLink();
                    }));
            h.body.addView(link);
          }
          h.body.addView(k.action(null, "로그아웃", () -> h.confirm("이 기기에서 로그아웃할까요?", this::logout)));
          h.body.addView(
              k.action(
                  null,
                  "개인정보처리방침",
                  () ->
                      h.startActivity(
                          new Intent(
                              Intent.ACTION_VIEW,
                              Uri.parse(BuildConfig.SERVER_URL + "/privacy")))));
          LinearLayout danger = k.paper();
          danger.addView(h.text("계정 삭제(탈퇴)", 20));
          danger.addView(
              k.note("갈매미·도감·재화·아이템·탐험·스토리 진행·댓글이 모두 지워지고 되돌릴 수 없어요. 서버 백업에는 최대 7일 남아요."));
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
                                  (done, error) -> logout())));
          remove.setBackground(h.material(0xffe8b4aa));
          remove.setEnabled(false);
          k.search(
              danger,
              "확인을 위해 ‘탈퇴’라고 적어 주세요",
              "",
              value -> remove.setEnabled(value.trim().equals("탈퇴")));
          danger.addView(remove);
          h.body.addView(danger);
        });
  }

  void logout() {
    h.api.token("");
    h.art.clear();
    h.state = new JSONObject();
    m.reset();
    NativeAlerts.schedule(h, new JSONObject());
    h.titleScreen();
  }

  void tutorial() {
    h.base("첫 출근 안내", "tutorial");
    String[] names = {"수비대에 오신 걸 환영해요", "첫 뽑기", "수비대 편성", "이야기 출발", "성장과 승진"},
        notes =
            {
              "항구를 함께 지킬 갈매미를 모으고 이야기를 따라 모험해요.",
              "뽑기에서 갈매미를 모아요. 중급·고급도 흔함·안흔함이 나올 수 있어요.",
              "함께할 1~3마리를 고르세요. 전투 편성은 단상에서 출전 순서를 확인해요.",
              "챕터를 고르고 열린 전투를 시작해요. 대결 뒤에도 스토리 체력은 유지돼요.",
              "경험치는 스토리 팀과 기술 화면에서 볼 수 있어요. 레벨업하면 체력이 회복돼요. 20·35·50레벨과 보스 완료로 승진해요."
            },
        icons = {"u-profile", "u-draw", "m-units", "u-story", "learn"};
    guide = Math.max(0, Math.min(4, guide));
    LinearLayout card = k.paper();
    m.intro(card, icons[guide], (guide + 1) + " / 5 · " + names[guide], notes[guide]);
    h.body.addView(card);
    LinearLayout steps = k.row();
    if (guide > 0)
      h.cell(
          steps,
          k.action(
              null,
              "‹ 이전",
              () -> {
                guide--;
                tutorial();
              }));
    if (guide < 4)
      h.cell(
          steps,
          k.action(
              null,
              "다음 ›",
              () -> {
                guide++;
                tutorial();
              }));
    h.body.addView(steps);
    h.body.addView(
        k.action(
            icons[guide],
            guide == 1 ? "뽑기로 가기" : guide == 2 ? "편성하러 가기" : guide == 3 ? "챕터 선택하기" : "로비로",
            () -> {
              String target =
                  guide == 1 ? "draw" : guide == 2 ? "team" : guide == 3 ? "story" : "home";
              if (guide == 2) h.selectionMode = "story";
              JSONObject progress = h.state.optJSONObject("tutorial");
              if (progress != null && progress.optInt("step") < 9)
                h.request(
                    "tutorial",
                    MainActivity.obj("step", Math.min(5, guide + 2)),
                    (r, e) -> h.load(target));
              else h.load(target);
            }));
    JSONObject progress = h.state.optJSONObject("tutorial");
    if (progress != null && progress.optInt("step") < 9)
      h.body.addView(
          k.action(
              null,
              "안내 마치기 · 첫 갈매미 받기",
              () -> h.mutate("tutorial", MainActivity.obj("step", 9), "home")));
    else h.body.addView(k.action(null, "안내 닫기", () -> h.load("home")));
  }
}

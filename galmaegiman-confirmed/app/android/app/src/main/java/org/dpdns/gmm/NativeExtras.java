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
  PopupWindow profilePopup;

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

  /** 수령·편성·시작은 목록의 길이와 무관하게 기본 화면에 남긴다. */
  LinearLayout[] fixedSplit(float leftWeight) {
    h.page.removeViewAt(h.page.getChildCount() - 1);
    LinearLayout line = k.row();
    line.setGravity(Gravity.TOP);
    LinearLayout.LayoutParams area = new LinearLayout.LayoutParams(-1, 0, 1);
    area.topMargin = h.dp(6);
    h.page.addView(line, area);
    LinearLayout left = k.column(), right = k.column();
    LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -1, leftWeight);
    first.rightMargin = h.dp(8);
    line.addView(left, first);
    line.addView(right, new LinearLayout.LayoutParams(0, -1, 1 - leftWeight));
    h.body = right;
    return new LinearLayout[] {left, right};
  }

  void fixedAction(LinearLayout parent, View action) {
    LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, h.dp(48));
    slot.topMargin = h.dp(6);
    parent.addView(action, slot);
  }

  TextView plaque(String text, float size) {
    TextView title = heading(text, size);
    title.setPadding(h.dp(14), h.dp(8), h.dp(14), h.dp(8));
    title.setTextColor(JournalTheme.WHITE);
    title.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.HEADER));
    return title;
  }

  ImageView sceneIcon(String file, int size) {
    ImageView icon = new ImageView(h);
    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
    icon.setImageBitmap(ThemeArt.of(h).image("icons/" + file + ".png"));
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    icon.setLayoutParams(new LinearLayout.LayoutParams(h.dp(size), h.dp(size)));
    return icon;
  }

  /** 목록만 넘긴다. 수령·대전 시작·편성은 항상 같은 화면에 남긴다. */
  void pageBar(LinearLayout parent, String tag, int index, int count, java.util.function.IntConsumer change) {
    LinearLayout line = k.row();
    Button previous = k.action(null, "‹", () -> change.accept(index - 1));
    previous.setTag(tag + "-previous"); previous.setContentDescription("이전 페이지");
    previous.setEnabled(index > 0);
    line.addView(previous, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    TextView number = heading((index + 1) + " / " + Math.max(1, count), 14);
    number.setGravity(Gravity.CENTER); number.setPadding(0, 0, 0, 0);
    line.addView(number, new LinearLayout.LayoutParams(0, h.dp(48), 1));
    Button next = k.action(null, "›", () -> change.accept(index + 1));
    next.setTag(tag + "-next"); next.setContentDescription("다음 페이지");
    next.setEnabled(index + 1 < count);
    line.addView(next, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    parent.addView(line, new LinearLayout.LayoutParams(-1, h.dp(48)));
  }

  void daily() {
    h.base("출석체크", "daily");
    LinearLayout[] cols = fixedSplit(.44f);
    m.fetch("daily", r -> {
      JSONObject attendance = r.optJSONObject("attend");
      if (attendance != null) {
        boolean today = attendance.optBoolean("today");
        int current = attendance.optInt("dayInWeek", 1);
        JSONArray rewards = h.list(attendance, "rewards");
        LinearLayout ledger = k.paper();
        ledger.setTag("daily-attendance-summary");
        LinearLayout hero = k.row();
        hero.addView(sceneIcon("daily", h.compact() ? 76 : 112));
        LinearLayout detail = k.column();
        detail.addView(heading(attendance.optInt("week") + "주째 · " + current + "일째", 18));
        int reward = today ? rewards.optInt(Math.max(0, current - 1)) : attendance.optInt("next");
        detail.addView(heading("지원서 " + reward + "장", 20));
        detail.addView(k.note(today ? "오늘 받은 출석 보상" : "오늘 받을 출석 보상"));
        hero.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
        ledger.addView(hero);
        if (attendance.optInt("streak") > 0)
          ledger.addView(k.note(attendance.optInt("streak") + "일 연속 출석 중"));
        cols[0].addView(ledger);
        Button attend = k.primary(null, today ? "출석 완료" : "지원서 " + reward + "장 받기",
            () -> h.mutate("attend", MainActivity.obj(), "daily"));
        attend.setTag("daily-attend");
        NativeTutorialController.anchorPreservingTag(attend, "attend"); attend.setEnabled(!today);
        fixedAction(cols[0], attend);
        fixedAction(cols[0], k.action(null, "출석표 · 보상 안내", () -> attendanceSheet(attendance)));
      }
      JSONArray missions = h.list(r, "missions");
      int done = 0;
      for (int i = 0; i < missions.length(); i++) if (missions.optJSONObject(i).optBoolean("done")) done++;
      cols[1].addView(plaque("오늘의 미션 · " + done + "/" + missions.length(), 18));
      LinearLayout board = k.column();
      board.setTag("daily-mission-pages");
      cols[1].addView(board, new LinearLayout.LayoutParams(-1, -2));
      int perPage = h.compact() ? (h.getResources().getConfiguration().fontScale > 1.15f ? 1 : 2) : 3;
      int count = Math.max(1, (missions.length() + perPage - 1) / perPage);
      int[] page = {0}; Runnable[] paint = new Runnable[1];
      paint[0] = () -> {
        board.removeAllViews();
        if (missions.length() == 0) board.addView(k.notice("오늘의 미션이 없어요."));
        for (int i = page[0] * perPage; i < Math.min(missions.length(), (page[0] + 1) * perPage); i++) {
          LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, -2);
          slot.topMargin = h.dp(5); board.addView(missionCard(missions.optJSONObject(i)), slot);
        }
        pageBar(board, "daily-missions", page[0], count, value -> { page[0] = value; paint[0].run(); });
        TextView reset = k.note("달성 시 자동 지급 · 한국 시간 밤 12시 초기화");
        reset.setTextSize(12); board.addView(reset);
      };
      paint[0].run();
    });
  }

  void attendanceSheet(JSONObject attendance) {
    k.openSheet(
        "출석표 · 보상 안내",
        () -> {
          LinearLayout contents = k.column();
          contents.addView(k.note("출석은 날마다·주마다 보상이 커져요. 하루 빠지면 1일째부터 시작해요."));
          contents.addView(k.note("한국 시간 밤 12시에 초기화해요."));
          JSONArray rewards = h.list(attendance, "rewards");
          int current = attendance.optInt("dayInWeek", 1);
          boolean today = attendance.optBoolean("today");
          for (int i = 0; i < rewards.length(); i++) {
            int day = i + 1;
            boolean got = day < current || day == current && today;
            LinearLayout card = k.paper();
            if (day == current) card.setBackground(new GamePanelDrawable(h));
            LinearLayout row = k.row();
            row.addView(
                k.image("/duel/icons/" + (day == 7 ? "u-gift" : "u-ticket") + ".png", 28, 28));
            TextView reward = heading(day + "일째 · 지원서 " + rewards.optInt(i) + "장", 17);
            row.addView(reward, new LinearLayout.LayoutParams(0, -2, 1));
            TextView status = k.note(got ? "✓ 받음" : day == current ? "오늘" : "다음 보상");
            if (got) status.setTextColor(JournalTheme.POSITIVE_INFO);
            row.addView(status);
            card.addView(row);
            contents.addView(card);
          }
          return contents;
        });
  }

  /** 보상은 숫자와 단위를 한 덩어리로 측정해 큰 글자에서도 세로로 갈라지지 않는다. */
  LinearLayout missionCard(JSONObject mission) {
    LinearLayout card = k.column();
    card.setTag("daily-mission-" + mission.optString("id", mission.optString("label")));
    card.setPadding(h.dp(8), h.dp(7), h.dp(8), h.dp(7));
    card.setBackground(new GamePaperDrawable(h));
    LinearLayout heading = k.row();
    TextView label = h.text(RecruitmentText.display(mission.optString("label")), 15);
    label.setTypeface(h.headline);
    label.setPadding(0, 0, h.dp(8), 0);
    heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(k.image("/duel/icons/u-ticket.png", 22, 22));
    TextView reward = h.text(String.format(Locale.KOREA, "%,d장", mission.optInt("tickets")), 15);
    reward.setTypeface(h.prose, android.graphics.Typeface.BOLD);
    reward.setTag("native-mission-reward");
    reward.setPadding(h.dp(4), 0, 0, 0);
    reward.setSingleLine(true);
    reward.setContentDescription("지원서 " + reward.getText());
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
    if (mission.optBoolean("done")) status.setTextColor(JournalTheme.POSITIVE_INFO);
    detail.addView(status, new LinearLayout.LayoutParams(-2, -2));
    card.addView(detail);
    ProgressBar bar = new ProgressBar(h, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(goal);
    bar.setProgress(value);
    bar.setProgressTintList(
        android.content.res.ColorStateList.valueOf(
            mission.optBoolean("done") ? JournalTheme.TEAL : JournalTheme.BRASS));
    card.addView(bar, new LinearLayout.LayoutParams(-1, h.dp(7)));
    return card;
  }

  void pvp() {
    h.base("랭크 · 순위표", "pvp");
    LinearLayout[] cols = fixedSplit(.46f);
    m.fetch("pvp/rank", r -> {
      JSONObject me = r.optJSONObject("me");
      if (me != null) {
        LinearLayout card = k.paper();
        card.setTag("rank-player-summary");
        LinearLayout hero = k.row(); hero.addView(sceneIcon("team", h.compact() ? 76 : 112));
        LinearLayout detail = k.column();
        detail.addView(heading(me.optString("tier") + " · " + me.optInt("point") + "점", 20));
        detail.addView(k.note((me.isNull("rank") || me.optInt("rank") <= 0 ? "아직 순위가 없어요" : "전체 " + me.optInt("rank") + "위")
            + " · " + me.optInt("wins") + "승 " + me.optInt("losses") + "패"));
        hero.addView(detail, new LinearLayout.LayoutParams(0, -2, 1)); card.addView(hero); cols[0].addView(card);
      }
      cols[0].addView(k.notice("양쪽 모두 Lv" + r.optInt("level") + "로 맞춰요."));
      LinearLayout links = k.row();
      Button normal = k.primary(null, "일반게임", () -> h.startFight("normal", null));
      normal.setTag("rank-normal");
      Button ranked = k.primary(null, "랭크게임", () -> h.confirm("중간에 나가면 패배로 처리돼요. 시작할까요?", () -> h.startFight("rank", null)));
      ranked.setTag("rank-start");
      h.cell(links, normal); h.cell(links, ranked); cols[0].addView(links);
      fixedAction(cols[0], k.action(null, "전투 편성", () -> { h.selectionMode = "pvp"; m.teamDraft = null; h.team(); }));
      JSONArray top = h.list(r, "top");
      cols[1].addView(plaque("방범대 순위", 18));
      LinearLayout table = k.column(); table.setTag("rank-table-pages"); cols[1].addView(table);
      int perPage = h.compact() ? 2 : 4, count = Math.max(1, (top.length() + perPage - 1) / perPage);
      int[] page = {0}; Runnable[] paint = new Runnable[1];
      paint[0] = () -> {
        table.removeAllViews();
        if (top.length() == 0) table.addView(k.notice("아직 랭크게임을 한 사람이 없어요."));
        for (int i = page[0] * perPage; i < Math.min(top.length(), (page[0] + 1) * perPage); i++) {
          JSONObject player = top.optJSONObject(i);
          LinearLayout row = k.paper(); row.setTag("rank-row-" + player.optInt("rank"));
          if (player.optBoolean("me")) row.setBackground(new GamePanelDrawable(h));
          TextView name = heading(player.optInt("rank") + "위 · " + player.optString("name"), 16);
          row.addView(name);
          TextView score = k.note(player.optString("tier") + " · " + player.optInt("point") + "점 · "
              + player.optInt("wins") + "승 " + player.optInt("losses") + "패");
          score.setTextSize(13); row.addView(score); table.addView(row);
        }
        pageBar(table, "rank-table", page[0], count, value -> { page[0] = value; paint[0].run(); });
      };
      paint[0].run();
    });
  }

  void rankSheet(JSONArray top) {
    k.openSheet(
        "전체 순위표",
        () -> {
          LinearLayout table = k.column();
          LinearLayout header = k.row();
          for (String label : new String[] {"순위", "방범대", "티어", "점수", "승 / 패"})
            h.cell(header, heading(label, 17));
          table.addView(header);
          if (top.length() == 0) table.addView(k.note("아직 랭크게임을 한 사람이 없어요."));
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
          return table;
        });
  }

  void practice() {
    h.base("연습 · 자유 대결", "practice");
    LinearLayout[] cols = fixedSplit(.5f);
    practiceTeam(cols[0], false);
    practiceTeam(cols[1], true);
    LinearLayout options = k.paper();
    LinearLayout line = k.row();
    LinearLayout sliders = k.row();
    slider(sliders, "레벨", h.practiceLevel, 1, 60, v -> h.practiceLevel = v);
    slider(sliders, "승진 단계", h.practicePromotion, 0, 3, v -> h.practicePromotion = v);
    line.addView(sliders, new LinearLayout.LayoutParams(0, -2, .66f));
    LinearLayout start = k.column();
    start.addView(k.note("연습 결과는 저장되지 않아요."));
    Button begin = k.primary("mode-pvp", "연습 시작", () -> h.startFight("practice", "1-6"));
    begin.setTag("practice-start");
    start.addView(begin, new LinearLayout.LayoutParams(-1, h.dp(48)));
    line.addView(start, new LinearLayout.LayoutParams(0, -2, .34f));
    options.addView(line);
    k.footer(options);
  }

  void practiceTeam(LinearLayout parent, boolean enemy) {
    ArrayList<String> ids = enemy ? h.practiceEnemy : h.practiceRoster;
    LinearLayout header = k.row();
    header.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.HEADER));
    header.setPadding(h.dp(4), 0, h.dp(4), 0);
    TextView caption = heading(enemy ? "연습 상대" + (ids.isEmpty() ? " · 1-6" : "") : "우리 방범대", 17);
    caption.setTextColor(JournalTheme.WHITE);
    header.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
    Button choose = k.action(enemy ? null : "m-units", enemy ? "상대 선택" : "편성", () -> picker(enemy));
    choose.setTag(enemy ? "practice-enemy-picker" : "practice-roster-picker");
    header.addView(choose, new LinearLayout.LayoutParams(h.dp(enemy ? 124 : 116), h.dp(48)));
    parent.addView(header);
    JSONArray all = h.list(h.state, "practiceUnits");
    JSONArray defaultEnemy = enemy && ids.isEmpty() ? practiceDefaultEnemy() : null;
    LinearLayout cards = k.row();
    cards.setTag(enemy ? "practice-enemy-summary" : "practice-roster-summary");
    cards.setGravity(Gravity.CENTER);
    for (int slot = 0; slot < 3; slot++) {
      String id = slot < ids.size() ? ids.get(slot) : "";
      JSONObject unit = null;
      for (int i = 0; i < all.length(); i++)
        if (id.equals(all.optJSONObject(i).optString("id"))) {
          unit = all.optJSONObject(i);
          break;
        }
      if (defaultEnemy != null && slot < defaultEnemy.length()) {
        JSONObject foe = defaultEnemy.optJSONObject(slot);
        String foeId = foe.optString("id");
        JSONObject names = h.state.optJSONObject("names");
        JSONObject portraits = h.state.optJSONObject("portraits");
        unit =
            MainActivity.obj(
                "id", foeId, "name", names == null ? foeId : names.optString(foeId, foeId),
                "image", portraits == null ? "" : portraits.optString(foeId));
      }
      if (defaultEnemy != null && slot >= defaultEnemy.length()) continue;
      LinearLayout card = k.column();
      card.setGravity(Gravity.CENTER);
      card.setPadding(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
      card.setTag((enemy ? "practice-enemy-card-" : "practice-roster-card-") + slot);
      if (unit != null) {
        if (unit.optString("id").startsWith("V")) {
          // 이야기 기본 상대는 등록증 발급 대상이 아닌 빌런이다. 실제 서버 초상을 크게 쓴다.
          ImageView portrait = k.image(unit.optString("image"), 1, 1);
          portrait.setTag("practice-default-portrait");
          portrait.setScaleType(ImageView.ScaleType.FIT_CENTER);
          card.addView(portrait, new LinearLayout.LayoutParams(-1, 0, 1));
          card.addView(RegistrationCardView.stateLabel(h, unit.optString("name")));
        } else {
          RegistrationCardView full = new RegistrationCardView(h, unit, RegistrationCardView.FULL);
          card.addView(full, new LinearLayout.LayoutParams(-1, -1));
        }
        card.setContentDescription((slot + 1) + "번 " + RegistrationCardView.description(unit) + " · 편성 변경");
      } else {
        card.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.NAV));
        card.addView(sceneIcon("team", 56));
        TextView empty = heading("빈자리", 15); empty.setTextColor(JournalTheme.WHITE);
        empty.setGravity(Gravity.CENTER); card.addView(empty);
        card.setContentDescription((slot + 1) + "번 빈자리 · 갈매미 선택");
      }
      card.setMinimumHeight(h.dp(48));
      card.setOnClickListener(
          v -> {
            if (!h.busy) picker(enemy);
          });
      LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
      cell.setMargins(h.dp(2), 0, h.dp(2), 0); cards.addView(card, cell);
    }
    LinearLayout.LayoutParams cardArea = new LinearLayout.LayoutParams(-1, 0, 1);
    cardArea.topMargin = h.dp(4); parent.addView(cards, cardArea);
    if (defaultEnemy != null && defaultEnemy.length() == 0) {
      LinearLayout fallback = k.paper();
      fallback.addView(heading("이야기 1-6 상대", 17));
      fallback.addView(k.note("상대를 고르면 선택한 갈매미와 연습해요."));
      parent.addView(fallback);
    }
  }

  JSONArray practiceDefaultEnemy() {
    JSONArray chapters = h.list(h.state, "chapters");
    for (int i = 0; i < chapters.length(); i++) {
      JSONArray stages = h.list(chapters.optJSONObject(i), "stages");
      for (int j = 0; j < stages.length(); j++) {
        JSONObject stage = stages.optJSONObject(j);
        if ("1-6".equals(stage.optString("id"))) return h.list(stage, "enemy");
      }
    }
    return new JSONArray();
  }

  void picker(boolean enemy) {
    ArrayList<String> draft = new ArrayList<>(enemy ? h.practiceEnemy : h.practiceRoster);
    JSONArray all = h.list(h.state, "practiceUnits");
    LinearLayout contents = k.column();
    android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
    int[] page = {0};
    int count = Math.max(1, (all.length() + 2) / 3);
    Runnable[] paint = new Runnable[1];
    paint[0] = () -> {
      contents.removeAllViews();
      LinearLayout cards = k.row(); cards.setGravity(Gravity.CENTER);
      for (int i = page[0] * 3; i < Math.min(all.length(), (page[0] + 1) * 3); i++) {
        JSONObject unit = all.optJSONObject(i); String id = unit.optString("id");
        RegistrationCardView card = new RegistrationCardView(h, unit, RegistrationCardView.FULL);
        card.setTag("practice-choice-" + id);
        card.setBackground(RegistrationCardView.selection(h, draft.contains(id)));
        card.setSelected(draft.contains(id));
        card.setContentDescription(RegistrationCardView.description(unit) + (draft.contains(id) ? " · 선택됨" : " · 선택"));
        card.setOnClickListener(v -> {
          if (h.busy) return;
          if (draft.contains(id)) draft.remove(id);
          else if (draft.size() < 3) draft.add(id);
          else { h.message("3마리까지 선택해 주세요."); return; }
          paint[0].run();
        });
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -1, 1);
        slot.setMargins(h.dp(4), h.dp(3), h.dp(4), h.dp(3)); cards.addView(card, slot);
      }
      contents.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1));
      pageBar(contents, "practice-choices", page[0], count, value -> { page[0] = value; paint[0].run(); });
    };
    paint[0].run();
    dialog[0] =
        new GameDialog(h)
            .setTitle(enemy ? "연습 상대 1~3마리" : "연습 방범대 1~3마리")
            .setFixedBody(contents, h.compact() ? 232 : 382)
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
    slider.setProgressTintList(null);
    slider.setProgressBackgroundTintList(null);
    slider.setProgressDrawable(new BrassSliderTrack(h));
    slider.setThumbTintList(null);
    slider.setThumb(new BrassSliderThumb(h));
    slider.setSplitTrack(false);
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
    if (profilePopup != null) profilePopup.dismiss();
    LinearLayout panel = k.paper();
    panel.setTag("profile-popup");
    panel.setPadding(h.dp(12), h.dp(10), h.dp(12), h.dp(12));
    panel.setBackground(new GamePanelDrawable(h));
    TextView name = heading(h.me().optString("nickname", "수비조장"), 20);
    name.setTag("profile-name");
    name.setTextColor(JournalTheme.WHITE);
    name.setPadding(h.dp(16), h.dp(10), h.dp(16), h.dp(10));
    name.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.HEADER));
    panel.addView(name, new LinearLayout.LayoutParams(-1, -2));
    TextView rank = k.note("랭크를 불러오고 있어요…");
    rank.setTag("profile-rank");
    rank.setPadding(h.dp(6), h.dp(8), h.dp(6), h.dp(8));
    panel.addView(rank);
    int width =
        Math.min(
            h.dp(324),
            (int) h.safeBounds(h.root.getWidth(), h.root.getHeight()).width() - h.dp(16));
    PopupWindow popup = new PopupWindow(panel, width, -2, true);
    profilePopup = popup;
    popup.setOnDismissListener(() -> { if (profilePopup == popup) profilePopup = null; });
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
              null,
              entry[1],
              () -> {
                popup.dismiss();
                if (entry[2].equals("settings")) settings();
                else h.load(entry[2]);
              });
      button.setTag("profile-action-" + entry[2]);
      button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, h.dp(48));
      row.topMargin = h.dp(5);
      panel.addView(button, row);
    }
    popup.showAsDropDown(anchor, 0, h.dp(8));
    h.api.call(
        "pvp/rank",
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
                + " · 지원서 "
                + h.me().optInt("credits")
                + " · 새우gang "
                + h.me().optInt("snack")));
    TextView version = k.note("Android " + BuildConfig.VERSION_NAME);
    version.setTextSize(12);
    profile.addView(version);
    cols[0].addView(profile);
    LinearLayout controls = k.paper();
    controls.addView(heading("계정 및 기기", 18));
    LinearLayout links = k.row();
    h.cell(links, k.action("u-settings", "설정", this::settings));
    h.cell(links, k.action("mode-rank", "랭크 순위", () -> h.load("pvp")));
    controls.addView(links);
    controls.addView(
        k.action(
            null,
            "개인정보처리방침",
            () ->
                h.startActivity(
                    new Intent(
                        Intent.ACTION_VIEW, Uri.parse(BuildConfig.SERVER_URL + "/privacy")))));
    controls.addView(
        k.action(null, "로그아웃", () -> h.confirm("이 기기에서 로그아웃할까요? 서버에 저장된 진행은 유지돼요.", this::logout)));
    Button remove = k.action(null, "계정 삭제", this::deleteAccountSheet);
    remove.setBackground(new GameButtonDrawable(h, 0xffe8b4aa));
    controls.addView(remove);
    cols[1].addView(controls);
    m.fetch(
        "me",
        r -> {
          if (r.optBoolean("kakaoLogin")) {
            LinearLayout link = k.paper();
            link.addView(heading("카카오 계정", 18));
            boolean linked = r.optBoolean("kakao");
            boolean linking =
                h.getSharedPreferences("native_account", 0).getBoolean("linking", false);
            link.addView(
                k.note(
                    linking
                        ? "연결 확인 중 · 진행은 유지돼요."
                        : linked ? "✓ 카카오 계정 연결됨" : "코드 없이 로그인할 수 있어요."));
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
          cols[0].addView(k.action("m-account", "계정 안내", () -> accountInfoSheet(r)));
        });
  }

  void accountInfoSheet(JSONObject account) {
    k.openSheet(
        "계정 안내",
        () -> {
          LinearLayout info = k.column();
          info.setTag("account-help-content");
          info.addView(plaque(h.me().optString("nickname", "수비조장"), 20));
          info.addView(k.note("갈매미와 도감·재화·스토리 진행은 서버에 저장돼요. 로그아웃해도 유지됩니다."));
          if (account.optBoolean("kakaoLogin"))
            info.addView(
                k.note(
                    account.optBoolean("kakao")
                        ? "카카오 계정이 연결되어 있어요. 연결을 해제하면 다음부터 카톡 코드로 로그인해요. 진행은 유지돼요."
                        : "지금 계정에 카카오를 연결하면 기존 갈매미와 스토리를 유지한 채 다음부터 코드 없이 로그인할 수 있어요."));
          info.addView(k.note("Android " + BuildConfig.VERSION_NAME));
          return info;
        });
  }

  void deleteAccountSheet() {
    android.app.AlertDialog[] sheet = new android.app.AlertDialog[1];
    sheet[0] =
        k.openSheet(
            "계정 삭제(탈퇴)",
            () -> {
              LinearLayout danger = k.column();
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
                                      (done, error) -> {
                                        if (sheet[0] != null) sheet[0].dismiss();
                                        finishLogout();
                                      })));
              remove.setBackground(new GameButtonDrawable(h, 0xffe8b4aa));
              remove.setEnabled(false);
              k.search(
                  danger,
                  "확인을 위해 ‘탈퇴’라고 적어 주세요",
                  "",
                  value -> remove.setEnabled(value.trim().equals("탈퇴")));
              danger.addView(remove);
              return danger;
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

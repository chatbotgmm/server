package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;
import org.json.*;

/** 기존 게임 화면의 구성과 흐름을 옮긴다. 화면마다 다른 레이아웃을 유지한다. */
final class NativeMenus {
  final MainActivity h;
  final MenuKit k;
  final NativeLibrary library;
  final NativeEconomy economy;
  final NativeExtras extras;
  final NativeGrowthGuidance growth;
  String query = "", grade = "all", recipeQuery = "", recipeId = "";
  boolean craftOnly, teamChart;
  String selectedStageId = "";
  JSONObject lastDraw;
  int pendingFetches;
  ArrayList<String> teamDraft, mixDraft = new ArrayList<>();
  String teamMode = "", unitReturn = "story";
  int teamScrollX;
  ArrayList<String> teamSavedIds = new ArrayList<>();
  String teamReturn = "", teamQuery = "", teamAttr = "all", stageFocusId = "";
  boolean teamSelectedOnly;

  NativeMenus(MainActivity h) {
    this.h = h;
    k = new MenuKit(h);
    library = new NativeLibrary(this);
    economy = new NativeEconomy(this);
    extras = new NativeExtras(this);
    growth = new NativeGrowthGuidance(h, k);
  }

  void reset() {
    query = "";
    grade = "all";
    recipeQuery = "";
    recipeId = "";
    lastDraw = null;
    teamDraft = null;
    teamSavedIds.clear();
    teamMode = "";
    teamReturn = "";
    teamQuery = "";
    teamAttr = "all";
    teamSelectedOnly = false;
    stageFocusId = "";
    selectedStageId = "";
    mixDraft.clear();
    library.reset();
    economy.resetAcquisition();
    unitReturn = "story";
    teamScrollX = 0;
    extras.guide = 0;
  }

  void fetch(String endpoint, Consumer<JSONObject> next) {
    int epoch = h.epoch;
    pendingFetches++;
    h.api.call(
        endpoint,
        null,
        (r, e) -> {
          pendingFetches--;
          if (h.isFinishing() || h.epoch != epoch) return;
          if (e != null) {
            if (h.api.token().isEmpty()) h.login();
            h.message(e);
          } else next.accept(r);
        });
  }

  void intro(LinearLayout parent, String icon, String name, String note) {
    LinearLayout row = k.row();
    if (parent.getBackground() == null) {
      row.setBackground(new GamePaperDrawable(h));
      row.setPadding(h.dp(6), h.dp(4), h.dp(6), h.dp(4));
    }
    row.addView(k.image("/duel/icons/" + icon + ".png", 40, 40));
    LinearLayout words = k.column();
    words.addView(heading(name, 22));
    if (!note.isEmpty()) words.addView(k.note(note));
    row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    parent.addView(row);
  }

  TextView heading(String label, int size) {
    TextView title = h.text(label, size);
    title.setTypeface(h.headline);
    return title;
  }

  void modes() {
    h.base("게임 모드", "modes");
    AppSkin.apply(h, "training");
    LinearLayout[] cols = k.split(.49f);
    LinearLayout campaign = cols[0], pvp = cols[1];
    campaign.addView(k.section("캠페인"));
    Button story = k.primary("u-story", "스토리", () -> h.load("story"));
    NativeTutorialController.anchor(story, "story");
    campaign.addView(story);
    campaign.addView(k.action("m-units", "다음 이야기 · 편성", () -> modeTeamSheet(false)));
    Button adventure = k.action("mode-adv", "모험 준비 중", () -> {});
    adventure.setEnabled(false);
    campaign.addView(adventure);
    campaign.addView(k.notice("완료한 전투에 다시 도전해 경험치를 모을 수 있어요."));
    pvp.addView(k.section("PvP"));
    TextView rank = k.notice("랭크 불러오는 중…"), rules = k.notice("");
    LinearLayout start = k.row();
    h.cell(start, k.action("mode-normal", "일반 게임", () -> startPvp("normal")));
    h.cell(start, k.primary("mode-rank", "랭크게임", () -> startPvp("rank")));
    pvp.addView(start);
    LinearLayout links = k.row();
    h.cell(links, k.action(null, "순위표", () -> h.load("pvp")));
    h.cell(links, k.action(null, "연습", h::practice));
    h.cell(
        links,
        k.action(
            null,
            "전투 편성",
            () -> {
              h.selectionMode = "pvp";
              h.load("team");
            }));
    pvp.addView(links);
    pvp.addView(rank);
    pvp.addView(rules);
    pvp.addView(k.action("m-units", "현재 편성 확인", () -> modeTeamSheet(true)));
    fetch(
        "pvp/rank",
        result -> {
          JSONObject me = result.optJSONObject("me");
          if (me != null)
            rank.setText(
                me.optString("tier")
                    + " · "
                    + me.optInt("point")
                    + "점"
                    + (me.optInt("rank") > 0 ? " · " + me.optInt("rank") + "위" : "")
                    + "\n"
                    + me.optInt("wins")
                    + "승 "
                    + me.optInt("losses")
                    + "패 · Lv"
                    + result.optInt("level"));
          rules.setText(
              "승리 +"
                  + result.optInt("win")
                  + " / 패배 −"
                  + result.optInt("lose")
                  + "점"
                  + (result.optBoolean("team", h.list(h.me(), "battleTeam").length() == 3)
                      ? ""
                      : " · 전투 편성 3마리 필요"));
        });
  }

  void modeTeamSheet(boolean pvp) {
    k.openSheet(
        pvp ? "현재 전투 편성" : "다음 이야기",
        () -> {
          LinearLayout contents = k.column();
          addModeTeamSummary(contents, pvp);
          return contents;
        });
  }

  void addModeTeamSummary(LinearLayout parent, boolean pvp) {
    LinearLayout summary = k.paper();
    summary.addView(heading(pvp ? "현재 전투 편성" : "다음 이야기", 18));
    if (!pvp) {
      JSONObject next = null;
      JSONArray chapters = h.list(h.state, "chapters");
      for (int c = 0; c < chapters.length() && next == null; c++) {
        JSONArray stages = h.list(chapters.optJSONObject(c), "stages");
        for (int i = 0; i < stages.length(); i++) {
          JSONObject stage = stages.optJSONObject(i);
          if (stage.optBoolean("open") && !stage.optBoolean("cleared")) {
            next = stage;
            break;
          }
        }
      }
      summary.addView(k.note(next == null ? "완료한 이야기에 다시 도전할 수 있어요." : next.optString("title")));
    }
    LinearLayout line = k.row();
    JSONArray selected = h.list(pvp ? h.me() : h.state, pvp ? "battleTeam" : "team");
    for (int i = 0; i < selected.length(); i++) {
      String key = selected.optString(i);
      JSONObject unit = pvp ? ownedSpecies(key) : h.unit(key);
      LinearLayout member = k.column();
      member.setGravity(Gravity.CENTER_HORIZONTAL);
      member.addView(k.image(pvp ? "/thumbs/" + key + ".jpg" : unit.optString("image"), 76, 76));
      TextView name = k.note((i == 0 ? "선봉 · " : "") + unit.optString("name"));
      name.setGravity(Gravity.CENTER);
      name.setMaxLines(2);
      member.addView(name);
      h.cell(line, member);
    }
    if (selected.length() == 0) line.addView(k.note("아직 편성한 갈매미가 없어요."));
    summary.addView(line);
    parent.addView(summary);
  }

  JSONObject ownedSpecies(String id) {
    JSONArray units = h.list(h.me(), "units");
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.optJSONObject(i);
      if (id.equals(unit.optString("id"))) return unit;
    }
    return new JSONObject();
  }

  void startPvp(String mode) {
    if (h.list(h.me(), "battleTeam").length() != 3) {
      h.confirm(
          "PvP는 전투 편성 3마리가 필요해요. 지금 편성할까요?",
          () -> {
            h.selectionMode = "pvp";
            teamDraft = null;
            h.load("team");
          });
      return;
    }
    if (mode.equals("rank"))
      h.confirm("실제 유저와 실시간으로 대전해요. 매칭 후 나가면 패배로 기록돼요. 시작할까요?", () -> h.startFight(mode, null));
    else h.startFight(mode, null);
  }

  void story(boolean stages) {
    JSONArray chapters = h.list(h.state, "chapters");
    JSONObject chapter = chapters.optJSONObject(h.chapter);
    h.base(
        stages && chapter != null ? chapter.optString("title") : "스토리 · 챕터 선택",
        stages ? "stages" : "story");
    LinearLayout[] cols = k.split(.36f);
    LinearLayout team = cols[0], content = cols[1];
    JSONArray keys = h.list(h.state, "team");
    ArrayList<JSONObject> storyTeam = new ArrayList<>();
    ArrayList<View> storyCards = new ArrayList<>();
    ArrayList<String> storyKeys = new ArrayList<>();
    for (int i = 0; i < keys.length(); i++) {
      String key = keys.optString(i);
      JSONObject u = h.unit(key);
      storyTeam.add(u);
      LinearLayout tile =
          storyRosterUnit(
              u,
              i,
              () -> {
                unitReturn = stages ? "stages" : "story";
                h.unitDetail(key);
              },
              () -> reorderStory(key, stages));
      LinearLayout.LayoutParams unitSlot = new LinearLayout.LayoutParams(-1, -2);
      unitSlot.bottomMargin = h.dp(2);
      team.addView(tile, unitSlot);
      storyCards.add(tile);
      storyKeys.add(key);
    }
    NativeTeamReorder.bind(
        h,
        team,
        storyCards,
        storyKeys,
        changed -> {
          int screenEpoch = h.epoch;
          int[] scroll = scrollPositions(h.page);
          h.request(
              "native/team",
              MainActivity.obj("keys", MainActivity.arr(changed)),
              (r, e) -> {
                if (screenEpoch == h.epoch) refreshStory(stages, scroll);
              });
        });
    if (storyCards.size() > 1 && h.contentHeightDp() >= 360) {
      TextView dragHint = k.note("↕ 끌어서 선봉 변경 · 바로 저장");
      dragHint.setTag("story-order-drag-hint");
      dragHint.setTextSize(h.uiText(12));
      dragHint.setTextColor(JournalTheme.INK);
      dragHint.setBackground(new GamePaperDrawable(h));
      dragHint.setPadding(h.dp(6), h.dp(2), h.dp(6), h.dp(2));
      team.addView(dragHint, 0);
    }

    if (keys.length() == 0) team.addView(k.note("먼저 수비대를 정해 주세요."));
    LinearLayout storyHeader = (LinearLayout) h.page.getChildAt(0);
    Button editTeam =
        k.action(
            "m-units",
            "편성",
            () -> {
              h.selectionMode = "story";
              teamDraft = null;
              teamReturn = stages ? "stages" : "story";
              h.team();
            });
    Button healing =
        k.action(
            "potion",
            "치료약",
            () -> library.healSheet(null, () -> h.load(stages ? "stages" : "story")));
    editTeam.setPadding(h.dp(6), 0, h.dp(6), h.dp(3));
    healing.setPadding(h.dp(6), 0, h.dp(6), h.dp(3));
    LinearLayout.LayoutParams teamAction = new LinearLayout.LayoutParams(h.dp(88), h.dp(48));
    teamAction.leftMargin = h.dp(4);
    LinearLayout.LayoutParams healAction = new LinearLayout.LayoutParams(h.dp(104), h.dp(48));
    healAction.leftMargin = h.dp(4);
    storyHeader.addView(editTeam, 2, teamAction);
    storyHeader.addView(healing, 3, healAction);
    if (!stages || chapter == null) {
      HorizontalScrollView scroll = new HorizontalScrollView(h);
      LinearLayout row = k.row();
      scroll.addView(row);
      content.addView(scroll);
      for (int i = 0; i < chapters.length(); i++) {
        int index = i;
        JSONObject c = chapters.optJSONObject(i);
        JSONArray ss = h.list(c, "stages");
        int cleared = 0, opened = 0;
        for (int j = 0; j < ss.length(); j++) {
          if (ss.optJSONObject(j).optBoolean("cleared")) cleared++;
          if (ss.optJSONObject(j).optBoolean("open")) opened++;
        }
        FrameLayout card = new FrameLayout(h);
        String scene =
            ss.length() > 0 ? ss.optJSONObject(0).optString("scene", "harbor") : "harbor";
        ImageView backdrop = k.image("/duel/bg/" + scene + ".jpg", 1, 1);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout info = k.column();
        info.setBackground(new GamePaperDrawable(h));
        info.setPadding(h.dp(14), h.dp(12), h.dp(14), h.dp(12));
        TextView name = heading(c.optString("title"), 19);
        name.setTextColor(JournalTheme.INK);
        info.addView(name);
        TextView progress = k.note(cleared + " / " + ss.length() + " 완료");
        progress.setTextColor(JournalTheme.MUTED);
        info.addView(progress);
        JSONObject chapterLock = c.optJSONObject("lock");
        if (chapterLock != null && !chapterLock.optBoolean("open")) {
          TextView need = k.note("잠김 · 소개에서 조건 확인");
          need.setTextColor(0xff8b4d13);
          info.addView(need);
        } else if (c.optInt("needStage") > 0) {
          TextView need = k.note("팀 전원 " + c.optInt("needStage") + "단계 승진");
          need.setTextColor(0xff276646);
          info.addView(need);
        }
        Button enter =
            k.action(
                null,
                "선택 ›",
                () -> {
                  h.chapter = index;
                  h.stages();
                });
        if (index == 0) NativeTutorialController.anchor(enter, "chapter");
        LinearLayout chapterActions = k.row();
        h.cell(chapterActions, enter);
        h.cell(chapterActions, k.action(null, "소개", () -> storyGuide(c)));
        info.addView(chapterActions);
        card.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        card.setBackground(new GamePaperDrawable(h));
        card.setClipToOutline(true);
        card.setOutlineProvider(
            new ViewOutlineProvider() {
              public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), h.dp(16));
              }
            });
        int availableHeight = h.contentHeightDp() - 88;
        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(h.dp(260), h.dp(Math.max(144, availableHeight)));
        lp.setMargins(h.dp(4), 0, h.dp(4), 0);
        row.addView(card, lp);
      }
      LinearLayout guide = k.paper();
      guide.addView(heading("성장 안내", 20));
      guide.addView(k.note("완료한 전투에 다시 도전해요."));
      JSONObject shownChapter = chapter == null ? new JSONObject() : chapter;
      LinearLayout guideActions = k.row();
      h.cell(guideActions, k.action(null, "성장", () -> storyGuide(shownChapter)));
      h.cell(guideActions, k.action(null, "합동기", () -> storyCombos(storyTeam)));
      guide.addView(guideActions);
      guide.addView(k.action(null, "스토리 처음부터", this::resetStory));
      LinearLayout.LayoutParams guideSlot = new LinearLayout.LayoutParams(h.dp(240), -1);
      guideSlot.setMargins(h.dp(4), 0, h.dp(4), 0);
      row.addView(guide, guideSlot);
    } else {
      JSONArray stagesInChapter = h.list(chapter, "stages");
      int chosen = -1;
      String desired = stageFocusId.isEmpty() ? selectedStageId : stageFocusId;
      for (int i = 0; i < stagesInChapter.length(); i++)
        if (stagesInChapter.optJSONObject(i).optString("id").equals(desired)) chosen = i;
      if (chosen < 0) {
        chosen = 0;
        for (int i = 0; i < stagesInChapter.length(); i++) {
          JSONObject stage = stagesInChapter.optJSONObject(i);
          if (stage.optBoolean("open") && !stage.optBoolean("cleared")) {
            chosen = i;
            break;
          }
        }
      }
      stageFocusId = "";
      final int at = chosen;
      JSONObject selected = stagesInChapter.optJSONObject(chosen);
      if (selected != null) {
        selectedStageId = selected.optString("id");
        content.addView(stageCard(selected));
      } else content.addView(k.notice("아직 공개된 전투가 없어요."));
      LinearLayout navigation = k.row();
      Button previous = k.action(null, "‹ 이전", () -> selectStoryStage(stagesInChapter, at - 1));
      previous.setEnabled(at > 0);
      h.cell(navigation, previous);
      h.cell(navigation, k.action("u-story", "전투 선택", () -> storyStagePicker(stagesInChapter)));
      Button next = k.action(null, "다음 ›", () -> selectStoryStage(stagesInChapter, at + 1));
      next.setEnabled(at + 1 < stagesInChapter.length());
      h.cell(navigation, next);
      content.addView(navigation);
      if (chapter.optInt("needStage") > 0)
        content.addView(k.note("팀 전원 " + chapter.optInt("needStage") + "단계 승진 필요"));
      LinearLayout guideActions = k.row();
      h.cell(guideActions, k.action(null, "부 소개 · 성장", () -> storyGuide(chapter)));
      h.cell(guideActions, k.action(null, "합동기", () -> storyCombos(storyTeam)));
      content.addView(guideActions);
    }
    startStoryRefresh(stages);
  }

  /** 세 동료의 원문 이름·성장·체력을 한 화면에 두고 48dp 순서 손잡이를 유지한다. */
  LinearLayout storyRosterUnit(JSONObject unit, int order, Runnable open, Runnable reorder) {
    LinearLayout card = k.row(), info = k.column();
    card.setTag("story-team-unit-" + unit.optString("key"));
    card.setBackground(new GamePaperDrawable(h));
    card.setPadding(h.dp(6), h.dp(2), h.dp(6), h.dp(2));
    card.addView(k.image(unit.optString("image"), 44, 48));
    TextView name = heading(unit.optString("name"), h.compact() ? 15 : 19);
    name.setSingleLine(true);
    name.setPadding(h.dp(2), 0, h.dp(2), 0);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    info.addView(name);
    String attr = unit.optString("attrName", unit.optString("attr"));
    TextView health =
        k.note(
            "Lv"
                + unit.optInt("level")
                + " · "
                + attr
                + " · "
                + unit.optInt("hp")
                + "/"
                + unit.optInt("maxHp"));
    health.setPadding(h.dp(2), 0, h.dp(2), 0);
    info.addView(health);
    String growth =
        h.list(unit, "evolve").length() > 0
            ? "승진"
            : h.list(unit, "promo").length() > 0
                ? "전용기"
                : h.list(unit, "learn").length() > 0 ? "새 기술" : "";
    TextView experience =
        k.note(
            "XP "
                + unit.optInt("xp")
                + "/"
                + unit.optInt("xpNext")
                + (growth.isEmpty() ? "" : " · " + growth));
    experience.setPadding(h.dp(2), 0, h.dp(2), 0);
    experience.setTextColor(growth.isEmpty() ? JournalTheme.MUTED : JournalTheme.TEAL);
    info.addView(experience);
    card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
    TextView handle = h.text((order == 0 ? "선봉" : (order + 1) + "번") + "\n↕", 14);
    handle.setGravity(Gravity.CENTER);
    handle.setPadding(0, 0, 0, 0);
    handle.setTextColor(JournalTheme.TEAL);
    handle.setTag(NativeTeamReorder.HANDLE_TAG);
    handle.setFocusable(true);
    handle.setClickable(true);
    handle.setContentDescription(unit.optString("name") + " 출전 순서. 위아래로 끌어 변경, 누르면 순서 메뉴");
    card.addView(handle, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    card.setContentDescription(
        (order == 0 ? "선봉 " : (order + 1) + "번 ")
            + unit.optString("name")
            + " · "
            + health.getText()
            + " · "
            + experience.getText()
            + " · "
            + NativeGrowthGuidance.badge(unit)
            + ". 누르면 성장 보기. 손잡이를 위아래로 끌어 순서 변경");
    card.setOnClickListener(v -> open.run());
    card.setOnLongClickListener(
        v -> {
          reorder.run();
          return true;
        });
    return card;
  }

  void selectStoryStage(JSONArray stages, int index) {
    JSONObject stage = stages.optJSONObject(index);
    if (stage == null) return;
    selectedStageId = stage.optString("id");
    story(true);
  }

  void storyStagePicker(JSONArray stages) {
    k.openSheet(
        "전투 선택",
        () -> {
          LinearLayout choices = k.column();
          for (int i = 0; i < stages.length(); i++) {
            int index = i;
            JSONObject stage = stages.optJSONObject(i);
            Button select =
                k.action(
                    null,
                    (stage.optBoolean("cleared") ? "✓ " : stage.optBoolean("open") ? "" : "잠김 · ")
                        + stage.optString("title"),
                    () -> selectStoryStage(stages, index));
            select.setSelected(stage.optString("id").equals(selectedStageId));
            choices.addView(select, new LinearLayout.LayoutParams(-1, h.dp(48)));
          }
          return choices;
        });
  }

  void storyCombos(Collection<JSONObject> members) {
    k.openSheet(
        "우리 수비대 합동기",
        () -> {
          LinearLayout content = k.column();
          content.addView(growth.combos(members));
          return content;
        });
  }

  void storyGuide(JSONObject chapter) {
    k.openSheet(
        "부 소개 · 성장 안내",
        () -> {
          LinearLayout guide = k.column();
          guide.addView(heading(chapter.optString("title"), 22));
          if (!chapter.optString("intro").isEmpty())
            guide.addView(k.note(chapter.optString("intro")));
          JSONObject lock = chapter.optJSONObject("lock");
          if (lock != null && !lock.optBoolean("open"))
            guide.addView(k.notice(lock.optString("reason")));
          if (chapter.optInt("needStage") > 0)
            guide.addView(k.note("팀 전원 " + chapter.optInt("needStage") + "단계 승진 필요"));
          guide.addView(k.note("진 전투에서도 경험치를 받아요. 완료한 전투는 다시 도전해 레벨을 올릴 수 있어요."));
          guide.addView(k.note("레벨이 오르면 체력이 가득 차요. 새 기술·전용 기술·승진 가능 표시는 팀 칸에서 확인해 주세요."));
          guide.addView(k.action(null, "스토리 처음부터", this::resetStory));
          return guide;
        });
  }

  LinearLayout stageCard(JSONObject stage) {
    JSONArray foes = h.list(stage, "enemy");
    JSONObject foe = foes.optJSONObject(0);
    String id = foe == null ? "" : foe.optString("id");
    JSONObject portraits = h.state.optJSONObject("portraits");
    LinearLayout card = k.paper(), line = k.row();
    card.setTag("story-stage-" + stage.optString("id"));
    line.addView(k.image(portraits == null ? "" : portraits.optString(id), 60, 60));
    line.addView(
        heading(
            (stage.optBoolean("cleared") ? "완료 · " : stage.optBoolean("open") ? "" : "잠김 · ")
                + stage.optString("title"),
            20),
        new LinearLayout.LayoutParams(0, -2, 1));
    card.addView(line);
    ArrayList<String> opponents = new ArrayList<>();
    JSONObject names = h.state.optJSONObject("names");
    for (int j = 0; j < foes.length(); j++) {
      JSONObject enemy = foes.optJSONObject(j);
      opponents.add(
          (names == null
                  ? enemy.optString("id")
                  : names.optString(enemy.optString("id"), enemy.optString("id")))
              + " Lv"
              + enemy.optInt("level")
              + (enemy.optBoolean("boss") ? " · 보스" : ""));
    }
    card.addView(k.note(android.text.TextUtils.join(" · ", opponents)));
    Button start =
        k.action(
            "u-story",
            stage.optBoolean("cleared")
                ? "다시 도전"
                : stage.optBoolean("open") ? "이야기 시작" : "잠금 조건 확인",
            () -> {
              if (!stage.optBoolean("open")) {
                showStageLock(stage);
                return;
              }
              h.storyScene = stage.optString("scene", "harbor");
              h.storyFoe = id;
              h.talk(
                  withTip(h.list(stage, "before"), stage.optString("hint")),
                  stage.optString("title"),
                  () -> h.startFight("story", stage.optString("id")));
            });
    if (stage.optBoolean("open")) k.emphasize(start);
    if (stage.optString("id").equals("P-1")) NativeTutorialController.anchor(start, "stage");
    card.addView(start);
    return card;
  }

  void showStageLock(JSONObject stage) {
    JSONObject lock = stage.optJSONObject("lock");
    LinearLayout content = k.column();
    content.setPadding(h.dp(12), h.dp(8), h.dp(12), h.dp(8));
    content.addView(
        k.note(lock == null ? "앞 전투의 완료 여부와 현재 수비대를 다시 확인해 주세요." : lock.optString("reason")));
    JSONArray requirements = h.list(lock == null ? new JSONObject() : lock, "requirements");
    for (int i = 0; i < requirements.length(); i++) {
      JSONObject requirement = requirements.optJSONObject(i);
      boolean met = requirement.optBoolean("met");
      TextView item = k.note((met ? "✓ " : "○ ") + requirement.optString("label"));
      item.setTextColor(met ? 0xff276646 : 0xff8b4d13);
      content.addView(item);
      if (!met) {
        String action = requirement.optString("action");
        if (action.equals("stage") || action.equals("growth") || action.equals("team")) {
          String caption =
              action.equals("stage")
                  ? "필요한 전투로"
                  : action.equals("growth") ? requirement.optString("name") + " 성장" : "수비대 편성";
          content.addView(k.action(null, caption, () -> navigateRequirement(requirement)));
        }
        JSONArray conditions = h.list(requirement, "conditions");
        for (int j = 0; j < conditions.length(); j++) {
          JSONObject condition = conditions.optJSONObject(j);
          content.addView(
              k.note(
                  "  "
                      + (condition.optBoolean("met") ? "✓ " : "○ ")
                      + condition.optString("label")));
          if (!condition.optBoolean("met") && condition.optString("action").equals("stage"))
            content.addView(k.action(null, "필요한 부 진행", () -> navigateRequirement(condition)));
        }
      }
    }
    new GameDialog(h)
        .setTitle(stage.optString("title") + " · 잠금 조건")
        .setView(k.scroll(content))
        .setNegativeButton("닫기", null)
        .show();
  }

  void navigateRequirement(JSONObject requirement) {
    if (h.visibleDialog != null) h.visibleDialog.dismiss();
    String action = requirement.optString("action");
    if (action.equals("growth") && !requirement.optString("key").isEmpty()) {
      unitReturn = "stages";
      h.unitDetail(requirement.optString("key"));
    } else if (action.equals("team")) {
      h.selectionMode = "story";
      teamDraft = null;
      teamReturn = "stages";
      h.team();
    } else if (action.equals("stage")) {
      String id = requirement.optString("stageId");
      int chapter = requirement.optInt("chapterIndex", h.chapter);
      JSONArray chapters = h.list(h.state, "chapters");
      for (int c = 0; c < chapters.length(); c++) {
        JSONArray stages = h.list(chapters.optJSONObject(c), "stages");
        for (int i = 0; i < stages.length(); i++)
          if (!id.isEmpty() && id.equals(stages.optJSONObject(i).optString("id"))) chapter = c;
      }
      h.chapter = Math.max(0, Math.min(chapters.length() - 1, chapter));
      stageFocusId = id;
      h.load("stages");
    }
  }

  void revealStage(View card) {
    ViewParent parent = card.getParent();
    while (parent instanceof View) {
      if (parent instanceof ScrollView) {
        ScrollView scroll = (ScrollView) parent;
        android.graphics.Rect bounds = new android.graphics.Rect();
        card.getDrawingRect(bounds);
        scroll.offsetDescendantRectToMyCoords(card, bounds);
        scroll.smoothScrollTo(0, bounds.top);
        break;
      }
      parent = parent.getParent();
    }
  }

  void startStoryRefresh(boolean stages) {
    android.os.Handler ticker = new android.os.Handler(android.os.Looper.getMainLooper());
    String currentScreen = stages ? "stages" : "story";
    View page = h.page;
    int screenEpoch = h.epoch;
    Runnable refresh =
        new Runnable() {
          public void run() {
            if (!page.isAttachedToWindow()
                || !currentScreen.equals(h.screen)
                || screenEpoch != h.epoch) return;
            if (h.busy || !h.getWindow().getDecorView().hasWindowFocus()) {
              ticker.postDelayed(this, 60_000);
              return;
            }
            refreshStory(stages, scrollPositions(page));
          }
        };
    ticker.postDelayed(refresh, 60_000);
    page.addOnAttachStateChangeListener(
        new View.OnAttachStateChangeListener() {
          public void onViewAttachedToWindow(View v) {}

          public void onViewDetachedFromWindow(View v) {
            ticker.removeCallbacks(refresh);
          }
        });
  }

  void refreshStory(boolean stages, int[] scroll) {
    int epoch = h.epoch;
    h.busy = true;
    h.api.call(
        "native/state",
        null,
        (updated, error) -> {
          h.busy = false;
          if (h.isFinishing() || epoch != h.epoch) return;
          if (error != null) {
            startStoryRefresh(stages);
            return;
          }
          h.state = updated;
          h.stateUpdated();
          story(stages);
          View page = h.page;
          page.post(
              () -> {
                if (page == h.page) restoreScroll(page, scroll, new int[] {0});
              });
        });
  }

  int[] scrollPositions(View view) {
    ArrayList<Integer> values = new ArrayList<>();
    gatherScroll(view, values);
    int[] positions = new int[values.size()];
    for (int i = 0; i < positions.length; i++) positions[i] = values.get(i);
    return positions;
  }

  void gatherScroll(View view, ArrayList<Integer> values) {
    if (view instanceof ScrollView) values.add(view.getScrollY());
    if (view instanceof HorizontalScrollView) values.add(view.getScrollX());
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) gatherScroll(group.getChildAt(i), values);
    }
  }

  void restoreScroll(View view, int[] values, int[] at) {
    if (view instanceof ScrollView && at[0] < values.length) view.scrollTo(0, values[at[0]++]);
    if (view instanceof HorizontalScrollView && at[0] < values.length)
      view.scrollTo(values[at[0]++], 0);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        restoreScroll(group.getChildAt(i), values, at);
    }
  }

  void reorderStory(String key, boolean stages) {
    JSONArray saved = h.list(h.state, "team");
    ArrayList<String> keys = new ArrayList<>();
    for (int i = 0; i < saved.length(); i++) keys.add(saved.optString(i));
    if (keys.size() < 2) {
      h.message("팀에 두 마리 이상 있을 때 순서를 바꿀 수 있어요.");
      return;
    }
    String[] positions = new String[keys.size()];
    for (int i = 0; i < positions.length; i++) positions[i] = i == 0 ? "선봉으로" : (i + 1) + "번째로";
    new GameDialog(h)
        .setTitle(h.unit(key).optString("name") + " · 출전 순서")
        .setItems(
            positions,
            (d, target) -> {
              keys.remove(key);
              keys.add(target, key);
              h.request(
                  "native/team",
                  MainActivity.obj("keys", MainActivity.arr(keys)),
                  (r, e) -> h.load(stages ? "stages" : "story"));
            })
        .setNegativeButton("닫기", null)
        .show();
  }

  void resetStory() {
    h.confirm(
        "레벨·경험치·배운 기술·승진·완료한 스토리를 모두 초기화할까요? 가진 갈매미와 재화는 유지돼요.",
        () ->
            h.confirm(
                "초기화한 스토리는 되돌릴 수 없어요. 처음부터 시작할까요?",
                () ->
                    h.request(
                        "native/story/reset",
                        MainActivity.obj("confirm", "RESET"),
                        (r, e) -> {
                          h.chapter = 0;
                          h.load("story");
                        })));
  }

  JSONArray withTip(JSONArray before, String hint) {
    JSONArray lines = new JSONArray();
    for (int i = 0; i < before.length(); i++) lines.put(before.opt(i));
    if (!hint.isEmpty()) lines.put(MainActivity.obj("who", "TIP", "say", hint));
    return lines;
  }

  void startPodiumQuotes(TextView label, Collection<JSONObject> picked) {
    if (!h.motionEnabled) return;
    ArrayList<String> quotes = new ArrayList<>();
    for (JSONObject u : picked) {
      String quote = u.optString("quote");
      if (quote.isEmpty()) {
        JSONArray owned = h.list(h.me(), "units");
        for (int i = 0; i < owned.length(); i++)
          if (u.optString("id").equals(owned.optJSONObject(i).optString("id")))
            quote = owned.optJSONObject(i).optString("quote");
      }
      if (!quote.isEmpty()) quotes.add(u.optString("name") + " · 「" + quote + "」");
    }
    if (quotes.isEmpty()) return;
    android.os.Handler timer = new android.os.Handler(android.os.Looper.getMainLooper());
    int epoch = h.epoch;
    Runnable cycle =
        new Runnable() {
          int index;

          public void run() {
            if (!label.isAttachedToWindow() || epoch != h.epoch) return;
            label.setText(quotes.get(index++ % quotes.size()));
            label.setTextColor(JournalTheme.MUTED);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            timer.postDelayed(this, 3600);
          }
        };
    timer.postDelayed(cycle, 3600);
    label.addOnAttachStateChangeListener(
        new View.OnAttachStateChangeListener() {
          public void onViewAttachedToWindow(View v) {}

          public void onViewDetachedFromWindow(View v) {
            timer.removeCallbacks(cycle);
          }
        });
  }

  void defenderTabs(LinearLayout parent, int active) {
    k.tabs(
        parent,
        new String[] {"내 갈매미", "전투 편성", "아이템"},
        active,
        i -> {
          if (i == active) return;
          Runnable change =
              () -> {
                if (i == 0) h.collection();
                else if (i == 1) {
                  h.selectionMode =
                      NativeTutorialController.get(h).needsStoryTeam() ? "story" : "pvp";
                  teamDraft = null;
                  h.team();
                } else h.inventory();
              };
          if (h.screen.equals("team")) requestLeaveTeam(change);
          else change.run();
        });
    View last = parent.getChildAt(parent.getChildCount() - 1);
    if (last instanceof LinearLayout && ((LinearLayout) last).getChildCount() > 1)
      NativeTutorialController.anchor(((LinearLayout) last).getChildAt(1), "team");
  }

  boolean isTeamDirty() {
    return teamDraft != null && !teamDraft.equals(teamSavedIds);
  }

  String teamReturnTarget() {
    if (!teamReturn.isEmpty()) return teamReturn;
    return teamMode.equals("pvp") ? "collection" : "story";
  }

  void requestLeaveTeam(Runnable leave) {
    if (h.busy) return;
    if (!isTeamDirty()) {
      teamDraft = null;
      leave.run();
      return;
    }
    new GameDialog(h)
        .setTitle("편성을 저장할까요?")
        .setMessage("선택한 갈매미와 출전 순서가 아직 저장되지 않았어요.")
        .setPositiveButton("저장하고 나가기", (dialog, which) -> saveTeamDraft(leave))
        .setNeutralButton(
            "변경 취소",
            (dialog, which) -> {
              teamDraft = null;
              leave.run();
            })
        .setNegativeButton("계속 편집", null)
        .show();
  }

  void saveTeamDraft(Runnable completed) {
    if (teamDraft == null || teamDraft.isEmpty()) {
      h.message("1마리 이상 골라 주세요.");
      return;
    }
    ArrayList<String> submitted = new ArrayList<>(teamDraft);
    boolean pvp = teamMode.equals("pvp");
    h.request(
        pvp ? "battle-team" : "native/team",
        MainActivity.obj(pvp ? "ids" : "keys", MainActivity.arr(submitted)),
        (result, error) -> {
          if (error != null) return;
          teamSavedIds = submitted;
          teamDraft = null;
          completed.run();
        });
  }

  void team() {
    boolean pvp = h.selectionMode.equals("pvp");
    String mode = pvp ? "pvp" : "story";
    boolean beginning = teamDraft == null || !mode.equals(teamMode);
    if (beginning) {
      String source = h.screen;
      if (!source.equals("team") && !source.equals("unit"))
        teamReturn =
            source.equals("stages")
                    || source.equals("story")
                    || source.equals("modes")
                    || source.equals("collection")
                    || source.equals("acquisition")
                    || source.equals("pvp")
                ? source
                : pvp ? "collection" : "story";
      teamMode = mode;
      teamScrollX = 0;
      teamQuery = "";
      teamAttr = "all";
      teamSelectedOnly = false;
      teamSavedIds = new ArrayList<>();
      JSONArray saved = h.list(pvp ? h.me() : h.state, pvp ? "battleTeam" : "team");
      for (int i = 0; i < saved.length(); i++) teamSavedIds.add(saved.optString(i));
      teamDraft = new ArrayList<>(teamSavedIds);
    } else {
      // 저장 응답이 끊긴 뒤 상태를 다시 확인했을 때도 임시 선택은 유지하고 실제 저장본만 갱신한다.
      JSONArray saved = h.list(pvp ? h.me() : h.state, pvp ? "battleTeam" : "team");
      teamSavedIds = new ArrayList<>();
      for (int i = 0; i < saved.length(); i++) teamSavedIds.add(saved.optString(i));
    }
    h.base(pvp ? "수비대" : "스토리 수비대 편성", "team");
    AppSkin.apply(h, "training");
    if (pvp && h.contentHeightDp() >= 360) {
      defenderTabs(h.body, 1);
      View tabs = h.body.getChildAt(0);
      h.body.removeView(tabs);
      h.page.addView(tabs, h.page.getChildCount() - 1);
    } else if (pvp) {
      LinearLayout header = (LinearLayout) h.page.getChildAt(0);
      header.addView(
          k.action(null, "갈매미", () -> requestLeaveTeam(h::collection)),
          2,
          new LinearLayout.LayoutParams(h.dp(76), h.dp(48)));
      header.addView(
          k.action(null, "아이템", () -> requestLeaveTeam(h::inventory)),
          3,
          new LinearLayout.LayoutParams(h.dp(76), h.dp(48)));
    }
    LinearLayout[] cols = k.fixedSplit(.46f);
    LinearLayout podium = k.row(), summary = cols[1];
    podium.setTag("team-podium");
    podium.setGravity(Gravity.TOP);
    cols[0].addView(podium);
    ArrayList<JSONObject> candidates = new ArrayList<>();
    JSONArray source = h.list(pvp ? h.me() : h.state, "units");
    for (int i = 0; i < source.length(); i++) {
      JSONObject unit = source.optJSONObject(i);
      if (!pvp || unit.optBoolean("top")) candidates.add(unit);
    }
    TextView status = k.note("");
    status.setTag("team-draft-status");
    Button[] save = {null};
    final int[] activeSlot = {0}, candidatePage = {0};
    Runnable[] render = new Runnable[1];
    render[0] =
        () -> {
          podium.removeAllViews();
          summary.removeAllViews();
          ArrayList<View> slots = new ArrayList<>();
          ArrayList<String> slotIds = new ArrayList<>();
          ArrayList<JSONObject> picked = new ArrayList<>();
          for (int slot = 0; slot < 3; slot++) {
            final int chosenSlot = slot;
            String id = slot < teamDraft.size() ? teamDraft.get(slot) : "";
            JSONObject member = null;
            for (JSONObject candidate : candidates)
              if (id.equals(candidate.optString(pvp ? "id" : "key"))) member = candidate;
            JSONObject unit = member;
            LinearLayout card = k.paper();
            card.setTag("lineup-slot-" + slot);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setBackground(new GamePaperDrawable(h));
            card.setSelected(slot == activeSlot[0]);
            card.setPadding(h.dp(6), h.dp(4), h.dp(6), h.dp(4));
            LinearLayout crest = h.compact() ? k.row() : null;
            if (crest != null) card.addView(crest);
            TextView order = h.text(slot == 0 ? "선봉" : (slot + 1) + "번", 16);
            order.setGravity(Gravity.CENTER);
            order.setPadding(0, 0, 0, 0);
            order.setTextColor(JournalTheme.TEAL);
            if (crest != null)
              crest.addView(order, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
            else card.addView(order, new LinearLayout.LayoutParams(-1, h.dp(48)));
            if (unit == null) {
              TextView empty = k.note("비어 있음");
              empty.setGravity(Gravity.CENTER);
              if (crest != null)
                crest.addView(empty, 0, new LinearLayout.LayoutParams(0, h.dp(48), 1));
              else card.addView(empty, new LinearLayout.LayoutParams(-1, h.dp(132)));
              card.setOnClickListener(v -> { activeSlot[0] = Math.min(chosenSlot, teamDraft.size()); render[0].run(); });
              card.setContentDescription((slot == 0 ? "선봉" : (slot + 1) + "번") + " 빈칸. 누르면 갈매미 선택");
            } else {
              picked.add(unit);
              order.setTag(NativeTeamReorder.HANDLE_TAG);
              order.setContentDescription(unit.optString("name") + " 출전 순서. 좌우로 끌어 변경");
              order.setFocusable(true);
              order.setClickable(true);
              int portrait = h.compact() ? 48 : 132;
              ImageView image =
                  k.image(
                      pvp ? "/thumbs/" + unit.optString("id") + ".jpg" : unit.optString("image"),
                      portrait,
                      portrait);
              image.setTag("lineup-portrait");
              if (crest != null)
                crest.addView(image, 0, new LinearLayout.LayoutParams(0, h.dp(48), 1));
              else card.addView(image);
              TextView name = heading(unit.optString("name"), h.compact() ? 15 : 19);
              name.setGravity(Gravity.CENTER);
              name.setMaxLines(2);
              name.setPadding(h.dp(2), 0, h.dp(2), 0);
              card.addView(name);
              TextView meta =
                  k.note(
                      (pvp ? "" : "Lv" + unit.optInt("level") + " · ")
                          + unit.optString("attrName", unit.optString("attr")));
              meta.setGravity(Gravity.CENTER);
              meta.setPadding(h.dp(2), 0, h.dp(2), 0);
              card.addView(meta);
              if (!pvp) {
                TextView hp = k.note(unit.optInt("hp") + " / " + unit.optInt("maxHp"));
                hp.setGravity(Gravity.CENTER);
                hp.setPadding(h.dp(2), 0, h.dp(2), 0);
                card.addView(hp);
                if (!h.compact())
                  growth.meter(card, unit.optInt("hp"), unit.optInt("maxHp"), 0xff356954, 5);
              }
              card.setOnClickListener(
                  v -> {
                    activeSlot[0] = chosenSlot;
                    render[0].run();
                  });
              card.setOnLongClickListener(
                  v -> {
                    reorderDraft(id, render[0]);
                    return true;
                  });
              card.setContentDescription(
                  (slot == 0 ? "선봉 " : (slot + 1) + "번 ")
                      + unit.optString("name")
                      + ". 누르면 이 자리의 교체 후보 선택, 좌우로 끌어 순서 변경");
              slots.add(card);
              slotIds.add(id);
            }
            LinearLayout.LayoutParams cardSlot = new LinearLayout.LayoutParams(0, -2, 1);
            cardSlot.setMargins(h.dp(3), 0, h.dp(3), 0);
            podium.addView(card, cardSlot);
          }
          NativeTeamPodiumReorder.bind(
              h,
              podium,
              slots,
              slotIds,
              changed -> {
                teamDraft.clear();
                teamDraft.addAll(changed);
                render[0].run();
              });
          LinearLayout paging = k.row();
          int pageCount = Math.max(1, (candidates.size() + 5) / 6);
          candidatePage[0] = Math.max(0, Math.min(candidatePage[0], pageCount - 1));
          Button previous = k.action(null, "‹", () -> { candidatePage[0]--; render[0].run(); });
          previous.setContentDescription("이전 후보 페이지");
          previous.setEnabled(candidatePage[0] > 0);
          paging.addView(previous, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
          TextView pageLabel = k.note((activeSlot[0] + 1) + "번 교체 · "
              + (candidatePage[0] + 1) + " / " + pageCount);
          pageLabel.setGravity(Gravity.CENTER);
          paging.addView(pageLabel, new LinearLayout.LayoutParams(0, h.dp(48), 1));
          Button next = k.action(null, "›", () -> { candidatePage[0]++; render[0].run(); });
          next.setContentDescription("다음 후보 페이지");
          next.setEnabled(candidatePage[0] < pageCount - 1);
          paging.addView(next, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
          Button remove = k.action(null, "비우기", () -> {
            if (activeSlot[0] < teamDraft.size()) teamDraft.remove(activeSlot[0]);
            render[0].run();
          });
          remove.setEnabled(activeSlot[0] < teamDraft.size());
          paging.addView(remove, new LinearLayout.LayoutParams(h.dp(72), h.dp(48)));
          summary.addView(paging);
          LinearLayout grid = k.column();
          grid.setTag("team-inline-candidates");
          summary.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
          for (int row = 0; row < 2; row++) {
            LinearLayout strip = k.row();
            grid.addView(strip, new LinearLayout.LayoutParams(-1, 0, 1));
            for (int column = 0; column < 3; column++) {
              int index = candidatePage[0] * 6 + row * 3 + column;
              LinearLayout tile = k.paper();
              tile.setPadding(h.dp(4), h.dp(2), h.dp(4), h.dp(2));
              tile.setGravity(Gravity.CENTER);
              LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
              cell.setMargins(h.dp(2), h.dp(2), h.dp(2), h.dp(2));
              strip.addView(tile, cell);
              if (index >= candidates.size()) { tile.setVisibility(View.INVISIBLE); continue; }
              JSONObject candidate = candidates.get(index);
              String key = candidate.optString(pvp ? "id" : "key");
              String image = pvp ? "/thumbs/" + candidate.optString("id") + ".jpg"
                  : candidate.optString("image");
              ImageView portrait = k.image(image, 40, 40);
              tile.addView(portrait, new LinearLayout.LayoutParams(-1, 0, 1));
              TextView candidateName = k.note((teamDraft.contains(key) ? "✓ " : "")
                  + candidate.optString("name"));
              candidateName.setTextColor(JournalTheme.INK);
              candidateName.setSingleLine(true);
              candidateName.setEllipsize(android.text.TextUtils.TruncateAt.END);
              candidateName.setGravity(Gravity.CENTER);
              tile.addView(candidateName);
              TextView meta = k.note((pvp ? "" : "Lv" + candidate.optInt("level") + " · ")
                  + candidate.optString("attrName", candidate.optString("attr")));
              meta.setSingleLine(true);
              meta.setGravity(Gravity.CENTER);
              tile.addView(meta);
              tile.setSelected(teamDraft.contains(key));
              tile.setContentDescription(candidate.optString("name") + " · " + meta.getText()
                  + " · " + (activeSlot[0]+1) + "번에 배치");
              tile.setOnClickListener(v -> {
                int target = Math.min(activeSlot[0], teamDraft.size());
                int old = teamDraft.indexOf(key);
                if (old >= 0 && old != target && target < teamDraft.size()) {
                  String swapped = teamDraft.get(target);
                  teamDraft.set(target, key); teamDraft.set(old, swapped);
                } else if (old < 0) {
                  if (target < teamDraft.size()) teamDraft.set(target, key);
                  else if (teamDraft.size() < 3) teamDraft.add(key);
                }
                render[0].run();
              });
            }
          }
          if (candidates.isEmpty()) summary.addView(k.note("배치할 수 있는 대원이 없어요."));
          status.setText(
              (isTeamDirty() ? "변경됨 · 저장 필요" : "✓ 저장된 편성") + " · " + teamDraft.size() + "/3");
          status.setTextColor(isTeamDirty() ? 0xff8b4d13 : 0xff276646);
          if (save[0] != null) save[0].setEnabled(!teamDraft.isEmpty() && isTeamDirty());
        };
    TextView drag = k.note("자리 → 후보 선택 · ↔ 순서 변경");
    drag.setTextColor(JournalTheme.INK);
    drag.setTag("team-order-drag-hint");
    LinearLayout footer = k.paper(), line = k.row(), footerStatus = k.column();
    footerStatus.addView(status);
    footerStatus.addView(drag);
    line.addView(footerStatus, new LinearLayout.LayoutParams(0, -2, 1));
    Button random =
        k.action(
            null,
            "무작위",
            () -> {
              ArrayList<JSONObject> shuffled = new ArrayList<>(candidates);
              Collections.shuffle(shuffled);
              teamDraft.clear();
              for (int i = 0; i < Math.min(3, shuffled.size()); i++)
                teamDraft.add(shuffled.get(i).optString(pvp ? "id" : "key"));
              render[0].run();
            });
    line.addView(random, new LinearLayout.LayoutParams(h.dp(88), h.dp(48)));
    if (!pvp && h.list(h.me(), "battleTeam").length() > 0) {
      Button importTeam =
          k.action(
              null,
              "불러오기",
              () -> {
                ArrayList<String> imported = new ArrayList<>();
                JSONArray species = h.list(h.me(), "battleTeam");
                for (int i = 0; i < species.length(); i++)
                  for (JSONObject unit : candidates)
                    if (species.optString(i).equals(unit.optString("id"))
                        && !imported.contains(unit.optString("key"))) {
                      imported.add(unit.optString("key"));
                      break;
                    }
                if (imported.isEmpty()) {
                  h.message("가진 갈매미로 된 전투 편성이 없어요.");
                  return;
                }
                teamDraft.clear();
                teamDraft.addAll(imported);
                render[0].run();
              });
      line.addView(importTeam, new LinearLayout.LayoutParams(h.dp(100), h.dp(48)));
    }
    Button cancel =
        k.action(
            null,
            "변경 취소",
            () -> {
              teamDraft.clear();
              teamDraft.addAll(teamSavedIds);
              render[0].run();
            });
    line.addView(cancel, new LinearLayout.LayoutParams(h.dp(104), h.dp(48)));
    save[0] = k.primary(null, "저장", () -> saveTeamDraft(() -> h.load(teamReturnTarget())));
    save[0].setTag("team-save");
    NativeTutorialController.anchor(save[0], "team-save");
    line.addView(save[0], new LinearLayout.LayoutParams(h.dp(112), h.dp(48)));
    footer.addView(line);
    k.footer(footer);
    render[0].run();
  }

  void teamPicker(boolean pvp, ArrayList<JSONObject> candidates, Runnable updated) {
    k.openSheet(
        "갈매미 선택 · 최대 3마리",
        () -> {
          LinearLayout content = k.column(), controls = k.row(), list = k.column();
          if (!pvp && NativeTutorialController.get(h).needsStoryTeam()) {
            TextView guidance = k.notice("갈매미를 눌러 출전시킨 뒤 닫고, 편성을 저장해요.");
            guidance.setTag("team-picker-guidance");
            content.addView(guidance);
          }
          EditText search = k.search(content, "이름·속성 검색", teamQuery, value -> {});
          search.setTag("team-search");
          Button attr = k.action(null, "모든 속성 ▾", () -> {});
          attr.setTag("team-attr-filter");
          Button selected = k.action(null, "선택만", () -> {});
          selected.setTag("team-selected-filter");
          h.cell(controls, attr);
          h.cell(controls, selected);
          content.addView(controls);
          list.setTag("team-candidates-scroll");
          content.addView(list);
          Runnable[] paint = new Runnable[1];
          paint[0] =
              () -> {
                list.removeAllViews();
                ArrayList<View> cards = new ArrayList<>();
                for (JSONObject unit : candidates) {
                  String id = unit.optString(pvp ? "id" : "key");
                  int order = teamDraft.indexOf(id);
                  if (!candidateMatches(unit, teamQuery, teamAttr, teamSelectedOnly && order < 0))
                    continue;
                  LinearLayout tile =
                      k.tile(
                          unit,
                          pvp
                              ? "/thumbs/" + unit.optString("id") + ".jpg"
                              : unit.optString("image"),
                          (order < 0 ? "" : "✓ " + (order == 0 ? "선봉 · " : (order + 1) + "번 · "))
                              + (pvp ? unit.optString("tierName") : "Lv" + unit.optInt("level")),
                          () -> {
                            if (teamDraft.contains(id)) teamDraft.remove(id);
                            else if (teamDraft.size() < 3) teamDraft.add(id);
                            else {
                              h.message("3마리까지 고를 수 있어요.");
                              return;
                            }
                            updated.run();
                            paint[0].run();
                          });
                  tile.setTag("lineup-candidate-" + id);
                  tile.setSelected(order >= 0);
                  if (order >= 0) tile.setBackground(new GamePaperDrawable(h, JournalTheme.TEAL));
                  if (!pvp && unit.optString("id").equals("D1"))
                    NativeTutorialController.anchor(tile, "team-unit");
                  if (!pvp)
                    tile.setOnLongClickListener(
                        v -> {
                          unitReturn = "team";
                          h.unitDetail(id);
                          return true;
                        });
                  cards.add(tile);
                }
                if (cards.isEmpty()) list.addView(k.notice("조건에 맞는 갈매미가 없어요. 검색어와 필터를 바꿔 보세요."));
                else k.tiles(list, cards, h.compact() ? 3 : 4);
                attr.setText(
                    teamAttr.equals("all")
                        ? "모든 속성 ▾"
                        : attrName(h.state.optJSONObject("attributes"), teamAttr) + " ▾");
                selected.setSelected(teamSelectedOnly);
              };
          search.addTextChangedListener(
              new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                  teamQuery = s.toString();
                  paint[0].run();
                }

                public void afterTextChanged(android.text.Editable s) {}
              });
          attr.setOnClickListener(
              v -> {
                ArrayList<String> attrs = new ArrayList<>();
                attrs.add("all");
                JSONObject definitions = h.state.optJSONObject("attributes");
                if (definitions != null) {
                  Iterator<String> names = definitions.keys();
                  while (names.hasNext()) attrs.add(names.next());
                }
                String[] labels = new String[attrs.size()];
                for (int i = 0; i < labels.length; i++)
                  labels[i] = i == 0 ? "모든 속성" : attrName(definitions, attrs.get(i));
                new GameDialog(h)
                    .setTitle("속성으로 찾기")
                    .setItems(
                        labels,
                        (d, which) -> {
                          teamAttr = attrs.get(which);
                          paint[0].run();
                        })
                    .setNegativeButton("닫기", null)
                    .show();
              });
          selected.setOnClickListener(
              v -> {
                teamSelectedOnly = !teamSelectedOnly;
                paint[0].run();
              });
          paint[0].run();
          return content;
        });
  }

  static boolean candidateMatches(
      JSONObject unit, String query, String attr, boolean excludedBySelection) {
    if (excludedBySelection) return false;
    String code = unit.optString("attrCode", unit.optString("attr"));
    if (!attr.equals("all") && !attr.equals(code)) return false;
    String normalized = query.toLowerCase(Locale.KOREAN).replaceAll("\\s+", "");
    String searchable =
        (unit.optString("name") + unit.optString("attrName") + unit.optString("attr") + code)
            .toLowerCase(Locale.KOREAN)
            .replaceAll("\\s+", "");
    return searchable.contains(normalized);
  }

  void reorderDraft(String id, Runnable repaint) {
    if (teamDraft.size() < 2) return;
    String[] positions = new String[teamDraft.size()];
    for (int i = 0; i < positions.length; i++) positions[i] = i == 0 ? "선봉으로" : (i + 1) + "번째로";
    new GameDialog(h)
        .setTitle("출전 순서 변경")
        .setItems(
            positions,
            (d, to) -> {
              teamDraft.remove(id);
              teamDraft.add(to, id);
              repaint.run();
            })
        .setNegativeButton("닫기", null)
        .show();
  }

  void chart(LinearLayout parent, Collection<String> team, boolean species) {
    JSONObject types = h.state.optJSONObject("typeChart"),
        attrs = h.state.optJSONObject("attributes");
    if (types == null || attrs == null) return;
    LinkedHashSet<String> mine = new LinkedHashSet<>();
    ArrayList<JSONObject> picked = new ArrayList<>();
    for (String id : team) {
      JSONObject unit = species ? ownedSpecies(id) : h.unit(id);
      if (unit.length() == 0) continue;
      mine.add(unit.optString("attrCode", unit.optString("attr")));
      picked.add(unit);
    }
    ArrayList<String> ordered = new ArrayList<>(mine);
    Iterator<String> all = types.keys();
    while (all.hasNext()) {
      String attr = all.next();
      if (!ordered.contains(attr)) ordered.add(attr);
    }
    if (ordered.isEmpty()) return;
    String[] selected = {ordered.get(0)};
    LinearLayout controls = k.row(), current = k.column();
    parent.addView(controls);
    parent.addView(current);
    Button select = k.action(null, "속성 선택", () -> {});
    h.cell(controls, select);
    h.cell(
        controls,
        k.action(
            null,
            "전체 상성",
            () ->
                k.openSheet(
                    "전체 속성 상성",
                    () -> {
                      LinearLayout entries = k.column();
                      for (String attr : ordered) entries.addView(affinityCard(attr, mine, true));
                      return entries;
                    })));
    Runnable paint =
        () -> {
          current.removeAllViews();
          select.setText(attrName(attrs, selected[0]) + " ▾");
          current.addView(affinityCard(selected[0], mine, false));
        };
    select.setOnClickListener(
        v -> {
          String[] labels = new String[ordered.size()];
          for (int i = 0; i < labels.length; i++)
            labels[i] =
                attrName(attrs, ordered.get(i)) + (mine.contains(ordered.get(i)) ? " · 우리 팀" : "");
          new GameDialog(h)
              .setTitle("공격 속성 선택")
              .setItems(
                  labels,
                  (d, which) -> {
                    selected[0] = ordered.get(which);
                    paint.run();
                  })
              .setNegativeButton("닫기", null)
              .show();
        });
    paint.run();
    parent.addView(k.notice("공격 속성 → 방어 속성 · 유리 ×1.5 / 불리 ×0.67"));
    if (!mine.isEmpty()) {
      parent.addView(k.notice("팀 속성 · " + attrNames(attrs, mine)));
      Button summary =
          k.action(
              null,
              "우리 팀 상성 요약",
              () ->
                  k.openSheet(
                      "우리 팀 상성",
                      () -> {
                        LinearLayout detail = k.column();
                        for (JSONObject unit : picked) {
                          LinearLayout who = k.row();
                          who.addView(
                              k.note(unit.optString("name")),
                              new LinearLayout.LayoutParams(0, -2, 1));
                          String attr = unit.optString("attrCode", unit.optString("attr"));
                          who.addView(k.chip(attrName(attrs, attr), attr));
                          detail.addView(who);
                        }
                        LinkedHashSet<String> good = new LinkedHashSet<>(),
                            danger = new LinkedHashSet<>();
                        for (String attr : mine) {
                          JSONArray strong = h.list(types.optJSONObject(attr), "strong");
                          for (int i = 0; i < strong.length(); i++) good.add(strong.optString(i));
                          Iterator<String> attackers = types.keys();
                          while (attackers.hasNext()) {
                            String attacker = attackers.next();
                            JSONArray targets = h.list(types.optJSONObject(attacker), "strong");
                            for (int i = 0; i < targets.length(); i++)
                              if (attr.equals(targets.optString(i))) danger.add(attacker);
                          }
                        }
                        danger.removeAll(good);
                        detail.addView(k.note("유리한 상대 · " + attrNames(attrs, good)));
                        detail.addView(k.note("보완할 상대 · " + attrNames(attrs, danger)));
                        return detail;
                      }));
      summary.setTag("type-team-summary-toggle");
      parent.addView(summary);
    }
  }

  LinearLayout affinityCard(String attr, Collection<String> mine, boolean reasons) {
    JSONObject types = h.state.optJSONObject("typeChart"),
        attrs = h.state.optJSONObject("attributes");
    JSONObject type = types.optJSONObject(attr);
    ArrayList<String> strong = new ArrayList<>(), weak = new ArrayList<>();
    JSONArray targets = h.list(type, "strong"), explanations = h.list(type, "why");
    for (int i = 0; i < targets.length(); i++) strong.add(targets.optString(i));
    Iterator<String> others = types.keys();
    while (others.hasNext()) {
      String other = others.next();
      JSONArray attacks = h.list(types.optJSONObject(other), "strong");
      for (int i = 0; i < attacks.length(); i++)
        if (attr.equals(attacks.optString(i))) weak.add(other);
    }
    LinearLayout card = k.paper(), head = k.row();
    card.setTag("type-chart-" + attr);
    head.addView(k.chip(attrName(attrs, attr), attr));
    if (mine.contains(attr)) head.addView(h.text("  우리 팀", 14));
    card.addView(head);
    TextView good = k.note("유리 ×1.5  " + attrNames(attrs, strong));
    good.setTextColor(0xff276646);
    card.addView(good);
    TextView bad = k.note("불리 ×0.67  " + attrNames(attrs, weak));
    bad.setTextColor(0xff9b3c31);
    card.addView(bad);
    if (reasons) {
      for (int i = 0; i < targets.length(); i++)
        if (!explanations.optString(i).isEmpty())
          card.addView(
              k.note(attrName(attrs, targets.optString(i)) + " · " + explanations.optString(i)));
    } else {
      card.setContentDescription(
          attrName(attrs, attr) + " · " + good.getText() + " · " + bad.getText() + ". 누르면 상성 이유");
      card.setOnClickListener(
          v -> k.openSheet(attrName(attrs, attr) + " 상성", () -> affinityCard(attr, mine, true)));
    }
    return card;
  }

  String attrNames(JSONObject attrs, Collection<String> ids) {
    if (ids.isEmpty()) return "없음";
    ArrayList<String> names = new ArrayList<>();
    for (String id : ids) names.add(attrName(attrs, id));
    return android.text.TextUtils.join(" · ", names);
  }

  String attrName(JSONObject attrs, String id) {
    JSONObject a = attrs == null ? null : attrs.optJSONObject(id);
    return a == null ? id : a.optString("name", id);
  }
}

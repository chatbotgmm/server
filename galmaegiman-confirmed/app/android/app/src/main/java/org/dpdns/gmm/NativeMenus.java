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
    LinearLayout head = (LinearLayout) h.page.getChildAt(0);
    head.removeAllViews();
    head.setBackground(new GamePaperDrawable(h));
    head.setPadding(0, 0, 0, 0);
    h.title.setTextColor(JournalTheme.INK);
    h.title.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
    h.title.setGravity(Gravity.CENTER);
    head.addView(h.title, new LinearLayout.LayoutParams(-1, h.dp(40)));
    LinearLayout[] cols = k.split(.49f);
    LinearLayout campaign = k.paper(), pvp = k.paper();
    for (LinearLayout board : new LinearLayout[] {campaign, pvp}) {
      board.setBackground(new GamePanelDrawable(h));
      board.setPadding(h.dp(14), h.dp(18), h.dp(14), h.dp(18));
      board.setMinimumHeight(h.dp(h.compact() ? 200 : 280));
    }
    cols[0].addView(campaign);
    cols[1].addView(pvp);
    LinearLayout back = k.row();
    back.setGravity(Gravity.CENTER);
    back.addView(
        k.action(null, "돌아가기", h::back), new LinearLayout.LayoutParams(h.dp(104), h.dp(48)));
    k.footer(back);
    intro(campaign, "mode-camp", "캠페인", "이야기를 따라 항구를 지키고 갈매미를 성장시켜요.");
    Button story = k.action("u-story", "스토리", () -> h.load("story"));
    NativeTutorialController.anchor(story, "story");
    story.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
    campaign.addView(story);
    campaign.addView(k.note("이전 전투에 다시 도전해 경험치를 모을 수 있어요."));
    if (!h.compact()) addModeTeamSummary(campaign, false);
    Button adventure = k.action("mode-adv", "모험 준비 중", () -> {});
    adventure.setEnabled(false);
    campaign.addView(adventure);
    campaign.addView(k.note("준비 중 · 다음 업데이트"));
    intro(pvp, "mode-pvp", "PvP", "다른 수비대의 전투 편성과 대결 · 모두 같은 레벨");
    TextView rank = k.note("랭크 불러오는 중…");
    Button ranked = k.action("mode-rank", "랭크게임", () -> startPvp("rank"));
    ranked.setTextSize(h.uiText(14));
    pvp.addView(rank);
    fetch(
        "pvp",
        r -> {
          JSONObject me = r.optJSONObject("me");
          if (me != null)
            rank.setText(
                me.optString("tier")
                    + " · "
                    + me.optInt("point")
                    + "점 · "
                    + me.optInt("wins")
                    + "승 "
                    + me.optInt("losses")
                    + "패"
                    + (me.optInt("rank") > 0 ? " · " + me.optInt("rank") + "위" : "")
                    + " · Lv"
                    + r.optInt("level")
                    + (r.optBoolean("team", h.list(h.me(), "battleTeam").length() == 3)
                        ? ""
                        : "\n전투 편성에서 3마리를 정해 주세요."));
          rank.setText(
              rank.getText() + "\n승리 +" + r.optInt("win") + " / 패배 −" + r.optInt("lose") + "점");
        });
    pvp.addView(k.action("mode-normal", "일반 게임", () -> startPvp("normal")));
    pvp.addView(ranked);
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
    for (int i = 0; i < links.getChildCount(); i++) {
      Button link = (Button) links.getChildAt(i);
      link.setTextSize(h.uiText(14));
      link.setMinHeight(h.dp(48));
      link.setMinimumHeight(h.dp(48));
      link.setPadding(h.dp(4), 0, h.dp(4), h.dp(3));
    }
    pvp.addView(links);
    if (!h.compact()) addModeTeamSummary(pvp, true);
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
      h.confirm("랭크는 중간에 나가도 패배와 점수 차감이 적용돼요. 시작할까요?", () -> h.startFight(mode, null));
    else h.startFight(mode, null);
  }

  void story(boolean stages) {
    JSONArray chapters = h.list(h.state, "chapters");
    JSONObject chapter = chapters.optJSONObject(h.chapter);
    h.base(
        stages && chapter != null ? chapter.optString("title") : "스토리 · 챕터 선택",
        stages ? "stages" : "story");
    LinearLayout[] cols = k.split(.32f);
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
          growth.storyUnit(
              u,
              i,
              () -> {
                unitReturn = stages ? "stages" : "story";
                h.unitDetail(key);
              },
              () -> reorderStory(key, stages));
      LinearLayout.LayoutParams unitSlot = new LinearLayout.LayoutParams(-1, -2);
      unitSlot.bottomMargin = h.dp(4);
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
    if (!storyTeam.isEmpty()) team.addView(growth.combos(storyTeam));
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
        TextView note = k.note(c.optString("intro"));
        note.setTextColor(JournalTheme.INK);
        note.setMaxLines(2);
        note.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(note);
        JSONObject chapterLock = c.optJSONObject("lock");
        if (chapterLock != null && !chapterLock.optBoolean("open")) {
          TextView need = k.note("잠김 · " + chapterLock.optString("reason"));
          need.setMaxLines(2);
          need.setEllipsize(android.text.TextUtils.TruncateAt.END);
          need.setTextColor(0xff8b4d13);
          info.addView(need);
        } else if (c.optInt("needStage") > 0) {
          TextView need = k.note("✓ 팀 전원 " + c.optInt("needStage") + "단계 승진");
          need.setTextColor(0xff276646);
          info.addView(need);
        }
        Button enter =
            k.action(
                null,
                opened > 0 ? "들어가기 ›" : "미리 보기 ›",
                () -> {
                  h.chapter = index;
                  h.stages();
                });
        if (index == 0) NativeTutorialController.anchor(enter, "chapter");
        info.addView(enter);
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
            new LinearLayout.LayoutParams(h.dp(260), h.dp(Math.max(240, availableHeight)));
        lp.setMargins(h.dp(4), 0, h.dp(4), 0);
        row.addView(card, lp);
      }
      LinearLayout guide = k.paper();
      guide.addView(heading("성장 안내", 20));
      guide.addView(k.note("진 전투에서도 경험치를 받아요. 완료한 전투는 다시 도전해 레벨을 올릴 수 있어요."));
      guide.addView(k.note("레벨이 오르면 체력이 가득 차요. 새 기술·전용 기술·승진 가능 표시는 팀 칸에서 확인해 주세요."));
      guide.addView(k.action(null, "스토리 처음부터", this::resetStory));
      LinearLayout.LayoutParams guideSlot = new LinearLayout.LayoutParams(h.dp(240), -1);
      guideSlot.setMargins(h.dp(4), 0, h.dp(4), 0);
      row.addView(guide, guideSlot);
    } else {
      if (!chapter.optString("intro").isEmpty())
        content.addView(k.notice(chapter.optString("intro")));
      if (chapter.optInt("needStage") > 0)
        content.addView(k.notice("이 부는 팀 전원 " + chapter.optInt("needStage") + "단계 승진이 필요해요."));
      JSONArray ss = h.list(chapter, "stages");
      ArrayList<View> cards = new ArrayList<>();
      for (int i = 0; i < ss.length(); i++) {
        JSONObject st = ss.optJSONObject(i);
        JSONArray foes = h.list(st, "enemy");
        JSONObject foe = foes.optJSONObject(0);
        String id = foe == null ? "" : foe.optString("id");
        JSONObject portraits = h.state.optJSONObject("portraits");
        LinearLayout card = k.paper();
        LinearLayout line = k.row();
        line.addView(k.image(portraits == null ? "" : portraits.optString(id), 60, 60));
        line.addView(
            heading(
                (st.optBoolean("cleared") ? "완료 · " : st.optBoolean("open") ? "" : "잠김 · ")
                    + st.optString("title"),
                17),
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
                st.optBoolean("cleared") ? "다시 도전" : st.optBoolean("open") ? "이야기 시작" : "잠금 조건 확인",
                () -> {
                  if (!st.optBoolean("open")) {
                    showStageLock(st);
                    return;
                  }
                  h.storyScene = st.optString("scene", "harbor");
                  h.storyFoe = id;
                  h.talk(
                      withTip(h.list(st, "before"), st.optString("hint")),
                      st.optString("title"),
                      () -> h.startFight("story", st.optString("id")));
                });
        if (st.optString("id").equals("P-1")) NativeTutorialController.anchor(start, "stage");
        card.addView(start);
        card.setTag("story-stage-" + st.optString("id"));
        if (st.optString("id").equals(stageFocusId)) {
          stageFocusId = "";
          card.post(() -> revealStage(card));
        }
        cards.add(card);
      }
      k.tiles(content, cards, h.cols());
    }
    startStoryRefresh(stages);
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
    if (pvp) defenderTabs(h.body, 1);
    View tabs = pvp ? h.body.getChildAt(0) : null;
    if (tabs != null) h.body.removeView(tabs);
    if (tabs != null) h.page.addView(tabs, h.page.getChildCount() - 1);
    LinearLayout[] cols = k.split(h.compact() ? .42f : .44f);
    LinearLayout left = cols[0], right = cols[1];
    for (LinearLayout col : cols) {
      ScrollView old = (ScrollView) col.getParent();
      LinearLayout split = (LinearLayout) old.getParent();
      int index = split.indexOfChild(old);
      ViewGroup.LayoutParams slot = old.getLayoutParams();
      old.removeView(col);
      split.removeView(old);
      split.addView(col, index, slot);
      col.setBackground(new GamePaperDrawable(h));
      col.setPadding(h.dp(10), h.dp(6), h.dp(10), h.dp(6));
    }
    ArrayList<JSONObject> candidates = new ArrayList<>();
    JSONArray source = h.list(pvp ? h.me() : h.state, "units");
    for (int i = 0; i < source.length(); i++) {
      JSONObject unit = source.optJSONObject(i);
      if (!pvp || unit.optBoolean("top")) candidates.add(unit);
    }
    LinearLayout teamTabs = k.column();
    left.addView(teamTabs);
    FrameLayout preview = new FrameLayout(h);
    preview.setTag("team-fixed-podium");
    left.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout candidateControls = k.column();
    right.addView(candidateControls);
    EditText search = k.search(candidateControls, "이름·속성 검색", teamQuery, value -> {});
    search.setTag("team-search");
    LinearLayout filters = k.row();
    candidateControls.addView(filters);
    Button attrFilter = k.action(null, "모든 속성 ▾", () -> {});
    attrFilter.setTag("team-attr-filter");
    Button selectedFilter = k.action(null, "선택만", () -> {});
    selectedFilter.setTag("team-selected-filter");
    h.cell(filters, attrFilter);
    h.cell(filters, selectedFilter);
    for (Button button : new Button[] {attrFilter, selectedFilter}) {
      button.setTextSize(h.uiText(13));
      button.setMinHeight(h.dp(48));
      button.setMinimumHeight(h.dp(48));
      button.setPadding(h.dp(5), 0, h.dp(5), 0);
    }
    ScrollView candidateScroll = new ScrollView(h);
    candidateScroll.setTag("team-candidates-scroll");
    LinearLayout candidateGrid = k.column();
    candidateScroll.addView(candidateGrid);
    right.addView(candidateScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView[] draftStatus = {null};
    Button[] draftSave = {null};
    Runnable[] render = new Runnable[1];
    render[0] =
        () -> {
          teamTabs.removeAllViews();
          preview.removeAllViews();
          k.tabs(
              teamTabs,
              new String[] {"출전 편성", "상성표"},
              teamChart ? 1 : 0,
              i -> {
                teamChart = i == 1;
                render[0].run();
              });
          LinearLayout tabLine = (LinearLayout) teamTabs.getChildAt(0);
          for (int i = 0; i < tabLine.getChildCount(); i++) {
            View tab = tabLine.getChildAt(i);
            tab.setLayoutParams(new LinearLayout.LayoutParams(0, h.dp(48), 1));
          }
          if (teamChart) {
            LinearLayout chartContent = k.column();
            chart(chartContent, teamDraft, pvp);
            preview.addView(k.scroll(chartContent), new FrameLayout.LayoutParams(-1, -1));
          } else {
            LinearLayout podiumContent = k.column();
            LinearLayout podium = k.row();
            podium.setTag("team-podium");
            podium.setGravity(Gravity.TOP);
            podiumContent.addView(podium);
            ArrayList<View> slots = new ArrayList<>();
            ArrayList<String> slotIds = new ArrayList<>();
            for (int slot = 0; slot < 3; slot++) {
              String id = slot < teamDraft.size() ? teamDraft.get(slot) : "";
              JSONObject member = null;
              for (JSONObject candidate : candidates)
                if (id.equals(candidate.optString(pvp ? "id" : "key"))) member = candidate;
              final JSONObject unit = member;
              LinearLayout step = k.column();
              step.setTag("lineup-slot-" + slot);
              step.setBackground(new GamePanelDrawable(h));
              step.setPadding(h.dp(8), h.dp(8), h.dp(8), h.dp(12));
              step.setGravity(Gravity.CENTER_HORIZONTAL);
              TextView order =
                  h.text(slot == 0 ? "1 · 선봉" : (slot + 1) + "번", h.compact() ? 12 : 16);
              order.setPadding(0, 0, 0, 0);
              order.setGravity(Gravity.CENTER);
              step.addView(order);
              if (unit == null) {
                TextView empty = h.text("비어 있음", h.compact() ? 13 : 17);
                empty.setGravity(Gravity.CENTER);
                step.addView(
                    empty, new LinearLayout.LayoutParams(-1, h.dp(h.compact() ? 72 : 148)));
              } else {
                step.addView(
                    k.image(
                        pvp ? "/thumbs/" + unit.optString("id") + ".jpg" : unit.optString("image"),
                        h.compact() ? 62 : 120,
                        h.compact() ? 56 : 132));
                TextView name = h.text(unit.optString("name"), h.compact() ? 12 : 16);
                name.setTypeface(h.headline);
                name.setPadding(0, 0, 0, 0);
                name.setGravity(Gravity.CENTER);
                name.setSingleLine(false);
                name.setMaxLines(2);
                name.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
                name.setTextLocale(Locale.KOREA);
                if (android.os.Build.VERSION.SDK_INT >= 33)
                  name.setLineBreakWordStyle(
                      android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
                step.addView(name);
                step.setOnLongClickListener(
                    v -> {
                      reorderDraft(id, render[0]);
                      return true;
                    });
                step.setOnClickListener(
                    v -> {
                      teamDraft.remove(id);
                      render[0].run();
                    });
                slots.add(step);
                slotIds.add(id);
              }
              step.setContentDescription(
                  (slot == 0 ? "선봉 " : (slot + 1) + "번 ")
                      + (unit == null ? "빈칸" : unit.optString("name"))
                      + ". 누르면 제외, 끌거나 길게 눌러 순서 변경");
              h.cell(podium, step);
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
            ArrayList<JSONObject> picked = new ArrayList<>();
            for (String key : teamDraft)
              for (JSONObject candidate : candidates)
                if (key.equals(candidate.optString(pvp ? "id" : "key"))) picked.add(candidate);
            TextView combos = growth.combos(picked);
            combos.setMaxLines(h.compact() ? 2 : 4);
            combos.setEllipsize(android.text.TextUtils.TruncateAt.END);
            podiumContent.addView(combos);
            if (!h.compact()) {
              TextView hint = k.note("선봉부터 차례로 출전해요. 단상을 끌거나 길게 눌러 순서를 바꾸세요.");
              podiumContent.addView(hint);
              startPodiumQuotes(hint, picked);
            }
            preview.addView(podiumContent, new FrameLayout.LayoutParams(-1, -1));
          }
          candidateGrid.removeAllViews();
          ArrayList<View> choices = new ArrayList<>();
          for (JSONObject unit : candidates) {
            String id = unit.optString(pvp ? "id" : "key");
            int order = teamDraft.indexOf(id);
            if (!candidateMatches(unit, teamQuery, teamAttr, teamSelectedOnly && order < 0))
              continue;
            LinearLayout tile =
                k.tile(
                    unit,
                    pvp ? "/thumbs/" + unit.optString("id") + ".jpg" : unit.optString("image"),
                    pvp ? unit.optString("tierName") : "Lv" + unit.optInt("level"),
                    () -> {
                      if (teamDraft.contains(id)) teamDraft.remove(id);
                      else if (teamDraft.size() < 3) teamDraft.add(id);
                      else {
                        h.message("3마리까지 고를 수 있어요.");
                        return;
                      }
                      render[0].run();
                    });
            tile.setTag("lineup-candidate-" + id);
            if (!pvp && unit.optString("id").equals("D1"))
              NativeTutorialController.anchor(tile, "team-unit");
            tile.setSelected(order >= 0);
            tile.setPadding(h.dp(8), h.dp(8), h.dp(8), h.dp(12));
            View image = tile.getChildAt(0);
            tile.removeView(image);
            FrameLayout portrait = new FrameLayout(h);
            portrait.addView(image, new FrameLayout.LayoutParams(-1, -1));
            if (order >= 0) {
              TextView position = h.text(order == 0 ? "✓ 선봉" : "✓ " + (order + 1) + "번", 12);
              position.setPadding(h.dp(3), 0, h.dp(3), 0);
              position.setBackground(new GamePaperDrawable(h, JournalTheme.TEAL));
              position.setTextColor(JournalTheme.INK);
              portrait.addView(
                  position,
                  new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
              tile.setBackground(new GamePanelDrawable(h));
            }
            tile.addView(
                portrait, 0, new LinearLayout.LayoutParams(-1, h.dp(h.compact() ? 64 : 110)));
            TextView name = (TextView) tile.getChildAt(1);
            name.setTextSize(h.uiText(h.compact() ? 13 : 15));
            name.setPadding(0, h.dp(2), 0, h.dp(2));
            name.setSingleLine(false);
            name.setMaxLines(2);
            name.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
            name.setTextLocale(Locale.KOREA);
            if (android.os.Build.VERSION.SDK_INT >= 33)
              name.setLineBreakWordStyle(
                  android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
            TextView metadata = (TextView) tile.getChildAt(2);
            metadata.setText(
                unit.optString("attrName", unit.optString("attr")) + " · " + metadata.getText());
            metadata.setTextSize(h.uiText(h.compact() ? 12 : 14));
            metadata.setPadding(0, 0, 0, 0);
            metadata.setSingleLine(true);
            tile.setContentDescription(
                unit.optString("name")
                    + " · "
                    + unit.optString("attrName", unit.optString("attr"))
                    + (order < 0
                        ? " · 누르면 선택"
                        : " · " + (order == 0 ? "선봉" : order + 1 + "번") + " 선택됨 · 누르면 제외"));
            if (!pvp)
              tile.setOnLongClickListener(
                  v -> {
                    unitReturn = "team";
                    h.unitDetail(id);
                    return true;
                  });
            choices.add(tile);
          }
          if (choices.isEmpty())
            candidateGrid.addView(k.notice("조건에 맞는 갈매미가 없어요. 검색어와 필터를 바꿔 보세요."));
          else k.tiles(candidateGrid, choices, h.compact() ? 2 : h.cols());
          attrFilter.setText(
              teamAttr.equals("all")
                  ? "모든 속성 ▾"
                  : attrName(h.state.optJSONObject("attributes"), teamAttr) + " ▾");
          selectedFilter.setText(teamSelectedOnly ? "✓ 선택만" : "선택만");
          selectedFilter.setSelected(teamSelectedOnly);
          selectedFilter.setBackground(
              new GameButtonDrawable(
                  h, teamSelectedOnly ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER));
          if (draftStatus[0] != null) {
            draftStatus[0].setText(
                (isTeamDirty() ? "변경됨 · 저장 필요" : "✓ 저장된 편성") + " · " + teamDraft.size() + "/3");
            draftStatus[0].setTextColor(isTeamDirty() ? 0xff8b4d13 : 0xff276646);
          }
          if (draftSave[0] != null) draftSave[0].setEnabled(!teamDraft.isEmpty() && isTeamDirty());
        };
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            teamQuery = s.toString();
            candidateScroll.scrollTo(0, 0);
            render[0].run();
          }

          public void afterTextChanged(android.text.Editable value) {}
        });
    attrFilter.setOnClickListener(
        v -> {
          if (h.busy) return;
          LinkedHashSet<String> available = new LinkedHashSet<>();
          for (JSONObject unit : candidates)
            available.add(unit.optString("attrCode", unit.optString("attr")));
          ArrayList<String> attrs = new ArrayList<>(available);
          attrs.add(0, "all");
          String[] labels = new String[attrs.size()];
          JSONObject definitions = h.state.optJSONObject("attributes");
          for (int i = 0; i < attrs.size(); i++)
            labels[i] = attrs.get(i).equals("all") ? "모든 속성" : attrName(definitions, attrs.get(i));
          new GameDialog(h)
              .setTitle("속성으로 찾기")
              .setItems(
                  labels,
                  (dialog, which) -> {
                    teamAttr = attrs.get(which);
                    candidateScroll.scrollTo(0, 0);
                    render[0].run();
                  })
              .setNegativeButton("닫기", null)
              .show();
        });
    selectedFilter.setOnClickListener(
        v -> {
          if (h.busy) return;
          teamSelectedOnly = !teamSelectedOnly;
          candidateScroll.scrollTo(0, 0);
          render[0].run();
        });
    LinearLayout footer = k.paper(), line = k.row();
    TextView status = k.note("");
    status.setTag("team-draft-status");
    status.setTextSize(h.uiText(h.compact() ? 12 : 14));
    status.setPadding(h.dp(3), 0, h.dp(3), 0);
    draftStatus[0] = status;
    line.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
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
      importTeam.setTextSize(h.uiText(14));
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
    cancel.setTextSize(h.uiText(13));
    line.addView(cancel, new LinearLayout.LayoutParams(h.dp(104), h.dp(48)));
    Button save = k.action(null, "저장", () -> saveTeamDraft(() -> h.load(teamReturnTarget())));
    save.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
    save.setTag("team-save");
    NativeTutorialController.anchor(save, "team-save");
    draftSave[0] = save;
    line.addView(save, new LinearLayout.LayoutParams(h.dp(112), h.dp(48)));
    footer.addView(line);
    k.footer(footer);
    render[0].run();
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
    JSONObject chart = h.state.optJSONObject("typeChart"),
        attrs = h.state.optJSONObject("attributes");
    if (chart == null || attrs == null) return;
    LinkedHashSet<String> mine = new LinkedHashSet<>();
    ArrayList<JSONObject> picked = new ArrayList<>();
    for (String id : team) {
      if (!species) {
        JSONObject unit = h.unit(id);
        mine.add(unit.optString("attrCode"));
        picked.add(unit);
      } else {
        JSONArray own = h.list(h.me(), "units");
        for (int i = 0; i < own.length(); i++)
          if (id.equals(own.optJSONObject(i).optString("id"))) {
            JSONObject unit = own.optJSONObject(i);
            mine.add(unit.optString("attr"));
            picked.add(unit);
          }
      }
    }
    LinearLayout teamSummary = k.column();
    if (!h.compact()) parent.addView(k.notice("공격 속성 → 방어 속성 · 유리 ×1.5 / 불리 ×0.67 / 그 외 ×1"));
    if (!mine.isEmpty()) {
      LinearLayout summary = k.paper();
      summary.addView(heading("우리 팀", 18));
      for (JSONObject unit : picked) {
        LinearLayout who = k.row();
        TextView name = k.note(unit.optString("name"));
        who.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        String attr = unit.optString("attrCode", unit.optString("attr"));
        who.addView(k.chip(attrName(attrs, attr), attr));
        summary.addView(who);
      }
      LinkedHashSet<String> good = new LinkedHashSet<>(), danger = new LinkedHashSet<>();
      for (String a : mine) {
        JSONArray strong = h.list(chart.optJSONObject(a), "strong");
        for (int i = 0; i < strong.length(); i++) good.add(strong.optString(i));
        Iterator<String> others = chart.keys();
        while (others.hasNext()) {
          String other = others.next();
          JSONArray targets = h.list(chart.optJSONObject(other), "strong");
          for (int i = 0; i < targets.length(); i++)
            if (a.equals(targets.optString(i))) danger.add(other);
        }
      }
      danger.removeAll(good);
      summary.addView(k.note("유리한 상대 · " + attrNames(attrs, good)));
      summary.addView(k.note("보완할 상대 · " + attrNames(attrs, danger)));
      teamSummary.addView(summary);
    }
    ArrayList<String> ordered = new ArrayList<>(mine);
    Iterator<String> all = chart.keys();
    while (all.hasNext()) {
      String attr = all.next();
      if (!ordered.contains(attr)) ordered.add(attr);
    }
    HorizontalScrollView chooser = new HorizontalScrollView(h);
    LinearLayout choiceRow = k.row(), list = k.column();
    chooser.addView(choiceRow);
    parent.addView(chooser);
    parent.addView(list);
    if (teamSummary.getChildCount() > 0) {
      if (h.compact()) {
        Button summaryToggle = k.action(null, "우리 팀 요약 ▾", () -> {});
        summaryToggle.setTag("type-team-summary-toggle");
        summaryToggle.setTextSize(h.uiText(13));
        teamSummary.setVisibility(View.GONE);
        summaryToggle.setOnClickListener(
            v -> {
              boolean open = teamSummary.getVisibility() != View.VISIBLE;
              teamSummary.setVisibility(open ? View.VISIBLE : View.GONE);
              summaryToggle.setText(open ? "우리 팀 요약 ▴" : "우리 팀 요약 ▾");
            });
        parent.addView(summaryToggle);
      }
      parent.addView(teamSummary);
    }
    String[] selected = {mine.isEmpty() ? "" : mine.iterator().next()};
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          list.removeAllViews();
          choiceRow.removeAllViews();
          ArrayList<String> showing = new ArrayList<>();
          if (!selected[0].isEmpty()) showing.add(selected[0]);
          if (selected[0].isEmpty()) showing.addAll(ordered);
          Button everyone =
              k.action(
                  null,
                  "전체",
                  () -> {
                    selected[0] = "";
                    paint[0].run();
                  });
          everyone.setTextSize(13);
          choiceRow.addView(everyone, new LinearLayout.LayoutParams(h.dp(68), h.dp(48)));
          for (String attr : ordered) {
            Button select =
                k.action(
                    null,
                    attrName(attrs, attr),
                    () -> {
                      selected[0] = attr;
                      paint[0].run();
                    });
            select.setTextSize(13);
            if (attr.equals(selected[0]))
              select.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
            LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(h.dp(74), h.dp(48));
            slot.leftMargin = h.dp(3);
            choiceRow.addView(select, slot);
          }
          ArrayList<View> entries = new ArrayList<>();
          for (String attr : showing) {
            JSONObject c = chart.optJSONObject(attr);
            ArrayList<String> strong = new ArrayList<>(), weak = new ArrayList<>();
            JSONArray ss = h.list(c, "strong"), explanations = h.list(c, "why");
            for (int i = 0; i < ss.length(); i++) strong.add(ss.optString(i));
            Iterator<String> others = chart.keys();
            while (others.hasNext()) {
              String other = others.next();
              JSONArray targets = h.list(chart.optJSONObject(other), "strong");
              for (int i = 0; i < targets.length(); i++)
                if (attr.equals(targets.optString(i))) weak.add(other);
            }
            LinearLayout card = k.paper(), head = k.row();
            card.setTag("type-chart-" + attr);
            boolean focus = attr.equals(selected[0]);
            if (focus) card.setBackground(new GamePanelDrawable(h));
            head.addView(k.chip(attrName(attrs, attr), attr));
            if (mine.contains(attr)) head.addView(h.text("  우리 팀", 14));
            card.addView(head);
            TextView good = k.note("유리 ×1.5  " + attrNames(attrs, strong));
            good.setTextColor(0xff276646);
            card.addView(good);
            TextView bad = k.note("불리 ×0.67  " + attrNames(attrs, weak));
            bad.setTextColor(0xff9b3c31);
            card.addView(bad);
            if (focus && !h.compact())
              for (int i = 0; i < ss.length(); i++)
                if (!explanations.optString(i).isEmpty())
                  card.addView(
                      k.note(attrName(attrs, ss.optString(i)) + " · " + explanations.optString(i)));
            card.setOnClickListener(
                v -> {
                  selected[0] = attr;
                  paint[0].run();
                });
            entries.add(card);
          }
          k.tiles(list, entries, h.screen.equals("types") && !h.compact() ? 2 : 1);
        };
    paint[0].run();
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

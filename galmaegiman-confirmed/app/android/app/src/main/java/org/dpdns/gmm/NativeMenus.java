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
    mixDraft.clear();
    library.reset();
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
      row.setBackground(h.material(0xfffffaf0));
      row.setPadding(h.dp(6), h.dp(4), h.dp(6), h.dp(4));
    }
    row.addView(k.image("/duel/icons/" + icon + ".png", 40, 40));
    LinearLayout words = k.column();
    words.addView(h.text(name, 22));
    if (!note.isEmpty()) words.addView(k.note(note));
    row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    parent.addView(row);
  }

  void modes() {
    h.base("게임 모드", "modes");
    LinearLayout head = (LinearLayout) h.page.getChildAt(0);
    head.removeAllViews();
    head.setBackground(null);
    head.setPadding(0, 0, 0, 0);
    h.title.setTextColor(android.graphics.Color.WHITE);
    h.title.setShadowLayer(h.dp(2), 0, h.dp(1), 0xff5a3418);
    h.title.setGravity(Gravity.CENTER);
    head.addView(h.title, new LinearLayout.LayoutParams(-1, h.dp(40)));
    LinearLayout[] cols = k.split(.49f);
    LinearLayout campaign = k.paper(), pvp = k.paper();
    for (LinearLayout board : new LinearLayout[] {campaign, pvp}) {
      board.setBackground(new GamePanelDrawable(h));
      board.setPadding(h.dp(14), h.dp(18), h.dp(14), h.dp(18));
      board.setMinimumHeight(
          Math.max(h.dp(220), h.getResources().getDisplayMetrics().heightPixels - h.dp(126)));
    }
    cols[0].addView(campaign);
    cols[1].addView(pvp);
    LinearLayout back = k.row();
    back.setGravity(Gravity.CENTER);
    back.addView(
        k.painted("btn-back", "돌아가기", h::back), new LinearLayout.LayoutParams(h.dp(104), h.dp(48)));
    k.footer(back);
    intro(campaign, "mode-camp", "캠페인", "이야기를 따라 항구를 지키고 갈매미를 성장시켜요.");
    Button story = k.action("u-story", "스토리", () -> h.load("story"));
    NativeTutorialController.anchor(story, "story");
    story.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
    campaign.addView(story);
    campaign.addView(k.note("이전 전투에 다시 도전해 경험치를 모을 수 있어요."));
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
          ranked.setText("랭크게임 · 승리 +" + r.optInt("win") + " / 패배 −" + r.optInt("lose"));
        });
    pvp.addView(k.action("mode-normal", "일반게임 · 점수 없이", () -> startPvp("normal")));
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
      link.setTextSize(14);
      link.setMinHeight(h.dp(48));
      link.setMinimumHeight(h.dp(48));
      link.setPadding(h.dp(4), 0, h.dp(4), h.dp(3));
    }
    pvp.addView(links);
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
        View fade = new View(h);
        fade.setBackground(
            new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0x00000000, 0xe0181716}));
        card.addView(fade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout info = k.column();
        info.setPadding(h.dp(14), h.dp(12), h.dp(14), h.dp(12));
        TextView name = h.text(c.optString("title"), 19);
        name.setTextColor(Color.WHITE);
        info.addView(name);
        TextView progress = k.note(cleared + " / " + ss.length() + " 완료");
        progress.setTextColor(0xffeadac4);
        info.addView(progress);
        TextView note = k.note(c.optString("intro"));
        note.setTextColor(0xfff4eadc);
        note.setMaxLines(2);
        note.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(note);
        int required = c.optInt("needStage");
        if (required > 0) {
          boolean ready = !storyTeam.isEmpty();
          for (JSONObject member : storyTeam) ready &= member.optInt("stage") >= required;
          TextView need = k.note((ready ? "✓ " : "잠김 · ") + "팀 전원 " + required + "단계 승진 필요");
          need.setTextColor(ready ? 0xffa0e1aa : 0xffffd299);
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
      guide.addView(h.text("성장 안내", 20));
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
            h.text(
                (st.optBoolean("cleared") ? "✓ " : st.optBoolean("open") ? "" : "🔒 ")
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
                    h.message("앞 전투를 완료하고 챕터의 승진 조건을 확인해 주세요.");
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
        cards.add(card);
      }
      k.tiles(content, cards, h.cols());
    }
    startStoryRefresh(stages);
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
            label.setTextColor(0xff6a492e);
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
          if (i == 0) h.collection();
          else if (i == 1) {
            h.selectionMode = NativeTutorialController.get(h).needsStoryTeam() ? "story" : "pvp";
            teamDraft = null;
            h.team();
          } else h.inventory();
        });
    View last = parent.getChildAt(parent.getChildCount() - 1);
    if (last instanceof LinearLayout && ((LinearLayout) last).getChildCount() > 1)
      NativeTutorialController.anchor(((LinearLayout) last).getChildAt(1), "team");
  }

  void team() {
    boolean pvp = h.selectionMode.equals("pvp");
    h.base(pvp ? "수비대" : "스토리 수비대 편성", "team");
    if (pvp) defenderTabs(h.body, 1);
    // 그룹 탭을 페이지 머리글 아래에 유지하며 목록만 스크롤한다.
    View tabs = pvp ? h.body.getChildAt(0) : null;
    if (tabs != null) h.body.removeView(tabs);
    if (tabs != null) h.page.addView(tabs, h.page.getChildCount() - 1);
    LinearLayout[] cols = k.split(.48f);
    LinearLayout left = cols[0], right = cols[1];
    for (LinearLayout col : cols) {
      col.setBackground(new GamePaperDrawable(h));
      col.setPadding(h.dp(10), h.dp(8), h.dp(10), h.dp(8));
      ((ScrollView) col.getParent()).setFillViewport(true);
    }
    JSONArray savedTeam = h.list(pvp ? h.me() : h.state, pvp ? "battleTeam" : "team");
    ArrayList<String> savedIds = new ArrayList<>();
    for (int i = 0; i < savedTeam.length(); i++) savedIds.add(savedTeam.optString(i));
    String mode = pvp ? "pvp" : "story";
    if (teamDraft == null || !mode.equals(teamMode)) {
      teamMode = mode;
      teamScrollX = 0;
      teamDraft = new ArrayList<>();
      JSONArray saved = h.list(pvp ? h.me() : h.state, pvp ? "battleTeam" : "team");
      for (int i = 0; i < saved.length(); i++) teamDraft.add(saved.optString(i));
    }
    ArrayList<JSONObject> candidates = new ArrayList<>();
    JSONArray source = h.list(pvp ? h.me() : h.state, "units");
    for (int i = 0; i < source.length(); i++) {
      JSONObject u = source.optJSONObject(i);
      if (!pvp || u.optBoolean("top")) candidates.add(u);
    }
    HorizontalScrollView[] liveCandidates = {null};
    TextView[] draftStatus = {null};
    Button[] draftSave = {null};
    Runnable[] render = new Runnable[1];
    render[0] =
        () -> {
          if (liveCandidates[0] != null) teamScrollX = liveCandidates[0].getScrollX();
          left.removeAllViews();
          right.removeAllViews();
          k.tabs(
              left,
              new String[] {"단상", "상성표"},
              teamChart ? 1 : 0,
              i -> {
                teamChart = i == 1;
                render[0].run();
              });
          LinearLayout tabsLine = (LinearLayout) left.getChildAt(0);
          for (int i = 0; i < tabsLine.getChildCount(); i++) {
            LinearLayout.LayoutParams lp =
                (LinearLayout.LayoutParams) tabsLine.getChildAt(i).getLayoutParams();
            lp.width = h.dp(100);
            lp.topMargin = 0;
            lp.bottomMargin = 0;
          }
          if (teamChart) chart(left, teamDraft, pvp);
          else {

            LinearLayout podium = k.row();
            podium.setGravity(Gravity.BOTTOM);
            left.addView(podium);
            for (int slot : new int[] {1, 0, 2}) {
              String id = slot < teamDraft.size() ? teamDraft.get(slot) : "";
              JSONObject found = null;
              for (JSONObject u : candidates)
                if (id.equals(u.optString(pvp ? "id" : "key"))) found = u;
              JSONObject u = found;
              LinearLayout step = k.paper();
              step.setTag("lineup-slot-" + slot);
              step.setBackground(new GamePanelDrawable(h));
              boolean compact = h.compact();
              step.setPadding(
                  h.dp(compact ? 8 : 10),
                  h.dp(compact ? 6 : 12),
                  h.dp(compact ? 8 : 10),
                  h.dp(compact ? 6 : 12));
              step.setGravity(Gravity.CENTER_HORIZONTAL);
              if (u == null) {
                step.addView(h.text("＋", compact ? 32 : 48));
                step.addView(k.note("비어 있음"));
              } else {
                step.addView(
                    k.image(
                        pvp ? "/thumbs/" + u.optString("id") + ".jpg" : u.optString("image"),
                        compact ? (slot == 0 ? 58 : 48) : (slot == 0 ? 156 : 132),
                        compact ? (slot == 0 ? 58 : 48) : (slot == 0 ? 176 : 148)));
                TextView who = h.text(u.optString("name"), compact ? 12 : 18);
                who.setGravity(Gravity.CENTER);
                who.setMaxLines(1);
                who.setEllipsize(android.text.TextUtils.TruncateAt.END);
                step.addView(who);
                String attr = u.optString("attrName", u.optString("attr"));
                if (!compact)
                  step.addView(k.chip(attr, u.optString("attrCode", u.optString("attr"))));
              }
              TextView role =
                  h.text(
                      (slot + 1) + " · " + (slot == 0 ? "선봉" : slot + 1 + "번"), compact ? 12 : 16);
              role.setBackground(h.material(slot == 0 ? 0xffffe08a : 0xffe9c98f));
              step.addView(role);
              step.setContentDescription(
                  (slot == 0 ? "선봉 " : (slot + 1) + "번 ")
                      + (u == null ? "빈칸" : u.optString("name"))
                      + ". 길게 눌러 순서 변경");
              step.setOnLongClickListener(
                  v -> {
                    if (!id.isEmpty()) reorderDraft(id, render[0]);
                    return true;
                  });
              step.setOnClickListener(
                  v -> {
                    if (!id.isEmpty()) {
                      teamDraft.remove(id);
                      render[0].run();
                    }
                  });
              h.cell(podium, step);
            }
            ArrayList<JSONObject> pickedUnits = new ArrayList<>();
            for (String key : teamDraft)
              for (JSONObject candidate : candidates)
                if (key.equals(candidate.optString(pvp ? "id" : "key"))) pickedUnits.add(candidate);
            left.addView(growth.combos(pickedUnits));
          }
          TextView candidateTip = k.notice("누르면 선택·제외 · 단상을 길게 누르면 순서 변경");
          candidateTip.setTextSize(h.compact() ? 12 : 14);
          candidateTip.setPadding(h.dp(6), h.dp(3), h.dp(6), h.dp(3));
          right.addView(candidateTip);
          ArrayList<JSONObject> quoteUnits = new ArrayList<>();
          for (String key : teamDraft)
            for (JSONObject candidate : candidates)
              if (key.equals(candidate.optString(pvp ? "id" : "key"))) quoteUnits.add(candidate);
          startPodiumQuotes(candidateTip, quoteUnits);
          ArrayList<View> choices = new ArrayList<>();
          for (JSONObject u : candidates) {
            String id = u.optString(pvp ? "id" : "key");
            int order = teamDraft.indexOf(id);
            LinearLayout tile =
                k.tile(
                    u,
                    pvp ? "/thumbs/" + u.optString("id") + ".jpg" : u.optString("image"),
                    pvp ? u.optString("tierName") : "Lv" + u.optInt("level"),
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
            if (!pvp && u.optString("id").equals("D1"))
              NativeTutorialController.anchor(tile, "team-unit");
            tile.setSelected(order >= 0);
            View portraitImage = tile.getChildAt(0);
            tile.removeView(portraitImage);
            FrameLayout portrait = new FrameLayout(h);
            portrait.addView(portraitImage, new FrameLayout.LayoutParams(-1, -1));
            if (order >= 0) {
              TextView check = h.text("✓", 22);
              check.setTextColor(0xff16886f);
              check.setBackground(new GamePaperDrawable(h));
              check.setPadding(h.dp(5), 0, h.dp(5), 0);
              portrait.addView(
                  check, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT));
              TextView position = h.text(order == 0 ? "선봉" : (order + 1) + "번", 14);
              position.setBackground(h.material(order == 0 ? 0xffffda71 : 0xfff1dcb2));
              portrait.addView(
                  position,
                  new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
              tile.setBackground(h.material(0xfffbe8d3));
            }
            boolean compactCandidate = h.compact();
            int portraitHeight =
                compactCandidate
                    ? Math.min(64, Math.max(48, h.contentHeightDp() - (pvp ? 320 : 268)))
                    : 140;
            tile.addView(portrait, 0, new LinearLayout.LayoutParams(-1, h.dp(portraitHeight)));
            String attrCode = u.optString("attrCode", u.optString("attr"));
            TextView attrChip = k.chip(u.optString("attrName", u.optString("attr")), attrCode);
            tile.setContentDescription(
                u.optString("name")
                    + " · "
                    + u.optString("attrName", u.optString("attr"))
                    + (order < 0
                        ? " · 누르면 선택"
                        : " · " + (order == 0 ? "선봉" : order + 1 + "번") + " 선택됨 · 누르면 제외"));
            if (compactCandidate) {
              tile.setPadding(h.dp(8), h.dp(5), h.dp(8), h.dp(5));
              TextView candidateName = (TextView) tile.getChildAt(1);
              candidateName.setTextSize(14);
              candidateName.setMaxLines(1);
              candidateName.setEllipsize(android.text.TextUtils.TruncateAt.END);
              candidateName.setPadding(0, h.dp(2), 0, h.dp(2));
              TextView metadata = (TextView) tile.getChildAt(2);
              tile.removeView(metadata);
              metadata.setTextSize(13);
              metadata.setMaxLines(1);
              metadata.setPadding(0, h.dp(1), h.dp(4), h.dp(1));
              LinearLayout metaRow = k.row();
              metaRow.addView(metadata, new LinearLayout.LayoutParams(0, -2, 1));
              attrChip.setTextSize(12);
              metaRow.addView(attrChip);
              tile.addView(metaRow);
            } else tile.addView(attrChip);
            if (!pvp)
              tile.setOnLongClickListener(
                  v -> {
                    unitReturn = "team";
                    h.unitDetail(id);
                    return true;
                  });
            choices.add(tile);
          }
          HorizontalScrollView candidatesScroll = new HorizontalScrollView(h);
          LinearLayout candidatesRow = k.row();
          candidatesRow.setGravity(Gravity.TOP);
          for (View choice : choices) {
            LinearLayout.LayoutParams slot =
                new LinearLayout.LayoutParams(h.dp(h.compact() ? 132 : 170), -2);
            slot.setMargins(h.dp(3), 0, h.dp(3), 0);
            candidatesRow.addView(choice, slot);
          }
          candidatesScroll.setHorizontalScrollBarEnabled(true);
          candidatesScroll.addView(candidatesRow);
          liveCandidates[0] = candidatesScroll;
          right.addView(candidatesScroll);
          candidatesScroll.post(() -> candidatesScroll.scrollTo(teamScrollX, 0));
          if (draftStatus[0] != null)
            draftStatus[0].setText(
                (teamDraft.equals(savedIds) ? "저장된 편성" : "바뀐 편성")
                    + " · 출전 "
                    + teamDraft.size()
                    + " / 3");
          if (draftSave[0] != null)
            draftSave[0].setEnabled(!teamDraft.isEmpty() && !teamDraft.equals(savedIds));
        };
    render[0].run();
    LinearLayout footer = k.paper();
    LinearLayout line = k.row();
    TextView status =
        k.note(
            (teamDraft.equals(savedIds) ? "저장된 편성" : "바뀐 편성")
                + " · 출전 "
                + teamDraft.size()
                + " / 3");
    draftStatus[0] = status;
    status.setTextSize(h.compact() ? 12 : 14);
    line.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
    line.addView(
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
            }),
        new LinearLayout.LayoutParams(h.dp(96), h.dp(48)));
    if (!pvp && h.list(h.me(), "battleTeam").length() > 0) {
      Button importTeam =
          k.action(
              null,
              "편성 불러오기",
              () -> {
                ArrayList<String> imported = new ArrayList<>();
                JSONArray species = h.list(h.me(), "battleTeam");
                for (int i = 0; i < species.length(); i++)
                  for (JSONObject u : candidates)
                    if (species.optString(i).equals(u.optString("id"))
                        && !imported.contains(u.optString("key"))) {
                      imported.add(u.optString("key"));
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
      importTeam.setTextSize(h.uiText(15));
      line.addView(importTeam, new LinearLayout.LayoutParams(h.dp(138), h.dp(48)));
    }
    Button save =
        k.painted(
            "btn-save",
            "편성 저장",
            () -> {
              if (teamDraft.isEmpty()) {
                h.message("1마리 이상 골라 주세요.");
                return;
              }
              h.request(
                  pvp ? "battle-team" : "native/team",
                  MainActivity.obj(pvp ? "ids" : "keys", MainActivity.arr(teamDraft)),
                  (r, e) -> {
                    teamDraft = null;
                    h.load("team");
                  });
            });
    NativeTutorialController.anchor(save, "team-save");
    draftSave[0] = save;
    save.setEnabled(!teamDraft.isEmpty() && !teamDraft.equals(savedIds));
    line.addView(save, new LinearLayout.LayoutParams(h.dp(156), h.dp(48)));
    footer.addView(line);
    k.footer(footer);
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
    parent.addView(k.notice("공격 속성 → 방어 속성 · 유리 ×1.5 / 불리 ×0.67 / 그 외 ×1"));
    if (!mine.isEmpty()) {
      LinearLayout summary = k.paper();
      summary.addView(h.text("우리 팀", 18));
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
      parent.addView(summary);
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
    String[] selected = {mine.isEmpty() ? "" : mine.iterator().next()};
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          list.removeAllViews();
          choiceRow.removeAllViews();
          ArrayList<String> showing = new ArrayList<>();
          if (!selected[0].isEmpty()) showing.add(selected[0]);
          for (String attr : ordered) if (!showing.contains(attr)) showing.add(attr);
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
            if (focus)
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
          k.tiles(list, entries, h.screen.equals("types") ? 2 : 1);
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
    JSONObject a = attrs.optJSONObject(id);
    return a == null ? id : a.optString("name", id);
  }
}

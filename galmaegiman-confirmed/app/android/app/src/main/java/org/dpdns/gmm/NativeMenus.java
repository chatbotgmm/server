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
  String query = "", grade = "all", recipeQuery = "", recipeId = "";
  boolean craftOnly, teamChart;
  JSONObject lastDraw;
  ArrayList<String> teamDraft, mixDraft = new ArrayList<>();
  String teamMode = "", unitReturn = "story";

  NativeMenus(MainActivity h) {
    this.h = h;
    k = new MenuKit(h);
    library = new NativeLibrary(this);
    economy = new NativeEconomy(this);
    extras = new NativeExtras(this);
  }

  void reset() {
    query = "";
    grade = "all";
    recipeQuery = "";
    recipeId = "";
    lastDraw = null;
    teamDraft = null;
    mixDraft.clear();
    extras.guide = 0;
  }

  void fetch(String endpoint, Consumer<JSONObject> next) {
    int epoch = h.epoch;
    h.api.call(
        endpoint,
        null,
        (r, e) -> {
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
    LinearLayout[] cols = k.split(.49f);
    LinearLayout campaign = k.paper(), pvp = k.paper();
    cols[0].addView(campaign);
    cols[1].addView(pvp);
    intro(campaign, "mode-camp", "캠페인", "이야기를 따라 항구를 지키고 갈매미를 성장시켜요.");
    campaign.addView(k.action("u-story", "스토리 · 챕터 선택", () -> h.load("story")));
    Button adventure = k.action("mode-adv", "모험 · 상위 갈매미 포획", () -> {});
    adventure.setEnabled(false);
    campaign.addView(adventure);
    campaign.addView(k.note("준비 중 · 다음 업데이트"));
    intro(pvp, "mode-pvp", "PvP", "다른 수비대의 전투 편성과 대결 · 모두 같은 레벨");
    TextView rank = k.note("랭크 불러오는 중…");
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
                    + "패 · Lv"
                    + r.optInt("level"));
        });
    pvp.addView(k.action("mode-normal", "일반게임", () -> h.startFight("normal", null)));
    pvp.addView(
        k.action(
            "mode-rank",
            "랭크게임",
            () -> h.confirm("중간에 나가면 패배로 처리돼요. 시작할까요?", () -> h.startFight("rank", null))));
    pvp.addView(k.action("focus", "연습 · 자유 대결", h::practice));
    LinearLayout links = k.row();
    h.cell(links, k.action("mode-rank", "순위표", () -> h.load("pvp")));
    h.cell(
        links,
        k.action(
            "mode-normal",
            "전투 편성",
            () -> {
              h.selectionMode = "pvp";
              h.load("team");
            }));
    pvp.addView(links);
  }

  void story(boolean stages) {
    JSONArray chapters = h.list(h.state, "chapters");
    JSONObject chapter = chapters.optJSONObject(h.chapter);
    h.base(
        stages && chapter != null ? chapter.optString("title") : "스토리 · 챕터 선택",
        stages ? "stages" : "story");
    LinearLayout[] cols = k.split(.32f);
    LinearLayout team = cols[0], content = cols[1];
    intro(team, "m-units", "내 수비대", "갈매미를 눌러 기술·승진을 확인해요.");
    JSONArray keys = h.list(h.state, "team");
    for (int i = 0; i < keys.length(); i++) {
      String key = keys.optString(i);
      JSONObject u = h.unit(key);
      LinearLayout tile = k.paper();
      LinearLayout who = k.row();
      who.addView(k.image(u.optString("image"), 64, 64));
      TextView name =
          h.text(
              u.optString("name") + "\nLv" + u.optInt("level") + " · " + u.optString("attr"), 16);
      who.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
      tile.addView(who);
      k.progress(
          tile,
          "체력 " + u.optInt("hp") + "/" + u.optInt("maxHp"),
          u.optInt("hp"),
          u.optInt("maxHp"),
          0xff51c98b);
      k.progress(
          tile,
          "경험치 " + u.optInt("xp") + "/" + u.optInt("xpNext"),
          u.optInt("xp"),
          u.optInt("xpNext"),
          0xffe9a630);
      tile.addView(
          k.note(
              h.list(u, "evolve").length() > 0
                  ? "⬆ 승진 가능"
                  : h.list(u, "promo").length() > 0
                      ? "전용 기술 배우기"
                      : h.list(u, "learn").length() > 0 ? "새 기술 배우기" : "기술·승진 보기 ›"));
      tile.setOnClickListener(
          v -> {
            unitReturn = "story";
            h.unitDetail(key);
          });
      team.addView(tile);
    }
    if (keys.length() == 0) team.addView(k.note("먼저 수비대를 정해 주세요. 1~3마리로 출발할 수 있어요."));
    team.addView(
        k.action(
            "m-units",
            "편성 바꾸기",
            () -> {
              h.selectionMode = "story";
              teamDraft = null;
              h.team();
            }));
    team.addView(k.action("potion", "치료약 · 아이템", h::inventory));
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
        LinearLayout card = k.paper();
        String scene =
            ss.length() > 0 ? ss.optJSONObject(0).optString("scene", "harbor") : "harbor";
        card.addView(k.image("/duel/bg/" + scene + ".jpg", 180, 92));
        card.addView(h.text(c.optString("title"), 20));
        card.addView(k.note(c.optString("intro")));
        k.progress(card, cleared + " / " + ss.length() + " 완료", cleared, ss.length(), 0xffe9a630);
        card.addView(
            k.action(
                "u-story",
                opened > 0 ? "챕터 선택" : "잠겨 있음",
                () -> {
                  h.chapter = index;
                  h.stages();
                }));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(h.dp(220), -2);
        lp.setMargins(h.dp(4), 0, h.dp(4), 0);
        row.addView(card, lp);
      }
    } else {
      content.addView(k.note(chapter.optString("intro")));
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
        card.addView(k.note("상대 Lv" + (foe == null ? 0 : foe.optInt("level"))));
        card.addView(
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
                      h.list(st, "before"),
                      st.optString("title"),
                      () -> h.startFight("story", st.optString("id")));
                }));
        cards.add(card);
      }
      k.tiles(content, cards, h.cols());
    }
  }

  void defenderTabs(LinearLayout parent, int active) {
    k.tabs(
        parent,
        new String[] {"내 갈매미", "전투 편성", "아이템"},
        active,
        i -> {
          if (i == 0) h.collection();
          else if (i == 1) {
            h.selectionMode = "pvp";
            teamDraft = null;
            h.team();
          } else h.inventory();
        });
  }

  void team() {
    boolean pvp = h.selectionMode.equals("pvp");
    h.base(pvp ? "수비대 · 전투 편성" : "스토리 수비대 편성", "team");
    if (pvp) defenderTabs(h.body, 1);
    // 그룹 탭을 페이지 머리글 아래에 유지하며 목록만 스크롤한다.
    View tabs = pvp ? h.body.getChildAt(0) : null;
    if (tabs != null) h.body.removeView(tabs);
    if (tabs != null) h.page.addView(tabs, h.page.getChildCount() - 1);
    LinearLayout[] cols = k.split(.48f);
    LinearLayout left = cols[0], right = cols[1];
    String mode = pvp ? "pvp" : "story";
    if (teamDraft == null || !mode.equals(teamMode)) {
      teamMode = mode;
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
    Runnable[] render = new Runnable[1];
    render[0] =
        () -> {
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
          if (teamChart) chart(left, teamDraft, pvp);
          else {
            left.addView(k.note("선봉이 먼저 나가요 · 단상을 누르면 빠져요"));
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
              if (u == null) {
                step.addView(h.text("＋", 36));
                step.addView(k.note("비어 있음"));
              } else {
                step.addView(
                    k.image(
                        pvp ? "/thumbs/" + u.optString("id") + ".jpg" : u.optString("image"),
                        84,
                        slot == 0 ? 110 : 88));
                step.addView(h.text(u.optString("name"), 14));
              }
              TextView role = h.text((slot + 1) + " · " + (slot == 0 ? "선봉" : slot + 1 + "번"), 14);
              role.setBackground(h.material(slot == 0 ? 0xffffe08a : 0xffe9c98f));
              step.addView(role);
              step.setOnClickListener(
                  v -> {
                    if (!id.isEmpty()) {
                      teamDraft.remove(id);
                      render[0].run();
                    }
                  });
              h.cell(podium, step);
            }
          }
          right.addView(k.note("눌러서 넣기 · 다시 누르면 빼기 · 최대 3마리"));
          ArrayList<View> choices = new ArrayList<>();
          for (JSONObject u : candidates) {
            String id = u.optString(pvp ? "id" : "key");
            int order = teamDraft.indexOf(id);
            LinearLayout tile =
                k.tile(
                    u,
                    pvp ? "/thumbs/" + u.optString("id") + ".jpg" : u.optString("image"),
                    (order < 0 ? "" : (order + 1) + "번 · ")
                        + (pvp ? u.optString("tierName") : "Lv" + u.optInt("level")),
                    () -> {
                      if (teamDraft.contains(id)) teamDraft.remove(id);
                      else if (teamDraft.size() < 3) teamDraft.add(id);
                      else {
                        h.message("3마리까지 고를 수 있어요.");
                        return;
                      }
                      render[0].run();
                    });
            if (order >= 0) tile.setBackground(h.material(0xfffbe8d3));
            if (!pvp)
              tile.setOnLongClickListener(
                  v -> {
                    unitReturn = "team";
                    h.unitDetail(id);
                    return true;
                  });
            choices.add(tile);
          }
          k.tiles(right, choices, Math.max(2, h.cols()));
        };
    render[0].run();
    LinearLayout footer = k.paper();
    LinearLayout line = k.row();
    TextView status = k.note("편성을 바꾼 뒤 저장해 주세요.");
    line.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
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
    line.addView(save, new LinearLayout.LayoutParams(h.dp(180), h.dp(48)));
    footer.addView(line);
    k.footer(footer);
  }

  void chart(LinearLayout parent, Collection<String> team, boolean species) {
    JSONObject chart = h.state.optJSONObject("typeChart"),
        attrs = h.state.optJSONObject("attributes");
    if (chart == null || attrs == null) return;
    HashSet<String> mine = new HashSet<>();
    for (String id : team) {
      String unit = species ? id : h.unit(id).optString("id");
      JSONArray own = h.list(h.me(), "units");
      for (int i = 0; i < own.length(); i++)
        if (unit.equals(own.optJSONObject(i).optString("id")))
          mine.add(own.optJSONObject(i).optString("attr"));
    }
    Iterator<String> keys = chart.keys();
    HashSet<String> beats = new HashSet<>(), danger = new HashSet<>();
    while (keys.hasNext()) {
      String attr = keys.next();
      JSONObject c = chart.optJSONObject(attr);
      ArrayList<String> strong = new ArrayList<>(), weak = new ArrayList<>();
      JSONArray ss = h.list(c, "strong");
      for (int i = 0; i < ss.length(); i++) {
        String target = ss.optString(i);
        strong.add(attrName(attrs, target));
        if (mine.contains(attr)) beats.add(target);
      }
      Iterator<String> others = chart.keys();
      while (others.hasNext()) {
        String other = others.next();
        JSONArray targets = h.list(chart.optJSONObject(other), "strong");
        for (int i = 0; i < targets.length(); i++)
          if (attr.equals(targets.optString(i))) {
            weak.add(attrName(attrs, other));
            if (mine.contains(attr)) danger.add(other);
          }
      }
      LinearLayout row = k.paper();
      row.addView(h.text((mine.contains(attr) ? "★ " : "") + attrName(attrs, attr), 17));
      row.addView(
          k.note(
              "▲ 강함"
                  + " "
                  + android.text.TextUtils.join(" · ", strong)
                  + "\n▼ 조심 "
                  + android.text.TextUtils.join(" · ", weak)));
      parent.addView(row);
    }
    if (!mine.isEmpty()) {
      danger.removeAll(beats);
      ArrayList<String> b = new ArrayList<>(), d = new ArrayList<>();
      for (String a : beats) b.add(attrName(attrs, a));
      for (String a : danger) d.add(attrName(attrs, a));
      parent.addView(
          k.note(
              "우리 팀 ▲ "
                  + android.text.TextUtils.join(" · ", b)
                  + "\n조심 ▼ "
                  + android.text.TextUtils.join(" · ", d)));
    }
  }

  String attrName(JSONObject attrs, String id) {
    JSONObject a = attrs.optJSONObject(id);
    return a == null ? id : a.optString("name", id);
  }
}

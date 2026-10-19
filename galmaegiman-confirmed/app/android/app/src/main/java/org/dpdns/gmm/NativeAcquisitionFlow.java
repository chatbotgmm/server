package org.dpdns.gmm;

import android.view.Gravity;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;

/** 획득한 종류와 실제 소유 개체를 구분한다. 이동 전에는 읽기 요청으로만 동기화한다. */
final class NativeAcquisitionFlow {
  final MainActivity h;
  final MenuKit k;
  final String origin;
  final String account;
  final Set<String> before;
  Runnable reopen;
  JSONObject pendingReassessment;
  int pendingReassessmentIndex;
  String pendingReassessmentKey;
  int selected;

  NativeAcquisitionFlow(MainActivity h, String origin, Set<String> before) {
    this.h = h;
    this.k = new MenuKit(h);
    this.origin = origin;
    account = h.api.token();
    this.before = before == null ? null : new HashSet<>(before);
  }

  static Set<String> keys(JSONObject state) {
    Set<String> keys = new HashSet<>();
    JSONArray units = state.optJSONArray("units");
    if (units != null)
      for (int i = 0; i < units.length(); i++) {
        JSONObject unit = units.optJSONObject(i);
        if (unit != null && unit.optString("key").matches("o\\d+")) keys.add(unit.optString("key"));
      }
    return keys;
  }

  static List<JSONObject> matches(JSONObject state, String id, Set<String> before) {
    ArrayList<JSONObject> found = new ArrayList<>();
    JSONArray units = state.optJSONArray("units");
    if (units != null)
      for (int i = 0; i < units.length(); i++) {
        JSONObject unit = units.optJSONObject(i);
        if (unit != null
            && id.equals(unit.optString("id"))
            && unit.optString("key").matches("o\\d+")
            && (before == null || !before.contains(unit.optString("key")))) found.add(unit);
      }
    return found;
  }

  static boolean battleSpecies(JSONObject item) {
    return Arrays.asList("LIMITED", "TRANSCEND", "ETERNAL", "IMMORTAL")
        .contains(item.optString("tier"));
  }

  LinearLayout actions(JSONObject item) {
    LinearLayout row = k.row();
    row.setGravity(Gravity.CENTER);
    addAction(row, "상세", "acquisition-detail", () -> fresh(() -> detail(item)));
    if (battleSpecies(item)) {
      Button grow = addAction(row, "육성", "acquisition-grow", () -> choose(item, this::grow));
      k.emphasize(grow);
      addAction(row, "전투 편성", "acquisition-team", () -> choose(item, this::team));
    } else {
      Button craft = addAction(row, "조합식", "acquisition-craft", () -> recipes(item));
      k.emphasize(craft);
    }
    return row;
  }

  Button addAction(LinearLayout row, String text, String tag, Runnable action) {
    Button button = k.action(null, text, action);
    button.setTag(tag);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h.dp(48), 1);
    lp.setMargins(h.dp(4), 0, h.dp(4), 0);
    row.addView(button, lp);
    return button;
  }

  void fresh(Runnable next) {
    if (!account.equals(h.api.token())) {
      h.load(origin);
      return;
    }
    h.request(
        "native/state",
        null,
        (state, error) -> {
          h.state = state;
          h.stateUpdated();
          next.run();
        });
  }

  void choose(JSONObject item, Consumer<String> next) {
    fresh(
        () -> {
          if (!battleSpecies(item)) return;
          List<JSONObject> added =
              before == null
                  ? Collections.emptyList()
                  : matches(h.state, item.optString("id"), before);
          boolean acquired = !added.isEmpty();
          List<JSONObject> choices =
              acquired ? added : matches(h.state, item.optString("id"), null);
          if (choices.isEmpty()) {
            h.message("지금 보유 중인 전투 갈매미가 없어요. 조합·탐험 결과를 확인해 주세요.");
            return;
          }
          // 새로 생긴 실제 키 한 개만 자동 선택한다. 과거 결과는 기존 개체를 직접 고르게 한다.
          if (acquired && choices.size() == 1) {
            next.accept(choices.get(0).optString("key"));
            return;
          }
          String[] labels = new String[choices.size()];
          for (int i = 0; i < choices.size(); i++) labels[i] = individual(choices.get(i));
          new GameDialog(h)
              .setTitle(acquired ? "이번에 얻은 갈매미 선택" : "이전 결과 · 보유 갈매미 선택")
              .setItems(labels, (dialog, index) -> next.accept(choices.get(index).optString("key")))
              .setNegativeButton("닫기", null)
              .show();
        });
  }

  static String individual(JSONObject unit) {
    return unit.optString("name")
        + " · Lv"
        + unit.optInt("level")
        + " · 승진 "
        + unit.optInt("stage")
        + " · #"
        + unit.optString("key").replaceFirst("^o", "");
  }

  void grow(String key) {
    h.menus.unitReturn = "acquisition";
    h.unitDetail(key);
  }

  void team(String key) {
    if (h.state.optBoolean("activeBattle")) {
      h.message("진행 중인 전투를 마친 뒤 편성을 변경해 주세요.");
      return;
    }
    ArrayList<String> saved = new ArrayList<>();
    JSONArray roster = h.list(h.state, "team");
    for (int i = 0; i < roster.length(); i++) saved.add(roster.optString(i));
    if (saved.contains(key) || saved.size() < 3) {
      ArrayList<String> draft = new ArrayList<>(saved);
      if (!draft.contains(key)) draft.add(key);
      openTeam(saved, draft, key);
      return;
    }
    String[] slots = new String[saved.size()];
    for (int i = 0; i < slots.length; i++)
      slots[i] = (i + 1) + "번 · " + individual(h.unit(saved.get(i)));
    new GameDialog(h)
        .setTitle("교체할 자리 선택 · 저장 전 미리보기")
        .setItems(
            slots,
            (dialog, index) -> {
              ArrayList<String> draft = new ArrayList<>(saved);
              draft.set(index, key);
              openTeam(saved, draft, key);
            })
        .setNegativeButton("취소", null)
        .show();
  }

  void openTeam(ArrayList<String> saved, ArrayList<String> draft, String key) {
    h.selectionMode = "story";
    h.menus.teamMode = "story";
    h.menus.teamSavedIds = new ArrayList<>(saved);
    h.menus.teamDraft = new ArrayList<>(draft);
    h.menus.teamReturn = "acquisition";
    h.menus.teamQuery = "";
    h.menus.teamAttr = "all";
    h.menus.teamSelectedOnly = false;
    h.menus.teamScrollX = 0;
    h.team();
    Toast.makeText(h, h.unit(key).optString("name") + " 선택 · 편성을 확인하고 저장해 주세요.", Toast.LENGTH_SHORT)
        .show();
  }

  void recipes(JSONObject item) {
    h.menus.recipeQuery = item.optString("name");
    h.menus.recipeId = "";
    h.menus.craftOnly = false;
    h.load("craft");
  }

  void detail(JSONObject item) {
    JSONObject owned = item;
    JSONArray units = h.list(h.me(), "units");
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.optJSONObject(i);
      if (unit != null && item.optString("id").equals(unit.optString("id"))) owned = unit;
    }
    LinearLayout panel = k.paper();
    panel.setTag("acquisition-details");
    LinearLayout line = k.row(), words = k.column();
    line.addView(
        k.image("/thumbs/" + item.optString("img", item.optString("id")) + ".jpg", 92, 92));
    TextView title = h.text(item.optString("name"), 22);
    title.setTypeface(h.headline);
    words.addView(title);
    words.addView(
        k.note(
            item.optString("tierName")
                + (owned.optString("attrName").isEmpty()
                    ? ""
                    : " · " + owned.optString("attrName"))));
    words.addView(k.note("보유 " + owned.optInt("have") + "마리 · 탐험 " + owned.optInt("away") + "마리"));
    line.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    panel.addView(line);
    if (!owned.optString("intro").isEmpty()) panel.addView(k.note(owned.optString("intro")));
    String source = h.menus.economy.source(item.optString("id"));
    if (!source.isEmpty()) panel.addView(k.note("얻는 곳 · " + source));
    if (battleSpecies(item)) {
      List<JSONObject> added =
          before == null ? Collections.emptyList() : matches(h.state, item.optString("id"), before);
      panel.addView(
          k.note(added.isEmpty() ? "개체별 기술·능력치는 육성에서 확인해 주세요." : "이번 획득 · " + added.size() + "마리"));
      for (JSONObject unit : added) panel.addView(k.note(individual(unit)));
    } else panel.addView(k.note("수집·조합용 갈매미예요. 최상위 갈매미 33종은 육성하고 전투에 편성할 수 있어요."));
    GameDialog dialog = new GameDialog(h);
    dialog.setView(k.scroll(panel)).setPositiveButton("닫기", null);
    if (battleSpecies(item)) dialog.setNeutralButton("육성", (d, w) -> choose(item, this::grow));
    else dialog.setNeutralButton("조합식", (d, w) -> recipes(item));
    dialog.show();
  }
}


package org.dpdns.gmm;

import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 수비대의 목록·상세·아이템·기술·승진 구성. */
final class NativeLibrary {
  final NativeMenus m;
  final MainActivity h;
  final MenuKit k;

  NativeLibrary(NativeMenus menus) {
    m = menus;
    h = m.h;
    k = m.k;
  }

  void collection() {
    h.base("수비대", "collection");
    m.defenderTabs(h.body, 0);
    LinearLayout filters = k.column(), list = k.column();
    h.body.addView(filters);
    h.body.addView(list);
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          list.removeAllViews();
          JSONArray all = h.list(h.me(), "units");
          ArrayList<JSONObject> visible = new ArrayList<>();
          for (int i = 0; i < all.length(); i++) {
            JSONObject u = all.optJSONObject(i);
            boolean grade =
                m.grade.equals("all")
                    || m.grade.equals("top") && u.optBoolean("top")
                    || m.grade.equals("low")
                        && !u.optBoolean("top")
                        && !u.optString("tier").equals("LEGEND")
                        && !u.optString("tier").equals("HIDDEN")
                    || m.grade.equals(u.optString("tier"));
            if (grade
                && (u.optString("name") + u.optString("tierName") + u.optString("attrName"))
                    .toLowerCase(Locale.KOREA)
                    .contains(m.query.toLowerCase(Locale.KOREA))) visible.add(u);
          }
          ArrayList<View> cards = new ArrayList<>();
          for (int i = 0; i < visible.size(); i++) {
            int index = i;
            JSONObject u = visible.get(i);
            LinearLayout card = k.paper();
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout portrait = new FrameLayout(h);
            ImageView art = k.image("/thumbs/" + u.optString("id") + ".jpg", 100, 100);
            portrait.addView(art, new FrameLayout.LayoutParams(-1, -1));
            TextView have = h.text("×" + u.optInt("have"), 12);
            have.setBackground(new GamePaperDrawable(h));
            have.setPadding(h.dp(4), 0, h.dp(4), 0);
            portrait.addView(
                have, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT));
            card.addView(portrait, new LinearLayout.LayoutParams(-1, h.dp(96)));
            TextView name = h.text(u.optString("name"), 12);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(name);
            TextView grade = k.note(u.optString("tierName") + " · " + u.optString("attrName"));
            grade.setTextSize(11);
            grade.setGravity(Gravity.CENTER);
            card.addView(grade);
            card.setOnClickListener(v -> detail(visible, index));
            card.setOnLongClickListener(
                v -> {
                  hold(visible, index);
                  return true;
                });
            cards.add(card);
          }
          int usable = h.getResources().getConfiguration().screenWidthDp - 32;
          k.tiles(list, cards, Math.max(3, usable / 125));
          if (cards.isEmpty()) list.addView(k.note("찾는 갈매미가 없어요. 이름·등급·속성을 바꿔 찾아보세요."));
        };
    k.search(
        filters,
        "이름·등급·속성으로 찾기",
        m.query,
        value -> {
          m.query = value;
          paint[0].run();
        });
    String[] values = {"all", "top", "LEGEND", "HIDDEN", "low"};
    k.tabs(
        filters,
        new String[] {"전체", "최상위", "전설", "히든", "그 밖"},
        Arrays.asList(values).indexOf(m.grade),
        i -> {
          m.grade = values[i];
          collection();
        });
    ((LinearLayout) filters.getChildAt(filters.getChildCount() - 1)).setGravity(Gravity.LEFT);
    paint[0].run();
  }

  void detail(ArrayList<JSONObject> units, int at) {
    if (units.isEmpty()) return;
    JSONObject u = units.get(at);
    h.base(u.optString("name"), "collectionDetail");
    LinearLayout[] cols = k.split(.38f);
    cols[0].addView(k.image("/thumbs/" + u.optString("id") + ".jpg", 230, 230));
    LinearLayout info = k.paper();
    cols[1].addView(info);
    info.addView(h.text(u.optString("tierName") + " · " + u.optString("attrName"), 17));
    info.addView(h.text(u.optString("name"), 24));
    if (!u.optString("quote").isEmpty()) info.addView(k.note("「" + u.optString("quote") + "」"));
    info.addView(k.note(u.optString("tagline")));
    info.addView(k.note(u.optString("intro")));
    info.addView(
        k.note(
            "보유"
                + " "
                + u.optInt("have")
                + "마리 · 탐험 "
                + u.optInt("away")
                + " · 보호 "
                + u.optInt("locked")));
    info.addView(k.action(null, "갈매미 메뉴", () -> hold(units, at)));
    LinearLayout footer = k.row();
    h.cell(
        footer,
        k.action(null, "‹ 이전", () -> detail(units, (at - 1 + units.size()) % units.size())));
    h.cell(footer, k.action(null, "목록으로", this::collection));
    h.cell(footer, k.action(null, "다음 ›", () -> detail(units, (at + 1) % units.size())));
    k.footer(footer);
  }

  void hold(ArrayList<JSONObject> list, int at) {
    JSONObject u = list.get(at);
    String id = u.optString("id");
    LinearLayout options = k.paper();
    android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
    String[] names = {"자세히 보기", "조합하기", "탐험 편성에 넣기", u.optInt("locked") > 0 ? "보호 해제" : "보호하기"};
    Runnable[] actions = {
      () -> detail(list, at),
      () -> {
        m.recipeQuery = u.optString("name");
        m.recipeId = id;
        m.craftOnly = false;
        h.craft();
      },
      () -> {
        JSONArray draft = new JSONArray();
        JSONArray saved = h.list(h.me(), "party");
        for (int i = 0; i < saved.length(); i++) draft.put(saved.optString(i));
        draft.put(id);
        h.request("party", MainActivity.obj("ids", draft), (r, e) -> h.load("expedition"));
      },
      () ->
          h.confirm(
              "탐험 중이 아닌 갈매미의 보호 상태를 바꿀까요?",
              () ->
                  h.mutate(
                      "units/protect",
                      MainActivity.obj("id", id, "locked", u.optInt("locked") == 0),
                      "collection"))
    };
    for (int i = 0; i < names.length; i++) {
      int index = i;
      Button b =
          k.action(
              null,
              names[i],
              () -> {
                dialog[0].dismiss();
                actions[index].run();
              });
      if (i == 2)
        b.setEnabled(
            u.optBoolean("top")
                && u.optInt("have") > u.optInt("away")
                && h.me().isNull("trip")
                && h.list(h.me(), "party").length() < 10);
      if (i == 3) b.setEnabled(u.optInt("have") > u.optInt("away"));
      options.addView(b);
    }
    dialog[0] =
        new GameDialog(h)
            .setTitle(u.optString("name"))
            .setView(k.scroll(options))
            .setNegativeButton("닫기", null)
            .show();
  }

  LinearLayout skill(JSONObject sk, String tag, Runnable click) {
    LinearLayout card = k.paper();
    card.addView(k.note(tag));
    card.addView(h.text(sk.optString("name"), 19));
    String category =
        sk.optString("cat").equals("phys")
            ? "물리"
            : sk.optString("cat").equals("mag") ? "마법" : "능력치에 따라";
    String meta =
        sk.optInt("power") > 0
            ? "위력 "
                + sk.optInt("power")
                + (sk.optInt("hits") > 1 ? " ×" + sk.optInt("hits") : "")
                + " · "
                + category
            : "보조 기술";
    card.addView(k.note(meta));
    card.addView(k.note(sk.optString("desc")));
    if (click != null) card.addView(k.action("learn", "선택", click));
    return card;
  }

  void unit(String key) {
    h.currentKey = key;
    JSONObject u = h.unit(key);
    h.base(u.optString("name") + " · 기술과 승진", "unit");
    LinearLayout[] cols = k.split(.32f);
    LinearLayout who = k.paper();
    cols[0].addView(who);
    who.addView(k.image(u.optString("image"), 140, 120));
    who.addView(h.text(u.optString("name"), 20));
    who.addView(
        k.note(
            "Lv"
                + u.optInt("level")
                + " · "
                + u.optString("attr")
                + " · 승진 "
                + u.optInt("stage")
                + "단계"));
    k.progress(
        who,
        "체력 " + u.optInt("hp") + " / " + u.optInt("maxHp"),
        u.optInt("hp"),
        u.optInt("maxHp"),
        0xff51c98b);
    k.progress(
        who,
        "경험치 " + u.optInt("xp") + " / " + u.optInt("xpNext"),
        u.optInt("xp"),
        u.optInt("xpNext"),
        0xffe9a630);
    JSONObject stats = u.optJSONObject("stats");
    if (stats != null) {
      String[] ids = {"patk", "pdef", "matk", "mdef", "spd"},
          names = {"물리 공격", "물리 방어", "마법 공격", "마법 방어", "속도"};
      for (int i = 0; i < ids.length; i++)
        who.addView(k.note(names[i] + " " + stats.optInt(ids[i])));
    }
    JSONArray augs = h.list(u, "evoAugs");
    JSONObject definitions = h.state.optJSONObject("evoDefinitions");
    for (int i = 0; i < augs.length(); i++) {
      String id = augs.optString(i);
      JSONObject aug = definitions == null ? null : definitions.optJSONObject(id);
      who.addView(
          k.note(
              "직무 표창 · "
                  + (aug == null ? id : aug.optString("name") + "\n" + aug.optString("desc"))));
    }
    who.addView(k.action("potion", "치료약", h::inventory));
    JSONArray evolve = h.list(u, "evolve");
    who.addView(k.note(u.optInt("stage") >= 3 ? "최고 승진 단계" : "다음 승진: 20·35·50레벨과 해당 부 보스 완료"));
    if (evolve.length() > 0)
      who.addView(
          k.action(
              null,
              "승진하기",
              () -> {
                LinearLayout choices = k.column();
                android.app.AlertDialog[] picker = new android.app.AlertDialog[1];
                for (int i = 0; i < evolve.length(); i++) {
                  JSONObject a = evolve.optJSONObject(i);
                  choices.addView(
                      k.action(
                          null,
                          a.optString("name") + "\n" + a.optString("desc"),
                          () ->
                              h.confirm(
                                  "이 직무 표창으로 승진할까요?",
                                  () ->
                                      h.request(
                                          "native/growth",
                                          MainActivity.obj(
                                              "key",
                                              key,
                                              "kind",
                                              "evolve",
                                              "augment",
                                              a.optString("id")),
                                          (r, e) -> {
                                            if (picker[0] != null) picker[0].dismiss();
                                            h.load("unit");
                                          }))));
                }
                picker[0] =
                    new GameDialog(h)
                        .setTitle("직무 표창 선택")
                        .setView(k.scroll(choices))
                        .setNegativeButton("나중에", null)
                        .show();
              }));
    cols[1].addView(k.section("기술 · " + h.list(u, "skills").length() + " / 4칸"));
    LinearLayout actions = k.row();
    for (String kind : new String[] {"promo", "learn"}) {
      JSONArray offers = h.list(u, kind);
      if (offers.length() > 0)
        h.cell(
            actions,
            k.action(
                kind.equals("promo") ? "u-gift" : "learn",
                kind.equals("promo") ? "전용 기술" : "새 기술 배우기",
                () -> skillChoice(key, kind)));
    }
    cols[1].addView(actions);
    ArrayList<View> cards = new ArrayList<>();
    JSONArray skills = h.list(u, "skills");
    for (int i = 0; i < skills.length(); i++)
      cards.add(skill(skills.optJSONObject(i), "보유 기술", null));
    while (cards.size() < 4) {
      LinearLayout blank = k.paper();
      blank.addView(h.text("＋ 빈칸", 22));
      blank.addView(k.note("레벨업·승진으로 기술을 배워 채워요."));
      cards.add(blank);
    }
    k.tiles(cols[1], cards, 2);
  }

  void skillChoice(String key, String kind) {
    JSONArray skills = h.list(h.unit(key), kind);
    LinearLayout choices = k.column();
    android.app.AlertDialog[] popup = new android.app.AlertDialog[1];
    for (int i = 0; i < skills.length(); i++) {
      JSONObject sk = skills.optJSONObject(i);
      choices.addView(
          skill(
              sk,
              kind.equals("promo") ? "승진 전용 · 배울 기회는 안 써요" : "새로 배울 수 있는 기술",
              () -> {
                popup[0].dismiss();
                h.learn(key, kind, sk.optString("id"));
              }));
    }
    if (kind.equals("learn"))
      choices.addView(
          k.action(
              null,
              "이번 기술 학습 마치기",
              () -> {
                popup[0].dismiss();
                h.request(
                    "native/growth",
                    MainActivity.obj("key", key, "kind", "learn", "skill", JSONObject.NULL),
                    (r, e) -> h.load("unit"));
              }));
    popup[0] =
        new GameDialog(h)
            .setTitle(kind.equals("promo") ? "전용 기술 배우기" : "새 기술 배우기")
            .setView(k.scroll(choices))
            .setNegativeButton("나중에", null)
            .show();
  }

  void items() {
    h.base("수비대", "items");
    m.defenderTabs(h.body, 2);
    LinearLayout guidance = k.paper();
    guidance.addView(k.note("치료약은 전투 밖에서도 사용할 수 있어요. 다른 아이템은 스토리 대결 중에 사용해요."));
    h.body.addView(guidance);
    JSONObject defs = h.state.optJSONObject("itemDefs");
    JSONArray owned = h.list(h.state, "items");
    if (defs == null) return;
    ArrayList<View> cards = new ArrayList<>();
    Iterator<String> ids = defs.keys();
    while (ids.hasNext()) {
      String id = ids.next();
      JSONObject def = defs.optJSONObject(id);
      int count = 0;
      for (int i = 0; i < owned.length(); i++)
        if (id.equals(owned.optJSONObject(i).optString("id")))
          count = owned.optJSONObject(i).optInt("count");
      LinearLayout card = k.paper();
      card.addView(k.image("/duel/icons/" + id + ".png", 48, 48));
      card.addView(h.text(def.optString("name") + " · " + count + "개", 19));
      card.addView(k.note(def.optString("desc")));
      if (def.optInt("heal") > 0) {
        Button use = k.action("potion", "쓰기 · 갈매미 고르기", () -> heal(id));
        use.setEnabled(count > 0);
        card.addView(use);
      } else card.addView(k.note("스토리 대결 중에 사용할 수 있어요."));
      cards.add(card);
    }
    k.tiles(h.body, cards, h.cols());
    h.body.addView(
        k.action("u-shop", "아이템 구매", () -> h.load("shop")),
        new LinearLayout.LayoutParams(-1, h.dp(52)));
  }

  void heal(String id) {
    JSONArray units = h.list(h.state, "units");
    String[] names = new String[units.length()];
    for (int i = 0; i < units.length(); i++) {
      JSONObject u = units.optJSONObject(i);
      names[i] = u.optString("name") + " · " + u.optInt("hp") + " / " + u.optInt("maxHp");
    }
    if (names.length == 0) {
      h.message("회복할 갈매미가 없어요.");
      return;
    }
    new GameDialog(h)
        .setTitle("회복할 갈매미 선택")
        .setItems(
            names,
            (d, i) ->
                h.request(
                    "native/heal",
                    MainActivity.obj("key", units.optJSONObject(i).optString("key"), "item", id),
                    (r, e) -> h.load("items")))
        .setNegativeButton("취소", null)
        .show();
  }
}

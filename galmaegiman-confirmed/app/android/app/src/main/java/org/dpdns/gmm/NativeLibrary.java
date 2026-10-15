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
  JSONObject catalogData, catalogState;

  NativeLibrary(NativeMenus menus) {
    m = menus;
    h = m.h;
    k = m.k;
  }

  TextView heading(String text, float size) {
    TextView title = h.text(text, size);
    title.setTypeface(h.headline);
    return title;
  }

  void reset() {
    catalogData = null;
    catalogState = null;
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
            card.setTag("collection-card-" + u.optString("id"));
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout portrait = new FrameLayout(h);
            ImageView art =
                k.image(u.optString("image", "/thumbs/" + u.optString("id") + ".jpg"), 142, 142);
            portrait.addView(art, new FrameLayout.LayoutParams(-1, -1));
            TextView have =
                h.text(
                    u.optInt("have") > 0
                        ? "×" + u.optInt("have")
                        : u.optBoolean("known") ? "발견" : "미발견",
                    13);
            have.setBackground(new GamePaperDrawable(h));
            have.setPadding(h.dp(4), 0, h.dp(4), 0);
            portrait.addView(
                have, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT));
            card.addView(
                portrait, new LinearLayout.LayoutParams(-1, h.dp(h.compact() ? 128 : 142)));
            TextView name = h.text(u.optString("name"), 14);
            name.setTypeface(h.headline);
            name.setGravity(Gravity.CENTER);
            name.setTextLocale(Locale.KOREA);
            name.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
            if (android.os.Build.VERSION.SDK_INT >= 33)
              name.setLineBreakWordStyle(
                  android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
            card.addView(name);
            String attr = u.optString("attrName");
            TextView grade = k.note(u.optString("tierName") + (attr.isEmpty() ? "" : " · " + attr));
            grade.setTextSize(13);
            grade.setGravity(Gravity.CENTER);
            card.addView(grade);
            if (u.optInt("away") > 0) {
              TextView away = k.note("탐험 중 " + u.optInt("away"));
              away.setTextSize(13);
              card.addView(away);
            }
            card.setOnClickListener(v -> detail(visible, index));
            card.setOnLongClickListener(
                v -> {
                  hold(visible, index);
                  return true;
                });
            cards.add(card);
          }
          int usable = h.getResources().getConfiguration().screenWidthDp - 32;
          k.tiles(list, cards, Math.max(3, usable / 170));
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
    cols[0].addView(
        k.image(u.optString("image", "/thumbs/" + u.optString("id") + ".jpg"), 230, 230));
    cols[0].addView(
        k.note(u.optBoolean("known", u.optInt("have") > 0) ? "도감에 기록된 갈매미" : "아직 만나지 못했어요"));
    LinearLayout info = k.paper();
    cols[1].addView(info);
    info.addView(h.text(u.optString("tierName") + " · " + u.optString("attrName"), 17));
    TextView unitName = h.text(u.optString("name"), 24);
    unitName.setTypeface(h.headline);
    info.addView(unitName);
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
    ArrayList<JSONObject> individuals = new ArrayList<>();
    JSONArray roster = h.list(h.state, "units");
    for (int i = 0; i < roster.length(); i++)
      if (u.optString("id").equals(roster.optJSONObject(i).optString("id")))
        individuals.add(roster.optJSONObject(i));
    if (!individuals.isEmpty())
      info.addView(
          k.action(
              "learn",
              "기술·승진 확인",
              () -> {
                m.unitReturn = "collection";
                if (individuals.size() == 1) {
                  h.unitDetail(individuals.get(0).optString("key"));
                  return;
                }
                String[] names = new String[individuals.size()];
                for (int i = 0; i < names.length; i++)
                  names[i] =
                      "#"
                          + (i + 1)
                          + " · Lv"
                          + individuals.get(i).optInt("level")
                          + " · "
                          + NativeGrowthGuidance.badge(individuals.get(i));
                new GameDialog(h)
                    .setTitle("키울 갈매미 선택")
                    .setItems(
                        names,
                        (d, atUnit) -> h.unitDetail(individuals.get(atUnit).optString("key")))
                    .setNegativeButton("닫기", null)
                    .show();
              }));
    JSONObject catalogEntry = catalogState == h.state ? catalogEntry(u.optString("id")) : null;
    if (catalogEntry != null) acquisition(info, catalogEntry);
    else
      m.fetch(
          "native/catalog",
          data -> {
            catalogData = data;
            catalogState = h.state;
            JSONObject entry = catalogEntry(u.optString("id"));
            if (entry != null) acquisition(info, entry);
          });
    LinearLayout footer = k.row();
    h.cell(
        footer,
        k.action(null, "‹ 이전", () -> detail(units, (at - 1 + units.size()) % units.size())));
    h.cell(footer, k.action(null, "목록으로", this::collection));
    h.cell(footer, k.action(null, "다음 ›", () -> detail(units, (at + 1) % units.size())));
    k.footer(footer);
  }

  JSONObject catalogEntry(String id) {
    JSONArray entries = h.list(catalogData == null ? new JSONObject() : catalogData, "units");
    for (int i = 0; i < entries.length(); i++)
      if (id.equals(entries.optJSONObject(i).optString("id"))) return entries.optJSONObject(i);
    return null;
  }

  void acquisition(LinearLayout parent, JSONObject u) {
    if (!u.optString("source").isEmpty()) {
      parent.addView(k.section("얻는 곳"));
      parent.addView(k.note(u.optString("source")));
    }
    JSONArray materials = h.list(u, "materials");
    if (materials.length() > 0) {
      parent.addView(k.section("만드는 법"));
      for (int i = 0; i < materials.length(); i++) {
        JSONObject material = materials.optJSONObject(i);
        LinearLayout row = k.row();
        row.addView(
            k.image(
                "/thumbs/" + material.optString("img", material.optString("id")) + ".jpg", 36, 36));
        int usable = material.optInt("usable"), need = material.optInt("need");
        TextView text =
            k.note(
                material.optString("name")
                    + " · "
                    + usable
                    + "/"
                    + need
                    + (usable < need && !material.optString("source").isEmpty()
                        ? "\n" + material.optString("source")
                        : ""));
        text.setTextColor(usable >= need ? 0xff276646 : 0xff9b3c31);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (usable < need) {
          Button obtain =
              k.action(
                  null,
                  "얻기",
                  () -> {
                    String source = material.optString("source");
                    if (source.contains("교환") || source.contains("상점")) h.load("shop");
                    else if (source.contains("뽑기")) h.load("draw");
                    else {
                      m.recipeId = material.optString("id");
                      m.recipeQuery = material.optString("name");
                      m.craftOnly = false;
                      h.craft();
                    }
                  });
          obtain.setTextSize(13);
          row.addView(obtain, new LinearLayout.LayoutParams(h.dp(70), h.dp(48)));
        }
        parent.addView(row);
      }
      parent.addView(
          k.action(
              "u-emblem",
              "조합 화면으로",
              () -> {
                m.recipeId = u.optString("id");
                m.recipeQuery = u.optString("name");
                m.craftOnly = false;
                h.craft();
              }));
    }
    JSONArray used = h.list(u, "usedIn");
    if (used.length() > 0) {
      parent.addView(k.section("재료로 쓰이는 곳"));
      for (int i = 0; i < used.length(); i++) {
        JSONObject recipe = used.optJSONObject(i);
        parent.addView(
            k.action(
                "u-emblem",
                recipe.optString("name"),
                () -> {
                  m.recipeId = recipe.optString("id");
                  m.recipeQuery = recipe.optString("name");
                  m.craftOnly = false;
                  h.craft();
                }));
      }
    }
    if (!u.optString("hint").isEmpty()) parent.addView(k.notice(u.optString("hint")));
    JSONArray base = h.list(u, "baseNeeds");
    if (base.length() > 0) {
      parent.addView(k.section("처음부터 필요한 기본 재료"));
      for (int i = 0; i < base.length(); i++) {
        JSONObject material = base.optJSONObject(i);
        parent.addView(k.note(material.optString("name") + " ×" + material.optInt("need")));
      }
    }
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
            u.optBoolean("top") && u.optInt("have") > u.optInt("away") && h.me().isNull("trip"));
      if (i == 3) b.setEnabled(u.optInt("have") > u.optInt("away"));
      if (i == 1
          && !u.optBoolean("known", u.optInt("have") > 0)
          && u.optString("tier").equals("HIDDEN")) b.setEnabled(false);
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
    boolean compact = h.compact();
    LinearLayout card = k.paper();
    card.setPadding(h.dp(8), h.dp(6), h.dp(8), h.dp(6));
    LinearLayout heading = k.row();
    TextView kind = k.note(tag);
    kind.setTextSize(compact ? 10 : 12);
    heading.addView(kind, new LinearLayout.LayoutParams(0, -2, 1));
    String attr = sk.optString("attr");
    if (!attr.isEmpty()) {
      TextView chip =
          k.chip(
              m.attrName(
                  h.state.optJSONObject("attributes") == null
                      ? new JSONObject()
                      : h.state.optJSONObject("attributes"),
                  attr),
              attr);
      chip.setTextSize(compact ? 10 : 12);
      heading.addView(chip);
    }
    card.addView(heading);
    TextView title = h.text(sk.optString("name"), compact ? 16 : 20);
    title.setTypeface(h.headline);
    title.setMaxLines(2);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    card.addView(title);
    String category =
        sk.optString("cat").equals("phys")
            ? "물리"
            : sk.optString("cat").equals("mag") ? "마법" : "능력치에 따라";
    String reuse =
        sk.optString("id").startsWith("combo-")
            ? "전투당 1회"
            : sk.optInt("power") > 0 ? "연속 사용 가능" : "이미 적용 중이면 사용 제한";
    String meta =
        (sk.optInt("power") > 0
                ? category
                    + " · 위력 "
                    + sk.optInt("power")
                    + (sk.optInt("hits") > 1 ? " ×" + sk.optInt("hits") : "")
                : "보조 기술")
            + " · "
            + reuse;
    TextView numbers = k.note(meta);
    numbers.setTextSize(compact ? 12 : 14);
    card.addView(numbers);
    String effect = SkillCardView.effect(sk);
    if (!effect.equals("추가 효과 없음")) {
      TextView description = k.note(effect);
      description.setTextSize(compact ? 12 : 14);
      description.setMaxLines(compact ? 2 : 3);
      description.setEllipsize(android.text.TextUtils.TruncateAt.END);
      card.addView(description);
    }
    card.setMinimumHeight(h.dp(compact ? 92 : 120));
    if (click != null) {
      card.setMinimumHeight(h.dp(compact ? 100 : 128));
      card.setContentDescription(sk.optString("name") + " · " + meta + " · " + effect + ". 선택");
      card.setOnClickListener(v -> click.run());
    } else
      card.setOnClickListener(
          v ->
              new GameDialog(h)
                  .setTitle(sk.optString("name"))
                  .setMessage(meta + "\n\n" + effect)
                  .setPositiveButton("확인", null)
                  .show());
    return card;
  }

  String skillKind(JSONObject sk) {
    String id = sk.optString("id");
    return id.startsWith("basic-")
        ? "기본기"
        : id.equals("sig")
            ? "전용 기술"
            : id.startsWith("role-") ? "역할기" : id.startsWith("sub-") ? "보조 속성" : "일반 기술";
  }

  void unit(String key) {
    h.currentKey = key;
    JSONObject u = h.unit(key);
    h.base(u.optString("name") + " · 기술과 승진", "unit");
    LinearLayout[] cols = k.split(.32f);
    boolean compact = h.compact();
    LinearLayout who = k.paper();
    who.setTag("unit-summary");
    cols[0].addView(who);
    LinearLayout identity = compact ? k.row() : k.column();
    identity.setGravity(Gravity.TOP);
    identity.addView(k.image(u.optString("image"), compact ? 104 : 190, compact ? 114 : 204));
    LinearLayout labels = k.column();
    identity.addView(labels, new LinearLayout.LayoutParams(compact ? 0 : -1, -2, compact ? 1 : 0));
    who.addView(identity);
    TextView name = h.text(u.optString("name"), compact ? 18 : 26);
    name.setTypeface(h.headline);
    name.setTextLocale(Locale.KOREA);
    name.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      name.setLineBreakWordStyle(
          android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
    labels.addView(name);
    TextView meta =
        k.note(
            "Lv"
                + u.optInt("level")
                + " · "
                + u.optString("attr")
                + " · 승진 "
                + u.optInt("stage")
                + "단계");
    meta.setTextSize(compact ? 13 : 15);
    labels.addView(meta);
    String role = u.optString("role");
    String roleName =
        role.equals("ATTACK")
            ? "공격"
            : role.equals("DEFENSE") ? "수비" : role.equals("UTILITY") ? "유틸" : role;
    JSONObject synergyDefs = h.state.optJSONObject("synergies");
    JSONObject syn = synergyDefs == null ? null : synergyDefs.optJSONObject(u.optString("synergy"));
    TextView roleLabel = k.note(roleName + (syn == null ? "" : " · " + syn.optString("name")));
    roleLabel.setTextSize(compact ? 13 : 15);
    labels.addView(roleLabel);
    unitMeter(who, "체력", u.optInt("hp"), u.optInt("maxHp"), 0xff51c98b);
    unitMeter(who, "경험치", u.optInt("xp"), u.optInt("xpNext"), 0xffe9a630);
    unitDetails(cols[0], u);

    // 행동을 능력치·표창보다 먼저 배치해 스크롤 없이 다음 성장 단계를 찾게 한다.
    cols[1].addView(growthActions(key, u));
    cols[1].addView(k.section("기술 · " + h.list(u, "skills").length() + " / 4칸"));
    if (h.list(u, "learn").length() == 0 && h.list(u, "promo").length() == 0) {
      int next = u.optInt("nextLearnLevel");
      cols[1].addView(
          k.note(next > 0 ? "다음 배울 기회 · Lv" + next : "승진하면 전용 기술이 열려요. 배운 기술은 교체할 수 있어요."));
    }
    ArrayList<View> cards = new ArrayList<>();
    JSONArray skills = h.list(u, "skills");
    for (int i = 0; i < skills.length(); i++)
      cards.add(skill(skills.optJSONObject(i), skillKind(skills.optJSONObject(i)), null));
    while (cards.size() < 4) {
      LinearLayout blank = k.paper();
      blank.addView(heading("＋ 빈칸", 22));
      blank.addView(k.note("레벨업·승진으로 기술을 배워 채워요."));
      cards.add(blank);
    }
    k.tiles(cols[1], cards, 2);
  }

  void unitMeter(LinearLayout parent, String label, int value, int max, int color) {
    TextView numbers = k.note(label + " " + value + " / " + max);
    numbers.setTag(label.equals("체력") ? "unit-hp" : "unit-xp");
    numbers.setPadding(h.dp(8), h.dp(3), h.dp(8), h.dp(3));
    numbers.setTextSize(h.compact() ? 13 : 15);
    parent.addView(numbers);
    m.growth.meter(parent, value, max, color, h.compact() ? 6 : 9);
  }

  LinearLayout growthActions(String key, JSONObject unit) {
    LinearLayout panel = k.paper();
    panel.setTag("unit-growth-actions");
    TextView title = h.text("성장·회복", h.compact() ? 18 : 22);
    title.setTypeface(h.headline);
    panel.addView(title);
    TextView summary = k.note(NativeGrowthGuidance.actionSummary(unit));
    summary.setTag("unit-growth-status");
    summary.setTextSize(h.compact() ? 13 : 15);
    summary.setTextColor(MainActivity.INK);
    panel.addView(summary);
    LinearLayout actions = k.row();
    boolean primary = true;
    if (h.list(unit, "evolve").length() > 0) {
      Button promote = k.action(null, "승진하기", () -> promotion(key));
      promote.setTag("unit-promote");
      promote.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
      h.cell(actions, promote);
      primary = false;
    }
    for (String kind : new String[] {"promo", "learn"}) {
      if (h.list(unit, kind).length() == 0) continue;
      String label =
          kind.equals("promo") ? "전용 기술" : "새 기술 " + Math.max(1, unit.optInt("learnLeft", 1)) + "개";
      Button learn = k.action(null, label, () -> skillChoice(key, kind));
      learn.setTag("unit-" + kind);
      if (primary) learn.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
      primary = false;
      h.cell(actions, learn);
    }
    boolean needsHealing = unit.optInt("hp") < unit.optInt("maxHp");
    Button heal =
        k.action(
            null,
            needsHealing ? "회복하기" : "체력 가득",
            () -> healSheet(null, key, () -> h.load("unit")));
    heal.setTag("unit-heal");
    heal.setEnabled(needsHealing);
    if (primary && needsHealing)
      heal.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
    h.cell(actions, heal);
    for (int i = 0; i < actions.getChildCount(); i++) {
      Button action = (Button) actions.getChildAt(i);
      action.setTextSize(h.compact() ? 14 : 16);
      action.setPadding(h.dp(6), h.dp(5), h.dp(6), h.dp(7));
      action.getLayoutParams().height = h.dp(56);
    }
    panel.addView(actions);
    TextView condition = k.note(m.growth.promotionNeed(unit));
    condition.setTextSize(h.compact() ? 12 : 14);
    panel.addView(condition);
    return panel;
  }

  void unitDetails(LinearLayout parent, JSONObject unit) {
    LinearLayout details = k.paper();
    details.setTag("unit-extra-details");
    details.setVisibility(View.GONE);
    JSONObject stats = unit.optJSONObject("stats");
    if (stats != null) {
      details.addView(k.section("능력치"));
      String[] ids = {"patk", "pdef", "matk", "mdef", "spd"},
          names = {"물리 공격", "물리 방어", "마법 공격", "마법 방어", "속도"};
      for (int i = 0; i < ids.length; i++) {
        LinearLayout row = k.row();
        TextView label = k.note(names[i]);
        label.setTextSize(h.compact() ? 12 : 14);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = k.note(String.valueOf(stats.optInt(ids[i])));
        value.setTypeface(h.prose, android.graphics.Typeface.BOLD);
        value.setSingleLine(true);
        row.addView(value, new LinearLayout.LayoutParams(-2, -2));
        details.addView(row);
      }
    }
    details.addView(k.section("직무 표창"));
    JSONArray awards = h.list(unit, "evoAugs");
    JSONObject definitions = h.state.optJSONObject("evoDefinitions");
    for (int i = 0; i < awards.length(); i++) {
      String id = awards.optString(i);
      JSONObject award = definitions == null ? null : definitions.optJSONObject(id);
      details.addView(
          k.note(award == null ? id : award.optString("name") + "\n" + award.optString("desc")));
    }
    if (awards.length() == 0) details.addView(k.note("승진할 때 직무 표창을 골라요."));
    Button toggle = k.action(null, "능력치 · 직무 표창 보기 ▾", () -> {});
    toggle.setTag("unit-details-toggle");
    toggle.setTextSize(h.compact() ? 13 : 15);
    toggle.setOnClickListener(
        v -> {
          h.sound.click();
          boolean show = details.getVisibility() != View.VISIBLE;
          details.setVisibility(show ? View.VISIBLE : View.GONE);
          toggle.setText(show ? "능력치 · 직무 표창 접기 ▴" : "능력치 · 직무 표창 보기 ▾");
        });
    parent.addView(toggle);
    parent.addView(details);
  }

  void skillChoice(String key, String kind) {
    JSONObject unit = h.unit(key);
    JSONArray offers = h.list(unit, kind);
    if (offers.length() == 0) return;
    LinearLayout content = k.column();
    String guidance =
        kind.equals("promo")
            ? "승진 전용 기술 · 학습 기회를 사용하지 않아요."
            : "Lv"
                + unit.optInt("learnTier", unit.optInt("level"))
                + " 새 기술 · 이번 기회에 "
                + unit.optInt("learnLeft", 1)
                + "개 더 배울 수 있어요.";
    content.addView(k.notice(guidance));
    LinearLayout choiceList = k.column();
    content.addView(
        k.scroll(choiceList), new LinearLayout.LayoutParams(-1, h.dp(h.compact() ? 180 : 300)));
    JSONObject[] picked = {null};
    Button[] go = {null};
    android.app.AlertDialog[] popup = {null};
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          choiceList.removeAllViews();
          ArrayList<View> cards = new ArrayList<>();
          for (int i = 0; i < offers.length(); i++) {
            JSONObject sk = offers.optJSONObject(i);
            LinearLayout card =
                skill(
                    sk,
                    kind.equals("promo") ? "승진 전용" : "새 기술",
                    () -> {
                      picked[0] = sk;
                      paint[0].run();
                    });
            if (picked[0] == sk) card.setBackground(new GamePanelDrawable(h));
            cards.add(card);
          }
          k.tiles(choiceList, cards, 2);
          if (go[0] != null) {
            go[0].setEnabled(picked[0] != null);
            go[0].setText(
                picked[0] == null
                    ? "기술 선택"
                    : h.list(unit, "skills").length() >= 4 ? "다음 · 잊을 기술 선택" : "배우기");
          }
        };
    paint[0].run();
    LinearLayout footer = k.row();
    if (kind.equals("learn"))
      h.cell(
          footer,
          k.action(
              null,
              "이번 기회 마치기",
              () ->
                  h.confirm(
                      "이번 학습 기회의 남은 선택은 다시 받을 수 없어요. 마칠까요?",
                      () -> {
                        popup[0].dismiss();
                        commitLearning(key, kind, null, null);
                      })));
    else h.cell(footer, k.action(null, "나중에", () -> popup[0].dismiss()));
    go[0] =
        k.action(
            "learn",
            "기술 선택",
            () -> {
              if (picked[0] == null) return;
              popup[0].dismiss();
              if (h.list(unit, "skills").length() >= 4) forgetChoice(key, kind, picked[0]);
              else commitLearning(key, kind, picked[0].optString("id"), null);
            });
    go[0].setEnabled(false);
    h.cell(footer, go[0]);
    content.addView(footer);
    popup[0] =
        new GameDialog(h)
            .setTitle(kind.equals("promo") ? "전용 기술 배우기" : "새 기술 배우기")
            .setView(content)
            .setNegativeButton("닫기", null)
            .show();
  }

  void forgetChoice(String key, String kind, JSONObject next) {
    LinearLayout content = k.column();
    content.addView(
        k.notice("「" + next.optString("name") + "」 대신 잊을 기술을 골라 주세요. 기본기·전용 기술도 바꿀 수 있어요."));
    ArrayList<View> choices = new ArrayList<>();
    android.app.AlertDialog[] popup = {null};
    JSONArray mine = h.list(h.unit(key), "skills");
    for (int i = 0; i < mine.length(); i++) {
      JSONObject old = mine.optJSONObject(i);
      choices.add(
          skill(
              old,
              "잊을 기술",
              () -> {
                popup[0].dismiss();
                compareReplacement(key, kind, old, next);
              }));
    }
    k.tiles(content, choices, 2);
    popup[0] =
        new GameDialog(h)
            .setTitle("잊을 기술 선택")
            .setView(k.scroll(content))
            .setNegativeButton("다시 고르기", (d, which) -> skillChoice(key, kind))
            .show();
  }

  LinearLayout comparisonScroll(View content) {
    LinearLayout container = k.column();
    ScrollView scroll = k.scroll(content);
    scroll.setTag("growth-comparison-scroll");
    int available = Math.max(144, h.contentHeightDp() - 180);
    container.addView(
        scroll,
        new LinearLayout.LayoutParams(-1, h.dp(Math.min(h.compact() ? 210 : 420, available))));
    return container;
  }

  void compareReplacement(String key, String kind, JSONObject old, JSONObject next) {
    LinearLayout content = new NativeGrowthComparison(h, k).skills(old, next);
    new GameDialog(h)
        .setTitle("기술 교체 비교")
        .setView(comparisonScroll(content))
        .setNegativeButton("다시 선택", (d, which) -> forgetChoice(key, kind, next))
        .setPositiveButton(
            "교체 확정",
            (d, which) -> commitLearning(key, kind, next.optString("id"), old.optString("id")))
        .show();
  }

  void commitLearning(String key, String kind, String skill, String replace) {
    JSONObject body =
        MainActivity.obj(
            "key", key, "kind", kind, "skill", skill == null ? JSONObject.NULL : skill);
    if (replace != null)
      try {
        body.put("replace", replace);
      } catch (JSONException ignored) {
      }
    h.request(
        "native/growth",
        body,
        (r, e) -> {
          h.sound.effect("aug");
          m.fetch(
              "native/state",
              updated -> {
                h.state = updated;
                h.stateUpdated();
                unit(key);
                if (kind.equals("learn")
                    && skill != null
                    && h.list(h.unit(key), "learn").length() > 0) skillChoice(key, kind);
              });
        });
  }

  void promotion(String key) {
    JSONObject unit = h.unit(key);
    JSONArray evolve = h.list(unit, "evolve");
    if (evolve.length() == 0) return;
    LinearLayout options = k.column();
    options.addView(k.notice("직무 표창을 하나 골라요. 능력치와 전용 기술을 비교한 뒤 확정할 수 있어요."));
    options.addView(k.note("재화 소모 없음 · 승진하면 체력이 가득 회복돼요."));
    android.app.AlertDialog[] popup = {null};
    for (int i = 0; i < evolve.length(); i++) {
      JSONObject choice = evolve.optJSONObject(i);
      LinearLayout card = k.paper();
      card.addView(heading(choice.optString("name"), 19));
      card.addView(k.note(choice.optString("desc")));
      Button preview =
          k.action(
              "learn",
              "승진 미리 보기",
              () -> {
                popup[0].dismiss();
                comparePromotion(key, choice);
              });
      preview.setTag("promotion-preview-" + choice.optString("id"));
      card.addView(preview);
      options.addView(card);
    }
    popup[0] =
        new GameDialog(h)
            .setTitle((unit.optInt("stage") + 1) + "단계 승진 · 직무 표창")
            .setView(comparisonScroll(options))
            .setNegativeButton("나중에", null)
            .show();
  }

  void comparePromotion(String key, JSONObject choice) {
    if (!NativeGrowthComparison.hasPreview(choice)) {
      // 전후 능력치를 앱에서 추정하지 않는다. 서버가 준 현재 선택지를 다시 확인한다.
      m.fetch(
          "native/state",
          updated -> {
            h.state = updated;
            h.stateUpdated();
            JSONArray choices = h.list(h.unit(key), "evolve");
            for (int i = 0; i < choices.length(); i++) {
              JSONObject fresh = choices.optJSONObject(i);
              if (fresh.optString("id").equals(choice.optString("id"))
                  && NativeGrowthComparison.hasPreview(fresh)) {
                comparePromotion(key, fresh);
                return;
              }
            }
            h.message("승진 정보를 새로 확인했어요. 다시 표창을 골라 주세요.");
            unit(key);
          });
      return;
    }
    LinearLayout content = new NativeGrowthComparison(h, k).promotion(h.unit(key), choice);
    new GameDialog(h)
        .setTitle("승진 전후 비교")
        .setView(comparisonScroll(content))
        .setNegativeButton("다시 선택", (d, which) -> promotion(key))
        .setPositiveButton(
            "승진 확정",
            (d, which) ->
                h.confirm(
                    "「"
                        + choice.optString("name")
                        + "」을 받고 승진할까요?\n직무 표창은 바꿀 수 없어요.\n재화 소모 없음 · 체력 가득 회복",
                    () ->
                        h.request(
                            "native/growth",
                            MainActivity.obj(
                                "key", key, "kind", "evolve", "augment", choice.optString("id")),
                            (r, e) -> {
                              h.sound.effect("levelup");
                              m.fetch(
                                  "native/state",
                                  updated -> {
                                    h.state = updated;
                                    h.stateUpdated();
                                    unit(key);
                                    promotionSuccess(key, choice.optString("name"));
                                  });
                            })))
        .show();
  }

  void promotionSuccess(String key, String award) {
    JSONObject u = h.unit(key);
    LinearLayout content = k.column();
    content.setGravity(Gravity.CENTER_HORIZONTAL);
    ImageView portrait = k.image(u.optString("image"), 156, 156);
    content.addView(portrait);
    content.addView(heading("승진! " + "★".repeat(u.optInt("stage")), 26));
    content.addView(k.note(u.optString("name") + " · " + u.optInt("stage") + "단계 · 체력 회복"));
    content.addView(k.note("직무 표창 「" + award + "」"));
    if (h.motionEnabled) {
      portrait.setScaleX(.8f);
      portrait.setScaleY(.8f);
      portrait.setAlpha(0);
      portrait
          .animate()
          .scaleX(1)
          .scaleY(1)
          .alpha(1)
          .setDuration(700)
          .setInterpolator(new android.view.animation.OvershootInterpolator())
          .start();
    }
    boolean available = h.list(u, "promo").length() > 0;
    new GameDialog(h)
        .setView(content)
        .setPositiveButton(
            available ? "전용 기술 배우기" : "확인",
            (d, which) -> {
              if (available) skillChoice(key, "promo");
            })
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
      card.addView(heading(def.optString("name") + " · " + count + "개", 19));
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
    healSheet(id, () -> h.load("items"));
  }

  void healSheet(String initial, Runnable returnTo) {
    healSheet(initial, null, returnTo);
  }

  void healSheet(String initial, String preferredKey, Runnable returnTo) {
    JSONArray team = new JSONArray();
    if (preferredKey != null && !h.unit(preferredKey).optString("key").isEmpty())
      team.put(preferredKey);
    JSONArray saved = h.list(h.state, "team");
    for (int i = 0; i < saved.length(); i++)
      if (!saved.optString(i).equals(preferredKey)) team.put(saved.optString(i));
    if (team.length() == 0) {
      h.confirm(
          "먼저 스토리 수비대를 정할까요?",
          () -> {
            h.selectionMode = "story";
            m.teamDraft = null;
            h.team();
          });
      return;
    }
    LinearLayout body = k.column();
    android.app.AlertDialog[] popup = {null};
    String[] item = {initial}, target = {preferredKey};
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          body.removeAllViews();
          JSONObject defs = h.state.optJSONObject("itemDefs");
          body.addView(
              k.notice(
                  "시간이 지나도 체력이 회복돼요. 0 → 가득 "
                      + h.state.optInt("storyHpFullMinutes", 60)
                      + "분 · 레벨업·승진 시 가득 회복"));
          ArrayList<String> healers = new ArrayList<>();
          if (defs != null) {
            Iterator<String> keys = defs.keys();
            while (keys.hasNext()) {
              String id = keys.next();
              if (defs.optJSONObject(id).optInt("heal") > 0) healers.add(id);
            }
          }
          if (item[0] == null || itemCount(item[0]) == 0) {
            item[0] = null;
            for (String id : healers)
              if (itemCount(id) > 0) {
                item[0] = id;
                break;
              }
          }
          if (target[0] == null
              || h.unit(target[0]).optInt("hp") >= h.unit(target[0]).optInt("maxHp")) {
            target[0] = null;
            for (int i = 0; i < team.length(); i++) {
              JSONObject u = h.unit(team.optString(i));
              if (u.optInt("hp") < u.optInt("maxHp")) {
                target[0] = team.optString(i);
                break;
              }
            }
          }
          LinearLayout[] cols = {k.column(), k.column()};
          LinearLayout row = k.row();
          row.setGravity(Gravity.TOP);
          h.cell(row, cols[0]);
          h.cell(row, cols[1]);
          body.addView(row);
          cols[0].addView(heading("치료약", 18));
          for (String id : healers) {
            JSONObject def = defs.optJSONObject(id);
            Button choose =
                k.action(
                    id,
                    def.optString("name")
                        + " · "
                        + itemCount(id)
                        + "개\n체력 "
                        + def.optInt("heal")
                        + "%",
                    () -> {
                      item[0] = id;
                      paint[0].run();
                    });
            choose.setEnabled(itemCount(id) > 0);
            if (id.equals(item[0]))
              choose.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
            cols[0].addView(choose);
          }
          if (item[0] == null)
            cols[0].addView(
                k.action(
                    "u-shop",
                    "치료약 구매",
                    () -> {
                      popup[0].dismiss();
                      h.load("shop");
                    }));
          cols[1].addView(heading("회복할 갈매미", 18));
          for (int i = 0; i < team.length(); i++) {
            String key = team.optString(i);
            JSONObject u = h.unit(key);
            boolean full = u.optInt("hp") >= u.optInt("maxHp");
            LinearLayout choice = k.paper(), heading = k.row();
            choice.setTag("heal-target-" + key);
            choice.setSelected(key.equals(target[0]));
            heading.addView(k.image(u.optString("image"), 38, 38));
            TextView name = heading(u.optString("name") + (full ? " · 가득" : ""), 14);
            heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            if (key.equals(target[0])) heading.addView(h.text("✓", 18));
            choice.addView(heading);
            TextView hp = k.note("체력 " + u.optInt("hp") + "/" + u.optInt("maxHp"));
            hp.setTextSize(12);
            choice.addView(hp);
            m.growth.meter(choice, u.optInt("hp"), u.optInt("maxHp"), 0xff51c98b, 5);
            choice.setEnabled(!full);
            choice.setAlpha(full ? .6f : 1);
            if (key.equals(target[0])) choice.setBackground(new GamePanelDrawable(h));
            choice.setOnClickListener(
                v -> {
                  if (!full) {
                    target[0] = key;
                    paint[0].run();
                  }
                });
            cols[1].addView(choice);
          }
          LinearLayout footer = k.row();
          h.cell(
              footer,
              k.action(
                  null,
                  "닫기",
                  () -> {
                    popup[0].dismiss();
                    returnTo.run();
                  }));
          Button use =
              k.action(
                  "potion",
                  "치료약 쓰기",
                  () -> {
                    if (item[0] == null || target[0] == null) return;
                    h.request(
                        "native/heal",
                        MainActivity.obj("key", target[0], "item", item[0]),
                        (r, e) -> {
                          h.sound.effect("heal");
                          m.fetch(
                              "native/state",
                              updated -> {
                                h.state = updated;
                                h.stateUpdated();
                                paint[0].run();
                              });
                        });
                  });
          use.setEnabled(item[0] != null && target[0] != null);
          h.cell(footer, use);
          body.addView(footer);
        };
    paint[0].run();
    popup[0] = new GameDialog(h).setTitle("치료약 쓰기").setView(k.scroll(body)).show();
  }

  int itemCount(String id) {
    JSONArray items = h.list(h.state, "items");
    for (int i = 0; i < items.length(); i++)
      if (id.equals(items.optJSONObject(i).optString("id")))
        return items.optJSONObject(i).optInt("count");
    return 0;
  }
}

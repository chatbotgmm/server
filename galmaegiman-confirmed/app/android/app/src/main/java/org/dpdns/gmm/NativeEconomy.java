package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 기존 뽑기·조합·매점·탐험 화면의 그림과 수량·선택 동작. */
final class NativeEconomy {
  final NativeMenus m;
  final MainActivity h;
  final MenuKit k;

  NativeEconomy(NativeMenus menus) {
    m = menus;
    h = m.h;
    k = m.k;
  }

  void draw() {
    h.base("뽑기", "draw");
    m.fetch(
        "draw",
        r -> {
          LinearLayout banner = k.paper();
          intro(
              banner,
              "u-ticket",
              "뽑기권 " + (r.optBoolean("unlimited") ? "무제한" : r.optInt("credits") + "장"),
              h.waitText(r.optLong("wait")));
          JSONObject event = r.optJSONObject("event");
          if (event != null) banner.addView(k.note(event.optString("label")));
          LinearLayout actions = k.row();
          Button claim =
              k.action("u-ticket", "뽑기권 받기", () -> h.mutate("claim", MainActivity.obj(), "draw"));
          claim.setEnabled(r.optLong("wait") <= 0);
          h.cell(actions, claim);
          if (m.lastDraw != null)
            h.cell(
                actions,
                k.action("u-draw", "지난 결과", () -> reveal(m.lastDraw, () -> h.load("draw"), true)));
          banner.addView(actions);
          h.body.addView(banner);
          ArrayList<View> cards = new ArrayList<>();
          JSONArray gachas = h.list(r, "gachas");
          for (int i = 0; i < gachas.length(); i++) {
            JSONObject g = gachas.optJSONObject(i);
            int cost = Math.max(1, g.optInt("cost"));
            int limit = r.optBoolean("unlimited") ? 50 : Math.min(50, r.optInt("credits") / cost);
            LinearLayout card = k.paper();
            card.addView(k.image("/thumbs/" + g.optString("img") + ".jpg", 96, 80));
            card.addView(h.text(g.optString("label") + " 뽑기", 22));
            StringBuilder odds = new StringBuilder();
            JSONArray weights = h.list(g, "weights");
            for (int j = 0; j < weights.length(); j++) {
              JSONObject w = weights.optJSONObject(j);
              if (j > 0) odds.append(" · ");
              odds.append(w.optString("tierName"))
                  .append(" ")
                  .append(String.format(Locale.KOREA, "%.1f%%", w.optDouble("w")));
            }
            int[] amount = {1};
            Button go =
                k.action(
                    "u-draw",
                    "1회 뽑기 · 뽑기권 " + cost,
                    () ->
                        h.confirm(
                            g.optString("label")
                                + " "
                                + amount[0]
                                + "회 · 뽑기권 "
                                + cost * amount[0]
                                + "장을 사용할까요?",
                            () ->
                                h.mutate(
                                    "draw",
                                    MainActivity.obj(
                                        "kind", g.optString("kind"), "times", amount[0]),
                                    "draw")));
            k.quantity(
                card,
                limit,
                n -> {
                  amount[0] = n;
                  go.setText(n + "회 뽑기 · 뽑기권 " + cost * n);
                });
            go.setEnabled(limit > 0);
            if (limit <= 0) go.setText("뽑기권 부족");
            card.addView(go);
            card.addView(k.note("확률 · " + odds));
            cards.add(card);
          }
          k.tiles(h.body, cards, h.getResources().getConfiguration().screenWidthDp >= 1100 ? 3 : 2);
          JSONObject pity = r.optJSONObject("pity");
          if (pity != null) {
            LinearLayout status = k.paper();
            status.addView(
                k.note("확정 보상까지 " + pity.optInt("left") + "회 · " + pity.optString("tierName")));
            h.body.addView(status);
          }
        });
  }

  void intro(LinearLayout parent, String icon, String title, String note) {
    m.intro(parent, icon, title, note);
  }

  void reveal(JSONObject result, Runnable next, boolean again) {
    m.lastDraw = result;
    h.base("뽑기 결과", "drawResult");
    h.root.addView(
        new NativeReveal(h, result, next, again),
        new android.widget.FrameLayout.LayoutParams(-1, -1));
  }

  void craft(boolean mix) {
    h.base(mix ? "조합 · 재료로 섞기" : "조합 · 조합식", mix ? "mix" : "craft");
    LinearLayout tabs = k.column();
    k.tabs(tabs, new String[] {"조합식", "재료로 섞기"}, mix ? 1 : 0, i -> craft(i == 1));
    h.page.addView(tabs, h.page.getChildCount() - 1);
    LinearLayout[] cols = k.split(.47f);
    m.fetch(
        "craft",
        r -> {
          if (mix) mix(cols, r);
          else recipes(cols, r);
        });
  }

  void recipes(LinearLayout[] cols, JSONObject data) {
    LinearLayout left = cols[0], right = cols[1], controls = k.column(), list = k.column();
    left.addView(controls);
    left.addView(list);
    JSONArray recipes = h.list(data, "recipes");
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          list.removeAllViews();
          ArrayList<JSONObject> visible = new ArrayList<>();
          for (int i = 0; i < recipes.length(); i++) {
            JSONObject r = recipes.optJSONObject(i);
            String names = r.optString("name");
            JSONArray mats = h.list(r, "materials");
            for (int j = 0; j < mats.length(); j++)
              names += " " + mats.optJSONObject(j).optString("name");
            if ((!m.craftOnly || r.optBoolean("ready"))
                && names
                    .toLowerCase(Locale.KOREA)
                    .contains(m.recipeQuery.toLowerCase(Locale.KOREA))) visible.add(r);
          }
          if (visible.isEmpty()) {
            list.addView(k.note("찾는 조합식이 없어요. 필터나 검색어를 바꿔 보세요."));
            right.removeAllViews();
            return;
          }
          JSONObject current = null;
          for (JSONObject r : visible) if (m.recipeId.equals(r.optString("id"))) current = r;
          if (current == null) {
            current = visible.get(0);
            m.recipeId = current.optString("id");
          }
          for (JSONObject r : visible) {
            LinearLayout item = k.row();
            item.setBackground(
                h.material(m.recipeId.equals(r.optString("id")) ? 0xfffbe8d3 : 0xfffffaf0));
            item.addView(k.image("/thumbs/" + r.optString("img") + ".jpg", 56, 56));
            item.addView(
                h.text(
                    r.optString("name")
                        + "\n"
                        + (r.optBoolean("ready") ? r.optInt("max") + "개 가능" : "재료 부족"),
                    15),
                new LinearLayout.LayoutParams(0, -2, 1));
            item.setOnClickListener(
                v -> {
                  m.recipeId = r.optString("id");
                  paint[0].run();
                });
            list.addView(item);
          }
          recipe(right, current);
        };
    k.search(
        controls,
        "이름·재료로 찾기",
        m.recipeQuery,
        value -> {
          m.recipeQuery = value;
          paint[0].run();
        });
    Button only =
        k.action(
            null,
            (m.craftOnly ? "✓ " : "") + "만들 수 있는 것만",
            () -> {
              m.craftOnly = !m.craftOnly;
              craft(false);
            });
    controls.addView(only);
    paint[0].run();
  }

  void recipe(LinearLayout parent, JSONObject r) {
    parent.removeAllViews();
    LinearLayout panel = k.paper();
    parent.addView(panel);
    panel.addView(h.text(r.optString("name"), 24));
    panel.addView(k.note(r.optString("tierName") + (r.optBoolean("isNew") ? " · 처음 발견" : "")));
    panel.addView(k.image("/thumbs/" + r.optString("img") + ".jpg", 140, 130));
    JSONArray mats = h.list(r, "materials");
    ArrayList<View> tiles = new ArrayList<>();
    for (int j = 0; j < mats.length(); j++) {
      JSONObject a = mats.optJSONObject(j);
      tiles.add(
          k.tile(
              a,
              "/thumbs/" + a.optString("img") + ".jpg",
              a.optInt("usable")
                  + " / "
                  + a.optInt("need")
                  + " · 보호 "
                  + a.optInt("locked")
                  + " · 탐험 "
                  + a.optInt("away"),
              null));
    }
    k.tiles(panel, tiles, 2);
    int[] times = {1};
    Button make =
        k.action(
            "u-emblem",
            "만들기",
            () ->
                h.confirm(
                    "재료를 사용해 " + r.optString("name") + " " + times[0] + "개를 만들까요?",
                    () ->
                        performCraft(
                            "craft",
                            MainActivity.obj("id", r.optString("id"), "times", times[0]),
                            r,
                            mats)));
    k.quantity(
        panel,
        Math.min(50, r.optInt("max")),
        n -> {
          times[0] = n;
          make.setText(n + "개 만들기");
        });
    make.setEnabled(r.optBoolean("ready"));
    panel.addView(make);
  }

  void mix(LinearLayout[] cols, JSONObject data) {
    JSONArray pool = h.list(data, "pool");
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          cols[0].removeAllViews();
          cols[1].removeAllViews();
          cols[0].addView(k.note("재료를 눌러 넣기 · 보호·탐험 중인 갈매미는 제외해요."));
          ArrayList<View> choices = new ArrayList<>();
          for (int i = 0; i < pool.length(); i++) {
            JSONObject u = pool.optJSONObject(i);
            String id = u.optString("id");
            int left = u.optInt("usable") - Collections.frequency(m.mixDraft, id);
            LinearLayout tile =
                k.tile(
                    u,
                    "/thumbs/" + u.optString("img") + ".jpg",
                    "남은 " + left,
                    () -> {
                      if (m.mixDraft.size() >= 6 || left <= 0) {
                        h.message("사용 가능한 수량을 확인해 주세요.");
                        return;
                      }
                      m.mixDraft.add(id);
                      paint[0].run();
                    });
            choices.add(tile);
          }
          k.tiles(cols[0], choices, 2);
          cols[1].addView(h.text("섞기 · " + m.mixDraft.size() + " / 6마리", 22));
          ArrayList<View> slots = new ArrayList<>();
          for (int i = 0; i < 6; i++) {
            int index = i;
            if (i >= m.mixDraft.size()) {
              LinearLayout empty = k.paper();
              empty.addView(h.text("＋", 30));
              slots.add(empty);
              continue;
            }
            String id = m.mixDraft.get(i);
            JSONObject u = new JSONObject();
            for (int j = 0; j < pool.length(); j++)
              if (id.equals(pool.optJSONObject(j).optString("id"))) u = pool.optJSONObject(j);
            slots.add(
                k.tile(
                    u,
                    "/thumbs/" + u.optString("img") + ".jpg",
                    "빼기 ×",
                    () -> {
                      m.mixDraft.remove(index);
                      paint[0].run();
                    }));
          }
          k.tiles(cols[1], slots, 3);
          cols[1].addView(k.note("2~6마리로 섞어요. 맞지 않는 조합은 재료가 그대로 남아요."));
          LinearLayout actions = k.row();
          h.cell(
              actions,
              k.action(
                  null,
                  "비우기",
                  () -> {
                    m.mixDraft.clear();
                    paint[0].run();
                  }));
          Button make =
              k.action(
                  "u-emblem",
                  "섞기",
                  () -> {
                    JSONArray mats = new JSONArray();
                    for (String id : m.mixDraft)
                      for (int j = 0; j < pool.length(); j++)
                        if (id.equals(pool.optJSONObject(j).optString("id")))
                          mats.put(pool.optJSONObject(j));
                    h.confirm(
                        "선택한 재료로 섞을까요?",
                        () ->
                            performCraft(
                                "mix",
                                MainActivity.obj("ids", MainActivity.arr(m.mixDraft)),
                                null,
                                mats));
                  });
          make.setEnabled(m.mixDraft.size() >= 2);
          h.cell(actions, make);
          cols[1].addView(actions);
        };
    paint[0].run();
  }

  void performCraft(String endpoint, JSONObject body, JSONObject result, JSONArray materials) {
    h.request(
        endpoint,
        body,
        (r, e) -> {
          Object value = r.opt("result");
          boolean fail =
              value instanceof JSONObject && !((JSONObject) value).optBoolean("made", true);
          String text =
              value instanceof JSONObject
                  ? ((JSONObject) value).optString("text")
                  : String.valueOf(value);
          m.mixDraft.clear();
          NativeForge overlay =
              new NativeForge(h, materials, result, fail, text, () -> h.load("craft"));
          h.root.addView(overlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
          m.fetch(
              "me",
              me -> {
                String resultId =
                    result == null
                        ? (value instanceof JSONObject ? ((JSONObject) value).optString("id") : "")
                        : result.optString("id");
                JSONArray owned = h.list(me, "units");
                for (int i = 0; i < owned.length(); i++)
                  if (resultId.equals(owned.optJSONObject(i).optString("id")))
                    overlay.updateResult(owned.optJSONObject(i));
              });
        });
  }

  void shop() {
    h.base("상점 · 수비대 매점", "shop");
    m.fetch(
        "me",
        r -> {
          JSONObject prices = r.optJSONObject("prices"), rules = r.optJSONObject("itemRules");
          if (prices == null) return;
          int maxStack = rules == null ? 5 : rules.optInt("maxStack", 5);
          LinearLayout shelf = k.column();
          shelf.setPadding(h.dp(10), h.dp(10), h.dp(10), h.dp(10));
          shelf.setBackground(h.material(0xff8a5a2b));
          h.body.addView(k.note("대결 아이템은 종류마다 " + maxStack + "개까지 · 갈매미 교환·팔기는 카톡 상점"));
          h.body.addView(shelf);
          ArrayList<View> goods = new ArrayList<>();
          JSONObject tickets = prices.optJSONObject("tickets"),
              peanut = prices.optJSONObject("peanutTickets");
          if (tickets != null)
            goods.add(
                ware(
                    "u-ticket",
                    "뽑기권 " + tickets.optInt("amount") + "장",
                    "새우gang으로 사요",
                    tickets.optInt("snack"),
                    Math.min(50, r.optInt("snack") / Math.max(1, tickets.optInt("snack"))),
                    "새우gang",
                    "shop",
                    MainActivity.obj("item", "tickets")));
          if (peanut != null)
            goods.add(
                ware(
                    "u-peanut",
                    "뽑기권 " + peanut.optInt("amount") + "장",
                    "땅콩으로 바꿔요",
                    1,
                    Math.min(50, r.optInt("peanut")),
                    "땅콩",
                    "shop",
                    MainActivity.obj("item", "peanutTickets")));
          JSONArray defs = h.list(r, "itemDefs"), owned = h.list(r, "items");
          for (int i = 0; i < defs.length(); i++) {
            JSONObject d = defs.optJSONObject(i);
            int count = 0;
            for (int j = 0; j < owned.length(); j++)
              if (d.optString("id").equals(owned.optJSONObject(j).optString("id")))
                count = owned.optJSONObject(j).optInt("count");
            int price = d.optInt("price");
            int limit =
                Math.max(0, Math.min(maxStack - count, r.optInt("snack") / Math.max(1, price)));
            goods.add(
                ware(
                    d.optString("id"),
                    d.optString("name") + " · 가진 " + count + "개",
                    d.optString("desc"),
                    price,
                    limit,
                    "새우gang",
                    "items/buy",
                    MainActivity.obj("id", d.optString("id"))));
          }
          k.tiles(shelf, goods, h.getResources().getConfiguration().screenWidthDp >= 1100 ? 5 : 3);
        });
  }

  LinearLayout ware(
      String icon,
      String title,
      String desc,
      int price,
      int limit,
      String currency,
      String endpoint,
      JSONObject params) {
    LinearLayout card = k.paper();
    card.addView(k.image("/duel/icons/" + icon + ".png", 56, 56));
    card.addView(h.text(title, 18));
    card.addView(k.note(desc));
    int[] amount = {1};
    Button buy =
        k.action(
            icon,
            currency + " " + price,
            () ->
                h.confirm(
                    title
                        + " "
                        + amount[0]
                        + "개 · "
                        + currency
                        + " "
                        + price * amount[0]
                        + "개로 살까요?",
                    () -> {
                      try {
                        params.put("count", amount[0]);
                      } catch (JSONException ignored) {
                      }
                      h.mutate(endpoint, params, "shop");
                    }));
    k.quantity(
        card,
        limit,
        false,
        n -> {
          amount[0] = n;
          buy.setText(currency + " " + price * n);
        });
    buy.setEnabled(limit > 0);
    if (limit <= 0) buy.setText("살 수 없음");
    card.addView(buy);
    return card;
  }

  long time(String date) {
    try {
      return java.time.Instant.parse(date).toEpochMilli();
    } catch (Exception e) {
      return 0;
    }
  }

  void expedition() {
    boolean returning = h.screen.equals("expedition") && h.expeditionDraft != null;
    h.base("탐험대 편성", "expedition");
    JSONObject trip = h.me().optJSONObject("trip");
    if (trip != null) {
      h.expeditionDirty = false;
      LinearLayout panel = k.paper();
      h.body.addView(panel);
      long start = time(trip.optString("startedAt")),
          end = time(trip.optString("endsAt")),
          left = Math.max(0, end - System.currentTimeMillis());
      intro(
          panel,
          "mode-camp",
          left > 0 ? "탐험 중" : "탐험대가 돌아왔어요!",
          left > 0 ? h.waitText(left).replace("다음 지급", "남은 시간") : "보상을 받아요");
      k.progress(
          panel,
          "항구 → 탐험지 → 귀환",
          (int)
              Math.min(
                  100,
                  Math.max(
                      0, (System.currentTimeMillis() - start) * 100 / Math.max(1, end - start))),
          100,
          0xffe9a630);
      ArrayList<View> team = new ArrayList<>();
      JSONArray ids = h.list(trip, "ids");
      for (int i = 0; i < ids.length(); i++) {
        String id = ids.optString(i);
        JSONObject u = find(id);
        team.add(k.tile(u, "/thumbs/" + id + ".jpg", "탐험 중", null));
      }
      k.tiles(panel, team, Math.min(5, Math.max(1, ids.length())));
      if (left > 0)
        panel.addView(
            k.action(
                null,
                "바로 돌아오게 하기",
                () ->
                    m.fetch(
                        "me",
                        r -> {
                          JSONObject p = r.optJSONObject("prices"),
                              skip = p == null ? null : p.optJSONObject("skip");
                          int cost = skip == null ? 0 : skip.optInt("snack");
                          h.confirm(
                              "새우gang " + cost + "개로 탐험대를 바로 돌아오게 할까요?",
                              () ->
                                  h.mutate(
                                      "shop",
                                      MainActivity.obj("item", "skip", "count", 1),
                                      "expedition"));
                        })));
      else
        panel.addView(
            k.action(
                "u-gift",
                "탐험 보상 받기",
                () -> h.mutate("expedition/claim", MainActivity.obj(), "expedition")));
      int ep = h.epoch;
      h.api.ui.postDelayed(
          () -> {
            if (h.epoch == ep && !h.busy) h.load("expedition");
          },
          Math.min(60000, Math.max(1000, left + 300)));
      return;
    }
    if (!returning) {
      h.expeditionDraft = new ArrayList<>();
      JSONArray saved = h.list(h.me(), "party");
      for (int i = 0; i < saved.length(); i++) h.expeditionDraft.add(saved.optString(i));
      h.expeditionDirty = false;
    }
    LinearLayout[] cols = k.split(.55f);
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          cols[0].removeAllViews();
          cols[1].removeAllViews();
          cols[0].addView(k.note("보호된 갈매미도 탐험에 함께할 수 있어요."));
          JSONArray units = h.list(h.me(), "units");
          ArrayList<View> choices = new ArrayList<>();
          for (int i = 0; i < units.length(); i++) {
            JSONObject u = units.optJSONObject(i);
            if (!u.optBoolean("top")) continue;
            String id = u.optString("id");
            int left =
                u.optInt("have") - u.optInt("away") - Collections.frequency(h.expeditionDraft, id);
            choices.add(
                k.tile(
                    u,
                    "/thumbs/" + id + ".jpg",
                    "남은 " + left,
                    () -> {
                      if (left <= 0 || h.expeditionDraft.size() >= 10) {
                        h.message("탐험 편성과 사용 가능한 수량을 확인해 주세요.");
                        return;
                      }
                      h.expeditionDraft.add(id);
                      h.expeditionDirty = true;
                      paint[0].run();
                    }));
          }
          k.tiles(cols[0], choices, 3);
          cols[1].addView(h.text("탐험대 " + h.expeditionDraft.size() + " / 10", 22));
          ArrayList<View> selected = new ArrayList<>();
          for (int i = 0; i < h.expeditionDraft.size(); i++) {
            int index = i;
            String id = h.expeditionDraft.get(i);
            selected.add(
                k.tile(
                    find(id),
                    "/thumbs/" + id + ".jpg",
                    "빼기 ×",
                    () -> {
                      h.expeditionDraft.remove(index);
                      h.expeditionDirty = true;
                      paint[0].run();
                    }));
          }
          k.tiles(cols[1], selected, 3);
          cols[1].addView(
              k.action(
                  null,
                  "비우기",
                  () -> {
                    h.expeditionDraft.clear();
                    h.expeditionDirty = true;
                    paint[0].run();
                  }));
          JSONArray previous = h.list(h.me(), "previousParty");
          if (previous.length() > 0)
            cols[1].addView(
                k.action(
                    null,
                    "최근 탐험대 불러오기",
                    () -> {
                      h.expeditionDraft.clear();
                      for (int i = 0; i < previous.length(); i++)
                        h.expeditionDraft.add(previous.optString(i));
                      h.expeditionDirty = true;
                      paint[0].run();
                    }));
        };
    paint[0].run();
    LinearLayout footer = k.row();
    h.cell(
        footer,
        k.painted(
            "btn-save",
            "편성 저장",
            () ->
                h.request(
                    "party",
                    MainActivity.obj("ids", MainActivity.arr(h.expeditionDraft)),
                    (r, e) -> {
                      h.expeditionDirty = false;
                      try {
                        h.me().put("party", MainActivity.arr(h.expeditionDraft));
                      } catch (JSONException ignored) {
                      }
                      android.widget.Toast.makeText(
                              h, "탐험 편성을 저장했어요.", android.widget.Toast.LENGTH_SHORT)
                          .show();
                    })));
    h.cell(
        footer,
        k.action(
            "mode-camp",
            "탐험 출발",
            () -> {
              if (h.expeditionDraft.isEmpty()) {
                h.message("탐험대를 먼저 골라 주세요.");
                return;
              }
              h.confirm(
                  "탐험대 " + h.expeditionDraft.size() + "마리를 출발시킬까요?",
                  () ->
                      h.request(
                          "party",
                          MainActivity.obj("ids", MainActivity.arr(h.expeditionDraft)),
                          (r, e) -> {
                            h.expeditionDirty = false;
                            h.mutate("expedition/start", MainActivity.obj(), "expedition");
                          }));
            }));
    k.footer(footer);
  }

  JSONObject find(String id) {
    JSONArray all = h.list(h.me(), "units");
    for (int i = 0; i < all.length(); i++)
      if (id.equals(all.optJSONObject(i).optString("id"))) return all.optJSONObject(i);
    return MainActivity.obj("id", id, "name", id);
  }
}

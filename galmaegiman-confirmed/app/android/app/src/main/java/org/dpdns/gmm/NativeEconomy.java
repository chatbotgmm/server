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
  LinearLayout craftActions;

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
          LinearLayout banner = k.paper(), bannerRow = k.row(), words = k.column();
          LinearLayout titleLine = k.row();
          titleLine.addView(k.image("/duel/icons/u-ticket.png", 24, 24));
          titleLine.addView(
              h.text("뽑기권 " + (r.optBoolean("unlimited") ? "무제한" : r.optInt("credits") + "장"), 18));
          words.addView(titleLine);
          TextView wait = k.note("");
          words.addView(wait);
          JSONObject event = r.optJSONObject("event");
          if (event != null) words.addView(k.note(event.optString("label")));
          bannerRow.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
          if (m.lastDraw != null)
            bannerRow.addView(
                k.action("u-draw", "지난 결과", () -> reveal(m.lastDraw, () -> h.load("draw"), true)),
                new LinearLayout.LayoutParams(h.dp(112), h.dp(48)));
          Button claim = k.action("u-ticket", "뽑기권 받기", this::claimTickets);
          claim.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
          long ready = android.os.SystemClock.elapsedRealtime() + Math.max(0, r.optLong("wait"));
          tick(
              banner,
              () -> {
                long left = Math.max(0, ready - android.os.SystemClock.elapsedRealtime());
                claim.setEnabled(left == 0);
                wait.setText(
                    left > 0
                        ? "다음 뽑기권까지 " + duration(left)
                        : "지금 "
                            + r.optInt("claim")
                            + "장 받기 · "
                            + r.optDouble("cooldownHours")
                            + "시간마다");
              });
          bannerRow.addView(claim, new LinearLayout.LayoutParams(h.dp(176), h.dp(48)));
          banner.addView(bannerRow);
          h.body.addView(banner);
          ArrayList<View> cards = new ArrayList<>();
          LinearLayout drawActions = k.row();
          JSONArray gachas = h.list(r, "gachas");
          for (int i = 0; i < gachas.length(); i++) {
            JSONObject g = gachas.optJSONObject(i);
            int cost = Math.max(1, g.optInt("cost"));
            int limit = r.optBoolean("unlimited") ? 50 : Math.min(50, r.optInt("credits") / cost);
            LinearLayout card = k.paper();
            LinearLayout line = k.row();
            line.setGravity(Gravity.TOP);
            card.addView(line);
            ImageView portrait =
                k.image(
                    "/thumbs/" + g.optString("img") + ".jpg",
                    h.getResources().getConfiguration().screenHeightDp < 600 ? 48 : 60,
                    h.getResources().getConfiguration().screenHeightDp < 600 ? 48 : 60);
            line.addView(portrait);
            LinearLayout info = k.column();
            line.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            info.addView(h.text(g.optString("label") + " 뽑기", 18));
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
                    "u-ticket",
                    g.optString("label") + " 1회 · " + cost,
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
            k.compactQuantity(
                info,
                limit,
                n -> {
                  amount[0] = n;
                  go.setText(g.optString("label") + " " + n + "회 · " + cost * n);
                });
            quantityLimit(info, limit);
            go.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
            go.setEnabled(limit > 0);
            if (limit <= 0) go.setText("뽑기권 부족");
            go.setTextSize(15);
            go.setPadding(h.dp(4), h.dp(4), h.dp(4), h.dp(7));
            h.cell(drawActions, go);
            TextView chance = k.note("확률 · " + odds);
            chance.setTextSize(12);
            chance.setGravity(Gravity.CENTER);
            info.addView(chance);
            JSONObject pity = r.optJSONObject("pity");
            if (g.optString("kind").equals("HIGH") && pity != null)
              info.addView(
                  k.note(pity.optString("tierName") + " 보장까지 " + pity.optInt("left") + "회"));
            card.setMinimumHeight(h.dp(190));
            cards.add(card);
          }
          k.tiles(h.body, cards, 3);
          h.body.addView(k.action(null, "확률·획득 보상 안내", () -> drawGuide(r)));
          k.footer(drawActions);
        });
  }

  void claimTickets() {
    h.request(
        "claim",
        MainActivity.obj(),
        (r, e) -> {
          Object result = r.opt("result");
          // 서버 확정 수량을 먼저 가져와 결과 창 아래의 상단 재화도 즉시 갱신한다.
          int operationEpoch = h.epoch;
          h.busy = true;
          h.api.call(
              "native/state",
              null,
              (fresh, error) -> {
                h.busy = false;
                if (h.isFinishing() || h.epoch != operationEpoch) return;
                if (error == null) {
                  h.state = fresh;
                  NativeAlerts.schedule(h, h.me());
                  draw();
                }
                showTicketReward(result, error != null);
              });
        });
  }

  void showTicketReward(Object result, boolean refreshOnClose) {
    LinearLayout reward = k.column();
    reward.setPadding(h.dp(20), h.dp(16), h.dp(20), h.dp(8));
    LinearLayout head = k.row();
    head.addView(k.image("/duel/icons/u-ticket.png", 52, 52));
    head.addView(
        h.text(
            result != null && String.valueOf(result).contains("뽑기권 +") ? "뽑기권을 받았어요!" : "뽑기권 안내",
            22));
    reward.addView(head);
    TextView details =
        k.note(result == null ? "지급이 완료됐어요." : String.valueOf(result).replace("🎟", "").trim());
    details.setTextSize(17);
    reward.addView(details);
    new GameDialog(h)
        .setView(reward)
        .setPositiveButton(
            "확인",
            (d, w) -> {
              if (refreshOnClose) h.load("draw");
            })
        .show();
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
    h.base("조합", mix ? "mix" : "craft");
    LinearLayout tabs = k.column();
    k.tabs(tabs, new String[] {"조합식", "재료로 섞기"}, mix ? 1 : 0, i -> craft(i == 1));
    h.page.addView(tabs, h.page.getChildCount() - 1);
    LinearLayout[] cols = k.split(.47f);
    craftActions = k.row();
    k.footer(craftActions);
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
            craftActions.removeAllViews();
            craftActions.addView(k.note("조합식을 선택해 주세요."));
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
                        + r.optString("tierName")
                        + (r.optBoolean("isNew") ? " · 처음" : "")
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
    panel.addView(h.text(r.optString("name"), 22));
    panel.addView(k.note(r.optString("tierName") + (r.optBoolean("isNew") ? " · 처음 발견" : "")));
    JSONArray mats = h.list(r, "materials");
    HorizontalScrollView strip = new HorizontalScrollView(h);
    strip.setHorizontalScrollBarEnabled(false);
    LinearLayout flow = k.row();
    flow.setGravity(Gravity.TOP);
    strip.addView(flow);
    panel.addView(strip);
    ArrayList<TextView> counts = new ArrayList<>();
    for (int j = 0; j < mats.length(); j++) {
      JSONObject material = mats.optJSONObject(j);
      if (j > 0) flow.addView(h.text("＋", 18));
      LinearLayout slot = materialSlot(material, false);
      counts.add((TextView) slot.getChildAt(2));
      flow.addView(slot);
    }
    flow.addView(h.text("→", 22));
    flow.addView(materialSlot(r, true));
    panel.addView(
        k.note(
            r.optBoolean("ready")
                ? "최대 " + r.optInt("max") + "개까지 만들 수 있어요."
                : "붉은 재료가 부족해요. 재료를 누르면 얻는 곳을 볼 수 있어요."));
    panel.addView(k.note("보호·탐험 중인 갈매미는 조합 재료에서 제외해요."));
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
    craftActions.removeAllViews();
    LinearLayout quantity = k.column();
    k.compactQuantity(
        quantity,
        Math.min(50, r.optInt("max")),
        n -> {
          times[0] = n;
          make.setText(n > 1 ? n + "개 만들기" : "만들기");
          for (int i = 0; i < counts.size(); i++) {
            JSONObject material = mats.optJSONObject(i);
            int need = material.optInt("need") * n;
            counts.get(i).setText(material.optInt("usable") + " / " + need);
            counts
                .get(i)
                .setTextColor(material.optInt("usable") < need ? 0xffb44335 : MainActivity.INK);
          }
        });
    quantityLimit(quantity, r.optInt("max"));
    craftActions.addView(quantity, new LinearLayout.LayoutParams(0, -2, 1));
    make.setEnabled(r.optBoolean("ready"));
    if (!r.optBoolean("ready")) make.setText("재료 부족");
    make.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
    craftActions.addView(make, new LinearLayout.LayoutParams(h.dp(220), h.dp(48)));
  }

  LinearLayout materialSlot(JSONObject unit, boolean result) {
    LinearLayout slot = k.paper();
    slot.setGravity(Gravity.CENTER_HORIZONTAL);
    slot.setLayoutParams(new LinearLayout.LayoutParams(h.dp(compact() ? 86 : 112), -2));
    slot.addView(
        k.image(
            "/thumbs/" + unit.optString("img") + ".jpg", compact() ? 54 : 74, compact() ? 54 : 74));
    TextView name = h.text(unit.optString("name"), compact() ? 12 : 15);
    name.setMaxLines(2);
    name.setGravity(Gravity.CENTER);
    slot.addView(name);
    TextView count =
        k.note(
            result
                ? unit.optString("tierName")
                : unit.optInt("usable") + " / " + unit.optInt("need"));
    count.setTextSize(compact() ? 12 : 14);
    count.setGravity(Gravity.CENTER);
    slot.addView(count);
    if (!result) {
      boolean missing = unit.optInt("usable") < unit.optInt("need");
      if (missing) {
        slot.setBackground(new GamePaperDrawable(h, 0xffb44335));
        count.setTextColor(0xffb44335);
      }
      String held =
          (unit.optInt("locked") > 0 ? "보호 " + unit.optInt("locked") : "")
              + (unit.optInt("away") > 0 ? " · 탐험 " + unit.optInt("away") : "");
      if (!held.isEmpty()) slot.addView(k.note(held));
      slot.setContentDescription(unit.optString("name") + " 재료 · 얻는 곳 보기");
      slot.setOnClickListener(v -> materialGuide(unit));
    }
    return slot;
  }

  void mix(LinearLayout[] cols, JSONObject data) {
    JSONArray pool = h.list(data, "pool");
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          cols[0].removeAllViews();
          cols[1].removeAllViews();
          cols[0].addView(k.notice("눌러서 넣기 · 숨은 조합 찾기"));
          ArrayList<View> choices = new ArrayList<>();
          for (int i = 0; i < pool.length(); i++) {
            JSONObject u = pool.optJSONObject(i);
            String id = u.optString("id");
            int left = u.optInt("usable") - Collections.frequency(m.mixDraft, id);
            LinearLayout tile =
                k.tile(
                    u,
                    "/thumbs/" + u.optString("img") + ".jpg",
                    "남은 "
                        + left
                        + (Collections.frequency(m.mixDraft, id) > 0
                            ? " · 선택 " + Collections.frequency(m.mixDraft, id)
                            : ""),
                    () -> {
                      if (m.mixDraft.size() >= 6 || left <= 0) {
                        int picked = m.mixDraft.lastIndexOf(id);
                        if (picked < 0) return;
                        m.mixDraft.remove(picked);
                      } else m.mixDraft.add(id);
                      paint[0].run();
                    });
            for (int j = 0; j < tile.getChildCount(); j++)
              if (tile.getChildAt(j) instanceof ImageView)
                tile.getChildAt(j)
                    .setLayoutParams(
                        new LinearLayout.LayoutParams(
                            h.dp(compact() ? 58 : 92), h.dp(compact() ? 58 : 92)));
            tile.setAlpha(left > 0 || m.mixDraft.contains(id) ? 1 : .55f);
            choices.add(tile);
          }
          k.tiles(cols[0], choices, 2);
          cols[1].addView(k.section("섞기 · " + m.mixDraft.size() + " / 6마리"));
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
          cols[1].addView(k.notice("2~6마리로 섞어요. 맞지 않는 조합은 재료가 그대로 남아요."));
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
          make.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
          ((Button) actions.getChildAt(0)).setEnabled(!m.mixDraft.isEmpty());
          h.cell(actions, make);
          craftActions.removeAllViews();
          craftActions.addView(actions, new LinearLayout.LayoutParams(-1, -2));
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
    h.base("상점", "shop");
    m.fetch(
        "me",
        r -> {
          JSONObject prices = r.optJSONObject("prices"), rules = r.optJSONObject("itemRules");
          if (prices == null) return;
          int maxStack = rules == null ? 5 : rules.optInt("maxStack", 5);
          LinearLayout shelf = k.column();
          shelf.setPadding(h.dp(10), h.dp(10), h.dp(10), h.dp(10));
          shelf.setBackground(h.material(0xff8a5a2b));
          h.body.addView(k.notice("대결 아이템은 종류마다 " + maxStack + "개까지 · 갈매미 교환·팔기는 카톡 상점"));
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
                    MainActivity.obj("item", "tickets"),
                    "새우gang 부족"));
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
                    MainActivity.obj("item", "peanutTickets"),
                    "땅콩 부족"));
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
                    MainActivity.obj("id", d.optString("id")),
                    count >= maxStack ? "보유 한도" : "새우gang 부족"));
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
      JSONObject params,
      String blocked) {
    LinearLayout card = k.paper();
    LinearLayout nameLine = k.row();
    nameLine.addView(
        k.image("/duel/icons/" + icon + ".png", compact() ? 36 : 56, compact() ? 36 : 56));
    nameLine.addView(h.text(title, compact() ? 16 : 18), new LinearLayout.LayoutParams(0, -2, 1));
    card.addView(nameLine);
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
    quantityLimit(card, limit);
    buy.setEnabled(limit > 0);
    if (limit <= 0) buy.setText(blocked);
    buy.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
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
      showTrip(trip);
      return;
    }
    if (!returning) {
      h.expeditionDraft = new ArrayList<>();
      JSONArray saved = h.list(h.me(), "party");
      for (int i = 0; i < saved.length(); i++) h.expeditionDraft.add(saved.optString(i));
      h.expeditionDirty = false;
    }
    int max = partyMax();
    LinearLayout[] cols = k.split(.55f);
    LinearLayout footer = k.row();
    TextView status = k.note("");
    footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
    Runnable[] paint = new Runnable[1];
    Button save =
        k.painted(
            "btn-save",
            "편성 저장",
            () -> {
              ArrayList<String> submitted = new ArrayList<>(h.expeditionDraft);
              h.request(
                  "party",
                  MainActivity.obj("ids", MainActivity.arr(submitted)),
                  (r, e) -> {
                    try {
                      h.me().put("party", MainActivity.arr(submitted));
                    } catch (JSONException ignored) {
                    }
                    h.expeditionDirty = !h.expeditionDraft.equals(submitted);
                    paint[0].run();
                    Toast.makeText(h, "탐험 편성을 저장했어요.", Toast.LENGTH_SHORT).show();
                  });
            });
    Button go =
        k.action(
            "mode-camp",
            "탐험 출발",
            () -> {
              if (partyChanged()) {
                h.message("편성을 저장한 뒤 출발해 주세요.");
                return;
              }
              if (h.expeditionDraft.isEmpty()) return;
              h.confirm(
                  "탐험대 " + h.expeditionDraft.size() + "마리를 출발시킬까요?\n귀환까지 전투·조합에 사용할 수 없어요.",
                  () -> h.mutate("expedition/start", MainActivity.obj(), "expedition"));
            });
    go.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
    footer.addView(save, new LinearLayout.LayoutParams(h.dp(152), h.dp(48)));
    footer.addView(go, new LinearLayout.LayoutParams(h.dp(176), h.dp(48)));
    k.footer(footer);
    paint[0] =
        () -> {
          cols[0].removeAllViews();
          cols[1].removeAllViews();
          cols[0].addView(k.notice("최상위 갈매미를 보내요 · 보호된 갈매미도 함께할 수 있어요."));
          JSONArray units = h.list(h.me(), "units");
          ArrayList<View> choices = new ArrayList<>();
          for (int i = 0; i < units.length(); i++) {
            JSONObject u = units.optJSONObject(i);
            if (!u.optBoolean("top") || u.optInt("have") - u.optInt("away") <= 0) continue;
            String id = u.optString("id");
            int picked = Collections.frequency(h.expeditionDraft, id);
            int left = u.optInt("have") - u.optInt("away") - picked;
            LinearLayout card = k.paper();
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.addView(
                k.image("/thumbs/" + id + ".jpg", compact() ? 58 : 90, compact() ? 58 : 90));
            TextView name = h.text(u.optString("name"), compact() ? 13 : 16);
            name.setGravity(Gravity.CENTER);
            card.addView(name);
            card.addView(k.note(synergyLabel(u.optString("synergy")) + " · 남은 " + left));
            if (picked > 0) {
              card.setBackground(new GamePaperDrawable(h, 0xffa8672e));
              card.addView(h.text("✓ 선택 " + picked + "마리", 13));
            }
            LinearLayout addRemove = k.row();
            Button remove =
                k.action(
                    null,
                    "−",
                    () -> {
                      int index = h.expeditionDraft.lastIndexOf(id);
                      if (index >= 0) h.expeditionDraft.remove(index);
                      paint[0].run();
                    });
            Button add =
                k.action(
                    null,
                    "＋",
                    () -> {
                      if (left > 0 && h.expeditionDraft.size() < max) h.expeditionDraft.add(id);
                      paint[0].run();
                    });
            remove.setEnabled(picked > 0);
            add.setEnabled(left > 0 && h.expeditionDraft.size() < max);
            h.cell(addRemove, remove);
            h.cell(addRemove, add);
            card.addView(addRemove);
            choices.add(card);
          }
          if (choices.isEmpty()) {
            cols[0].addView(k.notice("보낼 수 있는 최상위 갈매미가 없어요. 전설을 모아 조합해 보세요."));
            cols[0].addView(k.action("u-emblem", "조합식 보기", () -> h.load("craft")));
          } else k.tiles(cols[0], choices, compact() ? 3 : 4);
          cols[1].addView(k.section("탐험대 " + h.expeditionDraft.size() + " / " + max));
          if (h.expeditionDraft.isEmpty())
            cols[1].addView(k.note("왼쪽 ＋로 추가해 주세요. 같은 시너지끼리 보내면 보너스!"));
          showExpeditionEstimate(cols[1], h.expeditionDraft);
          ArrayList<View> selected = new ArrayList<>();
          for (int i = 0; i < h.expeditionDraft.size(); i++) {
            int index = i;
            String id = h.expeditionDraft.get(i);
            LinearLayout item = k.paper();
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.addView(
                k.image("/thumbs/" + id + ".jpg", compact() ? 44 : 78, compact() ? 44 : 78));
            TextView name = h.text(find(id).optString("name"), compact() ? 12 : 15);
            name.setGravity(Gravity.CENTER);
            item.addView(name);
            item.addView(k.note("빼기 ×"));
            item.setContentDescription(find(id).optString("name") + " 탐험대에서 빼기");
            item.setOnClickListener(
                v -> {
                  if (!h.busy) {
                    h.expeditionDraft.remove(index);
                    paint[0].run();
                  }
                });
            selected.add(item);
          }
          k.tiles(cols[1], selected, 3);
          LinearLayout auxiliary = k.row();
          Button clear =
              k.action(
                  null,
                  "비우기",
                  () -> {
                    h.expeditionDraft.clear();
                    paint[0].run();
                  });
          clear.setEnabled(!h.expeditionDraft.isEmpty());
          h.cell(auxiliary, clear);
          JSONArray previous = h.list(h.me(), "previousParty");
          if (previous.length() > 0)
            h.cell(
                auxiliary,
                k.action(
                    null,
                    "최근 탐험대",
                    () -> {
                      h.expeditionDraft.clear();
                      for (int i = 0;
                          i < previous.length() && h.expeditionDraft.size() < max;
                          i++) {
                        String id = previous.optString(i);
                        JSONObject u = find(id);
                        if (u.optBoolean("top")
                            && Collections.frequency(h.expeditionDraft, id)
                                < u.optInt("have") - u.optInt("away")) h.expeditionDraft.add(id);
                      }
                      if (h.expeditionDraft.size() < previous.length())
                        Toast.makeText(h, "지금 보낼 수 있는 갈매미만 불러왔어요.", Toast.LENGTH_SHORT).show();
                      paint[0].run();
                    }));
          cols[1].addView(auxiliary);
          cols[1].addView(k.action(null, "시너지 안내", this::synergyGuide));
          h.expeditionDirty = partyChanged();
          save.setEnabled(h.expeditionDirty);
          go.setEnabled(!h.expeditionDraft.isEmpty() && !h.expeditionDirty);
          status.setText(h.expeditionDirty ? "변경된 편성 · 먼저 저장해 주세요" : "저장된 편성");
        };
    paint[0].run();
  }

  void showTrip(JSONObject trip) {
    long start = time(trip.optString("startedAt")), end = time(trip.optString("endsAt"));
    LinearLayout panel = k.paper();
    h.body.addView(panel);
    TextView title = h.text("", 24), remaining = k.note("");
    panel.addView(title);
    panel.addView(remaining);
    ProgressBar progress = new ProgressBar(h, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(1000);
    progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffe9a630));
    panel.addView(progress, new LinearLayout.LayoutParams(-1, h.dp(12)));
    panel.addView(k.note("항구 → 탐험지 → 귀환"));
    JSONArray ids = h.list(trip, "ids");
    ArrayList<String> names = new ArrayList<>(), party = new ArrayList<>();
    ArrayList<View> members = new ArrayList<>();
    for (int i = 0; i < ids.length(); i++) {
      String id = ids.optString(i);
      party.add(id);
      names.add(find(id).optString("name"));
      if (i < 5) members.add(k.tile(find(id), "/thumbs/" + id + ".jpg", "탐험대", null));
    }
    panel.addView(k.note(String.join(", ", names)));
    k.tiles(panel, members, Math.max(1, Math.min(5, members.size())));
    showExpeditionEstimate(panel, party);
    Button action =
        k.action(
            "u-gift",
            "",
            () -> {
              if (System.currentTimeMillis() >= end)
                h.mutate("expedition/claim", MainActivity.obj(), "expedition");
              else
                m.fetch(
                    "me",
                    me -> {
                      JSONObject prices = me.optJSONObject("prices"),
                          skip = prices == null ? null : prices.optJSONObject("skip");
                      int cost = skip == null ? -1 : skip.optInt("snack", -1);
                      if (cost < 0) {
                        h.message("귀환 비용을 불러오지 못했어요. 다시 시도해 주세요.");
                        return;
                      }
                      if (me.optInt("snack") < cost) {
                        h.message("새우gang " + cost + "개가 필요해요.");
                        return;
                      }
                      h.confirm(
                          "새우gang " + cost + "개로 바로 귀환할까요?",
                          () ->
                              h.mutate(
                                  "shop",
                                  MainActivity.obj("item", "skip", "count", 1),
                                  "expedition"));
                    });
            });
    action.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PRIMARY));
    LinearLayout footer = k.row();
    footer.addView(action, new LinearLayout.LayoutParams(-1, h.dp(48)));
    k.footer(footer);
    tick(
        panel,
        () -> {
          long left = Math.max(0, end - System.currentTimeMillis());
          title.setText(left > 0 ? "탐험 중" : "탐험대가 돌아왔어요!");
          remaining.setText(left > 0 ? "남은 시간 " + duration(left) : "보상을 받을 수 있어요.");
          progress.setProgress(
              (int)
                  Math.min(
                      1000,
                      Math.max(
                          0,
                          (System.currentTimeMillis() - start) * 1000 / Math.max(1, end - start))));
          action.setText(left > 0 ? "바로 귀환" : "탐험 보상 받기");
        });
  }

  boolean partyChanged() {
    JSONArray saved = h.list(h.me(), "party");
    if (h.expeditionDraft == null || h.expeditionDraft.size() != saved.length()) return true;
    for (int i = 0; i < saved.length(); i++)
      if (!h.expeditionDraft.get(i).equals(saved.optString(i))) return true;
    return false;
  }

  int partyMax() {
    JSONObject prices = h.me().optJSONObject("prices");
    return Math.max(1, prices == null ? 10 : prices.optInt("maxParty", 10));
  }

  boolean compact() {
    return h.getResources().getConfiguration().screenHeightDp < 600;
  }

  String duration(long millis) {
    long minutes = (Math.max(0, millis) + 59999) / 60000;
    return minutes >= 60 ? minutes / 60 + "시간 " + minutes % 60 + "분" : minutes + "분";
  }

  // 페이지에 붙어 있는 동안만 갱신한다. 재화를 다시 받거나 화면을 재생성하지 않는다.
  void tick(View owner, Runnable update) {
    int epoch = h.epoch;
    Runnable task =
        new Runnable() {
          @Override
          public void run() {
            if (h.epoch != epoch || h.isFinishing()) return;
            update.run();
            owner.postDelayed(this, 1000);
          }
        };
    owner.addOnAttachStateChangeListener(
        new View.OnAttachStateChangeListener() {
          @Override
          public void onViewAttachedToWindow(View view) {
            owner.removeCallbacks(task);
            task.run();
          }

          @Override
          public void onViewDetachedFromWindow(View view) {
            owner.removeCallbacks(task);
          }
        });
    update.run();
    if (owner.isAttachedToWindow()) task.run();
  }

  void quantityLimit(LinearLayout parent, int limit) {
    View group = parent.getChildAt(parent.getChildCount() - 1);
    if (!(group instanceof LinearLayout)) return;
    LinearLayout row = (LinearLayout) group;
    for (int i = 0; i < row.getChildCount(); i++) {
      View child = row.getChildAt(i);
      child.setEnabled(
          limit > 0
              && (!(child instanceof Button)
                  || !((Button) child).getText().equals("최대")
                  || limit > 1));
    }
  }

  JSONObject economy() {
    JSONObject economy = h.state.optJSONObject("economy");
    if (economy == null) economy = h.me().optJSONObject("economy");
    return economy == null ? new JSONObject() : economy;
  }

  String source(String id) {
    JSONObject sources = h.state.optJSONObject("sources");
    String source = sources == null ? "" : sources.optString(id);
    if (source.isEmpty()) source = find(id).optString("source");
    return source;
  }

  void materialGuide(JSONObject material) {
    LinearLayout panel = k.paper();
    panel.addView(h.text(material.optString("name"), 22));
    panel.addView(
        k.note("사용 가능 " + material.optInt("usable") + " · 필요 " + material.optInt("need")));
    String where = material.optString("source");
    if (where.isEmpty()) where = source(material.optString("id"));
    panel.addView(k.note(where.isEmpty() ? "도감에서 얻는 곳을 확인해 주세요." : "얻는 곳 · " + where));
    panel.addView(
        k.note(
            "보호 "
                + material.optInt("locked")
                + " · 탐험 "
                + material.optInt("away")
                + "마리는 재료로 쓰지 않아요."));
    GameDialog dialog = new GameDialog(h);
    dialog.setView(panel).setPositiveButton("닫기", (d, w) -> {});
    if (where.contains("뽑기")) dialog.setNeutralButton("뽑기", (d, w) -> h.load("draw"));
    if (where.contains("조합"))
      dialog.setNegativeButton(
          "조합식",
          (d, w) -> {
            m.recipeQuery = material.optString("name");
            m.craftOnly = false;
            craft(false);
          });
    if (where.contains("교환")) panel.addView(k.note("원하는 재료 교환은 카톡 채널에서 ‘상점’을 입력해 주세요."));
    dialog.show();
  }

  void drawGuide(JSONObject draw) {
    LinearLayout panel = k.paper();
    panel.addView(h.text("확률·획득 보상", 22));
    panel.addView(k.note("중급·고급에서도 흔함·안흔함이 나와요. 보장은 고급 뽑기에만 적용돼요."));
    JSONObject rewards = economy().optJSONObject("rewards");
    JSONArray gachas = h.list(draw, "gachas");
    LinkedHashMap<String, String> tiers = new LinkedHashMap<>();
    for (int i = 0; i < gachas.length(); i++) {
      JSONArray weights = h.list(gachas.optJSONObject(i), "weights");
      for (int j = 0; j < weights.length(); j++) {
        JSONObject weight = weights.optJSONObject(j);
        tiers.put(weight.optString("tier"), weight.optString("tierName"));
      }
    }
    if (rewards != null) {
      panel.addView(k.note("획득 시 자동 지급되는 새우gang · 뽑기권 환급이 아니에요."));
      for (Map.Entry<String, String> tier : tiers.entrySet()) {
        JSONObject reward = rewards.optJSONObject(tier.getKey());
        if (reward != null)
          panel.addView(
              k.note(
                  tier.getValue()
                      + " · 첫 발견 "
                      + reward.optInt("first")
                      + " / 중복 "
                      + reward.optInt("duplicate")));
      }
    }
    panel.addView(k.note("확률·보상·미션·보장은 웹앱과 동일한 서버에서 처리해요."));
    new GameDialog(h).setView(k.scroll(panel)).setPositiveButton("확인", (d, w) -> {}).show();
  }

  String synergyLabel(String key) {
    JSONObject rules = economy().optJSONObject("expedition");
    JSONObject effects = rules == null ? null : rules.optJSONObject("effects");
    JSONObject effect = effects == null ? null : effects.optJSONObject(key);
    if (effect != null) return effect.optString("label", key);
    String[] keys = {"BASIC", "GOLD", "DARK", "SEA", "BLOSSOM", "COSMOS", "OUTFIT"};
    String[] labels = {"기본", "황금", "암흑", "바다", "벚꽃", "우주", "갈 의복"};
    for (int i = 0; i < keys.length; i++) if (keys[i].equals(key)) return labels[i];
    return "시너지 없음";
  }

  void showExpeditionEstimate(LinearLayout parent, List<String> ids) {
    LinkedHashMap<String, Integer> groups = new LinkedHashMap<>();
    for (String id : ids) {
      String key = find(id).optString("synergy");
      if (!key.isEmpty() && !key.equals("null")) groups.put(key, groups.getOrDefault(key, 0) + 1);
    }
    if (groups.isEmpty()) return;
    ArrayList<String> labels = new ArrayList<>();
    for (Map.Entry<String, Integer> group : groups.entrySet())
      labels.add(synergyLabel(group.getKey()) + " " + group.getValue() + "마리");
    parent.addView(k.note("시너지 · " + String.join(" · ", labels)));
    JSONObject rules = economy().optJSONObject("expedition");
    if (rules == null) return;
    ArrayList<EconomyPreview.Member> members = new ArrayList<>();
    for (String id : ids) members.add(new EconomyPreview.Member(id, find(id).optString("synergy")));
    EconomyPreview.Plan plan = EconomyPreview.estimate(previewRules(rules), members);
    parent.addView(
        k.notice(
            "예상 · "
                + plan.minutes
                + "분 / 새우gang "
                + plan.snacks
                + (plan.doublePercent > 0 ? " · 2배 " + plan.doublePercent + "%" : "")));
    ArrayList<String> extra = new ArrayList<>();
    if (plan.commonPercent > 0) extra.add("흔함 1마리 " + plan.commonPercent + "%");
    if (plan.uncommonPercent > 0) extra.add("안흔함 1마리 " + plan.uncommonPercent + "%");
    if (plan.ticketPercent > 0) extra.add("뽑기권 1장 " + plan.ticketPercent + "%");
    if (!extra.isEmpty()) parent.addView(k.note("추가 보상 · " + String.join(" · ", extra)));
    if (!plan.active.isEmpty()) parent.addView(k.note("적용 중 · " + String.join(" · ", plan.active)));
  }

  EconomyPreview.Rules previewRules(JSONObject data) {
    EconomyPreview.Rules rules = new EconomyPreview.Rules();
    rules.minutes = data.optInt("minutes");
    rules.snackPerUnit = data.optInt("snackPerUnit");
    JSONArray levels = h.list(data, "levels");
    rules.levels = new int[] {levels.optInt(0), levels.optInt(1)};
    JSONObject effects = data.optJSONObject("effects");
    if (effects != null) {
      Iterator<String> keys = effects.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        JSONObject effect = effects.optJSONObject(key);
        String field =
            key.equals("BASIC")
                ? "snackPercent"
                : key.equals("GOLD")
                    ? "doublePercent"
                    : key.equals("DARK")
                        ? "minutes"
                        : key.equals("SEA")
                            ? "commonPercent"
                            : key.equals("BLOSSOM")
                                ? "uncommonPercent"
                                : key.equals("COSMOS") ? "ticketPercent" : "snackPerUnit";
        JSONArray values = h.list(effect, field);
        rules.effects.put(key, new int[] {values.optInt(0), values.optInt(1)});
        rules.labels.put(key, effect.optString("label", key));
      }
    }
    JSONObject counts = data.optJSONObject("countBonusPercent");
    if (counts != null) {
      Iterator<String> keys = counts.keys();
      while (keys.hasNext()) {
        String count = keys.next();
        try {
          rules.countBonuses.put(Integer.parseInt(count), counts.optInt(count));
        } catch (NumberFormatException ignored) {
        }
      }
    }
    return rules;
  }

  void synergyGuide() {
    LinearLayout panel = k.paper();
    panel.addView(h.text("탐험 시너지", 22));
    JSONObject data = economy().optJSONObject("expedition");
    if (data != null) {
      EconomyPreview.Rules rules = previewRules(data);
      panel.addView(
          k.note("같은 시너지 " + rules.levels[0] + "마리부터 1단계 · " + rules.levels[1] + "마리부터 2단계"));
      for (String key : rules.effects.keySet()) {
        int[] v = rules.effects.get(key);
        String effect =
            key.equals("BASIC")
                ? "새우gang +" + v[0] + "/+" + v[1] + "%"
                : key.equals("GOLD")
                    ? "새우gang 2배 " + v[0] + "/" + v[1] + "%"
                    : key.equals("DARK")
                        ? "탐험 시간 " + v[0] + "/" + v[1] + "분"
                        : key.equals("SEA")
                            ? "흔함 1마리 " + v[0] + "/" + v[1] + "%"
                            : key.equals("BLOSSOM")
                                ? "안흔함 1마리 " + v[0] + "/" + v[1] + "%"
                                : key.equals("COSMOS")
                                    ? "뽑기권 1장 " + v[0] + "/" + v[1] + "%"
                                    : "마리당 새우gang +" + v[0] + "/+" + v[1];
        panel.addView(k.note(synergyLabel(key) + " · " + effect));
      }
      panel.addView(k.note("같은 갈매미를 여러 마리 보내면 가장 많은 한 종류의 개수 보너스가 추가돼요."));
    } else panel.addView(k.note("같은 시너지끼리 보내면 보너스가 적용돼요. 편성에서 적용 중인 시너지를 확인해 주세요."));
    new GameDialog(h).setView(k.scroll(panel)).setPositiveButton("확인", (d, w) -> {}).show();
  }

  JSONObject find(String id) {
    JSONArray all = h.list(h.me(), "units");
    for (int i = 0; i < all.length(); i++)
      if (id.equals(all.optJSONObject(i).optString("id"))) return all.optJSONObject(i);
    return MainActivity.obj("id", id, "name", id);
  }
}

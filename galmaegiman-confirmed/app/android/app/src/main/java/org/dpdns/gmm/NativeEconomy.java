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
  Set<String> drawBefore;
  NativeAcquisitionFlow drawFlow, acquisitionFlow;
  String shopFocus = "";
  final LinkedHashMap<String, Button> shopActions = new LinkedHashMap<>();
  final LinkedHashMap<String, LinearLayout> shopCards = new LinkedHashMap<>();

  NativeEconomy(NativeMenus menus) {
    m = menus;
    h = m.h;
    k = m.k;
  }

  void resetAcquisition() {
    drawBefore = null;
    drawFlow = null;
    acquisitionFlow = null;
    shopFocus = "";
    shopActions.clear();
    shopCards.clear();
  }

  void draw() {
    h.base("뽑기", "draw");
    m.fetch(
        "draw",
        r -> {
          // 낮은 가로 화면에서는 정보의 줄 수를 줄이고, 수량·총 비용·실행을 기본에 남긴다.
          boolean shortDraw =
              h.contentHeightDp() < 390
                  || (compact() && h.getResources().getConfiguration().fontScale > 1.15f);
          LinearLayout banner = k.paper(), line = k.row(), words = k.column();
          TextView credits =
              h.text("뽑기권 " + (r.optBoolean("unlimited") ? "무제한" : r.optInt("credits") + "장"), 18);
          TextView wait = k.note("");
          if (shortDraw) {
            banner.setPadding(h.dp(10), h.dp(4), h.dp(10), h.dp(4));
            words.setOrientation(LinearLayout.HORIZONTAL);
            words.setGravity(Gravity.CENTER_VERTICAL);
            words.addView(credits, new LinearLayout.LayoutParams(0, -2, 1));
            words.addView(wait, new LinearLayout.LayoutParams(0, -2, 1));
          } else {
            words.addView(credits);
            words.addView(wait);
          }
          line.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
          Button claim = k.primary("u-ticket", "받기", this::claimTickets);
          long ready = android.os.SystemClock.elapsedRealtime() + Math.max(0, r.optLong("wait"));
          tick(
              banner,
              () -> {
                long left = Math.max(0, ready - android.os.SystemClock.elapsedRealtime());
                claim.setEnabled(left == 0);
                wait.setText(
                    left > 0 ? "다음 지급 " + duration(left) : "지금 " + r.optInt("claim") + "장 받기");
              });
          line.addView(claim, new LinearLayout.LayoutParams(h.dp(112), h.dp(48)));
          line.addView(
              k.action(null, "확률·보장", () -> drawGuide(r)),
              new LinearLayout.LayoutParams(h.dp(120), h.dp(48)));
          if (m.lastDraw != null)
            line.addView(
                k.action(null, "지난 결과", () -> reveal(m.lastDraw, () -> h.load("draw"), true)),
                new LinearLayout.LayoutParams(h.dp(112), h.dp(48)));
          banner.addView(line);
          h.body.addView(banner);
          LinearLayout cards = k.row();
          cards.setGravity(Gravity.TOP);
          h.body.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1));
          JSONArray gachas = h.list(r, "gachas");
          for (int i = 0; i < gachas.length(); i++) {
            JSONObject g = gachas.optJSONObject(i);
            int cost = Math.max(1, g.optInt("cost"));
            int limit = r.optBoolean("unlimited") ? 50 : Math.min(50, r.optInt("credits") / cost);
            LinearLayout card = k.paper(), heading = k.row(), label = k.column();
            card.setTag("draw-card-" + g.optString("kind"));
            heading.addView(
                k.image(
                    "/thumbs/" + g.optString("img") + ".jpg",
                    shortDraw ? 24 : compact() ? 48 : 88,
                    shortDraw ? 24 : compact() ? 48 : 88));
            TextView name = h.text(g.optString("label") + " 뽑기", 18);
            name.setTypeface(h.headline);
            if (shortDraw) {
              card.setPadding(h.dp(8), h.dp(2), h.dp(8), h.dp(2));
              name.setPadding(h.dp(2), 0, h.dp(2), 0);
              name.setSingleLine(true);
              heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
              TextView costLabel = k.note(cost + "장/회");
              costLabel.setPadding(0, 0, 0, 0);
              costLabel.setSingleLine(true);
              heading.addView(costLabel);
            } else {
              label.addView(name);
              label.addView(k.note("1회 · " + cost + "장  ›"));
              heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            }
            heading.setContentDescription(g.optString("label") + " 뽑기 · 확률과 보장 보기");
            heading.setOnClickListener(v -> gachaGuide(g, r));
            card.addView(heading);
            int[] amount = {1};
            TextView total = k.note("");
            total.setTag("draw-total-" + g.optString("kind"));
            total.setGravity(Gravity.CENTER);
            if (shortDraw) total.setPadding(0, 0, 0, 0);
            Button go =
                k.primary(
                    "u-ticket",
                    "1회 뽑기",
                    () ->
                        h.confirm(
                            g.optString("label")
                                + " "
                                + amount[0]
                                + "회 · 뽑기권 "
                                + cost * amount[0]
                                + "장을 사용할까요?",
                            () ->
                                performDraw(
                                    "draw",
                                    MainActivity.obj(
                                        "kind", g.optString("kind"), "times", amount[0]),
                                    "draw")));
            go.setTag("draw-submit-" + g.optString("kind"));
            k.compactQuantity(
                card,
                limit,
                n -> {
                  amount[0] = n;
                  total.setText(n + "회 · 총 " + cost * n + "장");
                  go.setText(n + "회 뽑기");
                });
            quantityLimit(card, limit);
            card.getChildAt(card.getChildCount() - 1)
                .setTag("draw-quantity-" + g.optString("kind"));
            card.addView(total);
            go.setEnabled(limit > 0);
            if (limit <= 0) {
              go.setText("뽑기권 부족");
              total.setText("필요 " + cost + "장 · 보유 " + r.optInt("credits") + "장");
            }
            card.addView(go, new LinearLayout.LayoutParams(-1, h.dp(48)));
            JSONObject pity = r.optJSONObject("pity");
            TextView hint =
                k.note(
                    g.optString("kind").equals("HIGH") && pity != null
                        ? pity.optString("tierName") + " 보장까지 " + pity.optInt("left") + "회"
                        : "흔함·안흔함 포함");
            hint.setGravity(Gravity.CENTER);
            if (!shortDraw) card.addView(hint);
            LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -1, 1);
            slot.setMargins(h.dp(3), h.dp(4), h.dp(3), 0);
            cards.addView(k.viewport(g.optString("label") + " 뽑기", card), slot);
          }
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
    reward.setTag("ticket-reward-body");
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
        .setScrollBody(reward)
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
    if (!again || drawFlow == null) {
      drawFlow = new NativeAcquisitionFlow(h, "draw", again ? null : drawBefore);
      drawBefore = null;
    }
    acquisitionFlow = drawFlow;
    drawFlow.reopen = () -> reveal(result, next, true);
    h.base("뽑기 결과", "drawResult");
    h.root.addView(
        new NativeReveal(h, result, next, again, drawFlow),
        new android.widget.FrameLayout.LayoutParams(-1, -1));
  }

  void performDraw(String endpoint, JSONObject body, String target) {
    if (h.busy) return;
    drawBefore = NativeAcquisitionFlow.keys(h.state);
    h.mutate(endpoint, body, target);
  }

  void resumeAcquisition() {
    if (acquisitionFlow != null
        && acquisitionFlow.reopen != null
        && acquisitionFlow.account.equals(h.api.token())) acquisitionFlow.reopen.run();
    else h.load("draw");
  }

  String acquisitionOrigin() {
    return acquisitionFlow == null ? "draw" : acquisitionFlow.origin;
  }

  void craft(boolean mix) {
    h.base("조합", mix ? "mix" : "craft");
    LinearLayout tabs = k.column();
    k.tabs(tabs, new String[] {"조합식", "재료로 섞기"}, mix ? 1 : 0, i -> craft(i == 1));
    h.page.addView(tabs, h.page.getChildCount() - 1);
    LinearLayout[] cols = k.split(.25f);
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
    JSONArray recipes = h.list(data, "recipes");
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          cols[0].removeAllViews();
          JSONObject current = null;
          for (int i = 0; i < recipes.length(); i++) {
            JSONObject recipe = recipes.optJSONObject(i);
            if (recipe.optString("id").equals(m.recipeId)) current = recipe;
          }
          if (current == null && recipes.length() > 0) {
            current = recipes.optJSONObject(0);
            m.recipeId = current.optString("id");
          }
          Button select = k.action(null, "조합식 선택  ›", () -> recipePicker(recipes, paint[0]));
          select.setTag("craft-recipe-picker");
          cols[0].addView(select, new LinearLayout.LayoutParams(-1, h.dp(48)));
          cols[0].addView(k.note(recipes.length() + "개 조합식 · 이름·재료 검색"));
          cols[0].addView(
              k.action(
                  null,
                  "조합 안내",
                  () -> {
                    LinearLayout guide = k.paper();
                    guide.addView(k.note("재료를 눌러 얻는 곳을 확인해요. 보호·탐험 중인 갈매미는 재료에서 제외해요."));
                    new GameDialog(h)
                        .setTitle("조합 안내")
                        .setScrollBody(guide)
                        .setPositiveButton("닫기", (d, w) -> {})
                        .show();
                  }),
              new LinearLayout.LayoutParams(-1, h.dp(48)));
          if (current == null) {
            cols[1].removeAllViews();
            cols[1].addView(k.notice("아직 조합식이 없어요."));
            craftActions.removeAllViews();
            return;
          }
          recipe(cols[1], current);
        };
    paint[0].run();
  }

  void recipePicker(JSONArray recipes, Runnable repaint) {
    LinearLayout content = k.column(), controls = k.column(), list = k.column();
    content.addView(controls);
    content.addView(list);
    android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
    Runnable[] filter = new Runnable[1];
    filter[0] =
        () -> {
          list.removeAllViews();
          int found = 0;
          for (int i = 0; i < recipes.length(); i++) {
            JSONObject recipe = recipes.optJSONObject(i);
            String names = recipe.optString("name");
            JSONArray materials = h.list(recipe, "materials");
            for (int j = 0; j < materials.length(); j++)
              names += " " + materials.optJSONObject(j).optString("name");
            if ((m.craftOnly && !recipe.optBoolean("ready"))
                || !names
                    .toLowerCase(Locale.KOREA)
                    .contains(m.recipeQuery.toLowerCase(Locale.KOREA))) continue;
            found++;
            LinearLayout row = k.paper(), line = k.row();
            line.addView(k.image("/thumbs/" + recipe.optString("img") + ".jpg", 56, 56));
            line.addView(
                h.text(
                    recipe.optString("name")
                        + "\n"
                        + recipe.optString("tierName")
                        + (recipe.optBoolean("isNew") ? " · 처음" : "")
                        + " · "
                        + (recipe.optBoolean("ready") ? recipe.optInt("max") + "개 가능" : "재료 부족"),
                    16),
                new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(line);
            row.setSelected(recipe.optString("id").equals(m.recipeId));
            row.setContentDescription(recipe.optString("name") + " 조합식 선택");
            row.setOnClickListener(
                v -> {
                  if (h.busy) return;
                  m.recipeId = recipe.optString("id");
                  dialog[0].dismiss();
                  repaint.run();
                });
            list.addView(row);
          }
          if (found == 0) list.addView(k.notice("찾는 조합식이 없어요. 필터나 검색어를 바꿔 보세요."));
        };
    k.search(
        controls,
        "이름·재료로 찾기",
        m.recipeQuery,
        value -> {
          m.recipeQuery = value;
          filter[0].run();
        });
    Button only = k.action(null, (m.craftOnly ? "✓ " : "") + "만들 수 있는 것만", null);
    only.setOnClickListener(
        v -> {
          m.craftOnly = !m.craftOnly;
          only.setText((m.craftOnly ? "✓ " : "") + "만들 수 있는 것만");
          filter[0].run();
        });
    controls.addView(only, new LinearLayout.LayoutParams(-1, h.dp(48)));
    filter[0].run();
    dialog[0] =
        new GameDialog(h)
            .setTitle("조합식 선택")
            .setMaxWidthDp(900)
            .setScrollBody(content)
            .setNegativeButton("닫기", (d, w) -> {})
            .show();
  }

  void recipe(LinearLayout parent, JSONObject r) {
    parent.removeAllViews();
    LinearLayout panel = k.paper();
    parent.addView(panel, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout heading = k.row();
    TextView resultName = h.text(r.optString("name"), 20);
    resultName.setTypeface(h.headline);
    heading.addView(resultName, new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(k.note(r.optString("tierName") + (r.optBoolean("isNew") ? " · 처음" : "")));
    panel.addView(heading);
    JSONArray mats = h.list(r, "materials");
    boolean fixedChain = mats.length() <= 2;
    LinearLayout flow = k.row();
    flow.setGravity(Gravity.CENTER_VERTICAL);
    if (fixedChain) panel.addView(flow, new LinearLayout.LayoutParams(-1, 0, 1));
    else {
      HorizontalScrollView strip = new HorizontalScrollView(h);
      strip.setHorizontalScrollBarEnabled(true);
      strip.addView(flow);
      panel.addView(strip, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    ArrayList<TextView> counts = new ArrayList<>();
    for (int j = 0; j < mats.length(); j++) {
      JSONObject material = mats.optJSONObject(j);
      if (j > 0) flow.addView(h.text("＋", 18));
      LinearLayout slot = materialSlot(material, false, fixedChain);
      counts.add((TextView) slot.getChildAt(2));
      flow.addView(slot);
    }
    flow.addView(h.text("→", 22));
    flow.addView(materialSlot(r, true, fixedChain));
    TextView availability =
        k.note(
            r.optBoolean("ready")
                ? "최대 " + r.optInt("max") + "개 · 재료를 눌러 얻는 곳 보기"
                : "재료 부족 · 붉은 재료를 눌러 얻는 곳 보기");
    if (!compact()) panel.addView(availability);
    heading.setContentDescription(r.optString("name") + " 조합 정보 보기");
    heading.setOnClickListener(
        v -> {
          LinearLayout details = k.paper();
          details.addView(
              k.note(r.optString("tierName") + (r.optBoolean("isNew") ? " · 처음 발견" : "")));
          details.addView(k.note(availability.getText().toString()));
          details.addView(k.note("보호·탐험 중인 갈매미는 조합 재료에서 제외해요. 재료를 누르면 얻는 곳을 볼 수 있어요."));
          new GameDialog(h)
              .setTitle(r.optString("name"))
              .setScrollBody(details)
              .setPositiveButton("닫기", (d, w) -> {})
              .show();
        });
    int[] times = {1};
    Button make =
        k.primary(
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
    craftActions.addView(make, new LinearLayout.LayoutParams(h.dp(220), h.dp(48)));
  }

  LinearLayout materialSlot(JSONObject unit, boolean result) {
    return materialSlot(unit, result, false);
  }

  LinearLayout materialSlot(JSONObject unit, boolean result, boolean bounded) {
    LinearLayout slot = k.paper();
    if (compact()) slot.setPadding(h.dp(4), h.dp(2), h.dp(4), h.dp(2));
    slot.setGravity(Gravity.CENTER_HORIZONTAL);
    ImageView art =
        k.image(
            "/thumbs/" + unit.optString("img") + ".jpg",
            compact() ? 48 : 100,
            compact() ? 48 : 100);
    art.setTag(result ? "craft-result-image" : "craft-material-image-" + unit.optString("id"));
    slot.addView(art);
    TextView name = h.text(unit.optString("name"), compact() ? 14 : 16);
    name.setPadding(0, compact() ? 0 : h.dp(4), 0, compact() ? 0 : h.dp(4));
    name.setSingleLine(!bounded);
    if (bounded) {
      name.setMaxLines(2);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }
    name.setGravity(Gravity.CENTER);
    int nameWidth = (int) Math.ceil(name.getPaint().measureText(unit.optString("name")));
    slot.setLayoutParams(
        new LinearLayout.LayoutParams(
            Math.max(
                h.dp(compact() ? 104 : 144),
                nameWidth + slot.getPaddingLeft() + slot.getPaddingRight() + h.dp(2)),
            -2));
    if (bounded) slot.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    slot.addView(name);
    TextView count =
        k.note(
            result
                ? unit.optString("tierName")
                : unit.optInt("usable") + " / " + unit.optInt("need"));
    count.setTextSize(h.uiText(14));
    if (compact()) count.setPadding(0, 0, 0, 0);
    count.setTag(result ? "craft-result-tier" : "craft-material-count-" + unit.optString("id"));
    count.setGravity(Gravity.CENTER);
    slot.addView(count);
    if (result && bounded) {
      slot.setContentDescription(unit.optString("name") + " · " + unit.optString("tierName"));
      slot.setOnClickListener(
          v -> h.message(unit.optString("name") + " · " + unit.optString("tierName")));
    }
    if (!result) {
      boolean missing = unit.optInt("usable") < unit.optInt("need");
      if (missing) {
        slot.setBackground(new GamePaperDrawable(h, 0xffb44335));
        count.setTextColor(0xffb44335);
      }
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
          Button choose = k.action(null, "재료 선택  ›", () -> mixMaterialPicker(pool, paint[0]));
          choose.setEnabled(m.mixDraft.size() < 6);
          cols[0].addView(choose, new LinearLayout.LayoutParams(-1, h.dp(48)));
          cols[0].addView(k.note("2~6마리로 섞어요.\n맞지 않는 조합은 재료가 남아요."));
          cols[0].addView(
              k.action(
                  null,
                  "재료 사용 안내",
                  () -> {
                    LinearLayout guide = k.paper();
                    guide.addView(
                        k.note(
                            "보호·탐험 중인 갈매미는 제외해요. 선택한 재료를 누르면 한 마리씩 뺄 수 있어요. 같은 종류를 여러 마리 넣을 수도"
                                + " 있어요."));
                    new GameDialog(h)
                        .setTitle("재료로 섞기")
                        .setScrollBody(guide)
                        .setPositiveButton("닫기", (d, w) -> {})
                        .show();
                  }),
              new LinearLayout.LayoutParams(-1, h.dp(48)));
          cols[1].addView(k.section("선택한 재료 · " + m.mixDraft.size() + " / 6"));
          HorizontalScrollView strip = new HorizontalScrollView(h);
          strip.setHorizontalScrollBarEnabled(true);
          LinearLayout slots = k.row();
          strip.addView(slots);
          cols[1].addView(strip, new LinearLayout.LayoutParams(-1, 0, 1));
          for (int i = 0; i < 6; i++) {
            int index = i;
            LinearLayout slot = k.paper();
            slot.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams box =
                new LinearLayout.LayoutParams(h.dp(compact() ? 106 : 150), -1);
            box.setMargins(h.dp(2), 0, h.dp(2), 0);
            slots.addView(slot, box);
            if (i >= m.mixDraft.size()) {
              slot.setTag("mix-empty-slot-" + i);
              slot.addView(h.text("＋", 28));
              slot.addView(k.note("재료 추가"));
              slot.setContentDescription("재료 " + (i + 1) + " 추가 · 재료 선택 열기");
              slot.setOnClickListener(
                  v -> {
                    if (!h.busy) mixMaterialPicker(pool, paint[0]);
                  });
            } else {
              String id = m.mixDraft.get(i);
              JSONObject unit = new JSONObject();
              for (int j = 0; j < pool.length(); j++)
                if (id.equals(pool.optJSONObject(j).optString("id"))) unit = pool.optJSONObject(j);
              slot.setTag("mix-material-" + id);
              slot.addView(
                  k.image(
                      "/thumbs/" + unit.optString("img") + ".jpg",
                      compact() ? 48 : 88,
                      compact() ? 48 : 88));
              TextView name = h.text(unit.optString("name"), 14);
              name.setGravity(Gravity.CENTER);
              name.setSingleLine(true);
              int fullNameWidth =
                  (int) Math.ceil(name.getPaint().measureText(unit.optString("name")))
                      + name.getPaddingLeft()
                      + name.getPaddingRight()
                      + slot.getPaddingLeft()
                      + slot.getPaddingRight();
              box.width = Math.max(box.width, fullNameWidth);
              slot.setLayoutParams(box);
              slot.addView(name);
              slot.addView(k.note("빼기 ×"));
              slot.setContentDescription(unit.optString("name") + " 재료에서 빼기");
              slot.setOnClickListener(
                  v -> {
                    if (!h.busy) {
                      m.mixDraft.remove(index);
                      paint[0].run();
                    }
                  });
            }
          }
          LinearLayout actions = k.row();
          Button clear =
              k.action(
                  null,
                  "비우기",
                  () -> {
                    m.mixDraft.clear();
                    paint[0].run();
                  });
          clear.setEnabled(!m.mixDraft.isEmpty());
          h.cell(actions, clear);
          Button make =
              k.primary(
                  "u-emblem",
                  m.mixDraft.size() >= 2 ? m.mixDraft.size() + "마리 섞기" : "2마리 이상 선택",
                  () -> {
                    JSONArray materials = new JSONArray();
                    for (String id : m.mixDraft)
                      for (int j = 0; j < pool.length(); j++)
                        if (id.equals(pool.optJSONObject(j).optString("id")))
                          materials.put(pool.optJSONObject(j));
                    h.confirm(
                        "선택한 재료로 섞을까요?",
                        () ->
                            performCraft(
                                "mix",
                                MainActivity.obj("ids", MainActivity.arr(m.mixDraft)),
                                null,
                                materials));
                  });
          make.setEnabled(m.mixDraft.size() >= 2);
          h.cell(actions, make);
          craftActions.removeAllViews();
          craftActions.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        };
    paint[0].run();
  }

  void mixMaterialPicker(JSONArray pool, Runnable repaint) {
    LinearLayout panel = k.paper();
    panel.addView(h.text("재료 추가", 22));
    panel.addView(k.note("선택 " + m.mixDraft.size() + " / 6마리 · 사용할 수 있는 재료만 보여요."));
    android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
    int available = 0;
    for (int i = 0; i < pool.length(); i++) {
      JSONObject unit = pool.optJSONObject(i);
      String id = unit.optString("id");
      int left = unit.optInt("usable") - Collections.frequency(m.mixDraft, id);
      if (left <= 0) continue;
      available++;
      Button add =
          k.action(
              null,
              unit.optString("name") + " · 남은 " + left + "마리",
              () -> {
                if (h.busy || m.mixDraft.size() >= 6) return;
                m.mixDraft.add(id);
                dialog[0].dismiss();
                repaint.run();
              });
      add.setTag("mix-picker-material-" + id);
      panel.addView(add, new LinearLayout.LayoutParams(-1, h.dp(48)));
    }
    if (available == 0) panel.addView(k.note("사용할 수 있는 재료가 없어요. 보호·탐험 중인 갈매미는 재료로 쓰지 않아요."));
    dialog[0] =
        new GameDialog(h).setView(k.scroll(panel)).setNegativeButton("닫기", (d, w) -> {}).show();
  }

  void performCraft(String endpoint, JSONObject body, JSONObject result, JSONArray materials) {
    if (h.busy) return;
    Set<String> before = NativeAcquisitionFlow.keys(h.state);
    String origin = endpoint.equals("mix") ? "mix" : "craft";
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
          NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, origin, before);
          String resultId =
              result == null
                  ? (value instanceof JSONObject ? ((JSONObject) value).optString("id") : "")
                  : result.optString("id");
          JSONObject card =
              result == null && !resultId.isEmpty()
                  ? MainActivity.obj("id", resultId, "name", "조합한 갈매미", "img", resultId)
                  : result;
          NativeForge overlay = forgeResult(materials, card, fail, text, flow, true);
          h.request(
              "native/state",
              null,
              (fresh, error) -> {
                h.state = fresh;
                h.stateUpdated();
                JSONArray owned = h.list(h.me(), "units");
                for (int i = 0; i < owned.length(); i++)
                  if (resultId.equals(owned.optJSONObject(i).optString("id")))
                    overlay.updateResult(owned.optJSONObject(i));
              });
        });
  }

  NativeForge forgeResult(
      JSONArray materials,
      JSONObject result,
      boolean fail,
      String text,
      NativeAcquisitionFlow flow,
      boolean animate) {
    acquisitionFlow = flow;
    h.base("조합 결과", "craftResult");
    NativeForge overlay =
        new NativeForge(
            h, materials, ownedResult(result), fail, text, () -> h.load(flow.origin), flow);
    flow.reopen = () -> forgeResult(materials, overlay.result, fail, text, flow, false);
    h.root.addView(overlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
    if (!animate) overlay.showResult(false);
    return overlay;
  }

  JSONObject ownedResult(JSONObject result) {
    if (result == null) return null;
    JSONArray owned = h.list(h.me(), "units");
    for (int i = 0; i < owned.length(); i++) {
      JSONObject unit = owned.optJSONObject(i);
      if (unit != null && result.optString("id").equals(unit.optString("id"))) return unit;
    }
    return result;
  }

  void shop() {
    h.base("상점", "shop");
    m.fetch(
        "me",
        r -> {
          JSONObject prices = r.optJSONObject("prices"), rules = r.optJSONObject("itemRules");
          if (prices == null) return;
          int maxStack = rules == null ? 5 : rules.optInt("maxStack", 5);
          ArrayList<JSONObject> offers = new ArrayList<>();
          JSONObject tickets = prices.optJSONObject("tickets"),
              peanut = prices.optJSONObject("peanutTickets");
          if (tickets != null)
            offers.add(
                shopOffer(
                    "u-ticket",
                    "뽑기권 " + tickets.optInt("amount") + "장",
                    "새우gang으로 뽑기권을 사요.",
                    tickets.optInt("snack"),
                    Math.min(50, r.optInt("snack") / Math.max(1, tickets.optInt("snack"))),
                    "새우gang",
                    "shop",
                    MainActivity.obj("item", "tickets"),
                    "새우gang 부족",
                    ""));
          if (peanut != null)
            offers.add(
                shopOffer(
                    "u-peanut",
                    "뽑기권 " + peanut.optInt("amount") + "장",
                    "땅콩으로 뽑기권을 바꿔요.",
                    1,
                    Math.min(50, r.optInt("peanut")),
                    "땅콩",
                    "shop",
                    MainActivity.obj("item", "peanutTickets"),
                    "땅콩 부족",
                    ""));
          JSONArray defs = h.list(r, "itemDefs"), owned = h.list(r, "items");
          for (int i = 0; i < defs.length(); i++) {
            JSONObject d = defs.optJSONObject(i);
            int count = 0;
            for (int j = 0; j < owned.length(); j++)
              if (d.optString("id").equals(owned.optJSONObject(j).optString("id")))
                count = owned.optJSONObject(j).optInt("count");
            int price = d.optInt("price"),
                limit =
                    Math.max(0, Math.min(maxStack - count, r.optInt("snack") / Math.max(1, price)));
            offers.add(
                shopOffer(
                    d.optString("id"),
                    d.optString("name"),
                    d.optString("desc"),
                    price,
                    limit,
                    "새우gang",
                    "items/buy",
                    MainActivity.obj("id", d.optString("id")),
                    count >= maxStack ? "보유 한도" : "새우gang 부족",
                    "보유 " + count + " / " + maxStack));
          }
          LinearLayout[] cols = k.split(.30f);
          Runnable[] paint = new Runnable[1];
          paint[0] =
              () -> {
                cols[0].removeAllViews();
                cols[1].removeAllViews();
                shopActions.clear();
                shopCards.clear();
                JSONObject selected = null;
                for (JSONObject offer : offers)
                  if (shopFocus.equals(offer.optString("key"))) selected = offer;
                if (selected == null) {
                  for (JSONObject offer : offers)
                    if (offer.optInt("limit") > 0) {
                      selected = offer;
                      break;
                    }
                }
                if (selected == null && !offers.isEmpty()) selected = offers.get(0);
                cols[0].addView(
                    k.action(null, "상품 선택  ›", () -> shopPicker(offers, paint[0])),
                    new LinearLayout.LayoutParams(-1, h.dp(48)));
                cols[0].addView(
                    k.notice("상품 " + offers.size() + "종 · 대결 아이템 종류별 " + maxStack + "개까지"));
                cols[0].addView(
                    k.action(
                        null, "교환·판매 안내", () -> h.message("갈매미 교환·팔기는 카톡 채널에서 ‘상점’을 입력해 주세요.")),
                    new LinearLayout.LayoutParams(-1, h.dp(48)));
                if (selected == null) {
                  cols[1].addView(k.notice("판매 중인 상품이 없어요."));
                  return;
                }
                shopFocus = selected.optString("key");
                JSONObject o = selected;
                cols[1].addView(
                    ware(
                        o.optString("icon"),
                        o.optString("title"),
                        o.optString("desc"),
                        o.optInt("price"),
                        o.optInt("limit"),
                        o.optString("currency"),
                        o.optString("endpoint"),
                        o.optJSONObject("params"),
                        o.optString("blocked"),
                        o.optString("holding")));
                if (o.optInt("limit") > 0) focusShopOffer();
              };
          paint[0].run();
        });
  }

  JSONObject shopOffer(
      String icon,
      String title,
      String desc,
      int price,
      int limit,
      String currency,
      String endpoint,
      JSONObject params,
      String blocked,
      String holding) {
    return MainActivity.obj(
        "icon",
        icon,
        "title",
        title,
        "desc",
        desc,
        "price",
        price,
        "limit",
        limit,
        "currency",
        currency,
        "endpoint",
        endpoint,
        "params",
        params,
        "blocked",
        blocked,
        "holding",
        holding,
        "key",
        endpoint + ":" + params.optString("id", params.optString("item")));
  }

  void shopPicker(List<JSONObject> offers, Runnable repaint) {
    LinearLayout content = k.column();
    android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
    for (JSONObject offer : offers) {
      LinearLayout item = k.paper(), line = k.row();
      line.addView(k.image("/duel/icons/" + offer.optString("icon") + ".png", 48, 48));
      line.addView(
          h.text(
              offer.optString("title")
                  + "\n"
                  + offer.optString("currency")
                  + " "
                  + offer.optInt("price")
                  + "개"
                  + (offer.optString("holding").isEmpty()
                      ? ""
                      : " · " + offer.optString("holding")),
              16),
          new LinearLayout.LayoutParams(0, -2, 1));
      line.addView(h.text("›", 22));
      item.addView(line);
      item.setSelected(shopFocus.equals(offer.optString("key")));
      item.setContentDescription(offer.optString("title") + " 상품 선택");
      item.setOnClickListener(
          v -> {
            if (!h.busy) {
              shopFocus = offer.optString("key");
              dialog[0].dismiss();
              repaint.run();
            }
          });
      content.addView(item);
    }
    dialog[0] =
        new GameDialog(h)
            .setTitle("상품 선택")
            .setMaxWidthDp(900)
            .setScrollBody(content)
            .setNegativeButton("닫기", (d, w) -> {})
            .show();
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
    return ware(icon, title, desc, price, limit, currency, endpoint, params, blocked, "");
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
      String blocked,
      String holding) {
    String offerKey = endpoint + ":" + params.optString("id", params.optString("item"));
    LinearLayout card = k.paper();
    card.setTag("shop-offer-" + offerKey);
    LinearLayout nameLine = k.row();
    nameLine.addView(
        k.image("/duel/icons/" + icon + ".png", compact() ? 56 : 88, compact() ? 56 : 88));
    TextView name = h.text(title, compact() ? 18 : 22);
    name.setTypeface(h.headline);
    name.setTextLocale(Locale.KOREA);
    name.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      name.setLineBreakWordStyle(
          android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
    LinearLayout offer = k.column();
    offer.addView(name);
    TextView totalPrice = k.note(currency + " " + price + "개");
    totalPrice.setTag("shop-total-" + icon);
    totalPrice.setTextSize(h.uiText(16));
    totalPrice.setTextColor(MainActivity.INK);
    totalPrice.setPadding(h.dp(8), h.dp(3), h.dp(8), h.dp(4));
    offer.addView(totalPrice);
    nameLine.addView(offer, new LinearLayout.LayoutParams(0, -2, 1));
    card.addView(nameLine);
    if (!holding.isEmpty()) {
      TextView stock = k.note(holding);
      stock.setTag("shop-stock-" + icon);
      stock.setSingleLine(true);
      card.addView(stock);
    }
    Button description =
        k.action(
            null,
            "상품 설명  ›",
            () -> {
              LinearLayout details = k.paper();
              details.addView(k.note(desc));
              details.addView(
                  k.note(
                      holding.isEmpty()
                          ? "1개당 " + currency + " " + price + "개"
                          : holding + " · 1개당 " + currency + " " + price + "개"));
              new GameDialog(h)
                  .setTitle(title)
                  .setScrollBody(details)
                  .setPositiveButton("닫기", (d, w) -> {})
                  .show();
            });
    card.addView(description, new LinearLayout.LayoutParams(-1, h.dp(48)));
    int[] amount = {1};
    Button buy =
        k.action(
            icon,
            "구매하기",
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
    k.compactQuantity(
        card,
        limit,
        n -> {
          amount[0] = n;
          totalPrice.setText(currency + " " + price * n + "개");
          buy.setText(n > 1 ? n + "개 구매" : "구매하기");
        });
    quantityLimit(card, limit);
    buy.setEnabled(limit > 0);
    if (limit <= 0) buy.setText(blocked);
    buy.setBackground(new GameButtonDrawable(h, GameButtonDrawable.PAPER));
    buy.setTextSize(h.uiText(18));
    card.addView(buy, new LinearLayout.LayoutParams(-1, h.dp(48)));
    shopActions.put(offerKey, buy);
    shopCards.put(offerKey, card);
    card.setOnClickListener(
        v -> {
          if (h.busy) return;
          shopFocus = offerKey;
          focusShopOffer();
        });
    return card;
  }

  // 구매는 기존 확인창으로 처리하고, 선택한 보급품 하나만 강조한다.
  void focusShopOffer() {
    if (!shopActions.containsKey(shopFocus) || !shopActions.get(shopFocus).isEnabled()) {
      shopFocus = "";
      for (Map.Entry<String, Button> entry : shopActions.entrySet())
        if (entry.getValue().isEnabled()) {
          shopFocus = entry.getKey();
          break;
        }
    }
    for (Map.Entry<String, Button> entry : shopActions.entrySet()) {
      boolean selected = entry.getKey().equals(shopFocus);
      entry
          .getValue()
          .setBackground(
              new GameButtonDrawable(
                  h, selected ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER));
      LinearLayout card = shopCards.get(entry.getKey());
      card.setSelected(selected);
      card.setBackground(new GamePaperDrawable(h));
      // 부모의 선택 표시는 구매 확정 버튼의 주요 행동색을 바꾸지 않는다.
      entry.getValue().setDuplicateParentStateEnabled(false);
      entry.getValue().setSelected(false);
    }
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
    LinearLayout[] cols = k.split(.35f);
    LinearLayout footer = k.row();
    footer.setBackground(new GamePaperDrawable(h));
    footer.setPadding(h.dp(6), h.dp(4), h.dp(6), h.dp(4));
    TextView status = k.note("");
    status.setTextColor(MainActivity.INK);
    status.setTag("expedition-save-status");
    footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
    Runnable[] paint = new Runnable[1];
    Button save =
        k.action(
            "u-emblem",
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
        k.primary(
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
    footer.addView(save, new LinearLayout.LayoutParams(h.dp(152), h.dp(48)));
    footer.addView(go, new LinearLayout.LayoutParams(h.dp(176), h.dp(48)));
    k.footer(footer);
    paint[0] =
        () -> {
          cols[0].removeAllViews();
          cols[1].removeAllViews();
          LinearLayout summary = k.paper();
          summary.setTag("expedition-summary");
          summary.addView(h.text("탐험대 " + h.expeditionDraft.size() + " / " + max, 20));
          compactExpeditionEstimate(summary, h.expeditionDraft);
          summary.addView(
              k.action(null, "보상·시너지  ›", () -> expeditionDetails(h.expeditionDraft)),
              new LinearLayout.LayoutParams(-1, h.dp(48)));
          cols[0].addView(summary);
          Button choose = k.action(null, "갈매미 선택  ›", () -> expeditionPicker(max, paint[0]));
          choose.setTag("expedition-party-picker");
          cols[0].addView(choose, new LinearLayout.LayoutParams(-1, h.dp(48)));
          LinearLayout tools = k.row();
          Button clear =
              k.action(
                  null,
                  "비우기",
                  () -> {
                    h.expeditionDraft.clear();
                    paint[0].run();
                  });
          clear.setEnabled(!h.expeditionDraft.isEmpty());
          tools.addView(clear, new LinearLayout.LayoutParams(0, h.dp(48), 1));
          JSONArray previous = h.list(h.me(), "previousParty");
          if (previous.length() > 0)
            tools.addView(
                k.action(
                    null,
                    "최근 편성",
                    () -> {
                      h.expeditionDraft.clear();
                      for (int i = 0;
                          i < previous.length() && h.expeditionDraft.size() < max;
                          i++) {
                        String id = previous.optString(i);
                        JSONObject unit = find(id);
                        if (unit.optBoolean("top")
                            && Collections.frequency(h.expeditionDraft, id)
                                < unit.optInt("have") - unit.optInt("away"))
                          h.expeditionDraft.add(id);
                      }
                      if (h.expeditionDraft.size() < previous.length())
                        Toast.makeText(h, "지금 보낼 수 있는 갈매미만 불러왔어요.", Toast.LENGTH_SHORT).show();
                      paint[0].run();
                    }),
                new LinearLayout.LayoutParams(0, h.dp(48), 1));
          cols[1].addView(tools);
          partyStrip(cols[1], h.expeditionDraft, true, paint[0]);
          if (h.expeditionDraft.isEmpty())
            cols[1].addView(k.notice("갈매미를 선택해 탐험대를 꾸려 보세요. 같은 시너지끼리 보내면 보너스!"));
          h.expeditionDirty = partyChanged();
          save.setBackground(
              new GameButtonDrawable(
                  h, h.expeditionDirty ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER));
          go.setBackground(
              new GameButtonDrawable(
                  h, h.expeditionDirty ? GameButtonDrawable.PAPER : GameButtonDrawable.PRIMARY));
          save.setEnabled(h.expeditionDirty);
          go.setEnabled(!h.expeditionDraft.isEmpty() && !h.expeditionDirty);
          status.setText(h.expeditionDirty ? "편성 변경 · 저장 필요" : "저장된 편성");
        };
    paint[0].run();
  }

  void expeditionPicker(int max, Runnable repaint) {
    LinearLayout list = k.column();
    TextView summary = h.text("", 18);
    LinearLayout header = k.paper();
    header.addView(summary);
    header.addView(k.note("최상위 갈매미만 선택해요. 보호된 갈매미도 함께할 수 있어요."));
    Runnable[] paint = new Runnable[1];
    paint[0] =
        () -> {
          summary.setText("선택 " + h.expeditionDraft.size() + " / " + max);
          list.removeAllViews();
          JSONArray units = h.list(h.me(), "units");
          int available = 0;
          for (int i = 0; i < units.length(); i++) {
            JSONObject unit = units.optJSONObject(i);
            if (!unit.optBoolean("top") || unit.optInt("have") - unit.optInt("away") <= 0) continue;
            available++;
            String id = unit.optString("id");
            int picked = Collections.frequency(h.expeditionDraft, id),
                left = unit.optInt("have") - unit.optInt("away") - picked;
            LinearLayout row = k.paper(), line = k.row();
            line.addView(k.image("/thumbs/" + id + ".jpg", 56, 56));
            line.addView(
                h.text(
                    unit.optString("name")
                        + "\n"
                        + synergyLabel(unit.optString("synergy"))
                        + " · 남은 "
                        + left
                        + " · 선택 "
                        + picked,
                    16),
                new LinearLayout.LayoutParams(0, -2, 1));
            Button remove =
                k.action(
                    null,
                    "−",
                    () -> {
                      int index = h.expeditionDraft.lastIndexOf(id);
                      if (index >= 0) h.expeditionDraft.remove(index);
                      repaint.run();
                      paint[0].run();
                    });
            Button add =
                k.action(
                    null,
                    "＋",
                    () -> {
                      if (left > 0 && h.expeditionDraft.size() < max) h.expeditionDraft.add(id);
                      repaint.run();
                      paint[0].run();
                    });
            remove.setEnabled(picked > 0);
            add.setEnabled(left > 0 && h.expeditionDraft.size() < max);
            remove.setTag("expedition-remove-" + id);
            add.setTag("expedition-add-" + id);
            line.addView(remove, new LinearLayout.LayoutParams(h.dp(64), h.dp(48)));
            line.addView(add, new LinearLayout.LayoutParams(h.dp(64), h.dp(48)));
            row.addView(line);
            list.addView(row);
          }
          if (available == 0) {
            list.addView(k.notice("보낼 수 있는 최상위 갈매미가 없어요. 전설을 모아 조합해 보세요."));
            list.addView(k.action("u-emblem", "조합식 보기", () -> h.load("craft")));
          }
        };
    paint[0].run();
    new GameDialog(h)
        .setTitle("탐험 갈매미 선택")
        .setMaxWidthDp(900)
        .setFixedHeader(header)
        .setScrollBody(list)
        .setPositiveButton("선택 완료", (d, w) -> {})
        .show();
  }

  void partyStrip(LinearLayout parent, List<String> ids, boolean editable, Runnable repaint) {
    HorizontalScrollView strip = new HorizontalScrollView(h);
    strip.setHorizontalScrollBarEnabled(true);
    LinearLayout members = k.row();
    strip.addView(members);
    parent.addView(strip, new LinearLayout.LayoutParams(-1, 0, 1));
    for (int i = 0; i < ids.size(); i++) {
      int index = i;
      String id = ids.get(i);
      JSONObject unit = find(id);
      LinearLayout item = k.paper();
      item.setGravity(Gravity.CENTER);
      item.addView(k.image("/thumbs/" + id + ".jpg", compact() ? 56 : 100, compact() ? 56 : 100));
      TextView name = h.text(unit.optString("name"), 14);
      name.setGravity(Gravity.CENTER);
      name.setSingleLine(true);
      item.addView(name);
      item.addView(k.note(editable ? "빼기 ×" : "탐험대"));
      item.setContentDescription(unit.optString("name") + (editable ? " 탐험대에서 빼기" : " 탐험대"));
      if (editable)
        item.setOnClickListener(
            v -> {
              if (!h.busy) {
                h.expeditionDraft.remove(index);
                repaint.run();
              }
            });
      LinearLayout.LayoutParams slot =
          new LinearLayout.LayoutParams(
              Math.max(
                  h.dp(compact() ? 112 : 156),
                  (int) Math.ceil(name.getPaint().measureText(unit.optString("name")))
                      + name.getPaddingLeft()
                      + name.getPaddingRight()
                      + item.getPaddingLeft()
                      + item.getPaddingRight()),
              -1);
      slot.setMargins(h.dp(2), 0, h.dp(2), 0);
      members.addView(item, slot);
    }
  }

  void compactExpeditionEstimate(LinearLayout parent, List<String> ids) {
    JSONObject rules = economy().optJSONObject("expedition");
    if (rules == null || ids.isEmpty()) {
      parent.addView(k.note(ids.isEmpty() ? "갈매미를 선택해 주세요." : "보상·시너지에서 자세히 확인해요."));
      return;
    }
    ArrayList<EconomyPreview.Member> members = new ArrayList<>();
    for (String id : ids) members.add(new EconomyPreview.Member(id, find(id).optString("synergy")));
    EconomyPreview.Plan plan = EconomyPreview.estimate(previewRules(rules), members);
    parent.addView(k.note("예상 " + plan.minutes + "분 · 새우gang " + plan.snacks));
  }

  void expeditionDetails(List<String> ids) {
    LinearLayout details = k.paper();
    details.addView(k.note("항구 → 탐험지 → 귀환"));
    for (String id : ids)
      details.addView(
          k.note(find(id).optString("name") + " · " + synergyLabel(find(id).optString("synergy"))));
    showExpeditionEstimate(details, ids);
    details.addView(
        k.action(null, "시너지 안내", this::synergyGuide), new LinearLayout.LayoutParams(-1, h.dp(48)));
    new GameDialog(h)
        .setTitle("탐험 보상·시너지")
        .setScrollBody(details)
        .setPositiveButton("닫기", (d, w) -> {})
        .show();
  }

  void showTrip(JSONObject trip) {
    long start = time(trip.optString("startedAt")), end = time(trip.optString("endsAt"));
    LinearLayout panel = k.paper();
    h.body.addView(panel, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout heading = k.row();
    TextView title = h.text("", 22), remaining = k.note("");
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(remaining);
    panel.addView(heading);
    ProgressBar progress = new ProgressBar(h, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(1000);
    progress.setProgressTintList(android.content.res.ColorStateList.valueOf(JournalTheme.TEAL));
    panel.addView(progress, new LinearLayout.LayoutParams(-1, h.dp(12)));
    JSONArray ids = h.list(trip, "ids");
    ArrayList<String> party = new ArrayList<>();
    for (int i = 0; i < ids.length(); i++) party.add(ids.optString(i));
    partyStrip(panel, party, false, null);
    panel.addView(
        k.action(null, "보상·시너지  ›", () -> expeditionDetails(party)),
        new LinearLayout.LayoutParams(-1, h.dp(48)));
    Button action =
        k.primary(
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
    return h.compact();
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
    panel.setTag("material-guide-body");
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
    dialog.setScrollBody(panel).setPositiveButton("닫기", (d, w) -> {});
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

  void gachaGuide(JSONObject gacha, JSONObject draw) {
    LinearLayout panel = k.paper();
    panel.addView(h.text(gacha.optString("label") + " 뽑기 확률", 22));
    panel.addView(k.note("1회당 뽑기권 " + gacha.optInt("cost") + "장 · 낮은 등급도 나올 수 있어요."));
    JSONArray weights = h.list(gacha, "weights");
    for (int i = 0; i < weights.length(); i++) {
      JSONObject weight = weights.optJSONObject(i);
      LinearLayout row = k.row();
      row.addView(k.note(weight.optString("tierName")), new LinearLayout.LayoutParams(0, -2, 1));
      TextView percent = h.text(String.format(Locale.KOREA, "%.1f%%", weight.optDouble("w")), 17);
      percent.setSingleLine(true);
      percent.setPadding(h.dp(8), h.dp(5), h.dp(8), h.dp(5));
      row.addView(percent, new LinearLayout.LayoutParams(-2, -2));
      panel.addView(row);
    }
    JSONObject pity = draw.optJSONObject("pity");
    if (gacha.optString("kind").equals("HIGH") && pity != null)
      panel.addView(
          k.note(
              pity.optString("tierName") + " 보장까지 " + pity.optInt("left") + "회 · 고급 뽑기에만 누적돼요."));
    else panel.addView(k.note("이 뽑기는 보장 횟수를 누적하지 않아요."));
    new GameDialog(h).setView(k.scroll(panel)).setPositiveButton("확인", (d, w) -> {}).show();
  }

  void drawGuide(JSONObject draw) {
    LinearLayout panel = k.paper();
    panel.addView(h.text("확률·획득 보상", 22));
    panel.addView(
        k.note(
            "뽑기권은 " + draw.optDouble("cooldownHours") + "시간마다 " + draw.optInt("claim") + "장 받아요."));
    JSONObject event = draw.optJSONObject("event");
    if (event != null) panel.addView(k.note(event.optString("label")));
    panel.addView(k.note("중급·고급에서도 흔함·안흔함이 나와요. 보장은 고급 뽑기에만 적용돼요."));
    JSONArray offered = h.list(draw, "gachas");
    for (int i = 0; i < offered.length(); i++) {
      JSONObject gacha = offered.optJSONObject(i);
      panel.addView(
          k.action(null, gacha.optString("label") + " 확률 보기", () -> gachaGuide(gacha, draw)),
          new LinearLayout.LayoutParams(-1, h.dp(48)));
    }
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
    TextView synergy = k.note("시너지 · " + String.join(" · ", labels));
    synergy.setTextColor(MainActivity.INK);
    parent.addView(synergy);
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

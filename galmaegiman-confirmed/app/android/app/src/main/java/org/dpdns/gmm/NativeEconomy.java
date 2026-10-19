package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** 기존 모집·조합·매점·탐험 화면의 그림과 수량·선택 동작. */
final class NativeEconomy {
  final NativeMenus m;
  final MainActivity h;
  final MenuKit k;
  LinearLayout craftActions;
  Set<String> drawBefore;
  NativeAcquisitionFlow drawFlow, acquisitionFlow;
  String shopFocus = "";
  int recipePage, mixPage, crewPage, selectedCrewPage;
  Runnable recipeRepaint;
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
    h.base("모집", "draw");
    AppSkin.apply(h, "reception");
    m.fetch("draw", r -> {
      LinearLayout counter = economyBody("recruitment-counter");
      LinearLayout banner = k.row(), words = k.column();
      banner.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
      banner.setPadding(h.dp(8), 0, h.dp(8), 0);
      TextView credits = h.text("지원서 " + (r.optBoolean("unlimited") ? "무제한" : r.optInt("credits") + "장"), 15);
      TextView wait = h.text("", 13);
      credits.setPadding(h.dp(6), 0, h.dp(6), 0);
      wait.setPadding(h.dp(6), 0, h.dp(6), 0);
      words.addView(credits); words.addView(wait);
      banner.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
      Button claim = k.primary("u-ticket", "지원서 받기", this::claimTickets);
      claim.setTag("draw-claim");
      claim.setSingleLine(true);
      long ready = android.os.SystemClock.elapsedRealtime() + Math.max(0, r.optLong("wait"));
      tick(banner, () -> {
        long left = Math.max(0, ready - android.os.SystemClock.elapsedRealtime());
        claim.setEnabled(left == 0);
        wait.setText(left > 0 ? "다음 지급 " + duration(left) : "지금 " + r.optInt("claim") + "장 받기");
      });
      banner.addView(claim, new LinearLayout.LayoutParams(h.dp(184), h.dp(48)));
      banner.addView(k.action(null, "지급·보장", () -> drawGuide(r)),
          new LinearLayout.LayoutParams(h.dp(104), h.dp(48)));
      if (m.lastDraw != null)
        banner.addView(k.action(null, "지난 결과", () -> reveal(m.lastDraw, () -> h.load("draw"), true)),
            new LinearLayout.LayoutParams(h.dp(100), h.dp(48)));
      counter.addView(banner, new LinearLayout.LayoutParams(-1, h.dp(48)));

      LinearLayout stations = k.row();
      stations.setTag("recruitment-stations");
      stations.setGravity(Gravity.CENTER);
      stations.setBaselineAligned(false);
      counter.addView(stations, new LinearLayout.LayoutParams(-1, 0, 1));
      LinearLayout rail = k.column();
      rail.setTag("draw-purchase-rail");
      rail.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
      rail.setPadding(h.dp(8), h.dp(3), h.dp(8), h.dp(4));
      counter.addView(rail, new LinearLayout.LayoutParams(-1, -2));
      JSONArray offered = h.list(r, "gachas");
      JSONObject[] selected = {RecruitmentProbabilityView.find(offered, "LOW")};
      if (selected[0] == null) selected[0] = offered.optJSONObject(0);
      Map<String, View> choices = new LinkedHashMap<>();
      Runnable[] repaint = new Runnable[1];
      repaint[0] = () -> {
        rail.removeAllViews();
        JSONObject g = selected[0];
        if (g == null) {
          rail.addView(k.note("현재 모집 정보를 받지 못했어요. 화면을 다시 열어 주세요."));
          return;
        }
        for (Map.Entry<String, View> entry : choices.entrySet()) {
          boolean active = entry.getKey().equals(g.optString("kind"));
          entry.getValue().setSelected(active);
          View title = entry.getValue().findViewWithTag("draw-station-title-" + entry.getKey());
          title.setSelected(active);
        }
        TextView pity = h.text(RecruitmentProbabilityView.pityText(r), 13);
        pity.setTag("draw-pity");
        pity.setPadding(h.dp(6), 0, h.dp(6), 0);
        rail.addView(pity);
        LinearLayout line = k.row(), quantity = k.column();
        int cost = Math.max(1, g.optInt("cost"));
        int limit = r.optBoolean("unlimited") ? 50 : Math.min(50, r.optInt("credits") / cost);
        int[] amount = {1};
        TextView total = h.text("", 15);
        total.setTag("draw-total");
        total.setGravity(Gravity.CENTER);
        total.setPadding(h.dp(4), 0, h.dp(4), 0);
        Button go = k.primary("u-ticket", "모집하기", () ->
            h.confirm(recruitmentLabel(g) + " " + amount[0] + "회 · 지원서 " + cost * amount[0] + "장을 사용할까요?",
                () -> performDraw("draw", MainActivity.obj("kind", g.optString("kind"), "times", amount[0]), "draw")));
        go.setTag("draw-submit");
        go.setSingleLine(true);
        NativeTutorialController.anchorPreservingTag(go, "draw-go");
        k.compactQuantity(quantity, limit, n -> {
          amount[0] = n;
          total.setText(recruitmentLabel(g) + " " + n + "회\n총 지원서 " + cost * n + "장");
          go.setContentDescription(recruitmentLabel(g) + " · 모집하기 · " + n + "회 · 지원서 " + cost * n + "장");
        });
        quantityLimit(quantity, limit);
        quantityTargets(quantity);
        quantity.setTag("draw-quantity");
        line.addView(quantity, new LinearLayout.LayoutParams(h.dp(236), h.dp(48)));
        line.addView(total, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(go, new LinearLayout.LayoutParams(h.dp(184), h.dp(48)));
        go.setEnabled(limit > 0);
        if (limit <= 0) {
          go.setText("지원서 부족");
          go.setContentDescription(recruitmentLabel(g) + " · 지원서 부족");
          total.setText("필요 " + cost + "장 · 보유 " + r.optInt("credits") + "장");
        }
        rail.addView(line, new LinearLayout.LayoutParams(-1, -2));
      };
      for (int i = 0; i < offered.length(); i++) {
        JSONObject g = offered.optJSONObject(i);
        if (g == null) continue;
        String kind = g.optString("kind");
        LinearLayout station = k.column();
        station.setTag("draw-card-" + kind);
        station.setGravity(Gravity.CENTER);
        station.setPadding(h.dp(8), h.dp(5), h.dp(8), h.dp(5));
        TextView name = h.text(recruitmentLabel(g), 18);
        name.setTypeface(h.headline);
        name.setGravity(Gravity.CENTER);
        name.setPadding(h.dp(8), h.dp(2), h.dp(8), h.dp(2));
        name.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.TAB));
        name.setTextColor(JournalTheme.tabText());
        name.setTag("draw-station-title-" + kind);
        station.addView(name, new LinearLayout.LayoutParams(-1, -2));
        RecruitmentSeal envelope = new RecruitmentSeal(h);
        envelope.setTag("draw-station-envelope-" + kind);
        station.addView(envelope, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView cost = h.text("1회 · 지원서 " + g.optInt("cost") + "장", 14);
        cost.setPadding(h.dp(8), h.dp(2), h.dp(8), h.dp(2));
        cost.setGravity(Gravity.CENTER);
        cost.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
        station.addView(cost);
        station.setContentDescription(recruitmentLabel(g) + " 선택 · 1회당 지원서 " + g.optInt("cost") + "장");
        station.setOnClickListener(v -> { if (!h.busy) { selected[0] = g; repaint[0].run(); } });
        choices.put(kind, station);
        stations.addView(station, new LinearLayout.LayoutParams(0, -1, 1));
      }
      repaint[0].run();
    });
  }

  /** Reuses the production envelope atlas without animation, requests or new artwork. */
  static final class RecruitmentSeal extends View {
    final MainActivity host;
    final int epoch;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF target = new RectF();
    Rect source = ApplicationEnvelopeView.sourceRect(0);
    Bitmap atlas;

    RecruitmentSeal(MainActivity host) {
      super(host);
      this.host = host;
      epoch = host.epoch;
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      host.registrationArt().requestEnvelope(image -> {
        if (host.isFinishing() || host.isDestroyed() || host.epoch != epoch) return;
        atlas = image;
        if (image != null && !image.isRecycled()) source = envelopeInk(image);
        invalidate();
      });
    }

    // Atlas cells reserve animation travel space. Trim only their transparent margin at display time.
    static Rect envelopeInk(Bitmap atlas) {
      Rect frame = ApplicationEnvelopeView.sourceRect(0);
      int[] pixels = new int[frame.width() * frame.height()];
      atlas.getPixels(pixels, 0, frame.width(), frame.left, frame.top, frame.width(), frame.height());
      int left = frame.width(), top = frame.height(), right = -1, bottom = -1;
      for (int y = 0; y < frame.height(); y++) for (int x = 0; x < frame.width(); x++) {
        if ((pixels[y * frame.width() + x] >>> 24) <= 8) continue;
        left = Math.min(left, x); top = Math.min(top, y);
        right = Math.max(right, x); bottom = Math.max(bottom, y);
      }
      return right < left ? frame : new Rect(frame.left + left, frame.top + top,
          frame.left + right + 1, frame.top + bottom + 1);
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      if (atlas == null || atlas.isRecycled()) return;
      float scale = Math.min(getWidth() / (float) source.width(), getHeight() / (float) source.height());
      float width = source.width() * scale, height = source.height() * scale;
      target.set((getWidth() - width) / 2, (getHeight() - height) / 2,
          (getWidth() + width) / 2, (getHeight() + height) / 2);
      canvas.drawBitmap(atlas, source, target, paint);
    }
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
    String message = result == null ? "지급이 완료됐어요." : RecruitmentText.display(String.valueOf(result)).replace("🎟", "").trim();
    LinearLayout reward = k.column();
    reward.setTag("ticket-reward-body");
    reward.setPadding(h.dp(20), h.dp(16), h.dp(20), h.dp(8));
    LinearLayout head = k.row();
    head.addView(k.image("/duel/icons/u-ticket.png", 52, 52));
    head.addView(
        h.text(
            message.contains("지원서 +") ? "지원서를 받았어요!" : "지원서 안내",
            22));
    reward.addView(head);
    TextView details =
        k.note(message);
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
    h.base("모집 결과", "drawResult");
    AppSkin.apply(h, "reception");
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

  void economyScene(String place) {
    AppSkin.apply(h, place);
    if (place.equals("workshop") || place.equals("routes")) {
      View backdrop = h.root.findViewWithTag("theme-scene-background");
      if (backdrop != null) backdrop.setBackground(ThemeArt.of(h).background("backgrounds/bg-navigation-workshop-v1.png"));
      else h.page.setBackground(ThemeArt.of(h).background("backgrounds/bg-navigation-workshop-v1.png"));
    }
  }

  LinearLayout economyBody(String tag) {
    h.page.removeViewAt(h.page.getChildCount() - 1);
    LinearLayout body = k.column();
    body.setTag(tag);
    body.setPadding(h.dp(compact() ? 8 : 32), h.dp(6), h.dp(compact() ? 8 : 32), h.dp(6));
    h.page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    h.body = body;
    return body;
  }

  TextView caption(String text) {
    TextView label = k.note(text);
    label.setTypeface(h.headline);
    label.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
    label.setPadding(h.dp(10), h.dp(5), h.dp(10), h.dp(5));
    return label;
  }

  void quantityTargets(LinearLayout holder) {
    if (holder.getChildCount() == 0 || !(holder.getChildAt(0) instanceof LinearLayout)) return;
    LinearLayout row = (LinearLayout) holder.getChildAt(0);
    for (int i = 0; i < row.getChildCount(); i++) if (row.getChildAt(i) instanceof EditText) {
      LinearLayout.LayoutParams slot = (LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams();
      slot.width = h.dp(48);
      slot.height = h.dp(48);
      row.getChildAt(i).setLayoutParams(slot);
    }
  }

  void namePreview(TextView name, String fullName) {
    name.setTag("economy-name-preview");
    name.setContentDescription(fullName);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
  }

  LinearLayout choiceCopy(String name, String detail) {
    LinearLayout copy = k.column();
    TextView title = h.text(name, compact() ? 14 : 16);
    title.setPadding(h.dp(6), 0, h.dp(4), 0);
    title.setMaxLines(1);
    namePreview(title, name);
    TextView status = h.text(detail, compact() ? 12 : 14);
    status.setPadding(h.dp(6), 0, h.dp(4), 0);
    copy.addView(title);
    copy.addView(status);
    return copy;
  }

  void pageControls(LinearLayout parent, int page, int pages, java.util.function.IntConsumer turn, String tag) {
    LinearLayout pager = k.row();
    pager.setTag(tag);
    Button prev = k.action(null, "‹", () -> turn.accept(page - 1));
    prev.setEnabled(page > 0);
    prev.setContentDescription("이전 쪽");
    pager.addView(prev, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    TextView count = caption((page + 1) + " / " + Math.max(1, pages));
    count.setGravity(Gravity.CENTER);
    pager.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
    Button next = k.action(null, "›", () -> turn.accept(page + 1));
    next.setEnabled(page + 1 < pages);
    next.setContentDescription("다음 쪽");
    pager.addView(next, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    parent.addView(pager, new LinearLayout.LayoutParams(-1, h.dp(48)));
  }

  void craft(boolean mix) {
    recipeRepaint = null;
    h.base("조합", mix ? "mix" : "craft");
    economyScene("workshop");
    LinearLayout tools = k.row();
    tools.setTag("craft-inline-tools");
    Button recipes = k.action(null, "조합식", () -> craft(false));
    recipes.setSelected(!mix);
    recipes.setTag("craft-recipe-picker");
    Button materials = k.action(null, "재료로 섞기", () -> craft(true));
    materials.setSelected(mix);
    tools.addView(recipes, new LinearLayout.LayoutParams(h.dp(104), h.dp(48)));
    tools.addView(materials, new LinearLayout.LayoutParams(h.dp(132), h.dp(48)));
    if (!mix) {
      LinearLayout search = k.column();
      k.search(search, "이름·재료로 찾기", m.recipeQuery, query -> {
        m.recipeQuery = query;
        recipePage = 0;
        if (recipeRepaint != null) recipeRepaint.run();
      });
      tools.addView(search, new LinearLayout.LayoutParams(0, h.dp(48), 1));
      Button ready = k.action(null, m.craftOnly ? "✓ 가능만" : "가능만", null);
      ready.setOnClickListener(v -> {
        m.craftOnly = !m.craftOnly;
        ready.setText(m.craftOnly ? "✓ 가능만" : "가능만");
        recipePage = 0;
        if (recipeRepaint != null) recipeRepaint.run();
      });
      tools.addView(ready, new LinearLayout.LayoutParams(h.dp(108), h.dp(48)));
    } else {
      TextView help = caption("2~6마리 · 조합이 맞지 않으면 재료가 남아요");
      help.setMaxLines(2);
      tools.addView(help, new LinearLayout.LayoutParams(0, -2, 1));
    }
    h.page.addView(tools, h.page.getChildCount() - 1);
    LinearLayout[] cols = k.fixedSplit(.30f);
    craftActions = k.row();
    craftActions.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
    craftActions.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(4));
    k.footer(craftActions);
    m.fetch("craft", r -> {
      if (mix) mix(cols, r);
      else recipes(cols, r);
    });
  }

  void recipes(LinearLayout[] cols, JSONObject data) {
    JSONArray recipes = h.list(data, "recipes");
    Runnable[] paint = new Runnable[1];
    paint[0] = () -> {
      cols[0].removeAllViews();
      ArrayList<JSONObject> filtered = new ArrayList<>();
      JSONObject current = null;
      for (int i = 0; i < recipes.length(); i++) {
        JSONObject recipe = recipes.optJSONObject(i);
        if (recipe == null) continue;
        if (recipe.optString("id").equals(m.recipeId)) current = recipe;
        String names = recipe.optString("name");
        JSONArray materials = h.list(recipe, "materials");
        for (int j = 0; j < materials.length(); j++) names += " " + materials.optJSONObject(j).optString("name");
        if ((!m.craftOnly || recipe.optBoolean("ready"))
            && names.toLowerCase(Locale.KOREA).contains(m.recipeQuery.toLowerCase(Locale.KOREA))) filtered.add(recipe);
      }
      if (current == null && !filtered.isEmpty()) {
        current = filtered.get(0);
        m.recipeId = current.optString("id");
      }
      int perPage = h.contentHeightDp() >= 520 ? 4 : 2;
      int pages = Math.max(1, (filtered.size() + perPage - 1) / perPage);
      recipePage = Math.max(0, Math.min(recipePage, pages - 1));
      LinearLayout list = k.column();
      list.setTag("craft-recipe-list");
      for (int i = recipePage * perPage; i < Math.min(filtered.size(), (recipePage + 1) * perPage); i++) {
        JSONObject recipe = filtered.get(i);
        LinearLayout item = k.row();
        item.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
        item.setPadding(h.dp(5), h.dp(2), h.dp(5), h.dp(2));
        item.addView(k.image("/thumbs/" + recipe.optString("img") + ".jpg", compact() ? 36 : 52, compact() ? 36 : 52));
        item.addView(choiceCopy(recipe.optString("name"),
            recipe.optBoolean("ready") ? recipe.optInt("max") + "개 가능" : "재료 부족"),
            new LinearLayout.LayoutParams(0, -2, 1));
        item.setSelected(recipe.optString("id").equals(m.recipeId));
        item.setTag("craft-recipe-" + recipe.optString("id"));
        item.setContentDescription(recipe.optString("name") + " 조합식 선택");
        item.setOnClickListener(v -> {
          if (h.busy) return;
          m.recipeId = recipe.optString("id");
          paint[0].run();
        });
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, h.dp(compact() ? 56 : 76));
        slot.bottomMargin = h.dp(3);
        list.addView(item, slot);
      }
      if (filtered.isEmpty()) list.addView(caption("조건에 맞는 조합식이 없어요."));
      cols[0].addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
      pageControls(cols[0], recipePage, pages, page -> { recipePage = page; paint[0].run(); }, "craft-recipe-pages");
      if (current == null) {
        cols[1].removeAllViews();
        cols[1].addView(caption("아직 조합식이 없어요."));
        craftActions.removeAllViews();
      } else recipe(cols[1], current);
    };
    recipeRepaint = paint[0];
    paint[0].run();
  }

  void recipe(LinearLayout parent, JSONObject r) {
    parent.removeAllViews();
    LinearLayout panel = k.column();
    panel.setTag("craft-workbench");
    parent.addView(panel, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout heading = k.row();
    heading.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
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
    quantityTargets(quantity);
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
      namePreview(name, unit.optString("name"));
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
    paint[0] = () -> {
      cols[0].removeAllViews();
      cols[1].removeAllViews();
      ArrayList<JSONObject> available = new ArrayList<>();
      for (int i = 0; i < pool.length(); i++) {
        JSONObject unit = pool.optJSONObject(i);
        if (unit != null && unit.optInt("usable") > 0) available.add(unit);
      }
      int perPage = h.contentHeightDp() >= 520 ? 4 : 2;
      int pages = Math.max(1, (available.size() + perPage - 1) / perPage);
      mixPage = Math.max(0, Math.min(mixPage, pages - 1));
      LinearLayout choices = k.column();
      choices.setTag("mix-inline-materials");
      for (int i = mixPage * perPage; i < Math.min(available.size(), (mixPage + 1) * perPage); i++) {
        JSONObject unit = available.get(i);
        String id = unit.optString("id");
        int left = unit.optInt("usable") - Collections.frequency(m.mixDraft, id);
        LinearLayout item = k.row();
        item.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
        item.setPadding(h.dp(5), h.dp(2), h.dp(5), h.dp(2));
        item.addView(k.image("/thumbs/" + unit.optString("img") + ".jpg", compact() ? 36 : 52, compact() ? 36 : 52));
        item.addView(choiceCopy(unit.optString("name"), "남은 " + left + "마리"),
            new LinearLayout.LayoutParams(0, -2, 1));
        item.setTag("mix-picker-material-" + id);
        item.setEnabled(left > 0 && m.mixDraft.size() < 6);
        item.setAlpha(item.isEnabled() ? 1 : .6f);
        item.setContentDescription(unit.optString("name") + " 재료 추가 · 남은 " + left);
        item.setOnClickListener(v -> {
          if (h.busy || m.mixDraft.size() >= 6 || left <= 0) return;
          m.mixDraft.add(id);
          paint[0].run();
        });
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, h.dp(compact() ? 56 : 76));
        slot.bottomMargin = h.dp(3);
        choices.addView(item, slot);
      }
      if (available.isEmpty()) choices.addView(caption("사용할 수 있는 재료가 없어요."));
      cols[0].addView(choices, new LinearLayout.LayoutParams(-1, 0, 1));
      pageControls(cols[0], mixPage, pages, page -> { mixPage = page; paint[0].run(); }, "mix-material-pages");
      cols[1].addView(caption("선택한 재료 · " + m.mixDraft.size() + " / 6 · 누르면 빼기"));
      FrameLayout workbench = new FrameLayout(h);
      workbench.setTag("mix-workbench");
      LinearLayout slots = k.row();
      slots.setGravity(Gravity.CENTER_VERTICAL);
      slots.setBaselineAligned(false);
      workbench.addView(slots, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
      cols[1].addView(workbench, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int i = 0; i < 6; i++) {
        int index = i;
        LinearLayout slot = k.paper();
        slot.setPadding(h.dp(3), h.dp(5), h.dp(3), h.dp(5));
        slot.setGravity(Gravity.CENTER);
        slot.setMinimumHeight(h.dp(compact() ? 92 : 156));
        LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(0, -2, 1);
        box.setMargins(h.dp(2), 0, h.dp(2), 0);
        slots.addView(slot, box);
        if (i >= m.mixDraft.size()) {
          slot.setTag("mix-empty-slot-" + i);
          slot.addView(h.text("＋", 24));
          TextView empty = k.note("빈 자리");
          empty.setTextSize(h.uiText(12));
          empty.setGravity(Gravity.CENTER);
          slot.addView(empty);
          slot.setContentDescription("재료 " + (i + 1) + " 빈 자리 · 왼쪽 재료를 눌러 추가해요");
        } else {
          String id = m.mixDraft.get(i);
          JSONObject unit = new JSONObject();
          for (int j = 0; j < pool.length(); j++)
            if (id.equals(pool.optJSONObject(j).optString("id"))) unit = pool.optJSONObject(j);
          slot.setTag("mix-material-" + id);
          slot.addView(k.image("/thumbs/" + unit.optString("img") + ".jpg", compact() ? 40 : 76, compact() ? 40 : 76));
          TextView name = h.text(unit.optString("name"), compact() ? 12 : 14);
          name.setGravity(Gravity.CENTER);
          name.setMaxLines(2);
          namePreview(name, unit.optString("name"));
          name.setPadding(0, 0, 0, 0);
          slot.addView(name);
          slot.setContentDescription(unit.optString("name") + " 재료에서 빼기");
          slot.setOnClickListener(v -> {
            if (h.busy) return;
            m.mixDraft.remove(index);
            paint[0].run();
          });
        }
      }
      LinearLayout actions = k.row();
      Button clear = k.action(null, "비우기", () -> { m.mixDraft.clear(); paint[0].run(); });
      clear.setEnabled(!m.mixDraft.isEmpty());
      h.cell(actions, clear);
      Button make = k.primary("u-emblem", m.mixDraft.size() >= 2 ? m.mixDraft.size() + "마리 섞기" : "2마리 이상 선택", () -> {
        JSONArray materials = new JSONArray();
        for (String id : m.mixDraft)
          for (int j = 0; j < pool.length(); j++)
            if (id.equals(pool.optJSONObject(j).optString("id"))) materials.put(pool.optJSONObject(j));
        h.confirm("선택한 재료로 섞을까요?", () -> performCraft("mix",
            MainActivity.obj("ids", MainActivity.arr(m.mixDraft)), null, materials));
      });
      make.setEnabled(m.mixDraft.size() >= 2);
      make.setTag("mix-submit");
      h.cell(actions, make);
      craftActions.removeAllViews();
      craftActions.addView(actions, new LinearLayout.LayoutParams(-1, -2));
    };
    paint[0].run();
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
    economyScene("workshop");
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
    AppSkin.apply(h, "market");
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
                    "지원서 " + tickets.optInt("amount") + "장",
                    "새우gang으로 지원서를 사요.",
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
                    "지원서 " + peanut.optInt("amount") + "장",
                    "땅콩으로 지원서를 바꿔요.",
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
          LinearLayout panel = economyBody("market-counter");
          int perPage = 3;
          Runnable[] paint = new Runnable[1];
          paint[0] =
              () -> {
                panel.removeAllViews();
                shopActions.clear();
                shopCards.clear();
                JSONObject selected = null;
                for (JSONObject offer : offers)
                  if (shopFocus.equals(offer.optString("key"))) selected = offer;
                if (selected == null)
                  for (JSONObject offer : offers)
                    if (offer.optInt("limit") > 0) {
                      selected = offer;
                      break;
                    }
                if (selected == null && !offers.isEmpty()) selected = offers.get(0);
                if (selected == null) {
                  panel.addView(k.notice("판매 중인 상품이 없어요."));
                  return;
                }
                shopFocus = selected.optString("key");
                int index = Math.max(0, offers.indexOf(selected));
                int pages = (offers.size() + perPage - 1) / perPage, page = index / perPage;
                LinearLayout grid = k.column();
                grid.setTag("market-display");
                grid.setGravity(Gravity.BOTTOM);
                for (int row = 0; row < perPage / 3; row++) {
                  LinearLayout line = k.row();
                  line.setBaselineAligned(false);
                  line.setGravity(Gravity.BOTTOM);
                  for (int col = 0; col < 3; col++) {
                    int at = page * perPage + row * 3 + col;
                    View cell =
                        at < offers.size()
                            ? shopCard(offers.get(at), offers.get(at) == selected, paint[0])
                            : new View(h);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                    lp.setMargins(h.dp(compact() ? 8 : 26), h.dp(4), h.dp(compact() ? 8 : 26), h.dp(4));
                    line.addView(cell, lp);
                  }
                  grid.addView(line, new LinearLayout.LayoutParams(-1, -2));
                }
                panel.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
                if (pages > 1) {
                  LinearLayout pager = k.row();
                  pager.setGravity(Gravity.CENTER);
                  for (int p = 0; p < pages; p++) {
                    int target = p;
                    Button dot =
                        k.action(
                            null,
                            (p + 1) + "",
                            () -> {
                              shopFocus = offers.get(target * perPage).optString("key");
                              paint[0].run();
                            });
                    dot.setSelected(p == page);
                    dot.setContentDescription("상품 " + (p + 1) + "쪽");
                    pager.addView(dot, new LinearLayout.LayoutParams(h.dp(56), h.dp(48)));
                  }
                  panel.addView(pager);
                }
                panel.addView(shopBuyBar(selected));
              };          paint[0].run();
        });
  }

  /** 상품 카드: 아이콘·이름·가격·보유. 누르면 선택만 바꾸고 구매는 아래 막대에서 한다. */
  View shopCard(JSONObject o, boolean selected, Runnable repaint) {
    LinearLayout card = k.column();
    card.setGravity(Gravity.CENTER);
    card.setPadding(h.dp(6), h.dp(6), h.dp(6), h.dp(6));
    card.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
    card.setSelected(selected);
    card.setClickable(true);
    card.setTag("shop-card-" + o.optString("key"));
    int artSize = h.contentHeightDp() < 390 ? 56 : compact() ? 80 : 104;
    card.addView(k.image("/duel/icons/" + o.optString("icon") + ".png", artSize, artSize));
    TextView name = h.text(o.optString("title"), compact() ? 14 : 18);
    name.setTypeface(h.headline);
    name.setGravity(Gravity.CENTER);
    name.setMaxLines(2);
    card.addView(name);
    TextView price = k.note(o.optString("currency") + " " + o.optInt("price") + "개");
    price.setGravity(Gravity.CENTER);
    price.setTextSize(h.uiText(compact() ? 13 : 16));
    price.setPadding(0, 0, 0, 0);
    price.setSingleLine(true);
    card.addView(price);
    String state = o.optInt("limit") <= 0 ? o.optString("blocked") : o.optString("holding");
    if (!state.isEmpty()) {
      TextView extra = k.note(state);
      extra.setGravity(Gravity.CENTER);
      extra.setTextSize(h.uiText(compact() ? 12 : 14));
      extra.setPadding(0, 0, 0, 0);
      extra.setSingleLine(true);
      if (o.optInt("limit") <= 0) extra.setTextColor(JournalTheme.NEGATIVE_INFO);
      card.addView(extra);
    }
    card.setContentDescription(
        o.optString("title") + " · " + o.optString("currency") + " " + o.optInt("price") + "개"
            + (state.isEmpty() ? "" : " · " + state) + (selected ? " · 선택됨" : ""));
    card.setOnClickListener(
        v -> {
          if (h.busy) return;
          shopFocus = o.optString("key");
          repaint.run();
        });
    return card;
  }

  /** 선택 상품 설명 한 줄 + 수량 + 남색 「선택 상품 구매」. 구매 전 기존 확인창을 그대로 쓴다. */
  View shopBuyBar(JSONObject o) {
    LinearLayout bar = k.paper();
    bar.setTag("shop-purchase-rail");
    bar.setPadding(h.dp(10), h.dp(3), h.dp(10), h.dp(3));
    String title = o.optString("title"), currency = o.optString("currency");
    int price = o.optInt("price"), limit = o.optInt("limit");
    TextView about = k.note(title + " · " + o.optString("desc"));
    about.setTag("shop-selected-description");
    about.setTextColor(MainActivity.INK);
    about.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
    about.setMaxLines(2);
    about.setEllipsize(android.text.TextUtils.TruncateAt.END);
    bar.addView(about);
    LinearLayout line = k.row();
    int[] amount = {1};
    Button buy =
        k.primary(
            o.optString("icon"),
            "선택 상품 구매",
            () ->
                h.confirm(
                    title + " " + amount[0] + "개 · " + currency + " " + price * amount[0] + "개로 살까요?",
                    () -> {
                      JSONObject params = o.optJSONObject("params");
                      try {
                        params.put("count", amount[0]);
                      } catch (JSONException ignored) {
                      }
                      h.mutate(o.optString("endpoint"), params, "shop");
                    }));
    LinearLayout quantity = k.row();
    k.compactQuantity(
        quantity,
        limit,
        n -> {
          amount[0] = n;
          buy.setText(n > 1 ? n + "개 구매 · " + currency + " " + price * n : "선택 상품 구매");
        });
    quantityTargets(quantity);
    quantity.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(-1, h.dp(48)));
    line.addView(quantity, new LinearLayout.LayoutParams(0, -2, 1));
    buy.setEnabled(limit > 0);
    if (limit <= 0) buy.setText(o.optString("blocked"));
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, h.dp(48), 1);
    bp.leftMargin = h.dp(6);
    line.addView(buy, bp);
    bar.addView(line);
    shopActions.put(o.optString("key"), buy);
    return bar;
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
    economyScene("routes");
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
    LinearLayout chart = economyBody("expedition-chart");
    LinearLayout summary = k.row();
    summary.setTag("expedition-summary");
    summary.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
    LinearLayout notes = k.column();
    if (h.contentHeightDp() < 390) { notes.setOrientation(LinearLayout.HORIZONTAL); notes.setGravity(Gravity.CENTER_VERTICAL); }
    summary.addView(notes, new LinearLayout.LayoutParams(0, -2, 1));
    chart.addView(summary, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout columns = k.row();
    columns.setGravity(Gravity.BOTTOM);
    columns.setBaselineAligned(false);
    chart.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout selected = k.column(), candidates = k.column();
    LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -1, .44f);
    a.rightMargin = h.dp(16);
    columns.addView(selected, a);
    columns.addView(candidates, new LinearLayout.LayoutParams(0, -1, .56f));
    LinearLayout footer = k.row();
    footer.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
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
    summary.addView(k.action(null, "보상·시너지", () -> expeditionDetails(h.expeditionDraft)),
        new LinearLayout.LayoutParams(h.dp(124), h.dp(48)));
    Button clear = k.action(null, "비우기", () -> { h.expeditionDraft.clear(); paint[0].run(); });
    summary.addView(clear, new LinearLayout.LayoutParams(h.dp(80), h.dp(48)));
    JSONArray previous = h.list(h.me(), "previousParty");
    if (previous.length() > 0) summary.addView(k.action(null, "최근 편성", () -> {
      h.expeditionDraft.clear();
      for (int i = 0; i < previous.length() && h.expeditionDraft.size() < max; i++) {
        String id = previous.optString(i);
        JSONObject unit = find(id);
        if (unit.optBoolean("top") && Collections.frequency(h.expeditionDraft, id)
            < unit.optInt("have") - unit.optInt("away")) h.expeditionDraft.add(id);
      }
      if (h.expeditionDraft.size() < previous.length())
        Toast.makeText(h, "지금 보낼 수 있는 갈매미만 불러왔어요.", Toast.LENGTH_SHORT).show();
      paint[0].run();
    }), new LinearLayout.LayoutParams(h.dp(104), h.dp(48)));
    paint[0] = () -> {
      notes.removeAllViews();
      TextView heading = h.text("탐험대 " + h.expeditionDraft.size() + " / " + max, 18);
      heading.setTypeface(h.headline);
      heading.setPadding(h.dp(8), 0, h.dp(4), 0);
      notes.addView(heading);
      compactExpeditionEstimate(notes, h.expeditionDraft);
      selected.removeAllViews();
      candidates.removeAllViews();
      selected.addView(caption("선택한 대원 · 누르면 빼기"));
      candidates.addView(caption("보낼 수 있는 대원 · 누르면 넣기"));
      if (h.expeditionDraft.isEmpty()) {
        selected.addView(new View(h), new LinearLayout.LayoutParams(-1, 0, 1));
        selected.addView(caption("오른쪽에서 대원을 선택해 주세요."));
        selected.addView(new View(h), new LinearLayout.LayoutParams(-1, 0, 1));
      } else partyStrip(selected, h.expeditionDraft, true, paint[0]);
      candidateStrip(candidates, max, paint[0]);
      clear.setEnabled(!h.expeditionDraft.isEmpty());
      h.expeditionDirty = partyChanged();
      save.setBackground(new GameButtonDrawable(h, h.expeditionDirty ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER));
      go.setBackground(new GameButtonDrawable(h, h.expeditionDirty ? GameButtonDrawable.PAPER : GameButtonDrawable.PRIMARY));
      save.setEnabled(h.expeditionDirty);
      go.setEnabled(!h.expeditionDraft.isEmpty() && !h.expeditionDirty);
      status.setText(h.expeditionDirty ? "편성 변경 · 저장 필요" : "저장된 편성");
    };
    paint[0].run();
  }

  void partyStrip(LinearLayout parent, List<String> ids, boolean editable, Runnable repaint) {
    LinearLayout content = k.column();
    content.setTag("expedition-selected-crew");
    parent.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    int perPage = compact() ? 2 : 3;
    int pages = Math.max(1, (ids.size() + perPage - 1) / perPage);
    selectedCrewPage = Math.max(0, Math.min(selectedCrewPage, pages - 1));
    LinearLayout members = k.row();
    members.setGravity(Gravity.CENTER_VERTICAL);
    members.setBaselineAligned(false);
    content.addView(members, new LinearLayout.LayoutParams(-1, 0, 1));
    for (int i = selectedCrewPage * perPage; i < Math.min(ids.size(), (selectedCrewPage + 1) * perPage); i++) {
      int index = i;
      String id = ids.get(i);
      JSONObject unit = find(id);
      LinearLayout item = crewTile(unit, editable ? "빼기 ×" : "탐험 중");
      item.setTag("expedition-member-" + index);
      item.setContentDescription(unit.optString("name") + (editable ? " 탐험대에서 빼기" : " 탐험대"));
      if (editable) item.setOnClickListener(v -> {
        if (h.busy) return;
        h.expeditionDraft.remove(index);
        repaint.run();
      });
      LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -2, 1);
      slot.setMargins(h.dp(3), 0, h.dp(3), 0);
      members.addView(item, slot);
    }
    pageControls(content, selectedCrewPage, pages, page -> {
      selectedCrewPage = page;
      if (repaint != null) repaint.run();
      else { parent.removeView(content); partyStrip(parent, ids, false, null); }
    }, "expedition-selected-pages");
  }

  LinearLayout crewTile(JSONObject unit, String detail) {
    LinearLayout tile = k.paper();
    tile.setPadding(h.dp(4), h.dp(4), h.dp(4), h.dp(4));
    tile.setGravity(Gravity.CENTER);
    int art = compact() ? 44 : 112;
    tile.addView(k.image("/thumbs/" + unit.optString("id") + ".jpg", art, art));
    TextView name = h.text(unit.optString("name"), compact() ? 13 : 16);
    name.setPadding(0, 0, 0, 0);
    name.setMaxLines(2);
    namePreview(name, unit.optString("name"));
    name.setGravity(Gravity.CENTER);
    tile.addView(name);
    TextView summary = k.note(detail);
    summary.setPadding(0, 0, 0, 0);
    summary.setTextSize(h.uiText(compact() ? 12 : 14));
    summary.setGravity(Gravity.CENTER);
    tile.addView(summary);
    tile.setMinimumHeight(h.dp(96));
    return tile;
  }

  /** Every eligible candidate is reachable on the same screen, with bounded pagination. */
  void candidateStrip(LinearLayout parent, int max, Runnable repaint) {
    JSONArray units = h.list(h.me(), "units");
    ArrayList<JSONObject> available = new ArrayList<>();
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.optJSONObject(i);
      if (unit != null && unit.optBoolean("top") && unit.optInt("have") - unit.optInt("away") > 0)
        available.add(unit);
    }
    int perPage = compact() ? 2 : 3;
    int pages = Math.max(1, (available.size() + perPage - 1) / perPage);
    crewPage = Math.max(0, Math.min(crewPage, pages - 1));
    LinearLayout candidates = k.row();
    candidates.setTag("expedition-candidates");
    candidates.setGravity(Gravity.CENTER_VERTICAL);
    candidates.setBaselineAligned(false);
    parent.addView(candidates, new LinearLayout.LayoutParams(-1, 0, 1));
    for (int i = crewPage * perPage; i < Math.min(available.size(), (crewPage + 1) * perPage); i++) {
      JSONObject unit = available.get(i);
      String id = unit.optString("id");
      int left = unit.optInt("have") - unit.optInt("away") - Collections.frequency(h.expeditionDraft, id);
      LinearLayout tile = crewTile(unit, "남은 " + left);
      boolean can = left > 0 && h.expeditionDraft.size() < max;
      tile.setEnabled(can);
      tile.setAlpha(can ? 1f : .55f);
      tile.setTag("expedition-add-" + id);
      tile.setContentDescription(unit.optString("name") + " 탐험대에 넣기 · 남은 " + left);
      tile.setOnClickListener(v -> {
        if (h.busy || !can) return;
        h.expeditionDraft.add(id);
        repaint.run();
      });
      LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -2, 1);
      slot.setMargins(h.dp(3), 0, h.dp(3), 0);
      candidates.addView(tile, slot);
    }
    if (available.isEmpty()) candidates.addView(caption("보낼 수 있는 최상위 갈매미가 없어요."));
    pageControls(parent, crewPage, pages, page -> { crewPage = page; repaint.run(); }, "expedition-candidate-pages");
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
    LinearLayout panel = economyBody("expedition-trip");
    LinearLayout heading = k.row();
    heading.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
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
    return RecruitmentText.display(source);
  }

  void materialGuide(JSONObject material) {
    LinearLayout panel = k.paper();
    panel.setTag("material-guide-body");
    panel.addView(h.text(material.optString("name"), 22));
    panel.addView(
        k.note("사용 가능 " + material.optInt("usable") + " · 필요 " + material.optInt("need")));
    String where = material.optString("source");
    if (where.isEmpty()) where = source(material.optString("id"));
    where = RecruitmentText.display(where);
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
    if (where.contains("모집")) dialog.setNeutralButton("모집", (d, w) -> h.load("draw"));
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

  static String recruitmentLabel(JSONObject gacha) {
    switch (gacha.optString("kind")) {
      case "LOW": return "하급 모집";
      case "MID": return "중급 모집";
      case "HIGH": return "고급 모집";
      default: return RecruitmentText.display(gacha.optString("label")) + " 모집";
    }
  }

  void drawGuide(JSONObject draw) {
    LinearLayout panel = k.paper();
    panel.addView(h.text("지원서 지급·보장", 22));
    panel.addView(
        k.note(
            "지원서는 " + draw.optDouble("cooldownHours") + "시간마다 " + draw.optInt("claim") + "장 받아요."));
    JSONObject event = draw.optJSONObject("event");
    if (event != null) panel.addView(k.note(RecruitmentText.display(event.optString("label"))));
    panel.addView(k.note("중급·고급 모집에서도 흔함·안흔함이 나와요. 보장은 고급 모집에만 적용돼요."));
    panel.addView(k.note("등급별 상세 확률은 설정 > 모집 확률에서 한 번에 확인할 수 있어요."));
    panel.addView(k.note(RecruitmentProbabilityView.pityText(draw)));
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
      panel.addView(k.note("획득 시 자동 지급되는 새우gang · 지원서 환급이 아니에요."));
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
    if (plan.ticketPercent > 0) extra.add("지원서 1장 " + plan.ticketPercent + "%");
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
                                    ? "지원서 1장 " + v[0] + "/" + v[1] + "%"
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

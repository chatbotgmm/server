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
  String crewSelectionNote = "";
  int recipePage, mixPage, crewPage, selectedCrewPage;
  String mixQuery = "";
  Runnable recipeRepaint;
  TextView recipeIdentity;
  View recipeHint;
  final LinkedHashMap<String, Button> shopActions = new LinkedHashMap<>();
  final LinkedHashMap<String, LinearLayout> shopCards = new LinkedHashMap<>();

  NativeEconomy(NativeMenus menus) {
    m = menus;
    h = m.h;
    k = m.k;
  }

  void resetAcquisition() {
    mixQuery = "";
    mixPage = 0;
    drawBefore = null;
    drawFlow = null;
    acquisitionFlow = null;
    shopFocus = "";
    shopActions.clear();
    shopCards.clear();
  }

  void draw() {
    h.base("모집소", "draw");
    AppSkin.apply(h, "reception");
    // 이 화면만 짧은 장소 명패와 오른쪽 재화를 사용한다.
    LinearLayout header = h.root.findViewWithTag("theme-page-header");
    h.title.setGravity(Gravity.CENTER);
    h.title.setEllipsize(null);
    int titleWidth = Math.max(h.dp(132), (int) Math.ceil(h.title.getPaint().measureText("모집소")) + h.dp(32));
    h.title.setLayoutParams(new LinearLayout.LayoutParams(titleWidth, h.dp(48)));
    View air = new View(h);
    air.setTag("draw-header-space");
    header.addView(air, 2, new LinearLayout.LayoutParams(0, 1, 1));
    if (m.lastDraw != null) {
      Button previous = k.action(null, "지난 결과", () -> reveal(m.lastDraw, () -> h.load("draw"), true));
      previous.setTag("draw-last-result");
      previous.setTextSize(14);
      header.addView(previous, 2, new LinearLayout.LayoutParams(h.dp(104), h.dp(48)));
    }
    m.fetch("draw", r -> {
      LinearLayout counter = economyBody("recruitment-counter");
      counter.setPadding(h.dp(8), 0, h.dp(8), h.dp(4));
      LinearLayout stations = k.row();
      stations.setTag("recruitment-stations");
      stations.setGravity(Gravity.BOTTOM);
      stations.setBaselineAligned(false);
      counter.addView(stations, new LinearLayout.LayoutParams(-1, 0, 1));
      LinearLayout status = k.row();
      status.setTag("draw-status-line");
      status.setBaselineAligned(false);
      status.setGravity(Gravity.CENTER_VERTICAL);
      status.setPadding(h.dp(8), h.dp(2), h.dp(8), h.dp(2));
      status.setBackgroundColor(0xEAF7EBD2);
      TextView wait = h.text("", 12);
      wait.setTag("draw-claim-status");
      wait.setIncludeFontPadding(false);
      wait.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      TextView pity = h.text(RecruitmentProbabilityView.pityText(r), 12);
      pity.setTag("draw-pity");
      pity.setIncludeFontPadding(false);
      pity.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
      status.addView(wait, new LinearLayout.LayoutParams(0, -2, 1));
      status.addView(pity, new LinearLayout.LayoutParams(0, -2, 2));
      counter.addView(status, new LinearLayout.LayoutParams(-1, -2));
      LinearLayout rail = k.row();
      rail.setTag("draw-purchase-rail");
      rail.setBaselineAligned(false);
      rail.setGravity(Gravity.CENTER_VERTICAL);
      rail.setPadding(0, h.dp(4), 0, 0);
      counter.addView(rail, new LinearLayout.LayoutParams(-1, -2));
      Button claim = k.primary("u-ticket", "지원서 받기", this::claimTickets);
      claim.setTag("draw-claim");
      JournalButton.keepLabelDuringOperation(claim);
      claim.setSingleLine(true);
      claim.setTextSize(14);
      claim.setPadding(h.dp(10), 0, h.dp(10), 0);
      long ready = android.os.SystemClock.elapsedRealtime() + Math.max(0, r.optLong("wait"));
      tick(counter, () -> {
        long left = Math.max(0, ready - android.os.SystemClock.elapsedRealtime());
        claim.setEnabled(left == 0 && !h.busy);
        String message = left > 0 ? "다음 지급 " + duration(left) : "지금 " + r.optInt("claim") + "장 받기";
        wait.setText(message);
        claim.setContentDescription("지원서 받기 · " + message);
      });
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
          View station = entry.getValue();
          station.setSelected(active);
          station.findViewWithTag("draw-station-title-" + entry.getKey()).setSelected(active);
          station.findViewWithTag("draw-station-tray-" + entry.getKey()).setSelected(active);
        }
        int claimWidth = Math.max(h.dp(152), (int) Math.ceil(claim.getPaint().measureText("지원서 받기"))
            + claim.getCompoundPaddingLeft() + claim.getCompoundPaddingRight() + h.dp(4));
        rail.addView(claim, new LinearLayout.LayoutParams(claimWidth, h.dp(48)));
        LinearLayout quantity = k.column();
        int cost = Math.max(1, g.optInt("cost"));
        int limit = r.optBoolean("unlimited") ? 50 : Math.min(50, r.optInt("credits") / cost);
        int[] amount = {1};
        TextView total = h.text("", 13);
        total.setTag("draw-total");
        total.setIncludeFontPadding(false);
        total.setGravity(Gravity.CENTER);
        total.setPadding(h.dp(4), h.dp(4), h.dp(4), h.dp(4));
        total.setBackgroundColor(0xF2F7EBD2);
        Button go = k.primary(null, "모집하기", () ->
            h.confirm(recruitmentLabel(g) + " " + amount[0] + "회 · 지원서 " + cost * amount[0] + "장을 사용할까요?",
                () -> performDraw("draw", MainActivity.obj("kind", g.optString("kind"), "times", amount[0]), "draw")));
        go.setTag("draw-submit");
        go.setSingleLine(true);
        go.setTextSize(18);
        go.setPadding(h.dp(10), 0, h.dp(10), 0);
        NativeTutorialController.anchorPreservingTag(go, "draw-go");
        k.compactQuantity(quantity, limit, n -> {
          amount[0] = n;
          total.setText("총 지원서\n" + cost * n + "장");
          go.setContentDescription(recruitmentLabel(g) + " · 모집하기 · " + n + "회 · 지원서 " + cost * n + "장");
        });
        quantityLimit(quantity, limit);
        quantityTargets(quantity);
        quantity.setTag("draw-quantity");
        LinearLayout.LayoutParams quantitySlot = new LinearLayout.LayoutParams(h.dp(216), h.dp(48));
        quantitySlot.leftMargin = h.dp(6);
        rail.addView(quantity, quantitySlot);
        total.setMinHeight(h.dp(48));
        rail.addView(total, new LinearLayout.LayoutParams(0, -2, 1));
        int submitWidth = Math.max(h.dp(136), (int) Math.ceil(go.getPaint().measureText("지원서 부족")) + h.dp(24));
        LinearLayout.LayoutParams submitSlot = new LinearLayout.LayoutParams(submitWidth, h.dp(48));
        submitSlot.leftMargin = h.dp(6);
        rail.addView(go, submitSlot);
        go.setEnabled(limit > 0);
        if (limit <= 0) {
          go.setText("지원서 부족");
          go.setContentDescription(recruitmentLabel(g) + " · 지원서 부족");
          total.setText("필요 " + cost + "장\n보유 " + r.optInt("credits") + "장");
        }
      };
      for (int i = 0; i < offered.length(); i++) {
        JSONObject g = offered.optJSONObject(i);
        if (g == null) continue;
        String kind = g.optString("kind");
        LinearLayout station = new RecruitmentStation(h);
        station.setTag("draw-card-" + kind);
        station.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        station.setPadding(h.dp(8), h.dp(6), h.dp(8), h.dp(6));
        TextView name = h.text(recruitmentLabel(g), 20);
        name.setTypeface(h.headline);
        // Jua's Korean ink can exceed its ascent; retain the font's full top/bottom metrics.
        name.setIncludeFontPadding(true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(h.dp(12), h.dp(4), h.dp(12), h.dp(4));
        android.graphics.drawable.GradientDrawable chosenTitle = new android.graphics.drawable.GradientDrawable();
        chosenTitle.setColor(0xFFF8E4AA);
        chosenTitle.setCornerRadius(h.dp(5));
        chosenTitle.setStroke(h.dp(3), 0xFF294F60);
        android.graphics.drawable.StateListDrawable titleStates = new android.graphics.drawable.StateListDrawable();
        titleStates.addState(new int[] {android.R.attr.state_selected}, chosenTitle);
        titleStates.addState(new int[] {}, ThemeArt.of(h).drawable(ThemeArt.Surface.TAB));
        name.setBackground(titleStates);
        name.setTextColor(JournalTheme.tabText());
        name.setTag("draw-station-title-" + kind);
        station.addView(name, new LinearLayout.LayoutParams(-1, -2));
        RecruitmentTray tray = new RecruitmentTray(h, kind);
        tray.setTag("draw-station-tray-" + kind);
        station.addView(tray, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView cost = h.text("지원서 " + g.optInt("cost") + "장 / 1회", 15);
        cost.setIncludeFontPadding(false);
        cost.setPadding(h.dp(10), h.dp(4), h.dp(10), h.dp(4));
        cost.setGravity(Gravity.CENTER);
        cost.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
        cost.setTag("draw-station-cost-" + kind);
        station.addView(cost, new LinearLayout.LayoutParams(-2, -2));
        station.setContentDescription(recruitmentLabel(g) + " 선택 · 1회당 지원서 " + g.optInt("cost") + "장");
        station.setOnClickListener(v -> { if (!h.busy) { selected[0] = g; repaint[0].run(); } });
        choices.put(kind, station);
        stations.addView(station, new LinearLayout.LayoutParams(0, -1, 1));
      }
      repaint[0].run();
    });
  }

  /** 큰 화면에서도 이름표가 그림에서 멀리 떨어지지 않도록 한 묶음으로 카운터에 놓는다. */
  static final class RecruitmentStation extends LinearLayout {
    RecruitmentStation(MainActivity host) { super(host); setOrientation(VERTICAL); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      if (getChildCount() == 3 && MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
        int free = MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom();
        int width = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        for (int index : new int[] {0, 2}) {
          measureChild(getChildAt(index), widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
          free -= getChildAt(index).getMeasuredHeight();
        }
        LinearLayout.LayoutParams art = (LinearLayout.LayoutParams) getChildAt(1).getLayoutParams();
        RecruitmentTray tray = (RecruitmentTray) getChildAt(1);
        float ratio = tray.source.height() / (float) Math.max(1, tray.source.width());
        art.height = Math.max(0, Math.min(free, Math.round(width * ratio)));
        art.weight = 0;
      }
      super.onMeasure(widthSpec, heightSpec);
    }
  }

  /** 등급마다 다른 접수 트레이. 등록 카드와 봉투 애니메이션 원본은 건드리지 않는다. */
  static final class RecruitmentTray extends View {
    final String path;
    final Bitmap bitmap;
    // 비트맵별 최초 한 번만 읽고 프레임마다 알파를 훑지 않는다.
    static final Map<Bitmap, Rect> INK_BOUNDS = new WeakHashMap<>();
    final Rect source;
    final RectF selectionBounds = new RectF();
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF target = new RectF();
    final MainActivity host;

    RecruitmentTray(MainActivity host, String kind) {
      super(host);
      this.host = host;
      path = "recruitment/tray-" + kind.toLowerCase(Locale.ROOT) + "-v1.png";
      bitmap = ThemeArt.of(host).image(path);
      source = bitmap == null ? new Rect(0, 0, 3, 2) : inkBounds(bitmap);
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    static Rect inkBounds(Bitmap bitmap) {
      Rect cached = INK_BOUNDS.get(bitmap);
      if (cached != null) return new Rect(cached);
      int width = bitmap.getWidth(), height = bitmap.getHeight();
      int left = width, top = height, right = -1, bottom = -1;
      int[] row = new int[width];
      for (int y = 0; y < height; y++) {
        bitmap.getPixels(row, 0, width, 0, y, width, 1);
        for (int x = 0; x < width; x++) if ((row[x] >>> 24) > 8) {
          left = Math.min(left, x); top = Math.min(top, y);
          right = Math.max(right, x); bottom = Math.max(bottom, y);
        }
      }
      Rect found = right < left ? new Rect(0, 0, width, height) : new Rect(left, top, right + 1, bottom + 1);
      INK_BOUNDS.put(bitmap, found);
      return new Rect(found);
    }

    @Override public void setSelected(boolean selected) {
      super.setSelected(selected);
      invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      if (bitmap == null || bitmap.isRecycled()) return;
      float scale = Math.min(getWidth() / (float) source.width(), getHeight() / (float) source.height());
      // Float multiplication can overshoot the limiting edge by one ULP.
      // Clamp geometry itself; strict visible bounds remain unchanged.
      float width = Math.min(getWidth(), source.width() * scale);
      float height = Math.min(getHeight(), source.height() * scale);
      target.set((getWidth() - width) / 2, getHeight() - height, (getWidth() + width) / 2, getHeight());
      selectionBounds.set(target.left + width * .06f, target.bottom - height * .12f,
          target.right - width * .06f, target.bottom);
      if (isSelected()) {
        paint.setColor(0x75D3AB59);
        canvas.drawOval(selectionBounds, paint);
      }
      paint.setColor(Color.WHITE);
      canvas.drawBitmap(bitmap, source, target, paint);
      if (isSelected()) {
        // 금색 선택선은 투명 캔버스 바닥이 아닌 실제 목재 받침의 하단에 붙인다.
        paint.setColor(0xE6D3AB59);
        paint.setStrokeWidth(host.dp(2));
        float baseline = target.bottom - host.dp(1);
        canvas.drawLine(selectionBounds.left, baseline, selectionBounds.right, baseline, paint);
      }
    }
  }

  // Compatibility: home icon still uses the canonical first atlas frame, never the tray crop.
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
      float width = Math.min(getWidth(), source.width() * scale);
      float height = Math.min(getHeight(), source.height() * scale);
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
          String operationAccount = h.api.token();
          h.busy = true;
          View claim = h.root.findViewWithTag("draw-claim");
          if (claim != null) claim.setEnabled(false);
          h.api.call(
              "native/state",
              null,
              (fresh, error) -> {
                // 지난 화면/계정의 응답이 현재 작업의 잠금을 해제하지 않는다.
                if (h.isFinishing() || h.isDestroyed() || h.epoch != operationEpoch
                    || (!operationAccount.equals(h.api.token()) && !(error != null && h.api.token().isEmpty()))) return;
                h.busy = false;
                if (error != null && h.api.token().isEmpty()) {
                  h.login();
                  return;
                }
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
    if (h.busy) return;
    recipeRepaint = null;
    h.base("조합", mix ? "mix" : "craftRecipes");
    economyScene("workshop");
    LinearLayout tools = k.row();
    tools.setTag("craft-inline-tools");
    Button recipes = k.action(null, "결과로 찾기", () -> craft(false));
    recipes.setSelected(!mix);
    recipes.setTag("craft-recipe-picker");
    Button materials = k.action(null, "재료로 찾기", () -> craft(true));
    materials.setSelected(mix);
    materials.setTag("craft-material-picker");
    // 큰 글씨에서도 탭 이름이 두 줄로 잘리지 않게 글자 폭만큼 넓힌다(최소 120dp).
    for (Button tab : new Button[] {materials, recipes}) {
      tab.setMinWidth(h.dp(120));
      tab.setMinimumWidth(h.dp(120));
      tab.setSingleLine(true);
    }
    // 시안 순서: 결과로 찾기 | 재료로 찾기.
    tools.addView(recipes, new LinearLayout.LayoutParams(-2, h.dp(48)));
    tools.addView(materials, new LinearLayout.LayoutParams(-2, h.dp(48)));
    if (!mix) {
      LinearLayout search = k.column();
      k.search(search, "이름·재료로 찾기", m.recipeQuery, query -> {
        m.recipeQuery = query;
        m.recipeId = "";
        recipePage = 0;
        if (recipeRepaint != null) recipeRepaint.run();
      });
      tools.addView(search, new LinearLayout.LayoutParams(0, h.dp(48), 1));
      Button ready = k.action(null, m.craftOnly ? "✓ 가능만" : "가능만", null);
      ready.setOnClickListener(v -> {
        m.craftOnly = !m.craftOnly;
        m.recipeId = "";
        ready.setText(m.craftOnly ? "✓ 가능만" : "가능만");
        recipePage = 0;
        if (recipeRepaint != null) recipeRepaint.run();
      });
      tools.addView(ready, new LinearLayout.LayoutParams(h.dp(96), h.dp(48)));
    } else {
      // 재료 탭은 시안처럼 탭만 가운데에 둔다. 「맞지 않으면 재료 유지」는 확인창에서 안내한다.
      tools.setGravity(Gravity.CENTER);
    }
    h.page.addView(tools, h.page.getChildCount() - 1);
    LinearLayout[] cols = k.fixedSplit(.30f);
    craftActions = k.row();
    craftActions.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
    craftActions.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(4));
    if (!mix) {
      k.footer(craftActions);
      ((LinearLayout.LayoutParams) craftActions.getLayoutParams()).topMargin = 0;
    }
    m.fetch("craft", r -> {
      if (mix) mix(cols, r);
      else recipes(cols, r);
    });
  }

  void recipes(LinearLayout[] cols, JSONObject data) {
    JSONArray recipes = h.list(data, "recipes");
    boolean[] firstPaint = {true};
    Runnable[] paint = new Runnable[1];
    paint[0] = () -> {
      cols[0].removeAllViews();
      ArrayList<JSONObject> filtered = new ArrayList<>();
      JSONObject current = null;
      for (int i = 0; i < recipes.length(); i++) {
        JSONObject recipe = recipes.optJSONObject(i);
        if (recipe == null) continue;
        String names = recipe.optString("name");
        JSONArray materials = h.list(recipe, "materials");
        for (int j = 0; j < materials.length(); j++) names += " " + materials.optJSONObject(j).optString("name");
        if ((!m.craftOnly || recipe.optBoolean("ready"))
            && names.toLowerCase(Locale.KOREA).contains(m.recipeQuery.toLowerCase(Locale.KOREA))) filtered.add(recipe);
      }
      int perPage = h.contentHeightDp() >= 520 ? 4 : 2;
      int pages = Math.max(1, (filtered.size() + perPage - 1) / perPage);
      recipePage = Math.max(0, Math.min(recipePage, pages - 1));
      // Paging/filtering deliberately clears the workbench. A hidden candidate must not
      // remain actionable while the user is looking at a different page or result set.
      for (int i = recipePage * perPage; i < Math.min(filtered.size(), (recipePage + 1) * perPage); i++)
        if (filtered.get(i).optString("id").equals(m.recipeId)) current = filtered.get(i);
      if (current == null && firstPaint[0] && !filtered.isEmpty()) current = filtered.get(recipePage * perPage);
      m.recipeId = current == null ? "" : current.optString("id");
      firstPaint[0] = false;
      LinearLayout list = k.column();
      list.setTag("craft-recipe-list");
      LinearLayout picker = k.row();
      picker.setBaselineAligned(false);
      cols[0].addView(picker, new LinearLayout.LayoutParams(-1, 0, 1));
      picker.addView(list, new LinearLayout.LayoutParams(0, -1, 1));
      LinearLayout row = null;
      for (int i = recipePage * perPage; i < Math.min(filtered.size(), (recipePage + 1) * perPage); i++) {
        JSONObject recipe = filtered.get(i);
        if ((i - recipePage * perPage) % 2 == 0) {
          row = k.row();
          row.setBaselineAligned(false);
          list.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        LinearLayout item = fullEconomyTile(recipe,
            recipe.optBoolean("ready") ? recipe.optInt("max") + "개 가능" : "재료 부족");
        item.setSelected(recipe.optString("id").equals(m.recipeId));
        item.setBackground(RegistrationCardView.selection(h, item.isSelected()));
        item.setTag("craft-recipe-" + recipe.optString("id"));
        item.setContentDescription(recipe.optString("name") + " 조합식 선택");
        item.setOnClickListener(v -> {
          if (h.busy) return;
          m.recipeId = recipe.optString("id");
          paint[0].run();
        });
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -1, 1);
        slot.setMargins(h.dp(2), h.dp(2), h.dp(2), h.dp(2));
        row.addView(item, slot);
      }
      if (filtered.isEmpty()) list.addView(caption("조건에 맞는 조합식이 없어요."));
      crewPageControls(picker, recipePage, pages, page -> { recipePage = page; m.recipeId = ""; paint[0].run(); }, "craft-recipe-pages");
      if (current == null) {
        cols[1].removeAllViews();
        TextView choose = caption(filtered.isEmpty() ? "조건에 맞는 결과 후보가 없어요." : "왼쪽 결과 후보를 눌러 조합식을 선택해 주세요.");
        choose.setTag("craft-selection-empty");
        cols[1].addView(choose);
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
    TextView resultName = h.text("선택한 조합", 16);
    resultName.setTag("craft-selected-heading");
    resultName.setTypeface(h.headline);
    recipeIdentity = resultName;
    heading.addView(resultName, new LinearLayout.LayoutParams(0, -2, 1));
    recipeHint = k.note("재료 ＋ 재료 → 결과");
    heading.addView(recipeHint);
    panel.addView(heading);
    JSONArray mats = h.list(r, "materials");
    boolean fixedChain = mats.length() <= 2;
    LinearLayout flow = k.row();
    flow.setGravity(Gravity.CENTER_VERTICAL);
    flow.setBaselineAligned(false);
    if (fixedChain) panel.addView(flow, new LinearLayout.LayoutParams(-1, 0, 1));
    else {
      HorizontalScrollView strip = new HorizontalScrollView(h);
      strip.setHorizontalScrollBarEnabled(true);
      strip.addView(flow, new android.widget.FrameLayout.LayoutParams(-2, -1));
      panel.addView(strip, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    ArrayList<TextView> counts = new ArrayList<>();
    for (int j = 0; j < mats.length(); j++) {
      JSONObject material = mats.optJSONObject(j);
      if (j > 0) flow.addView(h.text("＋", 18));
      LinearLayout slot = materialSlot(material, false, fixedChain);
      counts.add(slot.findViewWithTag("craft-material-count-" + material.optString("id")));
      flow.addView(slot);
    }
    flow.addView(h.text("→", 22));
    flow.addView(materialSlot(r, true, fixedChain));
    TextView availability =
        k.note(
            r.optBoolean("ready")
                ? "최대 " + r.optInt("max") + "개 · 재료를 눌러 얻는 곳 보기"
                : "재료 부족 · 붉은 재료를 눌러 얻는 곳 보기");
    availability.setTag("craft-availability");
    availability.setTextColor(JournalTheme.NAVY);
    availability.setBackgroundColor(JournalTheme.PAPER);
    if (!compact()) panel.addView(availability);
    heading.setContentDescription(r.optString("name") + " 조합 정보 보기");
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
          recipeIdentity.setText("선택한 조합");
          recipeHint.setVisibility(View.VISIBLE);
          make.setText(n > 1 ? n + "개 만들기" : "만들기");
          for (int i = 0; i < counts.size(); i++) {
            JSONObject material = mats.optJSONObject(i);
            int need = material.optInt("need") * n;
            counts.get(i).setText("보유 " + material.optInt("usable") + " · 필요 " + need);
            counts
                .get(i)
                .setTextColor(material.optInt("usable") < need ? 0xffb44335 : MainActivity.INK);
          }
        });
    quantityLimit(quantity, r.optInt("max"));
    quantityTargets(quantity);
    craftActions.addView(quantity, new LinearLayout.LayoutParams(h.dp(236), h.dp(48)));
    craftActions.addView(new View(h), new LinearLayout.LayoutParams(0, 1, 1));
    make.setEnabled(r.optBoolean("ready"));
    if (!r.optBoolean("ready")) make.setText("재료 부족");
    craftActions.addView(make, new LinearLayout.LayoutParams(h.dp(220), h.dp(48)));
  }

  LinearLayout materialSlot(JSONObject unit, boolean result) {
    return materialSlot(unit, result, false);
  }

  /** Original FULL identity once, with only the changing quantity/state below it. */
  RegistrationCardView fullEconomyCard(JSONObject unit) {
    RegistrationCardView card = new RegistrationCardView(h, unit, RegistrationCardView.FULL);
    // A server-masked picture contains no printed identity. Keep only its exact public labels.
    if (!RegistrationCardView.canShow(unit)) {
      card.picture.setVisibility(View.INVISIBLE);
      FrameLayout photo = (FrameLayout) card.getChildAt(0);
      FrameLayout mask = new FrameLayout(h);
      mask.setTag("craft-masked-placeholder");
      mask.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
      View seal = new View(h) {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override protected void onDraw(Canvas canvas) {
          super.onDraw(canvas);
          paint.setTypeface(h.headline);
          paint.setColor(JournalTheme.MUTED);
          paint.setTextAlign(Paint.Align.CENTER);
          // Decorative unknown seal yields to the exact server-provided name/grade below.
          paint.setTextSize(Math.min(h.dp(24), Math.min(getWidth() * .5f, getHeight() * .6f)));
          canvas.drawText("?", getWidth() / 2f, getHeight() / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
        }
      };
      mask.addView(seal, new FrameLayout.LayoutParams(-1, -1));
      mask.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      photo.addView(mask, new FrameLayout.LayoutParams(-1, -1));
      card.name.setVisibility(View.VISIBLE);
      card.grade.setVisibility(View.VISIBLE);
      // 미공개 표기는 그림 밖에 있으므로 배경 풍경과 분리해서 읽을 수 있게 한다.
      // 서버가 가린 이름이나 등급을 추정하지 않고 기존 공개 문구만 그대로 사용한다.
      for (TextView label : new TextView[] {card.name, card.grade}) {
        label.setBackgroundColor(JournalTheme.PAPER);
        label.setTextColor(JournalTheme.INK);
        label.setIncludeFontPadding(true);
      }
    }
    return card;
  }

  LinearLayout fullEconomyTile(JSONObject unit, String detail) {
    LinearLayout tile = k.column();
    tile.setGravity(Gravity.CENTER);
    tile.setPadding(h.dp(2), 0, h.dp(2), 0);
    RegistrationCardView card = fullEconomyCard(unit);
    card.setTag("economy-full-card");
    tile.addView(card, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView state = RegistrationCardView.stateLabel(h, detail);
    state.setTag("economy-card-state");
    state.setSingleLine(false);
    state.setEllipsize(null);
    state.setPadding(h.dp(2), 0, h.dp(2), 0);
    state.setBackgroundColor(JournalTheme.PAPER);
    state.setTextColor(MainActivity.INK);
    tile.addView(state, new LinearLayout.LayoutParams(-1, -2));
    tile.setMinimumHeight(h.dp(48));
    return tile;
  }

  LinearLayout materialSlot(JSONObject unit, boolean result, boolean bounded) {
    LinearLayout slot = fullEconomyTile(unit,
        result ? "결과" : "보유 " + unit.optInt("usable") + " · 필요 " + unit.optInt("need"));
    RegistrationCardView art = (RegistrationCardView) slot.getChildAt(0);
    art.setTag(result ? "craft-result-image" : "craft-material-image-" + unit.optString("id"));
    slot.setLayoutParams(new LinearLayout.LayoutParams(h.dp(compact() ? 116 : 160), -1));
    if (bounded) slot.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1));
    TextView count = (TextView) slot.getChildAt(1);
    count.setTextSize(h.uiText(14));
    if (compact()) count.setPadding(0, 0, 0, 0);
    count.setTag(result ? "craft-result-tier" : "craft-material-count-" + unit.optString("id"));
    count.setGravity(Gravity.CENTER);
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
    if (h.contentHeightDp() < 390 && recipeIdentity != null) {
      boolean known = RegistrationCardView.canShow(unit);
      slot.setContentDescription((known ? unit.optString("name") : "미발견")
          + (result ? " 결과 · 눌러 이름 보기" : " 재료 · 눌러 이름·수량 보기")
          + (!result && known ? ", 길게 눌러 얻는 곳 보기" : ""));
      slot.setOnClickListener(v -> {
        recipeHint.setVisibility(View.GONE);
        recipeIdentity.setText((RegistrationCardView.canShow(unit) ? unit.optString("name") : "미발견")
            + " · " + count.getText());
      });
      if (!result && known) slot.setOnLongClickListener(v -> { materialGuide(unit); return true; });
    }
    return slot;
  }

  void mix(LinearLayout[] cols, JSONObject data) {
    JSONArray pool = h.list(data, "pool");
    int limit = data.optInt("maxMaterials", 6); // 구 서버의 기존 한도와 호환.
    // 화면 재진입 때 보호·편성·보유 수량이 바뀐 초안을 정리한다.
    ArrayList<String> valid = new ArrayList<>();
    for (String id : m.mixDraft) {
      JSONObject unit = mixUnit(pool, id);
      if (valid.size() < limit && Collections.frequency(valid, id) < unit.optInt("usable")) valid.add(id);
    }
    m.mixDraft.clear();
    m.mixDraft.addAll(valid);
    // 시안: 보유 갈매미맨 | 선택한 재료 › 가능한 결과, 아래에 선택 해제·조합하기.
    LinearLayout owned = benchPanel();
    cols[0].addView(owned, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout ownedHead = k.row();
    TextView ownedTitle = h.text("보유 갈매미맨", 16);
    ownedTitle.setTypeface(h.headline);
    ownedTitle.setTextColor(JournalTheme.PAPER);
    ownedTitle.setPadding(h.dp(4), 0, h.dp(6), 0);
    ownedTitle.setSingleLine(true);
    ownedHead.addView(ownedTitle, new LinearLayout.LayoutParams(-2, -2));
    LinearLayout search = k.column();
    ownedHead.addView(search, new LinearLayout.LayoutParams(0, h.dp(48), 1));
    owned.addView(ownedHead, new LinearLayout.LayoutParams(-1, h.dp(48)));
    LinearLayout inventory = k.column();
    owned.addView(inventory, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout bench = k.row();
    bench.setBaselineAligned(false);
    cols[1].addView(bench, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout selected = benchPanel(), results = benchPanel();
    bench.addView(selected, new LinearLayout.LayoutParams(0, -1, 1.1f));
    // 글꼴에 없는 기호 대신 화살표를 직접 그린다(선택한 재료 › 가능한 결과).
    View arrow = new View(h) {
      final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      final Path path = new Path();
      @Override protected void onDraw(Canvas canvas) {
        float x = getWidth() / 2f, y = getHeight() / 2f, s = Math.min(getWidth() * .32f, h.dp(8));
        path.reset();
        path.moveTo(x - s * .6f, y - s * 1.4f);
        path.lineTo(x + s * .8f, y);
        path.lineTo(x - s * .6f, y + s * 1.4f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(h.dp(5));
        paint.setColor(JournalTheme.NAVY);
        canvas.drawPath(path, paint);
        paint.setStrokeWidth(h.dp(3));
        paint.setColor(JournalTheme.PAPER);
        canvas.drawPath(path, paint);
      }
    };
    arrow.setTag("mix-arrow");
    arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    bench.addView(arrow, new LinearLayout.LayoutParams(h.dp(22), -1));
    bench.addView(results, new LinearLayout.LayoutParams(0, -1, 1));
    LinearLayout actions = k.row();
    actions.setTag("mix-actions");
    k.footer(actions);
    Runnable[] paint = new Runnable[1];
    Button clear = k.action(null, "선택 해제", () -> {
      if (h.busy) return;
      m.mixDraft.clear();
      paint[0].run();
    });
    clear.setTag("mix-clear");
    // 힌트는 성공 판정이 아니다. 미공개 조합은 서버가 확정 시에만 검사한다.
    Button make = k.primary(null, "조합하기", () -> confirmMix(pool));
    make.setTag("mix-submit");
    actions.addView(new View(h), new LinearLayout.LayoutParams(0, 1, .30f));
    LinearLayout.LayoutParams clearSlot = new LinearLayout.LayoutParams(0, h.dp(48), .26f);
    clearSlot.setMargins(h.dp(8), 0, h.dp(6), 0);
    actions.addView(clear, clearSlot);
    actions.addView(make, new LinearLayout.LayoutParams(0, h.dp(48), .44f));
    paint[0] = () -> {
      inventory.removeAllViews();
      selected.removeAllViews();
      results.removeAllViews();
      ArrayList<JSONObject> available = new ArrayList<>();
      for (int i = 0; i < pool.length(); i++) {
        JSONObject unit = pool.optJSONObject(i);
        if (unit != null && unit.optInt("usable") > 0
            && unit.optString("name").toLowerCase(Locale.KOREA).contains(mixQuery.toLowerCase(Locale.KOREA))) available.add(unit);
      }
      // 썸네일은 2열. 줄 수는 화면 높이로 정하고(작은 가로 1줄 ~ 큰 화면 3줄) 넘치면 쪽을 넘긴다.
      int rows = Math.max(1, Math.min(3, (h.contentHeightDp() - 236) / 64));
      int perPage = rows * 2;
      int pages = Math.max(1, (available.size() + perPage - 1) / perPage);
      mixPage = Math.max(0, Math.min(mixPage, pages - 1));
      LinearLayout choices = k.column();
      choices.setTag("mix-inventory");
      inventory.addView(choices, new LinearLayout.LayoutParams(-1, 0, 1));
      LinearLayout line = null;
      for (int n = 0; n < perPage; n++) {
        if (n % 2 == 0) {
          line = k.row();
          line.setBaselineAligned(false);
          choices.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        int i = mixPage * perPage + n;
        FrameLayout cell = new FrameLayout(h);
        line.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        if (i >= available.size()) continue;
        JSONObject unit = available.get(i);
        String id = unit.optString("id");
        int left = unit.optInt("usable") - Collections.frequency(m.mixDraft, id);
        Shape tile = thumbTile(unit);
        tile.setTag("mix-picker-material-" + id);
        tile.addView(countBadge(String.valueOf(left)), new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END));
        tile.setEnabled(left > 0 && m.mixDraft.size() < limit);
        tile.setAlpha(tile.isEnabled() ? 1 : .45f);
        tile.setContentDescription(unit.optString("name") + " 재료 추가 · 사용 가능 " + left);
        tile.setOnClickListener(v -> {
          if (h.busy || m.mixDraft.size() >= limit || left <= 0) return;
          m.mixDraft.add(id);
          paint[0].run();
        });
        cell.addView(tile, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
      }
      if (available.isEmpty()) {
        choices.removeAllViews();
        TextView none = k.note(mixQuery.isEmpty() ? "사용 가능한 재료가 없어요." : "검색과 맞는 갈매미맨이 없어요.");
        none.setTextColor(JournalTheme.PAPER);
        none.setGravity(Gravity.CENTER);
        choices.addView(none, new LinearLayout.LayoutParams(-1, -1));
      }
      if (pages > 1)
        pageControls(inventory, mixPage, pages, page -> { mixPage = page; paint[0].run(); }, "mix-material-pages");
      TextView count = benchHeading("선택한 재료 " + m.mixDraft.size() + "/" + limit);
      count.setTag("mix-count");
      count.setGravity(Gravity.CENTER);
      selected.addView(count, new LinearLayout.LayoutParams(-1, h.dp(48)));
      // 재료 칸은 최소 4칸, 고르면 다음 빈 칸이 생긴다(서버 한도까지). 4칸 넘으면 3열.
      int slots = Math.min(limit, Math.max(4, m.mixDraft.size() + 1));
      int columns = slots <= 4 ? 2 : 3;
      LinearLayout grid = k.column();
      grid.setTag("mix-workbench");
      selected.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
      boolean hint = false;
      LinearLayout slotLine = null;
      for (int i = 0; i < (slots + columns - 1) / columns * columns; i++) {
        if (i % columns == 0) {
          slotLine = k.row();
          slotLine.setBaselineAligned(false);
          grid.addView(slotLine, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        FrameLayout cell = new FrameLayout(h);
        slotLine.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        if (i >= slots) continue;
        if (i >= m.mixDraft.size()) {
          cell.addView(emptySlot(i), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
          continue;
        }
        int index = i;
        JSONObject unit = mixUnit(pool, m.mixDraft.get(i));
        hint |= unit.optBoolean("hiddenHint");
        Shape slot = thumbTile(unit);
        slot.setTag("mix-material-" + m.mixDraft.get(i));
        TextView remove = h.text("×", 15);
        remove.setTypeface(h.headline);
        remove.setTextColor(JournalTheme.WHITE);
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(0, 0, 0, 0);
        remove.setIncludeFontPadding(false);
        android.graphics.drawable.GradientDrawable seal = new android.graphics.drawable.GradientDrawable();
        seal.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        seal.setColor(JournalTheme.NAVY);
        seal.setStroke(h.dp(2), JournalTheme.PAPER);
        remove.setBackground(seal);
        remove.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams sealSlot = new FrameLayout.LayoutParams(h.dp(24), h.dp(24), Gravity.TOP | Gravity.END);
        sealSlot.setMargins(0, h.dp(2), h.dp(2), 0);
        slot.addView(remove, sealSlot);
        // 칸 전체가 48dp 이상의 빼기 버튼이다. 같은 재료가 여러 칸이면 누른 칸만 뺀다.
        slot.setContentDescription(unit.optString("name") + " 재료에서 빼기 · " + (index + 1) + "번째 칸");
        slot.setOnClickListener(v -> {
          if (h.busy) return;
          m.mixDraft.remove(index);
          paint[0].run();
        });
        cell.addView(slot, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
      }
      JSONObject match = m.mixDraft.size() >= 2 ? mixResult(h.list(data, "recipes")) : null;
      TextView resultHeading = benchHeading("가능한 결과");
      resultHeading.setTag("mix-result-heading");
      resultHeading.setGravity(Gravity.CENTER);
      results.addView(resultHeading, new LinearLayout.LayoutParams(-1, h.dp(48)));
      LinearLayout cards = k.row();
      cards.setBaselineAligned(false);
      cards.setTag("mix-results");
      results.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1));
      ArrayList<View> shown = new ArrayList<>();
      if (m.mixDraft.size() < 2) {
        shown.add(unknownResult("재료 선택 중", hint));
        shown.add(unknownResult("재료 선택 중", false));
      } else {
        // 공개 결과와 미공개 단서를 함께 보여도 같은 결과라고 암시하지 않는다.
        if (match != null) shown.add(fullEconomyCard(match));
        if (hint || match == null) shown.add(unknownResult(hint ? "미공개" : "결과 미확인", hint));
      }
      boolean tagged = false;
      for (View card : shown) {
        if (card instanceof HiddenRecipeFrame && !tagged) { card.setTag("mix-result-card"); tagged = true; }
        Shape ratio = new Shape(4f / 3f);
        ratio.addView(card, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout cell = new FrameLayout(h);
        cell.addView(ratio, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        cards.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
      }
      clear.setEnabled(!m.mixDraft.isEmpty());
      make.setEnabled(m.mixDraft.size() >= 2);
    };
    k.search(search, "검색", mixQuery, query -> {
      mixQuery = query;
      mixPage = 0;
      paint[0].run();
    });
    paint[0].run();
  }

  /** 시안의 남색 패널. 기존 남색 틀 그림을 그대로 쓴다. */
  LinearLayout benchPanel() {
    LinearLayout panel = k.column();
    panel.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.NAVY));
    panel.setPadding(h.dp(10), h.dp(8), h.dp(10), h.dp(10));
    return panel;
  }

  /** 정해진 가로세로 비율로 남는 칸 안에서 가장 크게 그린다(정사각 썸네일, 3:4 등록증). */
  final class Shape extends FrameLayout {
    final float ratio;
    Shape(float ratio) { super(h); this.ratio = ratio; }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
      int w = Math.max(1, Math.min(width, Math.round(height / ratio)));
      int t = Math.max(1, Math.min(height, Math.round(w * ratio)));
      super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(t, MeasureSpec.EXACTLY));
    }
  }

  Shape thumbTile(JSONObject unit) {
    Shape tile = new Shape(1f);
    android.graphics.drawable.GradientDrawable frame = new android.graphics.drawable.GradientDrawable();
    frame.setColor(JournalTheme.NAVY);
    frame.setCornerRadius(h.dp(6));
    frame.setStroke(h.dp(2), JournalTheme.BRASS);
    tile.setBackground(frame);
    tile.setPadding(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
    tile.setClickable(true);
    tile.setMinimumWidth(h.dp(48));
    tile.setMinimumHeight(h.dp(48));
    ImageView art = k.image("/thumbs/" + unit.optString("img", unit.optString("id")) + ".jpg", 1, 1);
    art.setScaleType(ImageView.ScaleType.CENTER_CROP);
    art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    tile.addView(art, new FrameLayout.LayoutParams(-1, -1));
    return tile;
  }

  TextView countBadge(String value) {
    TextView badge = h.text(value, 13);
    badge.setTypeface(h.headline);
    badge.setTextColor(JournalTheme.WHITE);
    badge.setGravity(Gravity.CENTER);
    badge.setIncludeFontPadding(false);
    badge.setMinWidth(h.dp(24));
    badge.setPadding(h.dp(6), h.dp(2), h.dp(6), h.dp(2));
    android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
    pill.setColor(0xE6101C26);
    pill.setCornerRadius(h.dp(10));
    badge.setBackground(pill);
    badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return badge;
  }

  View emptySlot(int index) {
    Shape slot = new Shape(1f);
    slot.setTag("mix-empty-slot-" + index);
    slot.setMinimumWidth(h.dp(48));
    slot.setMinimumHeight(h.dp(48));
    slot.setBackground(new android.graphics.drawable.Drawable() {
      final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      @Override public void draw(Canvas canvas) {
        RectF box = new RectF(getBounds());
        box.inset(h.dp(2), h.dp(2));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66101C26);
        canvas.drawRoundRect(box, h.dp(6), h.dp(6), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(h.dp(1.5f));
        paint.setColor(JournalTheme.BRASS);
        paint.setPathEffect(new DashPathEffect(new float[] {h.dp(5), h.dp(4)}, 0));
        canvas.drawRoundRect(box, h.dp(6), h.dp(6), paint);
        paint.setPathEffect(null);
      }
      @Override public void setAlpha(int alpha) { }
      @Override public void setColorFilter(ColorFilter filter) { }
      @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    });
    TextView plus = h.text("＋", 26);
    plus.setTextColor(JournalTheme.PAPER);
    plus.setGravity(Gravity.CENTER);
    plus.setPadding(0, 0, 0, 0);
    slot.addView(plus, new FrameLayout.LayoutParams(-1, -1));
    slot.setContentDescription("빈 재료 칸 " + (index + 1) + " · 왼쪽 보유 갈매미맨을 눌러 추가");
    return slot;
  }

  HiddenRecipeFrame unknownResult(String label, boolean hint) {
    JSONObject unknown = MainActivity.obj("known", false, "name", label, "tierName", "", "img", "unknown");
    HiddenRecipeFrame frame = new HiddenRecipeFrame(h, hint);
    RegistrationCardView card = fullEconomyCard(unknown);
    // 시안처럼 미공개 카드에는 상태 한 줄만 둔다(빈 등급 줄이 공간을 차지하지 않게).
    card.grade.setVisibility(View.GONE);
    frame.addView(card, new FrameLayout.LayoutParams(-1, -1));
    return frame;
  }

  /** 작업대 제목 칸: 글자를 세로 가운데에 두고 종이 모서리 장식과 겹치지 않게 띄운다. */
  TextView benchHeading(String text) {
    TextView label = caption(text);
    label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    label.setPadding(h.dp(16), 0, h.dp(16), 0);
    return label;
  }

  JSONObject mixUnit(JSONArray pool, String id) {
    for (int i = 0; i < pool.length(); i++) {
      JSONObject unit = pool.optJSONObject(i);
      if (unit != null && id.equals(unit.optString("id"))) return unit;
    }
    return new JSONObject();
  }

  JSONObject mixResult(JSONArray recipes) {
    ArrayList<String> selected = new ArrayList<>(m.mixDraft);
    Collections.sort(selected);
    for (int i = 0; i < recipes.length(); i++) {
      JSONObject recipe = recipes.optJSONObject(i);
      ArrayList<String> required = new ArrayList<>();
      JSONArray materials = h.list(recipe, "materials");
      for (int j = 0; j < materials.length(); j++) {
        JSONObject material = materials.optJSONObject(j);
        for (int n = 0; n < material.optInt("need"); n++) required.add(material.optString("id"));
      }
      Collections.sort(required);
      if (recipe.optBoolean("ready") && required.equals(selected)) return recipe;
    }
    return null;
  }

  boolean mixConfirming;
  void confirmMix(JSONArray pool) {
    if (h.busy || mixConfirming || m.mixDraft.size() < 2) return;
    mixConfirming = true;
    int epoch = h.epoch;
    String account = h.api.token();
    ArrayList<String> ids = new ArrayList<>(m.mixDraft);
    JSONArray materials = new JSONArray();
    for (String id : ids) materials.put(mixUnit(pool, id));
    GameDialog dialog = new GameDialog(h);
    dialog.setMessage("선택한 " + ids.size() + "마리를 사용해 조합할까요? 맞는 조합이 없으면 재료는 그대로예요.")
        .setNegativeButton("취소", null)
        .setPositiveButton("확인", (d, w) -> {
          if (h.epoch != epoch || !account.equals(h.api.token()) || !ids.equals(m.mixDraft)) return;
          performCraft("mix", MainActivity.obj("ids", MainActivity.arr(ids)), null, materials);
        });
    android.app.AlertDialog showing = dialog.show();
    showing.setOnDismissListener(d -> {
      mixConfirming = false;
      if (h.visibleDialog == showing) h.visibleDialog = null;
    });
  }

  void performCraft(String endpoint, JSONObject body, JSONObject result, JSONArray materials) {
    if (h.busy) return;
    Set<String> before = NativeAcquisitionFlow.keys(h.state);
    // 홈 기본 진입(craft)과 결과 탭 복원 경로를 분리한다. API 이름은 그대로 유지한다.
    String origin = endpoint.equals("mix") ? "mix" : "craftRecipes";
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
                    "보유 " + r.optInt("credits") + "장", tickets.optInt("amount"), "장"));
          if (peanut != null)
            offers.add(
                shopOffer(
                    "u-ticket",
                    "지원서 " + peanut.optInt("amount") + "장",
                    "땅콩으로 지원서를 바꿔요.",
                    1,
                    Math.min(50, r.optInt("peanut")),
                    "땅콩",
                    "shop",
                    MainActivity.obj("item", "peanutTickets"),
                    "땅콩 부족",
                    "보유 " + r.optInt("credits") + "장", peanut.optInt("amount"), "장"));
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
                    "보유 " + count + " / " + maxStack, 1, "개"));
          }
          LinearLayout panel = economyBody("market-counter");
          // Give the short safe-area shelf four more dp without changing the
          // calibrated card frame inset, text sizes, purchase controls or artwork.
          panel.setPadding(panel.getPaddingLeft(), h.dp(4), panel.getPaddingRight(), h.dp(4));
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
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
                    lp.setMargins(h.dp(compact() ? 8 : 26), h.dp(4), h.dp(compact() ? 8 : 26), h.dp(4));
                    line.addView(cell, lp);
                  }
                  grid.addView(line, new LinearLayout.LayoutParams(-1, -2));
                }
                LinearLayout shelf = k.row();
                shelf.setBaselineAligned(false);
                shelf.addView(grid, new LinearLayout.LayoutParams(0, -1, 1));
                if (pages > 1) {
                  LinearLayout pager = k.column();
                  pager.setTag("shop-inline-pages");
                  pager.setGravity(Gravity.CENTER);
                  Button previous = k.action(null, "‹", () -> {
                    shopFocus = offers.get((page - 1) * perPage).optString("key");
                    paint[0].run();
                  });
                  m.pagerChevron(previous, false, JournalTheme.NAVY);
                  previous.setTag("shop-page-previous");
                  previous.setEnabled(page > 0);
                  previous.setContentDescription("이전 상품 쪽");
                  pager.addView(previous, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
                  TextView count = caption((page + 1) + "/" + pages);
                  count.setGravity(Gravity.CENTER);
                  count.setPadding(0, h.dp(4), 0, h.dp(4));
                  pager.addView(count, new LinearLayout.LayoutParams(-1, -2));
                  Button next = k.action(null, "›", () -> {
                    shopFocus = offers.get((page + 1) * perPage).optString("key");
                    paint[0].run();
                  });
                  m.pagerChevron(next, true, JournalTheme.NAVY);
                  next.setTag("shop-page-next");
                  next.setEnabled(page + 1 < pages);
                  next.setContentDescription("다음 상품 쪽");
                  pager.addView(next, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
                  shelf.addView(pager, new LinearLayout.LayoutParams(h.dp(52), -1));
                }
                panel.addView(shelf, new LinearLayout.LayoutParams(-1, 0, 1));
                panel.addView(shopBuyBar(selected));
              };          paint[0].run();
        });
  }

  /** 상품 카드: 아이콘·이름·가격·보유. 누르면 선택만 바꾸고 구매는 아래 막대에서 한다. */
  View shopCard(JSONObject o, boolean selected, Runnable repaint) {
    ShopCardLayout card = new ShopCardLayout(h);
    card.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
    card.setSelected(selected);
    card.setClickable(true);
    card.setTag("shop-card-" + o.optString("key"));
    int artSize = h.contentHeightDp() < 390 ? 56 : compact() ? 80 : 104;
    card.setIcon(k.image("/duel/icons/" + o.optString("icon") + ".png", artSize, artSize), h.dp(artSize));
    TextView name = h.text(o.optString("title"), compact() ? 14 : 18);
    name.setTypeface(h.headline);
    name.setGravity(Gravity.CENTER);
    name.setMaxLines(2);
    name.setPadding(0, 0, 0, 0);
    name.setTag("shop-card-name");
    card.addView(name);
    TextView price = k.note(o.optString("currency") + " " + o.optInt("price") + "개");
    price.setGravity(Gravity.CENTER);
    price.setTextSize(h.uiText(compact() ? 13 : 16));
    price.setPadding(0, 0, 0, 0);
    price.setSingleLine(false);
    price.setHorizontallyScrolling(false);
    price.setMaxLines(2);
    price.setTag("shop-card-price");
    card.addView(price);
    String state = o.optInt("limit") <= 0 ? o.optString("blocked") : o.optString("holding");
    // Reserve the same native status row even when this offer has no status copy.
    // Together with MATCH_PARENT row children this aligns both frames and text baselines.
    TextView extra = k.note(state);
    extra.setGravity(Gravity.CENTER);
    extra.setTextSize(h.uiText(compact() ? 12 : 14));
    extra.setPadding(0, 0, 0, 0);
    extra.setSingleLine(false);
    extra.setHorizontallyScrolling(false);
    extra.setMaxLines(2);
    extra.setTag("shop-card-state");
    if (o.optInt("limit") <= 0) extra.setTextColor(JournalTheme.NEGATIVE_INFO);
    if (state.isEmpty()) extra.setVisibility(View.INVISIBLE);
    card.addView(extra);
    card.setContentDescription(
        o.optString("title") + " · 결제 " + o.optString("currency") + " " + o.optInt("price") + "개"
            + (state.isEmpty() ? "" : " · " + state) + (selected ? " · 선택됨" : ""));
    card.setOnClickListener(
        v -> {
          if (h.busy) return;
          shopFocus = o.optString("key");
          repaint.run();
        });
    return card;
  }

  /** Shop-only space budget; the shared art, fonts and purchase rail remain unchanged. */
  static final class ShopCardLayout extends LinearLayout {
    ImageView icon;
    int preferredIconHeight, preferredIconWidth;
    final int minimumIconHeight, headerGap;
    boolean compactHeader;

    ShopCardLayout(MainActivity host) {
      super(host);
      setOrientation(VERTICAL);
      setGravity(Gravity.CENTER);
      minimumIconHeight = host.dp(24);
      headerGap = host.dp(6);
      // PAPER's fixed 5×3 art bands are at most 80/3dp horizontally and 72/3dp
      // vertically (ThemeArt.surface). Keep content beyond those bands plus 1dp.
      setPadding(host.dp(28), host.dp(25), host.dp(28), host.dp(25));
    }

    void setIcon(ImageView view, int preferredHeight) {
      icon = view;
      preferredIconHeight = preferredHeight;
      preferredIconWidth = view.getLayoutParams().width;
      addView(view);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      if (icon == null) {
        super.onMeasure(widthSpec, heightSpec);
        return;
      }
      // Measure real font-scaled text without a vertical cap before assigning the
      // remaining height to the illustration. Never shrink or clip cost/status text.
      LinearLayout.LayoutParams art = (LinearLayout.LayoutParams) icon.getLayoutParams();
      art.width = preferredIconWidth;
      art.height = 0;
      super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      int textAndFrameHeight = getMeasuredHeight();
      boolean bounded = MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED;
      int available = bounded ? Math.max(0, MeasureSpec.getSize(heightSpec) - textAndFrameHeight)
          : preferredIconHeight;
      compactHeader = bounded && available < minimumIconHeight && getChildCount() == 4;
      if (compactHeader) {
        // Short safe-area viewports put the real product icon beside its name.
        // Prices, ownership, original font sizes and the ornamental inset stay intact.
        int width = getMeasuredWidth(), contentWidth = width - getPaddingLeft() - getPaddingRight();
        android.graphics.drawable.Drawable drawable = icon.getDrawable();
        float aspect = drawable != null && drawable.getIntrinsicHeight() > 0
            ? drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight() : 1;
        int iconWidth = Math.max(1, (int) Math.ceil(minimumIconHeight * aspect));
        icon.measure(MeasureSpec.makeMeasureSpec(iconWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(minimumIconHeight, MeasureSpec.EXACTLY));
        View name = getChildAt(1), price = getChildAt(2), status = getChildAt(3);
        name.measure(MeasureSpec.makeMeasureSpec(Math.max(0, contentWidth - iconWidth - headerGap), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        for (View label : new View[] {price, status})
          label.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
              MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        // Fill the same shelf budget as sibling cards; never hide an over-budget row.
        // The native glyph/line tests still reject a viewport too small for this layout.
        setMeasuredDimension(width, MeasureSpec.getSize(heightSpec));
        return;
      }
      art.height = Math.min(preferredIconHeight, Math.max(minimumIconHeight, available));
      super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
      if (!compactHeader) {
        super.onLayout(changed, left, top, right, bottom);
        return;
      }
      View name = getChildAt(1), price = getChildAt(2), status = getChildAt(3);
      int x = getPaddingLeft(), end = getWidth() - getPaddingRight();
      int headerHeight = Math.max(Math.max(icon.getMeasuredHeight(), name.getMeasuredHeight()),
          getHeight() - getPaddingTop() - getPaddingBottom() - price.getMeasuredHeight() - status.getMeasuredHeight());
      int y = getPaddingTop(), iconY = y + (headerHeight - icon.getMeasuredHeight()) / 2;
      icon.layout(x, iconY, x + icon.getMeasuredWidth(), iconY + icon.getMeasuredHeight());
      int nameY = y + (headerHeight - name.getMeasuredHeight()) / 2;
      name.layout(x + icon.getMeasuredWidth() + headerGap, nameY, end, nameY + name.getMeasuredHeight());
      y += headerHeight;
      price.layout(x, y, end, y + price.getMeasuredHeight());
      y += price.getMeasuredHeight();
      status.layout(x, y, end, y + status.getMeasuredHeight());
    }
  }

  /** 선택 상품 설명 한 줄 + 수량 + 남색 「선택 상품 구매」. 구매 전 기존 확인창을 그대로 쓴다. */
  View shopBuyBar(JSONObject o) {
    LinearLayout bar = k.paper();
    bar.setTag("shop-purchase-rail");
    bar.setPadding(h.dp(10), h.dp(3), h.dp(10), h.dp(3));
    String title = o.optString("title"), currency = o.optString("currency");
    int price = o.optInt("price"), limit = o.optInt("limit");
    boolean ticketPack = o.optString("productUnit").equals("장");
    int packSize = o.optInt("productCount", 1);
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
          String product = ticketPack ? "지원서 " + packSize * n + "장" : n + "개";
          buy.setText(product + " 구매 · " + currency + " " + price * n + "개");
          buy.setContentDescription(title + " · " + n + "묶음 · 총 " + product + " 구매 · 결제 " + currency + " " + price * n + "개");
        });
    quantityTargets(quantity);
    quantity.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(-1, h.dp(48)));
    line.addView(quantity, new LinearLayout.LayoutParams(h.dp(236), h.dp(48)));
    buy.setTextSize(h.uiText(14));
    buy.setMaxLines(2);
    buy.setSingleLine(false);
    buy.setEllipsize(null);
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
      String holding,
      int productCount,
      String productUnit) {
    return MainActivity.obj(
        "productCount", productCount,
        "productUnit", productUnit,
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
    crewSelectionNote = "";
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
    chart.setPadding(h.dp(8), 0, h.dp(8), 0);
    LinearLayout summary = k.row();
    summary.setTag("expedition-summary");
    summary.setBackground(ThemeArt.of(h).drawable(ThemeArt.Surface.PAPER));
    LinearLayout notes = k.column();
    notes.setTag("expedition-summary-copy");
    // Reserve a quiet reading inset inside the brass frame, without padding the 48dp actions.
    notes.setPadding(h.dp(12), h.dp(6), h.dp(12), h.dp(6));
    if (h.contentHeightDp() < 390) { notes.setOrientation(LinearLayout.HORIZONTAL); notes.setGravity(Gravity.CENTER_VERTICAL); }
    summary.addView(notes, new LinearLayout.LayoutParams(0, -2, 1));
    chart.addView(summary, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout columns = k.row();
    columns.setGravity(Gravity.BOTTOM);
    columns.setBaselineAligned(false);
    chart.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout selected = k.column(), candidates = k.column();
    LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -1, .46f);
    a.rightMargin = h.dp(8);
    columns.addView(selected, a);
    columns.addView(candidates, new LinearLayout.LayoutParams(0, -1, .54f));
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
    save.setTag("expedition-save");
    go.setTag("expedition-depart");
    footer.addView(save, new LinearLayout.LayoutParams(h.dp(152), h.dp(48)));
    footer.addView(go, new LinearLayout.LayoutParams(h.dp(176), h.dp(48)));
    k.footer(footer);
    ((LinearLayout.LayoutParams) footer.getLayoutParams()).topMargin = 0;
    summary.addView(k.action(null, "보상·시너지", () -> expeditionDetails(h.expeditionDraft)),
        new LinearLayout.LayoutParams(h.dp(124), h.dp(48)));
    Button clear = k.action(null, "비우기", () -> { h.expeditionDraft.clear(); crewSelectionNote = ""; paint[0].run(); });
    summary.addView(clear, new LinearLayout.LayoutParams(h.dp(80), h.dp(48)));
    JSONArray previous = h.list(h.me(), "previousParty");
    if (previous.length() > 0) summary.addView(k.action(null, "최근 편성", () -> {
      h.expeditionDraft.clear();
      crewSelectionNote = "";
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
      TextView heading = h.text("탐험대 " + h.expeditionDraft.size() + " / " + max, 16);
      heading.setTag("expedition-summary-count");
      heading.setTypeface(h.headline);
      heading.setPadding(h.dp(8), 0, h.dp(4), 0);
      notes.addView(heading);
      compactExpeditionEstimate(notes, h.expeditionDraft);
      selected.removeAllViews();
      candidates.removeAllViews();
      TextView selectedHeading = crewCaption(crewSelectionNote.isEmpty() ? "선택 대원 · 누르면 빼기" : crewSelectionNote);
      selectedHeading.setTag("expedition-selection-summary");
      selected.addView(selectedHeading);
      TextView candidateHeading = crewCaption(h.expeditionDraft.size() >= max ? "정원 " + max + "명 · 먼저 빼기" : "후보 · 누르면 넣기");
      candidateHeading.setTag("expedition-candidate-heading");
      candidates.addView(candidateHeading);
      partyStrip(selected, h.expeditionDraft, true, paint[0]);
      candidateStrip(candidates, max, paint[0]);
      clear.setEnabled(!h.expeditionDraft.isEmpty());
      h.expeditionDirty = partyChanged();
      save.setBackground(new GameButtonDrawable(h, h.expeditionDirty ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER));
      go.setBackground(new GameButtonDrawable(h, h.expeditionDirty ? GameButtonDrawable.PAPER : GameButtonDrawable.PRIMARY));
      save.setEnabled(h.expeditionDirty);
      go.setEnabled(!h.expeditionDraft.isEmpty() && !h.expeditionDirty);
      status.setText(h.expeditionDirty ? "① 편성 저장 → ② 출발" : "저장됨 · 출발할 수 있어요");
    };
    paint[0].run();
  }

  /** All ten ordered slots are visible together. Duplicate IDs still remove by exact index. */
  void partyStrip(LinearLayout parent, List<String> ids, boolean editable, Runnable repaint) {
    LinearLayout content = k.column();
    content.setTag("expedition-selected-crew");
    parent.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    selectedCrewPage = 0;
    int slots = Math.max(partyMax(), ids.size()), columns = editable ? 5 : 10;
    int rows = Math.max(1, (slots + columns - 1) / columns);
    for (int row = 0; row < rows; row++) {
      LinearLayout line = k.row();
      line.setBaselineAligned(false);
      content.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int col = 0; col < columns; col++) {
        int index = row * columns + col;
        if (index >= slots) { line.addView(new View(h), new LinearLayout.LayoutParams(0, -1, 1)); continue; }
        LinearLayout tile;
        if (index >= ids.size()) {
          tile = k.column();
          tile.setGravity(Gravity.CENTER);
          tile.setBackground(PartyCharacterView.selection(h, false));
          TextView empty = h.text("＋", 18);
          empty.setPadding(0, 0, 0, 0);
          empty.setGravity(Gravity.CENTER);
          tile.addView(empty);
          tile.setTag("expedition-empty-" + index);
          tile.setContentDescription("탐험대 " + (index + 1) + "번 빈 자리 · 후보에서 추가해요");
        } else {
          JSONObject unit = find(ids.get(index));
          tile = crewTile(unit, (index + 1) + (editable ? " ×" : ""));
          tile.setTag("expedition-member-" + index);
          tile.setContentDescription((index + 1) + "번 · " + unit.optString("name")
              + " · " + synergyLabel(unit.optString("synergy")) + (editable ? " · 탐험대에서 빼기" : " · 탐험 중"));
          if (editable) tile.setOnClickListener(v -> {
            if (h.busy) return;
            h.expeditionDraft.remove(index);
            crewSelectionNote = unit.optString("name") + " · 제외했어요";
            repaint.run();
          });
        }
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -1, 1);
        slot.setMargins(h.dp(2), h.dp(1), h.dp(2), h.dp(1));
        tile.setMinimumHeight(h.dp(48));
        line.addView(tile, slot);
      }
    }
  }

  TextView crewCaption(String text) {
    TextView label = caption(text);
    label.setTextSize(h.uiText(12));
    label.setPadding(h.dp(6), h.dp(1), h.dp(6), h.dp(1));
    return label;
  }

  LinearLayout crewTile(JSONObject unit, String detail) {
    LinearLayout tile = k.column();
    // Keep the one-line name and the full 48dp target; return two vertical dp
    // to the contained sprite at short high-density/font-scaled viewports.
    tile.setPadding(h.dp(2), h.dp(1), h.dp(2), h.dp(1));
    tile.setGravity(Gravity.CENTER);
    tile.setBackground(PartyCharacterView.selection(h, false));
    tile.setContentDescription(unit.optString("name") + " · " + detail);
    View portrait = PartyCharacterView.art(h, unit);
    portrait.setTag("expedition-character-art");
    portrait.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    tile.addView(portrait, new LinearLayout.LayoutParams(-1, 0, 1));
    // Keep the same single caption row: canonical names make small sprites identifiable.
    // Ordered slot/remove and remaining-copy information stays on the tile's accessible label.
    TextView status = PartyCharacterView.stateLabel(h, unit.optString("name"));
    status.setTextSize(h.uiText(12));
    status.setPadding(h.dp(2), 0, h.dp(2), 0);
    status.setSingleLine(false);
    status.setMaxLines(1);
    status.setHorizontallyScrolling(false);
    status.setEllipsize(android.text.TextUtils.TruncateAt.END);
    status.setTag("economy-name-preview");
    status.setContentDescription(unit.optString("name"));
    tile.addView(status, new LinearLayout.LayoutParams(-1, -2));
    tile.setMinimumHeight(h.dp(48));
    return tile;
  }

  /** The same bounded pages, beside rather than below the full cards on short landscape screens. */
  void crewPageControls(LinearLayout parent, int page, int pages, java.util.function.IntConsumer turn, String tag) {
    LinearLayout pager = k.column();
    pager.setGravity(Gravity.CENTER);
    pager.setTag(tag);
    Button prev = k.action(null, "‹", () -> turn.accept(page - 1));
    prev.setEnabled(page > 0);
    prev.setContentDescription("이전 쪽");
    if (tag.equals("craft-recipe-pages")) {
      prev.setTag(tag + "-previous");
      m.pagerChevron(prev, false);
    }
    pager.addView(prev, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    TextView count = h.text((page + 1) + "/" + Math.max(1, pages), 12);
    count.setPadding(0, 0, 0, 0);
    count.setGravity(Gravity.CENTER);
    count.setSingleLine(false);
    count.setMaxLines(1);
    count.setHorizontallyScrolling(false);
    count.setBackground(new GamePaperDrawable(h));
    if (!tag.startsWith("expedition-")) pager.addView(count, new LinearLayout.LayoutParams(h.dp(48), h.dp(24)));
    else pager.setContentDescription("후보 " + (page + 1) + " / " + Math.max(1, pages) + "쪽");
    Button next = k.action(null, "›", () -> turn.accept(page + 1));
    next.setEnabled(page + 1 < pages);
    next.setContentDescription("다음 쪽");
    if (tag.equals("craft-recipe-pages")) {
      next.setTag(tag + "-next");
      m.pagerChevron(next, true);
    }
    pager.addView(next, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(h.dp(48), -1);
    slot.leftMargin = h.dp(4);
    parent.addView(pager, slot);
  }

  /** Eligible top-tier candidates stay in server order, with eight compact portraits per page. */
  void candidateStrip(LinearLayout parent, int max, Runnable repaint) {
    JSONArray units = h.list(h.me(), "units");
    ArrayList<JSONObject> available = new ArrayList<>();
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.optJSONObject(i);
      if (unit != null && unit.optBoolean("top") && unit.optInt("have") - unit.optInt("away") > 0)
        available.add(unit);
    }
    int perPage = 8;
    int pages = Math.max(1, (available.size() + perPage - 1) / perPage);
    crewPage = Math.max(0, Math.min(crewPage, pages - 1));
    TextView heading = parent.findViewWithTag("expedition-candidate-heading");
    if (heading != null) heading.setText("후보 " + (crewPage + 1) + "/" + pages
        + (h.expeditionDraft.size() >= max ? " · 정원 " + max + "명, 먼저 빼기" : " · 눌러 추가"));
    LinearLayout candidates = k.column(), line = k.row();
    line.setBaselineAligned(false);
    parent.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
    candidates.setTag("expedition-candidates");
    line.addView(candidates, new LinearLayout.LayoutParams(0, -1, 1));
    for (int row = 0; row < 2; row++) {
      LinearLayout group = k.row();
      group.setBaselineAligned(false);
      candidates.addView(group, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int col = 0; col < 4; col++) {
        int index = crewPage * perPage + row * 4 + col;
        if (index >= available.size()) { group.addView(new View(h), new LinearLayout.LayoutParams(0, -1, 1)); continue; }
        JSONObject unit = available.get(index);
        String id = unit.optString("id");
        int left = unit.optInt("have") - unit.optInt("away") - Collections.frequency(h.expeditionDraft, id);
        LinearLayout tile = crewTile(unit, "남은 " + left);
        boolean can = left > 0 && h.expeditionDraft.size() < max;
        tile.setEnabled(can);
        tile.setAlpha(can ? 1f : .72f);
        tile.setTag("expedition-add-" + id);
        tile.setContentDescription(unit.optString("name") + " · " + synergyLabel(unit.optString("synergy"))
            + " · 남은 " + left + (h.expeditionDraft.size() >= max ? " · 정원 " + max + "명, 먼저 대원을 빼 주세요"
                : left <= 0 ? " · 모두 선택했어요" : " · 탐험대에 넣기"));
        tile.setOnClickListener(v -> {
          if (h.busy || !can) return;
          h.expeditionDraft.add(id);
          crewSelectionNote = unit.optString("name") + " · 남은 " + (left - 1);
          repaint.run();
        });
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -1, 1);
        slot.setMargins(h.dp(2), h.dp(1), h.dp(2), h.dp(1));
        group.addView(tile, slot);
      }
    }
    if (available.isEmpty()) candidates.addView(caption("보낼 수 있는 최상위 갈매미가 없어요."));
    crewPageControls(line, crewPage, pages, page -> { crewPage = page; repaint.run(); }, "expedition-candidate-pages");
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
    TextView estimate = k.note("예상 " + plan.minutes + "분 · 새우gang " + plan.snacks);
    estimate.setPadding(h.dp(4), 0, h.dp(4), 0);
    parent.addView(estimate);
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




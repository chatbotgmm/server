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
  Runnable recipeRepaint;
  NativeExpeditionPreparation expeditionPreparation;
  NativeRoster60Craft roster60Craft;
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
    h.title.setTypeface(RecruitmentScreen.recruitmentFont(h, true));
    h.title.setGravity(Gravity.CENTER);
    h.title.setEllipsize(null);
    h.title.setIncludeFontPadding(false);
    // Recruitment-only smooth navy header; do not replace shared theme drawables globally.
    android.graphics.drawable.GradientDrawable headerPanel = new android.graphics.drawable.GradientDrawable();
    headerPanel.setColor(0xEB10283F);
    headerPanel.setCornerRadius(h.dp(10));
    header.setBackground(headerPanel);
    h.title.setBackgroundColor(Color.TRANSPARENT);
    h.title.setTextColor(0xFFF4F2EB);
    int titleWidth = Math.max(h.dp(132), (int) Math.ceil(h.title.getPaint().measureText("모집소")) + h.dp(32));
    Paint.FontMetrics titleMetrics = h.title.getPaint().getFontMetrics();
    int titleHeight = Math.max(h.dp(48), (int) Math.ceil(titleMetrics.descent - titleMetrics.ascent)
        + h.title.getPaddingTop() + h.title.getPaddingBottom());
    h.title.setLayoutParams(new LinearLayout.LayoutParams(titleWidth, titleHeight));
    // Recruitment-local font choice; shared menus retain their existing Jua/Gowun faces.
    for (int i = 0; i < header.getChildCount(); i++) {
      View child = header.getChildAt(i);
      if (!(child instanceof TextView) || !(child.getTag() instanceof String)
          || !((String) child.getTag()).startsWith("currency-")) continue;
      TextView currency = (TextView) child;
      currency.setTypeface(RecruitmentScreen.recruitmentFont(h, false));
      currency.setIncludeFontPadding(false);
      LinearLayout.LayoutParams slot = (LinearLayout.LayoutParams) currency.getLayoutParams();
      slot.width = Math.max(slot.width, (int) Math.ceil(currency.getPaint().measureText(currency.getText().toString())) + h.dp(48));
      currency.setLayoutParams(slot);
    }
    View air = new View(h);
    air.setTag("draw-header-space");
    header.addView(air, 2, new LinearLayout.LayoutParams(0, 1, 1));
    boolean roster60 = h.roster60Menus();
    if (!roster60 && m.lastDraw != null) {
      Button previous = k.action(null, "↺", () -> reveal(m.lastDraw, () -> h.load("draw"), true));
      previous.setTag("draw-last-result");
      previous.setTextSize(22);
      previous.setContentDescription("지난 모집 결과 다시 보기");
      previous.setPadding(0, 0, 0, 0);
      header.addView(previous, 2, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    }
    // A response from another account must not attach to this screen, even at the same epoch.
    final int drawEpoch = h.epoch;
    final String drawAccount = h.api.token();
    // 새 60종은 예전 모집 API(draw)를 부르지 않는다. 지원서 받기 시각은 이미 받은 상태(me)에서 읽는다.
    if (roster60) { drawRoster60(header, drawEpoch, drawAccount); return; }
    m.fetch("draw", r -> {
      if (h.isFinishing() || h.isDestroyed() || h.epoch != drawEpoch
          || !drawAccount.equals(h.api.token())) return;
      LinearLayout counter = economyBody("recruitment-counter");
      counter.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(4));
      counter.addView(new RecruitmentScreen(this, r), new LinearLayout.LayoutParams(-1, -1));
    });
  }

  /** 새 60종: 같은 모집소 화면에 새 모집 API의 등급·비용·활성 여부를 얹고, 결과는 방범대 등록증으로 보인다. */
  void drawRoster60(LinearLayout header, int drawEpoch, String drawAccount) {
    h.api.call("native/roster60/recruit", null, (roster, error) -> {
      if (h.isFinishing() || h.isDestroyed() || h.epoch != drawEpoch || !drawAccount.equals(h.api.token())) return;
      LinearLayout counter = economyBody("recruitment-counter");
      counter.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(4));
      if (error != null || roster == null) { counter.addView(k.note("모집 준비 중 · " + error)); return; }
      try {
        Roster60RecruitPurchase purchase = new Roster60RecruitPurchase(h, roster);
        JSONObject last = purchase.receipt();
        if (last != null) {
          Button previous = k.action(null, "↺", () -> Roster60RecruitPurchase.showCards(h, last, null));
          previous.setTag("draw-last-result");
          previous.setTextSize(22);
          previous.setContentDescription("지난 모집 결과 다시 보기");
          previous.setPadding(0, 0, 0, 0);
          header.addView(previous, 2, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
        }
        counter.addView(new RecruitmentScreen(this, Roster60RecruitPurchase.merge(h.me(), roster, System.currentTimeMillis()), purchase),
            new LinearLayout.LayoutParams(-1, -1));
        // 결과를 확인하지 못한 요청이 있으면 같은 요청 번호로 다시 확인한다(새로 차감하지 않음).
        if (purchase.pending()) purchase.send();
      } catch (JSONException bad) { counter.addView(k.note("모집 정보를 읽지 못했어요.")); }
    });
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
      RecruitmentArtwork.Asset artwork = RecruitmentArtwork.load(host, kind);
      path = artwork.path;
      bitmap = artwork.bitmap;
      source = artwork.sourceRect();
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
    JSONObject tutorial=h.state.optJSONObject("tutorial");
    if(tutorial!=null&&"roster60-v1".equals(tutorial.optString("flow"))){
      if(roster60Craft==null)roster60Craft=new NativeRoster60Craft(this);
      roster60Craft.open(mix);return;
    }
    recipeRepaint = null;
    h.base("조합", mix ? "mix" : "craftRecipes");
    economyScene("workshop");
    NavyWorkbenchTheme.page(h);
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
      NavyWorkbenchTheme.tab(h, tab, tab.isSelected());
      tab.setTextSize(12);
    }
    // 시안 순서: 결과로 찾기 | 재료로 찾기.
    tools.addView(recipes, new LinearLayout.LayoutParams(-2, h.dp(48)));
    tools.addView(materials, new LinearLayout.LayoutParams(-2, h.dp(48)));
    // Search/ready filters were removed: no invisible query may hide carousel cards.
    m.recipeQuery = "";
    m.craftOnly = false;
    tools.setGravity(Gravity.END);
    NavyWorkbenchTheme.applyText(h, tools);
    NavyWorkbenchTheme.tab(h, recipes, !mix);
    NavyWorkbenchTheme.tab(h, materials, mix);
    recipes.setTextSize(12); materials.setTextSize(12);
    // applyText/button resets minima; restore the intended tab width AFTER styling.
    for (Button tab : new Button[] {recipes, materials}) {
      tab.setMinWidth(h.dp(120)); tab.setMinimumWidth(h.dp(120));
    }
    // Both modes share the right-anchored tab strip, leaving no second toolbar row.
    LinearLayout header = h.page.findViewWithTag("theme-page-header");
    // No horizontal viewport: BOTH tabs must be visible on the first layout.
    // MainActivity.base already omits currency for mix/craftRecipes (mapPage).
    // Existing title weight=1 consumes the remaining space before this unweighted strip.
    header.addView(tools, new LinearLayout.LayoutParams(-2, h.dp(48)));
    // 카드 탐색은 화살표 없이 스와이프. 기존 3열 비율과 원본 카드 비율을 유지한다.
    LinearLayout[] cols = mix ? navyMixColumns() : k.fixedSplit(.39f);
    craftActions = k.row();
    craftActions.setTag("craft-actions");
    craftActions.setBackground(NavyWorkbenchTheme.quiet(h,ThemeArt.Surface.NAVY));
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
    // Only the server-disclosed recipes are considered. Filtering never fetches or unmasks any.
    JSONArray recipes = h.list(data, "recipes");
    ArrayList<JSONObject> available = new ArrayList<>();
    for (int i=0;i<recipes.length();i++) {
      JSONObject recipe=recipes.optJSONObject(i);
      if(recipe!=null && CraftTierPolicy.result(recipe.optString("tier"))) available.add(recipe);
    }
    for(int i=0;i<available.size();i++)
      if(available.get(i).optString("id").equals(m.recipeId)) { recipePage=i; break; }
    recipePage=Math.max(0,Math.min(recipePage,available.size()-1));
    Runnable[] paint=new Runnable[1];
    paint[0]=()-> {
      cols[0].removeAllViews();
      LinearLayout list=k.column();
      list.setTag("craft-recipe-list");
      list.setPadding(h.dp(10),h.dp(8),h.dp(10),h.dp(8));
      cols[0].addView(list,new LinearLayout.LayoutParams(-1,-1));
      if(available.isEmpty()) {
        m.recipeId="";
        cols[1].removeAllViews(); craftActions.removeAllViews();
        TextView empty=h.text("공개된 상위 조합식이 없어요",14);
        NavyWorkbenchTheme.text(h,empty,14,false,NavyWorkbenchTheme.TEXT);
        empty.setPadding(h.dp(16),h.dp(12),h.dp(16),h.dp(12));
        empty.setTag("craft-selection-empty");
        cols[1].addView(empty);
        return;
      }
      recipePage=Math.max(0,Math.min(recipePage,available.size()-1));
      JSONObject current=available.get(recipePage);
      m.recipeId=current.optString("id");
      MixCarousel carousel=new MixCarousel(direction->{
        if(h.busy) return;
        int destination=recipePage+direction;
        if(destination<0 || destination>=available.size()) return;
        recipePage=destination; paint[0].run();
      });
      carousel.setTag("craft-result-carousel");
      carousel.page=recipePage; carousel.pages=available.size();
      carousel.actionLabel="조합식 보기";
      RegistrationCardView card=fullEconomyCard(current);
      card.setTag("craft-recipe-"+m.recipeId);
      card.setSelected(true);
      card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      carousel.setContentDescription((RegistrationCardView.canShow(current)?current.optString("name"):"미발견")
          + " 조합식 · 좌우로 밀어 결과 이동");
      carousel.tap=()-> { if(!h.busy) paint[0].run(); };
      card.setOnClickListener(v->carousel.performClick());
      carousel.addView(card,new FrameLayout.LayoutParams(-1,-1));
      list.addView(carousel,new LinearLayout.LayoutParams(-1,0,1));
      TextView position=h.text((recipePage+1)+" / "+available.size()+" · 밀어 이동",10);
      position.setTag("craft-result-position");
      NavyWorkbenchTheme.text(h,position,10,false,NavyWorkbenchTheme.MUTED);
      position.setGravity(Gravity.CENTER);
      list.addView(position,new LinearLayout.LayoutParams(-1,-2));
      recipe(cols[1],current);
      NavyWorkbenchTheme.applyText(h,cols[1]);
      NavyWorkbenchTheme.applyText(h,craftActions);
    };
    recipeRepaint=paint[0]; paint[0].run();
  }

  void recipe(LinearLayout parent, JSONObject r) {
    parent.removeAllViews();
    LinearLayout panel = k.column();
    panel.setTag("craft-workbench");
    parent.addView(panel, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout heading = k.row();
    heading.setBackground(NavyWorkbenchTheme.quiet(h,ThemeArt.Surface.NAVY));
    heading.setPadding(h.dp(16),h.dp(4),h.dp(16),h.dp(4));
    TextView resultName = h.text("선택한 조합", 16);
    resultName.setTag("craft-selected-heading");
    NavyWorkbenchTheme.text(h,resultName,16,true,NavyWorkbenchTheme.TEXT);
    recipeIdentity = resultName;
    heading.addView(resultName, new LinearLayout.LayoutParams(0, -2, 1));
    recipeHint = k.note("재료 ＋ 재료 → 결과");
    NavyWorkbenchTheme.text(h,(TextView) recipeHint,11,false,NavyWorkbenchTheme.MUTED);
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
    quantity.setTag("craft-quantity");
    craftActions.addView(quantity, new LinearLayout.LayoutParams(-2, -2));
    make.setEnabled(r.optBoolean("ready"));
    if (!r.optBoolean("ready")) make.setText("재료 부족");
    make.setTag("craft-submit");
    LinearLayout.LayoutParams makeSlot=new LinearLayout.LayoutParams(0,workbenchActionHeight(make),1);
    makeSlot.leftMargin=h.dp(8);
    craftActions.addView(make,makeSlot);
    NavyWorkbenchTheme.applyText(h,craftActions);
    sizeCraftQuantity(quantity);
    makeSlot.height=workbenchActionHeight(make);
  }

  /** Measure each quantity label after styling; do not give '최대' the same narrow weight as '+'. */
  void sizeCraftQuantity(LinearLayout quantity) {
    LinearLayout row=(LinearLayout)quantity.getChildAt(0);
    int width=0,height=h.dp(48);
    for(int i=0;i<row.getChildCount();i++) {
      View child=row.getChildAt(i);
      if(!(child instanceof TextView)) continue;
      TextView text=(TextView)child;
      text.setSingleLine(true);
      String widest=child instanceof EditText?"50":text.getText().toString();
      int w=Math.max(h.dp(48),(int)Math.ceil(text.getPaint().measureText(widest))
          +text.getCompoundPaddingLeft()+text.getCompoundPaddingRight()+h.dp(4));
      int ch=workbenchActionHeight(text);
      LinearLayout.LayoutParams slot=new LinearLayout.LayoutParams(w,ch);
      slot.setMargins(h.dp(2),0,h.dp(2),0);
      child.setLayoutParams(slot);
      width+=w+slot.leftMargin+slot.rightMargin; height=Math.max(height,ch);
    }
    quantity.setLayoutParams(new LinearLayout.LayoutParams(width,height));
  }

  LinearLayout materialSlot(JSONObject unit, boolean result) {
    return materialSlot(unit, result, false);
  }

  /** Original FULL identity once, with only the changing quantity/state below it. */
  RegistrationCardView fullEconomyCard(JSONObject unit) {
    RegistrationCardView card = new RegistrationCardView(h, unit, RegistrationCardView.FULL);
    if (h.screen.equals("mix") || h.screen.equals("craftRecipes")) {
      card.name.setTypeface(NavyWorkbenchTheme.font(h));
      card.grade.setTypeface(NavyWorkbenchTheme.font(h));
    }
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
      slot.setContentDescription(unit.optString("name") + " · " + TierDisplay.name(unit.optString("tier"), unit.optString("tierName")));
      slot.setOnClickListener(
          v -> h.message(unit.optString("name") + " · " + TierDisplay.name(unit.optString("tier"), unit.optString("tierName"))));
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

  /** 승인한 39:33:28 배치. 실제 남은 viewport 안에서 세 열을 함께 측정한다. */
  LinearLayout[] navyMixColumns() {
    h.page.removeViewAt(h.page.getChildCount() - 1);
    NavyWorkbenchRow row = new NavyWorkbenchRow();
    row.setTag("navy-workbench-columns");
    row.setBaselineAligned(false);
    LinearLayout[] cols = {benchPanel(), benchPanel(), benchPanel()};
    cols[1].setBackground(NavyWorkbenchTheme.quiet(h,ThemeArt.Surface.NAVY));
    cols[2].setBackground(NavyWorkbenchTheme.quiet(h,ThemeArt.Surface.NAVY));
    float[] weights = {.39f, .33f, .28f};
    for (int i=0;i<3;i++) {
      if(i>0) {
        View divider = new View(h);
        divider.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(h.dp(8),-1);
        d.setMargins(0,h.dp(16),0,h.dp(16));
        row.addView(divider,d);
      }
      row.addView(cols[i],new LinearLayout.LayoutParams(0,-1,weights[i]));
    }
    NavyWorkbenchViewport viewport = new NavyWorkbenchViewport(row);
    viewport.setTag("navy-workbench-viewport");
    h.page.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
    h.body=cols[1];
    return cols;
  }

  /** The parent has already subtracted safe-area insets, padding and the measured header.
   * Never manufacture a larger canvas here: weighted panel bodies get only the remainder. */
  final class NavyWorkbenchRow extends LinearLayout {
    NavyWorkbenchRow() { super(h); setOrientation(HORIZONTAL); }
  }

  final class NavyWorkbenchViewport extends FrameLayout {
    NavyWorkbenchViewport(NavyWorkbenchRow row) {
      super(h);
      addView(row, new FrameLayout.LayoutParams(-1, -1));
    }
  }

  View navyEmptyResult() {
    LinearLayout empty=k.column();
    ScrollView viewport = new ScrollView(h);
    viewport.setTag("mix-result-empty");
    viewport.setFillViewport(true);
    viewport.setVerticalScrollBarEnabled(true);
    viewport.addView(empty,new ScrollView.LayoutParams(-1,-2));
    empty.setGravity(Gravity.CENTER);
    empty.setPadding(h.dp(10),h.dp(8),h.dp(10),h.dp(8));
    empty.setBackground(NavyWorkbenchTheme.surface(h,0x33061d2d,0,10));
    TextView glyph=h.text("◇",32);
    NavyWorkbenchTheme.text(h,glyph,32,false,NavyWorkbenchTheme.GOLD);
    glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    empty.addView(glyph);
    TextView title=h.text("재료를 더 선택하세요",14);
    NavyWorkbenchTheme.text(h,title,14,true,NavyWorkbenchTheme.TEXT);
    title.setGravity(Gravity.CENTER);
    empty.addView(title,new LinearLayout.LayoutParams(-1,-2));
    TextView detail=h.text("선택한 재료에 맞는 결과를\n이곳에서 확인할 수 있어요",11);
    NavyWorkbenchTheme.text(h,detail,11,false,NavyWorkbenchTheme.MUTED);
    detail.setGravity(Gravity.CENTER);
    empty.addView(detail,new LinearLayout.LayoutParams(-1,-2));
    return viewport;
  }

  void mix(LinearLayout[] cols, JSONObject data) {
    JSONArray pool = h.list(data, "pool");
    int limit = data.optInt("maxMaterials", 6); // 구 서버의 기존 한도와 호환.
    // 화면 재진입 때 보호·편성·보유 수량이 바뀐 초안을 정리한다.
    ArrayList<String> valid = new ArrayList<>();
    for (String id : m.mixDraft) {
      JSONObject unit = mixUnit(pool, id);
      if (CraftTierPolicy.material(unit.optString("tier")) && valid.size() < limit
          && Collections.frequency(valid, id) < unit.optInt("usable")) valid.add(id);
    }
    m.mixDraft.clear();
    m.mixDraft.addAll(valid);
    // 시안: 보유 갈매미맨 | 선택한 재료 › 가능한 결과, 아래에 선택 해제·조합하기.
    LinearLayout owned = benchPanel();
    cols[0].addView(owned, new LinearLayout.LayoutParams(-1, -1));
    owned.setPadding(0, 0, 0, 0);
    owned.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    LinearLayout inventory = k.column();
    owned.addView(inventory, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout selected = cols[1], results = cols[2];
    LinearLayout actions = k.row(); // kept as callback owner; buttons live under their own columns.
    actions.setTag("mix-actions");
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
    NavyWorkbenchTheme.button(h, clear, false);
    NavyWorkbenchTheme.button(h, make, true);
    reserveWorkbenchAction(clear);
    reserveWorkbenchAction(make);
    paint[0] = () -> {
      inventory.removeAllViews();
      selected.removeAllViews();
      results.removeAllViews();
      ArrayList<JSONObject> available = new ArrayList<>();
      for (int i = 0; i < pool.length(); i++) {
        JSONObject unit = pool.optJSONObject(i);
        if (unit != null && unit.optInt("usable") > 0
            && CraftTierPolicy.material(unit.optString("tier"))) available.add(unit);
      }
      // 한 번에 원본 등록증 한 장. 밀기는 탐색, 별도의 짧은 탭은 재료 한 개 추가.
      int pages = available.size();
      mixPage = Math.max(0, Math.min(mixPage, pages - 1));
      TextView position = h.text(pages == 0 ? "0 / 0" : (mixPage + 1) + " / " + pages, 14);
      position.setTextColor(JournalTheme.PAPER);
      position.setGravity(Gravity.CENTER);
      position.setSingleLine(true);
      position.setPadding(0, 0, 0, 0);
      position.setIncludeFontPadding(false);
      position.setTag("mix-material-position");

      MixCarousel choices = new MixCarousel(direction -> {
        if (h.busy) return;
        int destination = mixPage + direction;
        if (destination < 0 || destination >= pages) return;
        mixPage = destination;
        paint[0].run();
      });
      choices.setTag("mix-inventory");
      inventory.addView(choices, new LinearLayout.LayoutParams(-1, 0, 1));
      choices.page = mixPage;
      choices.pages = pages;
      inventory.addView(position, new LinearLayout.LayoutParams(-1, -2));
      NavyWorkbenchTheme.text(h, position, 10, false, NavyWorkbenchTheme.MUTED);

      if (available.isEmpty()) {
        TextView none = k.note("사용 가능한 재료가 없어요.");
        none.setTextColor(JournalTheme.PAPER);
        none.setGravity(Gravity.CENTER);
        choices.addView(none, new FrameLayout.LayoutParams(-1, -1));
      } else {
        JSONObject unit = available.get(mixPage);
        String id = unit.optString("id");
        int chosen = Collections.frequency(m.mixDraft, id);
        int left = unit.optInt("usable") - chosen;
        RegistrationCardView card = fullEconomyCard(unit);
        card.setTag("mix-browse-card-" + id);
        card.setSelected(chosen > 0);
        card.setForeground(RegistrationCardView.selection(h, chosen > 0));
        // The carousel owns one gesture stream; its accessibility click uses this same action.
        Runnable add = () -> {
          if (h.busy) return;
          if (m.mixDraft.size() >= limit) { h.root.announceForAccessibility("재료는 " + limit + "개까지 선택할 수 있어요."); return; }
          if (Collections.frequency(m.mixDraft, id) >= unit.optInt("usable")) {
            h.root.announceForAccessibility("사용 가능한 재료가 없어요."); return;
          }
          m.mixDraft.add(id);
          paint[0].run();
          h.root.announceForAccessibility(unit.optString("name") + " 재료 추가");
        };
        card.setOnClickListener(v -> add.run());
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        choices.tap = add;
        choices.setSelected(chosen > 0);
        choices.setContentDescription(RegistrationCardView.description(unit) + " · 사용 가능 " + left
            + (chosen > 0 ? " · 선택됨" : " · 눌러 재료 추가") + " · 좌우로 밀어 이동");
        choices.addView(card, new FrameLayout.LayoutParams(-1, -1));
        position.setSingleLine(false);
        position.setText((mixPage + 1) + " / " + pages + " · 눌러 선택 · 밀어 이동"
            + (chosen > 0 ? " · " + chosen + "개 선택" : ""));
      }
      LinearLayout heading = k.row();
      heading.setGravity(Gravity.CENTER_VERTICAL);
      TextView selectedTitle = benchHeading("선택한 재료");
      heading.addView(selectedTitle, new LinearLayout.LayoutParams(0, -2, 1));
      TextView count = countBadge(m.mixDraft.size() + " / " + limit);
      count.setTag("mix-count");
      count.setContentDescription("선택한 재료 " + m.mixDraft.size() + "/" + limit);
      NavyWorkbenchTheme.text(h,count,12,true,NavyWorkbenchTheme.TEXT);
      count.setBackground(NavyWorkbenchTheme.surface(h,0x55071f30,0,4));
      heading.addView(count, new LinearLayout.LayoutParams(-2, -2));
      selected.addView(heading, new LinearLayout.LayoutParams(-1, -2));
      // 처음부터 서버 한도의 모든 칸을 보여 준다. 0/6인데 네 칸만 보이던 혼동을 없앤다.
      int slots = Math.max(0, limit);
      MixMaterialGrid grid = new MixMaterialGrid(slots);
      grid.setTag("mix-workbench");
      grid.setContentDescription("선택한 재료 " + m.mixDraft.size() + "/" + limit);
      MixSlotViewport slotViewport = new MixSlotViewport(grid);
      selected.addView(slotViewport, new LinearLayout.LayoutParams(-1, 0, 1));
      boolean hint = false;
      for (int i = 0; i < slots; i++) {
        FrameLayout cell = new FrameLayout(h);
        grid.addView(cell);
        if (i >= m.mixDraft.size()) {
          cell.addView(emptySlot(i), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
          continue;
        }
        int index = i;
        JSONObject unit = mixUnit(pool, m.mixDraft.get(i));
        hint |= unit.optBoolean("hiddenHint");
        Shape slot = thumbTile(unit);
        cell.setTag("mix-material-" + m.mixDraft.get(i));
        TextView remove = h.text("−", 15);
        NavyWorkbenchTheme.text(h,remove,15,true,NavyWorkbenchTheme.TEXT);

        remove.setTextColor(NavyWorkbenchTheme.INK);
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(0, 0, 0, 0);
        remove.setIncludeFontPadding(false);
        android.graphics.drawable.GradientDrawable seal = new android.graphics.drawable.GradientDrawable();
        seal.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        seal.setColor(JournalTheme.NAVY);
        seal.setStroke(h.dp(2), JournalTheme.PAPER);
        remove.setBackground(NavyWorkbenchTheme.quiet(h,ThemeArt.Surface.PAPER));
        remove.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams sealSlot = new FrameLayout.LayoutParams(h.dp(24), h.dp(24), Gravity.TOP | Gravity.END);
        sealSlot.setMargins(0, h.dp(2), h.dp(2), 0);
        slot.addView(remove, sealSlot);
        // 칸 전체가 48dp 이상의 빼기 버튼이다. 같은 재료가 여러 칸이면 누른 칸만 뺀다.
        cell.setContentDescription(unit.optString("name") + " 재료에서 빼기 · " + (index + 1) + "번째 칸");
        slot.setClickable(false);
        slot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        cell.setOnClickListener(v -> {
          if (h.busy) return;
          m.mixDraft.remove(index);
          paint[0].run();
          h.root.announceForAccessibility(unit.optString("name") + " 재료 해제");
        });
        cell.addView(slot, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
      }
      selected.addView(clear, new LinearLayout.LayoutParams(-1, workbenchActionHeight(clear)));
      JSONObject match = m.mixDraft.size() >= 2 ? mixResult(h.list(data, "recipes")) : null;
      TextView resultHeading = benchHeading("가능한 결과");
      resultHeading.setTag("mix-result-heading");
      resultHeading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      results.addView(resultHeading, new LinearLayout.LayoutParams(-1, -2));
      LinearLayout cards = k.row();
      cards.setBaselineAligned(false);
      cards.setTag("mix-results");
      cards.setContentDescription("가능한 결과");
      results.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1));
      ArrayList<View> shown = new ArrayList<>();
      if (m.mixDraft.size() < 2) {
        View empty = navyEmptyResult();
        if (hint) {
          HiddenRecipeFrame clue = new HiddenRecipeFrame(h, true);
          clue.setTag("mix-result-card");
          clue.addView(empty,new FrameLayout.LayoutParams(-1,-1));
          cards.addView(clue,new LinearLayout.LayoutParams(-1,-1));
        } else shown.add(empty);
      } else {
        // 공개 결과와 미공개 단서를 함께 보여도 같은 결과라고 암시하지 않는다.
        if (match != null) shown.add(fullEconomyCard(match));
        if (hint || match == null) shown.add(unknownResult(hint ? "미공개" : "결과 미확인", hint));
      }
      boolean tagged = false;
      for (View card : shown) {
        if ("mix-result-empty".equals(card.getTag())) {
          cards.addView(card, new LinearLayout.LayoutParams(-1, -1));
          continue;
        }
        if (card instanceof HiddenRecipeFrame && !tagged) { card.setTag("mix-result-card"); tagged = true; }
        Shape ratio = new Shape(4f / 3f);
        ratio.addView(card, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout cell = new FrameLayout(h);
        cell.addView(ratio, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        cards.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
      }
      results.addView(make, new LinearLayout.LayoutParams(-1, workbenchActionHeight(make)));
      clear.setEnabled(!m.mixDraft.isEmpty());
      make.setEnabled(m.mixDraft.size() >= 2);
    };
    paint[0].run();
  }

  /** 큰 글씨가 두 줄 이상이면 그 높이를 행동 행에 먼저 확보한다. 글씨를 줄이지 않는다. */
  void reserveWorkbenchAction(TextView action) {
    String fullLabel=action.getText().toString();
    String concise=fullLabel.equals("조합하기") ? "조합" : fullLabel.equals("선택 해제") ? "비우기" : fullLabel;
    action.setContentDescription(fullLabel);
    action.setSingleLine(false);
    action.setMaxLines(Integer.MAX_VALUE);
    action.setEllipsize(null);
    action.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
      int width=action.getWidth()-action.getCompoundPaddingLeft()-action.getCompoundPaddingRight();
      String display=width>0 && action.getPaint().measureText(fullLabel)>width ? concise : fullLabel;
      if(!display.contentEquals(action.getText())) { action.setText(display); return; }
      if(action.getLayout()==null || action.getLayoutParams()==null) return;
      int required=Math.max(workbenchActionHeight(action),action.getLayout().getHeight()
          +action.getCompoundPaddingTop()+action.getCompoundPaddingBottom()+h.dp(8));
      if(action.getLayoutParams().height!=required) {
        action.getLayoutParams().height=required;
        action.requestLayout();
      }
    });
  }

  int workbenchActionHeight(TextView action) {
    android.graphics.Paint.FontMetrics f = action.getPaint().getFontMetrics();
    // Workbench labels deliberately exclude the bundled font's oversized top/bottom padding.
    // Reserving it anyway starves weighted card lanes on 222dp-high, 200% screens.
    float inkHeight = action.getIncludeFontPadding() ? f.bottom-f.top : f.descent-f.ascent;
    return Math.max(h.dp(48), (int)Math.ceil(inkHeight)
        + action.getCompoundPaddingTop() + action.getCompoundPaddingBottom() + h.dp(8));
  }

  /** 폭이 좁으면 열 수를 줄인다. 최소 터치 높이보다 부족한 세로 공간은 스크롤한다. */
  final class MixMaterialGrid extends ViewGroup {
    final int slots;
    int viewportHeight, columns, rowHeight;
    MixMaterialGrid(int slots) { super(h); this.slots = slots; }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      int width = MeasureSpec.getSize(widthSpec);
      columns = Math.max(1, Math.min(slots <= 4 ? 2 : 3, width / Math.max(1, h.dp(48))));
      int rows = (slots + columns - 1) / columns;
      rowHeight = Math.max(h.dp(48), viewportHeight / Math.max(1, rows));
      for (int i = 0; i < getChildCount(); i++) {
        int col = i % columns;
        int cellWidth = (col + 1) * width / columns - col * width / columns;
        getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY));
      }
      setMeasuredDimension(width, rows * rowHeight);
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
      for (int i = 0; i < getChildCount(); i++) {
        int col = i % columns, row = i / columns;
        getChildAt(i).layout(col * getWidth() / columns, row * rowHeight,
            (col + 1) * getWidth() / columns, (row + 1) * rowHeight);
      }
    }
  }

  final class MixSlotViewport extends ScrollView {
    final MixMaterialGrid grid;
    MixSlotViewport(MixMaterialGrid grid) {
      super(h);
      this.grid = grid;
      setVerticalScrollBarEnabled(true);
      setScrollbarFadingEnabled(false);
      addView(grid, new ScrollView.LayoutParams(-1, -2));
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      grid.viewportHeight = MeasureSpec.getSize(heightSpec);
      super.onMeasure(widthSpec, heightSpec);
    }
  }

  /** One gesture owner: a drag, cancellation or second pointer can never become an add. */
  final class MixCarousel extends FrameLayout {
    final java.util.function.IntConsumer turn;
    final int slop;
    float downX, downY;
    boolean moved, cancelled = true;
    int page, pages;
    Runnable tap;
    String actionLabel = "재료 한 개 추가";
    MixCarousel(java.util.function.IntConsumer turn) {
      super(h); this.turn = turn;
      slop = ViewConfiguration.get(h).getScaledTouchSlop();
      setClickable(true); setFocusable(true);
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }
    @Override public boolean onTouchEvent(MotionEvent event) {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downX = event.getX(); downY = event.getY(); moved = false; cancelled = false;
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
          return true;
        case MotionEvent.ACTION_POINTER_DOWN:
          cancelled = true; return true;
        case MotionEvent.ACTION_MOVE:
          moved |= Math.abs(event.getX()-downX) > slop || Math.abs(event.getY()-downY) > slop;
          if (event.getPointerCount() != 1) cancelled = true;
          return true;
        case MotionEvent.ACTION_UP:
          float dx = event.getX()-downX, dy = event.getY()-downY;
          moved |= Math.abs(dx) > slop || Math.abs(dy) > slop;
          if (!cancelled) {
            if (moved && Math.abs(dx) >= Math.max(h.dp(48),getWidth()*.12f) && Math.abs(dx)>Math.abs(dy)*1.25f)
              turn.accept(dx < 0 ? 1 : -1);
            else if (!moved) performClick();
          }
          cancelled = true;
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
          return true;
        case MotionEvent.ACTION_CANCEL:
          cancelled = true;
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
          return true;
        default: return true;
      }
    }
    @Override public boolean performClick() {
      super.performClick(); if (tap != null) tap.run(); return true;
    }
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      info.setClassName("android.widget.Button");
      info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
          android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, actionLabel));
      info.setScrollable(pages > 1);
      if (page > 0) info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
          android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, "이전 갈매미맨"));
      if (page + 1 < pages) info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
          android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, "다음 갈매미맨"));
    }
    @Override public boolean performAccessibilityAction(int action, android.os.Bundle args) {
      if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && page > 0) { turn.accept(-1); return true; }
      if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && page + 1 < pages) { turn.accept(1); return true; }
      return super.performAccessibilityAction(action,args);
    }
  }

  /** 아주 낮은 가로 화면: 선택 칸 두 줄이 48dp를 넘도록 누르지 않는 제목·여백만 줄인다. */
  boolean tightBench() {
    return h.contentHeightDp() - 236 < 112;
  }

  /** 승인 시안: 개별 금테 대신 하나의 배경과 얇은 구분선. */
  LinearLayout benchPanel() {
    LinearLayout panel = k.column();
    panel.setPadding(h.dp(10), h.dp(8), h.dp(10), h.dp(8));
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
    Shape tile = new Shape(4f / 3f);
    android.graphics.drawable.GradientDrawable frame = new android.graphics.drawable.GradientDrawable();
    frame.setColor(0xffc3b58d);
    frame.setCornerRadius(h.dp(6));
    frame.setStroke(Math.max(1,h.dp(.8f)),0xffa79570);
    tile.setBackground(frame);
    tile.setPadding(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
    tile.setClickable(true);
    tile.setMinimumWidth(h.dp(48));
    tile.setMinimumHeight(h.dp(48));
    RegistrationCardView art = fullEconomyCard(unit);
    art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
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
    slot.setBackground(NavyWorkbenchTheme.surface(h,0x66051d2d,0xff67716b,8));
    TextView plus = h.text("＋", 26);
    NavyWorkbenchTheme.text(h,plus,26,false,0xffd9c595);
    plus.setGravity(Gravity.CENTER);
    plus.setPadding(0, 0, 0, 0);
    slot.addView(plus, new FrameLayout.LayoutParams(-1, -1));
    slot.setContentDescription("빈 재료 칸 " + (index + 1) + " · 왼쪽에서 카드를 찾고 재료 추가");
    return slot;
  }

  /** 한국어도 음절 중간이 아니라 띄어쓰기에서만 줄을 바꾼다(실제 폭을 재서, SDK 24 호환). */
  static void wrapAtSpaces(TextView label, String text) {
    label.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
    label.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      int width = label.getWidth() - label.getCompoundPaddingLeft() - label.getCompoundPaddingRight();
      if (width <= 0) return;
      StringBuilder wrapped = new StringBuilder(), line = new StringBuilder();
      for (String word : text.split(" ")) {
        String candidate = line.length() == 0 ? word : line + " " + word;
        if (line.length() > 0 && label.getPaint().measureText(candidate) > width) {
          wrapped.append(line).append('\n');
          line.setLength(0);
        }
        if (line.length() > 0) line.append(' ');
        line.append(word);
      }
      wrapped.append(line);
      if (!wrapped.toString().contentEquals(label.getText())) label.post(() -> label.setText(wrapped));
    });
  }

  HiddenRecipeFrame unknownResult(String label, boolean hint) {
    // 아주 낮은 화면의 작은 카드에서는 음절 중간에서 끊기지 않게 한 단어로 줄인다.
    if (tightBench()) label = label.equals("재료 선택 중") ? "선택 중" : label.equals("결과 미확인") ? "미확인" : label;
    JSONObject unknown = MainActivity.obj("known", false, "name", label, "tierName", "", "img", "unknown");
    HiddenRecipeFrame frame = new HiddenRecipeFrame(h, hint);
    RegistrationCardView card = fullEconomyCard(unknown);
    // 시안처럼 미공개 카드에는 상태 한 줄만 둔다(빈 등급 줄이 공간을 차지하지 않게).
    card.grade.setVisibility(View.GONE);
    wrapAtSpaces(card.name, label);
    frame.addView(card, new FrameLayout.LayoutParams(-1, -1));
    return frame;
  }

  /** 작업대 제목 칸: 글자를 세로 가운데에 두고 종이 모서리 장식과 겹치지 않게 띄운다. */
  TextView benchHeading(String text) {
    TextView label = new TextView(h) {
      @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        // At accessibility sizes use a shorter equivalent label, not smaller or clipped glyphs.
        String display = available > 0 && getPaint().measureText(text) > available
            ? (text.equals("선택한 재료") ? "재료" : text.equals("가능한 결과") ? "결과" : text) : text;
        if (!display.contentEquals(getText())) setText(display);
        super.onMeasure(widthSpec, heightSpec);
      }
    };
    label.setText(text);
    label.setContentDescription(text);
    label.setBackgroundColor(Color.TRANSPARENT);
    NavyWorkbenchTheme.text(h,label,16,true,NavyWorkbenchTheme.TEXT);
    label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    label.setPadding(h.dp(6), h.dp(4), h.dp(6), h.dp(4)); // panel 10dp + heading 6dp = 16dp leading inset
    label.setMinHeight(h.dp(32));
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
    expeditionPreparation = null;
    h.base("탐험대", "expedition");
    economyScene("routes");
    expeditionHeader();
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
    expeditionPreparation = new NativeExpeditionPreparation(this);
  }

  /** All ten ordered slots are visible together. Duplicate IDs still remove by exact index. */
  void partyStrip(LinearLayout parent, List<String> ids, boolean editable, Runnable repaint) {
    LinearLayout content = k.column();
    content.setTag("expedition-selected-crew");
    parent.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    selectedCrewPage = 0;
    int slots = Math.max(partyMax(), ids.size()), columns = 5;
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
          tile.setBackground(expeditionSurface(false));
          TextView empty = h.text("＋", 18);
          empty.setTextColor(EXPEDITION_CREAM);
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
          if (!editable) tile.setOnClickListener(v -> expeditionMember(unit));
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
    label.setBackground(expeditionSurface(false));
    label.setTextColor(EXPEDITION_CREAM);
    label.setTextSize(h.uiText(12));
    label.setPadding(h.dp(6), h.dp(1), h.dp(6), h.dp(1));
    return label;
  }

  LinearLayout crewTile(JSONObject unit, String detail) {
    LinearLayout tile = k.column();
    // 탐험에만 적용: 원본 그림과 48dp 터치 영역, 두 줄 이름을 보존한다.
    tile.setPadding(h.dp(2), h.dp(1), h.dp(2), h.dp(1));
    tile.setGravity(Gravity.CENTER);
    tile.setBackground(PartyCharacterView.selection(h, false));
    tile.setContentDescription(unit.optString("name") + " · " + detail);
    PartyCharacterView.Artwork portrait = PartyCharacterView.art(h, unit);
    portrait.backplate(Color.TRANSPARENT);
    portrait.setTag("expedition-character-art");
    portrait.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    tile.addView(portrait, new LinearLayout.LayoutParams(-1, 0, 1));
    // 긴 전체 이름은 길게 누르기 또는 진행 중 카드 누르기로 확인한다.
    TextView status = PartyCharacterView.nameLabel(h, unit.optString("name"));
    status.setTextSize(h.uiText(12));
    status.setTypeface(h.prose);
    status.setPadding(h.dp(3), h.dp(1), h.dp(3), h.dp(1));
    status.setSingleLine(false);
    status.setMaxLines(h.getResources().getConfiguration().fontScale >= 1.6f ? 1 : 2);
    status.setTextColor(EXPEDITION_CREAM);
    status.setBackground(expeditionShape(0xe512293b, 0x809d956b, 7));
    status.setHorizontallyScrolling(false);
    status.setEllipsize(android.text.TextUtils.TruncateAt.END);
    status.setTag("economy-name-preview");
    status.setContentDescription(unit.optString("name"));
    tile.addView(status, new LinearLayout.LayoutParams(-1, -2));
    tile.setMinimumHeight(h.dp(48));
    tile.setOnLongClickListener(v -> { expeditionMember(unit); return true; });
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
    if (tag.startsWith("expedition-")) expeditionButton(prev, false);
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
    if (tag.startsWith("expedition-")) expeditionButton(next, false);
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
      if (unit != null && unit.optBoolean("top") && expeditionAvailable(unit) > 0)
        available.add(unit);
    }
    int candidateColumns = h.getResources().getConfiguration().screenWidthDp < 700 ? 3 : 4;
    int perPage = candidateColumns * 2;
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
      for (int col = 0; col < candidateColumns; col++) {
        int index = crewPage * perPage + row * candidateColumns + col;
        if (index >= available.size()) { group.addView(new View(h), new LinearLayout.LayoutParams(0, -1, 1)); continue; }
        JSONObject unit = available.get(index);
        String id = unit.optString("id");
        int left = expeditionAvailable(unit) - Collections.frequency(h.expeditionDraft, id);
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
      TextView empty = k.note(ids.isEmpty() ? "갈매미를 선택해 주세요." : "보상·시너지에서 자세히 확인해요.");
      empty.setTextColor(EXPEDITION_CREAM);
      parent.addView(empty);
      return;
    }
    ArrayList<EconomyPreview.Member> members = new ArrayList<>();
    for (String id : ids) members.add(new EconomyPreview.Member(id, find(id).optString("synergy")));
    EconomyPreview.Plan plan = EconomyPreview.estimate(previewRules(rules), members);
    TextView estimate = k.note(plan.minutes + "분 · 기본 계산 " + plan.snacks + " 새우gang");
    estimate.setTextColor(EXPEDITION_CREAM);
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

  static final int EXPEDITION_CREAM = 0xfff4ead4;

  android.graphics.drawable.Drawable expeditionShape(int fill, int edge, int radius) {
    android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
    shape.setColor(fill);
    shape.setCornerRadius(h.dp(radius));
    shape.setStroke(Math.max(1, h.dp(.65f)), edge);
    return shape;
  }

  android.graphics.drawable.Drawable expeditionSurface(boolean emphasis) {
    return expeditionShape(emphasis ? 0xec294659 : 0xd0162c40, 0xc9c0aa72, 12);
  }

  void expeditionHeader() {
    boolean narrow = h.getResources().getConfiguration().screenWidthDp < 700;
    h.page.setPadding(h.dp(narrow ? 16 : 28), 0, h.dp(narrow ? 16 : 28), h.dp(16));
    LinearLayout head = h.root.findViewWithTag("theme-page-header");
    View back = head.getChildAt(0);
    ArrayList<View> balances = new ArrayList<>();
    for (int i = 2; i < head.getChildCount(); i++) balances.add(head.getChildAt(i));
    head.removeAllViews();
    head.setPadding(0, 0, 0, 0);
    LinearLayout navigation = k.row();
    navigation.setTag("expedition-navigation");
    navigation.setBackground(new android.graphics.drawable.InsetDrawable(expeditionSurface(false), 0, h.dp(8), 0, h.dp(8)));
    back.setBackgroundColor(Color.TRANSPARENT);
    navigation.addView(back, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    h.title.setBackgroundColor(Color.TRANSPARENT);
    h.title.setTextColor(EXPEDITION_CREAM);
    h.title.setTypeface(h.prose);
    h.title.setTextSize(h.uiText(16));
    h.title.setPadding(0, 0, h.dp(10), 0);
    int labelWidth = (int) Math.ceil(h.title.getPaint().measureText("탐험대")) + h.dp(12);
    navigation.addView(h.title, new LinearLayout.LayoutParams(labelWidth, h.dp(48)));
    head.addView(navigation);
    head.addView(new View(h), new LinearLayout.LayoutParams(0, 1, 1));
    for (View balance : balances) {
      Button money = (Button) balance;
      money.setBackground(new android.graphics.drawable.InsetDrawable(expeditionSurface(false), 0, h.dp(8), 0, h.dp(8)));
      money.setTextColor(EXPEDITION_CREAM);
      int width = Math.max(h.dp(76), (int) Math.ceil(money.getPaint().measureText(money.getText().toString())) + h.dp(48));
      LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(width, h.dp(48));
      slot.leftMargin = h.dp(4);
      head.addView(money, slot);
    }
  }

  View expeditionCompass() {
    return new View(h) {
      final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
      @Override protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) * .68f;
        p.setColor(0xffd9c38b); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(h.dp(1));
        canvas.drawCircle(cx, cy, r * .68f, p);
        for (int i = 0; i < 8; i++) {
          double a = Math.PI * i / 4;
          canvas.drawLine(cx + (float)Math.sin(a)*r*.76f, cy + (float)Math.cos(a)*r*.76f,
              cx + (float)Math.sin(a)*r*1.18f, cy + (float)Math.cos(a)*r*1.18f, p);
        }
        Path needle = new Path();
        needle.moveTo(cx, cy-r); needle.lineTo(cx+r*.22f, cy); needle.lineTo(cx, cy+r);
        needle.lineTo(cx-r*.22f, cy); needle.close();
        p.setStyle(Paint.Style.FILL); canvas.drawPath(needle, p);
      }
    };
  }

  void expeditionButton(Button button, boolean emphasis) {
    android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
    states.addState(new int[] {android.R.attr.state_pressed}, expeditionShape(0xf03b5866, 0xffdfc78c, 24));
    states.addState(new int[] {}, expeditionShape(emphasis ? 0xf0294e60 : 0xe4112a3e, 0xd9c3ae77, 24));
    // 48dp hit region remains intact; the inset keeps the reference's slender pill silhouette.
    int visualInset = h.dp(h.getResources().getConfiguration().fontScale >= 1.6f ? 3 : 8);
    button.setBackground(new android.graphics.drawable.InsetDrawable(states, 0, visualInset, 0, visualInset));
    button.setTextColor(EXPEDITION_CREAM);
    button.setTypeface(h.prose);
    button.setMinHeight(h.dp(48));
    button.setMinimumHeight(h.dp(48));
    button.setPadding(h.dp(6), 0, h.dp(6), 0);
    button.setMaxLines(2);
    button.setTextSize(h.uiText(h.getResources().getConfiguration().fontScale >= 1.6f ? 10 : 14));
  }

  int expeditionAvailable(JSONObject unit) {
    if (unit.optBoolean("temporary")) return 0;
    int permanent = unit.has("permanentCount") ? unit.optInt("permanentCount")
        : unit.optInt("have") - unit.optInt("temporaryCount");
    return Math.max(0, permanent - unit.optInt("away"));
  }

  boolean validExpeditionParty() {
    if (h.expeditionDraft.size() > partyMax()) return false;
    for (String id : h.expeditionDraft) {
      JSONObject unit = find(id);
      if (!unit.optBoolean("top") || Collections.frequency(h.expeditionDraft, id) > expeditionAvailable(unit)) return false;
    }
    return true;
  }

  void expeditionMember(JSONObject unit) {
    if (h.busy || h.visibleDialog != null) return;
    h.message(unit.optString("name") + "\n" + synergyLabel(unit.optString("synergy")));
  }

  // The server anchor uses elapsedRealtime, so changing the device wall clock cannot finish a trip.
  long expeditionNow() { return TemporaryUnitPresentation.now(); }

  void refreshExpeditionOnResume() {
    if (!h.screen.equals("expedition") || h.busy || h.updateBlocks()) return;
    if (h.visibleDialog != null) h.visibleDialog.dismiss();
    h.load("expedition");
  }

  ArrayList<String> tripParty(JSONObject trip) {
    ArrayList<String> party = new ArrayList<>();
    JSONArray ids = h.list(trip, "ids");
    for (int i = 0; i < ids.length(); i++) party.add(ids.optString(i));
    return party;
  }

  String tripReward(JSONObject trip) {
    JSONObject plan = trip.optJSONObject("plan");
    if (plan == null) return "출발 보상 정보 확인 필요";
    long base = Math.round(plan.optInt("baseSnacks") * (1 + plan.optInt("snackPercent") / 100.0))
        + (long) plan.optInt("flatPerUnit") * plan.optInt("size");
    return "기본 계산 · " + base + " 새우gang";
  }

  String tripSynergies(JSONObject trip) {
    JSONObject plan = trip.optJSONObject("plan");
    if (plan == null) return "보상은 서버에서 수령 시 확정돼요";
    ArrayList<String> labels = new ArrayList<>();
    JSONArray active = h.list(plan, "synergies");
    for (int i = 0; i < active.length(); i++) {
      JSONObject item = active.optJSONObject(i);
      if (item != null) labels.add(item.optString("label") + " " + item.optInt("level") + "단계");
    }
    JSONObject bonus = plan.optJSONObject("countBonus");
    if (bonus != null) labels.add("중복 +" + bonus.optInt("percent") + "%");
    return labels.isEmpty() ? "적용 시너지 없음" : String.join(" · ", labels);
  }

  String tripChances(JSONObject trip) {
    JSONObject plan = trip.optJSONObject("plan");
    if (plan == null) return "확률 보상 정보 확인 필요";
    ArrayList<String> extra = new ArrayList<>();
    if (plan.optInt("doublePercent") > 0) extra.add("새우gang 2배 " + plan.optInt("doublePercent") + "%");
    if (plan.optInt("commonPercent") > 0) extra.add("커먼 1마리 " + plan.optInt("commonPercent") + "%");
    if (plan.optInt("uncommonPercent") > 0) extra.add("레어 1마리 " + plan.optInt("uncommonPercent") + "%");
    if (plan.optInt("ticketPercent") > 0) extra.add("지원서 1장 " + plan.optInt("ticketPercent") + "%");
    return extra.isEmpty() ? "추가 확률 보상 없음" : "추가 확률 · " + String.join(" · ", extra);
  }

  void tripDetails(JSONObject trip) {
    LinearLayout details = k.paper();
    details.addView(k.note(tripReward(trip)));
    details.addView(k.note("출발 시 저장된 계산값 · 통계적 기대값이 아니에요"));
    details.addView(k.note(tripChances(trip)));
    details.addView(k.note(tripSynergies(trip)));
    for (String id : tripParty(trip)) details.addView(k.note(find(id).optString("name")));
    details.addView(k.action(null, "시너지 안내", this::synergyGuide), new LinearLayout.LayoutParams(-1, h.dp(48)));
    new GameDialog(h).setTitle("탐험 보상·시너지").setScrollBody(details)
        .setPositiveButton("닫기", null).show();
  }

  // One read per deliberate action. Existing request/mutate owns busy, failures and uncertain-result recovery.
  void tripAction(JSONObject shownTrip, boolean claiming) {
    if (h.busy || h.visibleDialog != null) return;
    h.request("native/state", null, (fresh, ignored) -> {
      h.state = fresh;
      h.stateUpdated();
      JSONObject current = h.me().optJSONObject("trip");
      if (current == null || !current.optString("startedAt").equals(shownTrip.optString("startedAt"))) {
        h.show("expedition");
        return;
      }
      boolean complete = expeditionNow() > 0 && expeditionNow() >= time(current.optString("endsAt"));
      if (claiming && complete) {
        h.mutate("expedition/claim", MainActivity.obj(), "expedition");
        return;
      }
      if (complete || claiming || expeditionNow() <= 0) { h.show("expedition"); return; }
      JSONObject prices = h.me().optJSONObject("prices");
      JSONObject skip = prices == null ? null : prices.optJSONObject("skip");
      int cost = skip == null ? -1 : skip.optInt("snack", -1);
      h.show("expedition");
      if (cost < 0) { h.message("즉시 완료 비용을 불러오지 못했어요. 다시 시도해 주세요."); return; }
      if (h.me().optInt("snack") < cost) { h.message("새우gang " + cost + "개가 필요해요."); return; }
      final int confirmationEpoch = h.epoch;
      h.confirm("새우gang " + cost + "개로 즉시 완료할까요?\n보상 받기는 별도이며, 수령할 때 대원이 돌아와요.", () -> {
        if (h.epoch != confirmationEpoch || !h.screen.equals("expedition")) return;
        // Recheck price, balance and this same expedition after a long-open dialog / app return.
        h.request("native/state", null, (latest, error) -> {
          h.state = latest;
          h.stateUpdated();
          JSONObject active = h.me().optJSONObject("trip");
          JSONObject latestPrices = h.me().optJSONObject("prices");
          JSONObject latestSkip = latestPrices == null ? null : latestPrices.optJSONObject("skip");
          int latestCost = latestSkip == null ? -1 : latestSkip.optInt("snack", -1);
          if (active == null || !active.optString("startedAt").equals(current.optString("startedAt"))
              || expeditionNow() <= 0 || expeditionNow() >= time(active.optString("endsAt"))) {
            h.show("expedition"); return;
          }
          if (latestCost != cost || h.me().optInt("snack") < cost) {
            h.show("expedition"); h.message("비용 또는 잔액이 바뀌었어요. 다시 확인해 주세요."); return;
          }
          h.mutate("shop", MainActivity.obj("item", "skip", "count", 1), "expedition");
        });
      });
    });
  }

  void showTrip(JSONObject trip) {
    long start = time(trip.optString("startedAt")), end = time(trip.optString("endsAt"));
    boolean largeText = h.getResources().getConfiguration().fontScale >= 1.6f;
    boolean compactSummary = largeText || h.getResources().getConfiguration().fontScale >= 1.2f
        || h.getResources().getConfiguration().screenWidthDp < 700;
    LinearLayout panel = economyBody("expedition-trip");
    panel.setPadding(0, 0, 0, 0);
    panel.setOrientation(LinearLayout.HORIZONTAL);
    panel.setBaselineAligned(false);
    LinearLayout crew = k.column(), info = k.column();
    crew.setTag("expedition-crew-panel");
    info.setTag("expedition-status-panel");
    crew.setBackground(expeditionSurface(false));
    info.setBackground(expeditionSurface(false));
    crew.setPadding(h.dp(10), h.dp(4), h.dp(10), h.dp(10));
    info.setPadding(h.dp(12), h.dp(largeText ? 6 : 10), h.dp(12), h.dp(6));
    LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, .68f);
    left.rightMargin = h.dp(12);
    panel.addView(crew, left);
    panel.addView(info, new LinearLayout.LayoutParams(0, -1, .32f));
    ArrayList<String> party = tripParty(trip);
    LinearLayout heading = k.row();
    heading.addView(expeditionCompass(), new LinearLayout.LayoutParams(h.dp(34), h.dp(40)));
    TextView title = h.text("탐험 중", largeText ? 20 : 32);
    title.setTag("expedition-state");
    title.setIncludeFontPadding(false);
    title.setTypeface(h.prose);
    title.setTextColor(EXPEDITION_CREAM);
    title.setPadding(h.dp(4), 0, 0, 0);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView count = h.text("대원 " + party.size() + " / " + partyMax(), largeText ? 10 : 13);
    count.setTypeface(h.prose);
    count.setTextColor(EXPEDITION_CREAM); count.setPadding(0, 0, h.dp(4), 0);
    heading.addView(count);
    crew.addView(heading, new LinearLayout.LayoutParams(-1, h.dp(largeText ? 52 : 42)));
    View rule = new View(h); rule.setBackgroundColor(0xbac5aa6a);
    crew.addView(rule, new LinearLayout.LayoutParams(-1, Math.max(1, h.dp(.65f))));
    partyStrip(crew, party, false, null);

    TextView timerLabel = h.text("남은 시간", largeText ? 10 : 14);
    timerLabel.setTag("expedition-time-label");
    timerLabel.setTypeface(h.prose);
    timerLabel.setTextColor(EXPEDITION_CREAM);
    timerLabel.setPadding(0, 0, 0, 0);
    timerLabel.setSingleLine(true);
    info.addView(timerLabel);
    TextView remaining = h.text("", largeText ? 20 : 42);
    remaining.setTextColor(EXPEDITION_CREAM);
    remaining.setTypeface(h.prose);
    remaining.setIncludeFontPadding(false);
    remaining.setPadding(0, 0, 0, 0);
    remaining.setMaxLines(1);
    remaining.setTag("expedition-remaining");
    info.addView(remaining);
    ProgressBar progress = new ProgressBar(h, null, android.R.attr.progressBarStyleHorizontal);
    progress.setTag("expedition-progress");
    progress.setMax(1000);
    progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff35c6ee));
    progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x707c97ab));
    LinearLayout.LayoutParams progressSlot = new LinearLayout.LayoutParams(-1, h.dp(10));
    progressSlot.topMargin = h.dp(4);
    info.addView(progress, progressSlot);

    // The reference's compass still life is reserved for the actual saved-plan reward summary.
    LinearLayout summary = k.column();
    summary.setGravity(Gravity.CENTER_VERTICAL);
    info.addView(summary, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView reward = k.note(tripReward(trip));
    reward.setTag("expedition-base-reward");
    reward.setTextColor(EXPEDITION_CREAM);
    reward.setPadding(0, h.dp(2), 0, 0);
    reward.setTextSize(h.uiText(largeText ? 11 : 13));
    reward.setMaxLines(compactSummary ? 1 : 2);
    reward.setEllipsize(android.text.TextUtils.TruncateAt.END);
    reward.setContentDescription(tripReward(trip));
    summary.addView(reward);
    TextView synergy = k.note(tripSynergies(trip));
    synergy.setTextColor(0xffddd4bc);
    synergy.setPadding(0, h.dp(2), 0, 0);
    synergy.setTextSize(h.uiText(11));
    synergy.setMaxLines(2);
    synergy.setEllipsize(android.text.TextUtils.TruncateAt.END);
    if (compactSummary) synergy.setVisibility(View.GONE);
    summary.addView(synergy);
    // Large fonts and short safe areas reserve the timer and both full-size actions first.
    // The fixed detail action always exposes the complete saved plan if this summary cannot fit.
    summary.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
      int room = b - t;
      int rewardHeight = reward.getLineHeight() + reward.getPaddingTop() + reward.getPaddingBottom();
      int rewardVisibility = room >= rewardHeight ? View.VISIBLE : View.GONE;
      if (reward.getVisibility() != rewardVisibility) reward.setVisibility(rewardVisibility);
      int synergyVisibility = !compactSummary && room >= reward.getMeasuredHeight() + synergy.getMeasuredHeight()
          ? View.VISIBLE : View.GONE;
      if (synergy.getVisibility() != synergyVisibility) synergy.setVisibility(synergyVisibility);
    });

    LinearLayout actions = k.column();
    actions.setTag("expedition-trip-actions");
    Button details = k.action(null, "보상·시너지 보기  ›", () -> tripDetails(trip));
    details.setTag("expedition-trip-details");
    Button action = k.primary(null, "", () -> tripAction(trip, expeditionNow() > 0 && expeditionNow() >= end));
    action.setTag("expedition-trip-action");
    expeditionButton(details, false);
    expeditionButton(action, false);
    int actionHeight = h.dp(largeText ? 64 : 48);
    actions.addView(details, new LinearLayout.LayoutParams(-1, actionHeight));
    TextView costNote = k.note("확인 후 재화 사용");
    costNote.setGravity(Gravity.CENTER); costNote.setPadding(0, 0, 0, 0);
    costNote.setTextColor(0xffc2baa8); costNote.setTextSize(h.uiText(9));
    if (largeText) costNote.setVisibility(View.GONE);
    actions.addView(costNote);
    actions.addView(action, new LinearLayout.LayoutParams(-1, actionHeight));
    info.addView(actions);
    tick(panel, () -> {
      long now = expeditionNow(), leftMillis = Math.max(0, end - now);
      boolean complete = now > 0 && leftMillis == 0;
      title.setText(complete ? "수령 대기" : "탐험 중");
      timerLabel.setText(complete ? "탐험 완료" : "남은 시간");
      remaining.setText(now <= 0 ? "확인 필요" : complete ? "완료" : ((leftMillis + 59999) / 60000) + "분");
      remaining.setContentDescription(complete ? "탐험 완료 · 보상 수령 대기" : "남은 시간 " + duration(leftMillis));
      progress.setProgress(now <= 0 ? 0 : (int) Math.min(1000, Math.max(0, (now - start) * 1000 / Math.max(1, end - start))));
      JSONObject prices = h.me().optJSONObject("prices");
      JSONObject skip = prices == null ? null : prices.optJSONObject("skip");
      int cost = skip == null ? -1 : skip.optInt("snack", -1);
      action.setText(complete ? "보상 받기" : cost < 0 ? "즉시 완료 · 비용 확인" : "즉시 완료 ·" + (largeText ? "\n" : " ") + cost + " 새우gang");
      costNote.setText(complete ? "수령하면 대원이 돌아와요" : "확인 후 재화 사용");
      if (action.isSelected() != complete) {
        action.setSelected(complete); expeditionButton(action, complete);
      }
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
            "기본 계산 · "
                + plan.minutes
                + "분 / 새우gang "
                + plan.snacks
                + (plan.doublePercent > 0 ? " · 2배 " + plan.doublePercent + "%" : "")));
    ArrayList<String> extra = new ArrayList<>();
    if (plan.commonPercent > 0) extra.add("커먼 1마리 " + plan.commonPercent + "%");
    if (plan.uncommonPercent > 0) extra.add("레어 1마리 " + plan.uncommonPercent + "%");
    if (plan.ticketPercent > 0) extra.add("지원서 1장 " + plan.ticketPercent + "%");
    if (!extra.isEmpty()) parent.addView(k.note("추가 확률 보상 · " + String.join(" · ", extra)));
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
                            ? "커먼 1마리 " + v[0] + "/" + v[1] + "%"
                            : key.equals("BLOSSOM")
                                ? "레어 1마리 " + v[0] + "/" + v[1] + "%"
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








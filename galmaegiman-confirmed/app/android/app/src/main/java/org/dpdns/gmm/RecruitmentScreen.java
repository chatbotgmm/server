package org.dpdns.gmm;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Recruitment-only presentation. Requests, rewards and reveal remain in NativeEconomy. */
final class RecruitmentScreen extends ViewGroup {
  private static final int NAVY = 0xED193047, CREAM = 0xFFF5E2B5, INK = 0xFF17334A, WHITE = 0xFFF4F2EB;
  final NativeEconomy owner;
  final MainActivity h;
  final Typeface regularFont, boldFont;
  private static Typeface cachedRegular, cachedBold;
  final JSONObject response;
  final LinearLayout tiers, actions, quantity, costRow, information, purchase;
  final View ornament, lowerDivider, maximumDivider;
  final FrameLayout stage;
  final TextView heading, cost, pity, total, wait;
  final Button claim, go, minus, plus, maximum, info;
  final EditText count;
  final List<Tier> choices = new ArrayList<>();
  JSONObject selected;
  int amount = 1, limit, unitCost, headingSp = 32;
  boolean editing, compact, stackedSupport, layoutFits, decorationsReduced;
  ValueAnimator transition;
  String displayedKind = "";
  int leftWidth, stageWidth, rightWidth, footerHeight, bodyHeight;
  int measuredWidth = -1, measuredHeight = -1, contentRevision, measuredRevision = -1;
  float measuredFontScale = -1;
  int measuredDensity = -1;
  // 새 60종 모집: 같은 화면·트레이를 쓰고 요청만 새 API로 보낸다. null이면 기존 모집.
  final Roster60RecruitPurchase roster;

  RecruitmentScreen(NativeEconomy owner, JSONObject response) { this(owner, response, null); }

  RecruitmentScreen(NativeEconomy owner, JSONObject response, Roster60RecruitPurchase roster) {
    super(owner.h);
    this.roster = roster;
    this.owner = owner; h = owner.h; this.response = response;
    regularFont = recruitmentFont(h, false); boldFont = recruitmentFont(h, true);
    setTag("recruitment-stations");
    setPadding(h.dp(8), h.dp(11), h.dp(8), h.dp(6));
    tiers = column(); tiers.setTag("draw-tier-selector"); addView(tiers);
    stage = new FrameLayout(h); stage.setTag("draw-tray-stage");
    stage.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS); addView(stage);
    actions = column(); actions.setTag("draw-purchase-rail"); addView(actions);
    View informationSpace = new View(h); informationSpace.setTag("draw-information-space");
    informationSpace.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    actions.addView(informationSpace, new LinearLayout.LayoutParams(-1, h.dp(2), 1));
    information = column(); information.setTag("draw-information");
    information.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
        new int[] {0x00193047, 0xE6132C43, 0xEE132C43, 0xEE132C43,
            0xEE132C43, 0xEE132C43, 0xEE132C43, 0xE6132C43, 0x00193047}));
    actions.addView(information, new LinearLayout.LayoutParams(-1, -2));
    heading = label("", 32, true); heading.setPadding(h.dp(4), 0, h.dp(4), 0); heading.setTextColor(0xFFFFE5B6); heading.setGravity(Gravity.CENTER); information.addView(heading);
    ornament = new AnchorDivider(); ornament.setTag("draw-anchor-divider");
    information.addView(ornament, new LinearLayout.LayoutParams(-1, h.dp(6)));
    costRow = new LinearLayout(h); costRow.setGravity(Gravity.CENTER); costRow.setBaselineAligned(false);
    cost = label("", 16, false); cost.setTag("draw-unit-cost"); cost.setGravity(Gravity.CENTER);
    cost.setMinimumHeight(h.dp(28)); cost.setBackground(shape(0xB3193047, 0xFFC4AD7D));
    costRow.addView(cost, new LinearLayout.LayoutParams(h.dp(184), -2)); information.addView(costRow);
    info = button("", false, () -> new GameDialog(h).setTitle("모집 안내")
        .setMessage(RecruitmentProbabilityView.pityText(response)).setPositiveButton("닫기", (d, w) -> {}).show());
    info.setTypeface(regularFont); info.setTextSize(14); info.setTag("draw-pity");
    info.setBackground(pityStates()); info.setContentDescription("천장 보장 안내 · " + RecruitmentProbabilityView.pityText(response));
    information.addView(info, new LinearLayout.LayoutParams(-1, -2));
    // 새 60종에는 천장(보장) 규칙이 정해지지 않았다. 없는 규칙을 안내하지 않는다.
    if (roster != null) { info.setVisibility(GONE); roster.changed = () -> setAmount(amount); }
    pity = label(RecruitmentProbabilityView.pityText(response), 14, false); pity.setTag("draw-pity-detail");
    pity.setVisibility(GONE); information.addView(pity);
    lowerDivider = new AnchorDivider(); lowerDivider.setTag("draw-information-divider");
    information.addView(lowerDivider, new LinearLayout.LayoutParams(-1, h.dp(6)));
    View actionSpace = new View(h); actionSpace.setTag("draw-action-space"); actionSpace.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    actions.addView(actionSpace, new LinearLayout.LayoutParams(-1, h.dp(2), 1));
    purchase = column(); purchase.setTag("draw-purchase-box"); purchase.setPadding(h.dp(10), h.dp(6), h.dp(10), h.dp(6));
    purchase.setBackground(shape(NAVY, 0xFFC4AD7D)); actions.addView(purchase, new LinearLayout.LayoutParams(-1, -2));
    quantity = new LinearLayout(h); quantity.setGravity(Gravity.CENTER); quantity.setBaselineAligned(false); quantity.setTag("draw-quantity");
    minus = button("−", false, () -> setAmount(amount - 1)); minus.setContentDescription("모집 수량 줄이기");
    plus = button("＋", false, () -> setAmount(amount + 1)); plus.setContentDescription("모집 수량 늘리기");
    maximum = button("최대", false, () -> setAmount(limit)); maximum.setContentDescription("가능한 최대 수량");
    count = new EditText(h); count.setTypeface(regularFont); count.setIncludeFontPadding(false); count.setTextSize(16); count.setTextColor(WHITE);
    count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); count.setSingleLine(true); count.setGravity(Gravity.CENTER);
    count.setPadding(0, 0, 0, 0); count.setMinimumWidth(h.dp(48)); count.setMinimumHeight(h.dp(48));
    count.setBackground(new InsetDrawable(shape(0xFF10283D, 0x664F6578), 0, h.dp(6), 0, h.dp(6))); count.setContentDescription("모집 수량");
    minus.setBackground(new InsetDrawable(states(false), 0, h.dp(6), 0, h.dp(6)));
    plus.setBackground(new InsetDrawable(states(false), 0, h.dp(6), 0, h.dp(6)));
    maximum.setBackground(new InsetDrawable(states(false), 0, h.dp(6), 0, h.dp(6)));
    quantity.addView(minus, new LinearLayout.LayoutParams(h.dp(48), -2));
    quantity.addView(count, new LinearLayout.LayoutParams(h.dp(48), -2));
    quantity.addView(plus, new LinearLayout.LayoutParams(h.dp(48), -2));
    maximumDivider = new View(h); maximumDivider.setTag("draw-maximum-divider");
    maximumDivider.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); maximumDivider.setBackgroundColor(0xFFB79B65);
    quantity.addView(maximumDivider, new LinearLayout.LayoutParams(h.dp(1), h.dp(24)));
    quantity.addView(maximum, new LinearLayout.LayoutParams(h.dp(48), -2));
    purchase.addView(quantity);
    total = label("", 14, false); total.setTag("draw-total"); total.setGravity(Gravity.CENTER); total.setPadding(0, h.dp(2), 0, h.dp(2)); purchase.addView(total);
    go = button("모집하기", true, this::confirmDraw); go.setTextSize(18); go.setTag("draw-submit");
    NativeTutorialController.anchorPreservingTag(go, "draw-go"); purchase.addView(go, new LinearLayout.LayoutParams(-1, -2));
    claim = styleButton(owner.k.action("u-ticket", "지원서 받기", owner::claimTickets), "지원서 받기", true); claim.setTag("draw-claim"); JournalButton.keepLabelDuringOperation(claim); addView(claim);
    wait = label("", 14, false); wait.setTag("draw-claim-status"); addView(wait);
    long ready = android.os.SystemClock.elapsedRealtime() + Math.max(0, response.optLong("wait"));
    owner.tick(this, () -> {
      long remaining = Math.max(0, ready - android.os.SystemClock.elapsedRealtime());
      claim.setEnabled(remaining == 0 && !h.busy);
      // 받을 수량을 모르면(새 60종 상태 응답) 수량을 지어내지 않고 "지금 받기"만 표시한다.
      String status = remaining > 0 ? "다음 지급 " + owner.duration(remaining) : response.has("claim") ? "지금 " + response.optInt("claim") + "장 받기" : "지금 받기";
      if (!status.contentEquals(wait.getText())) { wait.setText(status); contentRevision++; }
      claim.setContentDescription("지원서 받기 · " + status);
    });
    JSONArray offered = h.list(response, "gachas");
    selected = RecruitmentProbabilityView.find(offered, "LOW");
    if (selected == null) selected = offered.optJSONObject(0);
    for (int i = 0; i < offered.length(); i++) {
      JSONObject g = offered.optJSONObject(i); if (g == null) continue;
      Tier tier = new Tier(g); choices.add(tier);
      LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, -2); slot.bottomMargin = i + 1 < offered.length() ? h.dp(4) : 0; tiers.addView(tier, slot);
    }
    count.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int n, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int n) {
        if (editing || s.length() == 0) return;
        try { setAmount(Integer.parseInt(s.toString())); } catch (NumberFormatException ignored) { setAmount(limit); }
      }
      public void afterTextChanged(Editable e) {}
    });
    count.setOnFocusChangeListener((v, focused) -> { if (!focused) setAmount(amount); });
    select(selected, false);
  }

  static Typeface recruitmentFont(MainActivity host, boolean bold) {
    Typeface cached = bold ? cachedBold : cachedRegular;
    if (cached != null) return cached;
    try {
      Typeface loaded = Typeface.createFromAsset(host.getAssets(), "native/recruitment/fonts/NotoSansKR-" + (bold ? "Bold" : "Regular") + ".ttf");
      if (bold) cachedBold = loaded; else cachedRegular = loaded;
      return loaded;
    } catch (RuntimeException missingFont) { return bold ? host.headline : host.prose; }
  }

  private LinearLayout column() { LinearLayout v = new LinearLayout(h); v.setOrientation(LinearLayout.VERTICAL); return v; }
  private TextView label(String text, int size, boolean title) {
    TextView v = h.text(text, size); v.setTypeface(title ? boldFont : regularFont); v.setTextColor(WHITE);
    v.setIncludeFontPadding(false); v.setPadding(h.dp(4), h.dp(2), h.dp(4), h.dp(2)); return v;
  }
  private GradientDrawable shape(int color, int stroke) {
    GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(h.dp(10)); if (stroke != 0) d.setStroke(h.dp(1), stroke); return d;
  }
  private StateListDrawable pityStates() {
    StateListDrawable d = new StateListDrawable();
    d.addState(new int[] {android.R.attr.state_pressed}, shape(0x5525425B, 0));
    d.addState(new int[] {android.R.attr.state_focused}, shape(0x3325425B, 0xFFE9EDF0));
    d.addState(new int[] {}, shape(Color.TRANSPARENT, 0)); return d;
  }
  /** Resolution-independent fine anchor ornament, not a font glyph or baked image. */
  final class AnchorDivider extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    AnchorDivider() { super(h); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onDraw(Canvas c) {
      float x = getWidth() / 2f, y = getHeight() / 2f, r = h.dp(3);
      p.setColor(0xFFD7B86F); p.setStrokeWidth(h.dp(1)); p.setStyle(Paint.Style.STROKE);
      c.drawLine(h.dp(10), y, x - h.dp(12), y, p); c.drawLine(x + h.dp(12), y, getWidth() - h.dp(10), y, p);
      c.drawCircle(x, y - h.dp(4), h.dp(1), p); c.drawLine(x, y - h.dp(3), x, y + h.dp(4), p);
      c.drawLine(x - r, y - h.dp(1), x + r, y - h.dp(1), p);
      c.drawArc(new RectF(x - h.dp(5), y - h.dp(5), x + h.dp(5), y + h.dp(4)), 0, 180, false, p);
    }
  }
  private StateListDrawable states(boolean primary) {
    StateListDrawable d = new StateListDrawable();
    d.addState(new int[] {-android.R.attr.state_enabled}, shape(0xF0445261, 0xFF71808C));
    d.addState(new int[] {android.R.attr.state_selected, android.R.attr.state_pressed}, shape(0xFFE8CE96, 0xFFE9EDF0));
    d.addState(new int[] {android.R.attr.state_selected, android.R.attr.state_focused}, shape(CREAM, 0xFFE9EDF0));
    d.addState(new int[] {android.R.attr.state_pressed}, shape(primary ? 0xFFE8CE96 : 0xFF25425B, 0xFFE9EDF0));
    d.addState(new int[] {android.R.attr.state_focused}, shape(primary ? CREAM : NAVY, 0xFFE9EDF0));
    d.addState(new int[] {android.R.attr.state_selected}, shape(CREAM, 0xFFB79B65));
    d.addState(new int[] {}, shape(primary ? CREAM : NAVY, 0x664F6578)); return d;
  }
  private Button button(String text, boolean primary, Runnable action) {
    return styleButton(h.button(text, action), text, primary);
  }
  private Button styleButton(Button b, String text, boolean primary) {
    b.setText(text); b.setAllCaps(false); b.setTypeface(boldFont); b.setTextSize(16);
    b.setMinWidth(h.dp(48)); b.setMinimumWidth(h.dp(48)); b.setMinHeight(h.dp(48)); b.setMinimumHeight(h.dp(48));
    b.setPadding(h.dp(8), h.dp(2), h.dp(8), h.dp(2)); b.setBackgroundTintList(null); b.setBackground(states(primary));
    b.setTextColor(new ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}}, new int[] {0xFFDADEE2, primary ? INK : WHITE}));
    b.setIncludeFontPadding(false); return b;
  }
  private void select(JSONObject g, boolean animate) {
    contentRevision++;
    selected = g; amount = 1;
    if (g == null) { setAmount(1); go.setText("다시 열어 주세요"); cost.setText("모집 정보를 받지 못했어요."); return; }
    String kind = g.optString("kind");
    unitCost = Math.max(1, g.optInt("cost")); limit = response.optBoolean("unlimited") ? 50 : Math.max(0, Math.min(50, response.optInt("credits") / unitCost));
    heading.setText(NativeEconomy.recruitmentLabel(g)); cost.setText(compact ? "1회 · 지원서 " + unitCost + "장" : "지원서 " + unitCost + "장 / 1회");
    for (Tier tier : choices) tier.refresh(kind.equals(tier.g.optString("kind")));
    setAmount(1); showTray(kind, animate); requestLayout();
  }
  private void setAmount(int value) {
    int nextAmount = Math.max(1, Math.min(Math.max(1, limit), value));
    if (amount != nextAmount) contentRevision++;
    amount = nextAmount;
    editing = true; String number = String.valueOf(amount); if (!number.contentEquals(count.getText())) count.setText(number); editing = false;
    // 새 60종: 서버가 비활성으로 보낸 등급(확률 승인 대기)과 결과 확인 중인 요청은 모집할 수 없다.
    boolean approved = roster == null || (selected != null && selected.optBoolean("enabled"));
    boolean waiting = roster != null && roster.pending();
    boolean available = selected != null && limit > 0 && approved && !waiting;
    minus.setEnabled(available); plus.setEnabled(available); count.setEnabled(available); maximum.setEnabled(limit > 1);
    total.setText("필요 " + ((long) unitCost * amount) + (compact ? " · 보유 " : "장 · 보유 ") + response.optInt("credits") + (compact ? "" : "장"));
    go.setEnabled(available); go.setText(available ? "모집하기" : !approved ? "승인 대기" : waiting ? "결과 확인 중" : "지원서 부족");
    go.setContentDescription((selected == null ? "" : NativeEconomy.recruitmentLabel(selected)) + " · " + go.getText() + " · " + amount + "회 · 지원서 " + ((long) unitCost * amount) + "장");
  }
  private void confirmDraw() {
    if (h.busy || selected == null || limit <= 0 || (h.visibleDialog != null && h.visibleDialog.isShowing())) return;
    if (roster != null && (roster.pending() || !selected.optBoolean("enabled"))) return;
    int epoch = h.epoch, times = amount; String account = h.api.token(), kind = selected.optString("kind"); JSONObject chosen = selected;
    h.confirm(NativeEconomy.recruitmentLabel(selected) + " " + times + "회 · 지원서 " + ((long) unitCost * times) + "장을 사용할까요?", () -> {
      if (!isAttachedToWindow() || h.epoch != epoch || !account.equals(h.api.token()) || h.busy) return;
      if (roster != null) roster.buy(chosen, times);
      else owner.performDraw("draw", MainActivity.obj("kind", kind, "times", times), "draw");
    });
  }

  private void showTray(String kind, boolean animate) {
    if (kind.equals(displayedKind)) return;
    if (transition != null) { transition.cancel(); transition = null; }
    View old = stage.getChildCount() == 0 ? null : stage.getChildAt(stage.getChildCount() - 1);
    stage.removeAllViews();
    if (old != null) { old.setAlpha(1); old.setTranslationY(0); stage.addView(old, new FrameLayout.LayoutParams(-1, -1)); }
    View next = new NativeEconomy.RecruitmentTray(h, kind); next.setTag("draw-hero-" + kind); stage.addView(next, new FrameLayout.LayoutParams(-1, -1)); displayedKind = kind;
    if (!animate || old == null || !NativeMotion.enabled(h, true)) { stage.removeAllViews(); stage.addView(next, new FrameLayout.LayoutParams(-1, -1)); return; }
    next.setAlpha(0); next.setTranslationY(h.dp(4));
    ValueAnimator animator = ValueAnimator.ofFloat(0, 1); transition = animator; animator.setDuration(220);
    animator.addUpdateListener(a -> { float p = (float) a.getAnimatedValue(); old.setAlpha(1 - p); next.setAlpha(p); next.setTranslationY(h.dp(4) * (1 - p)); });
    animator.addListener(new android.animation.AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(android.animation.Animator a) { if (transition != animator) return; stage.removeView(old); next.setAlpha(1); next.setTranslationY(0); transition = null; }
    }); animator.start();
  }
  private void settleTray() {
    ValueAnimator running = transition; transition = null;
    if (running != null) running.cancel();
    if (stage == null || stage.getChildCount() == 0) return;
    View latest = stage.getChildAt(stage.getChildCount() - 1);
    stage.removeAllViews(); latest.setAlpha(1); latest.setTranslationY(0);
    stage.addView(latest, new FrameLayout.LayoutParams(-1, -1));
  }
  @Override protected void onDetachedFromWindow() { settleTray(); super.onDetachedFromWindow(); }
  @Override protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility); if (visibility != VISIBLE) settleTray();
  }

  final class Tier extends LinearLayout {
    final JSONObject g; final TextView name, price; final NativeEconomy.RecruitmentTray thumbnail;
    final Paint decoration = new Paint(Paint.ANTI_ALIAS_FLAG);
    Tier(JSONObject g) {
      super(h); this.g = g; String kind = g.optString("kind"); setTag("draw-card-" + kind); setGravity(Gravity.CENTER_VERTICAL); setPadding(h.dp(8), h.dp(4), h.dp(20), h.dp(4));
      setMinimumHeight(h.dp(48)); setBackground(states(false)); setFocusable(true);
      thumbnail = new NativeEconomy.RecruitmentTray(h, kind); thumbnail.setTag("draw-station-tray-" + kind); thumbnail.setPadding(h.dp(2), h.dp(2), h.dp(2), h.dp(2)); thumbnail.setBackground(shape(0x99102030, 0xFFB79B65)); LinearLayout.LayoutParams thumbnailSlot = new LinearLayout.LayoutParams(h.dp(94), h.dp(52));
      thumbnailSlot.rightMargin = h.dp(14); addView(thumbnail, thumbnailSlot);
      LinearLayout copy = column(); name = label("", 16, true); name.setTag("draw-station-title-" + kind); price = label("", 14, false); price.setTag("draw-station-cost-" + kind); copy.addView(name); copy.addView(price); addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      copy.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      setOnClickListener(v -> { if (!h.busy && !h.updateBlocks()) { h.sound.click(); select(g, true); announceForAccessibility(NativeEconomy.recruitmentLabel(g) + " 선택"); } });
    }
    @Override protected void dispatchDraw(Canvas canvas) {
      super.dispatchDraw(canvas);
      if (!isSelected()) return;
      decoration.setStyle(Paint.Style.STROKE); decoration.setStrokeWidth(h.dp(1)); decoration.setColor(0xFFB79B65);
      canvas.drawRoundRect(new RectF(h.dp(1), h.dp(1), getWidth() - h.dp(1), getHeight() - h.dp(1)), h.dp(10), h.dp(10), decoration);
      decoration.setColor(0xFFFFEDC4);
      canvas.drawRoundRect(new RectF(h.dp(3), h.dp(3), getWidth() - h.dp(3), getHeight() - h.dp(3)), h.dp(8), h.dp(8), decoration);
      float cx = getWidth() - h.dp(11), cy = h.dp(13), radius = h.dp(7);
      decoration.setStyle(Paint.Style.FILL); decoration.setColor(0xFF795724); canvas.drawCircle(cx, cy, radius, decoration);
      decoration.setStyle(Paint.Style.STROKE); decoration.setStrokeWidth(h.dp(1) * 1.5f); decoration.setColor(0xFFFFEDC4);
      canvas.drawLine(cx - h.dp(3), cy, cx - h.dp(1), cy + h.dp(2), decoration);
      canvas.drawLine(cx - h.dp(1), cy + h.dp(2), cx + h.dp(4), cy - h.dp(3), decoration);
    }
    void refresh(boolean active) {
      setSelected(active); name.setSelected(active); thumbnail.setSelected(false);
      int verticalPadding = compact ? 0 : h.dp(4);
      if (getPaddingTop() != verticalPadding || getPaddingBottom() != verticalPadding)
        setPadding(h.dp(8), verticalPadding, h.dp(20), verticalPadding);
      String title = NativeEconomy.recruitmentLabel(g);
      String nameText = (compact ? title.replace(" 모집", "") + " · " + g.optInt("cost") + "장" : title);
      if (!nameText.contentEquals(name.getText())) name.setText(nameText);
      String priceText = "지원서 " + g.optInt("cost") + "장";
      if (!priceText.contentEquals(price.getText())) price.setText(priceText); name.setTextColor(active ? INK : WHITE); price.setTextColor(active ? INK : WHITE);
      price.setVisibility(compact ? GONE : VISIBLE); thumbnail.setVisibility(compact ? GONE : VISIBLE);
      setContentDescription(title + " · 1회당 지원서 " + g.optInt("cost") + "장" + (active ? " · 선택됨" : " · 선택"));
    }
  }

  private int naturalWidth(TextView v) { return Math.max(h.dp(48), (int) Math.ceil(v.getPaint().measureText(v.getText().toString())) + v.getCompoundPaddingLeft() + v.getCompoundPaddingRight()); }
  private void exactWidth(View v, int width) { v.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)); }
  @Override protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
    int w = Math.max(0, width - getPaddingLeft() - getPaddingRight());
    int hh = Math.max(0, height - getPaddingTop() - getPaddingBottom());
    // Keep the complete composition whenever the actual text/views fit, including large tablets.
    // Only measured pressure, not a phone/font-scale heuristic, activates the accessible compact mode.
    float fontScale = getResources().getConfiguration().fontScale;
    int density = getResources().getDisplayMetrics().densityDpi;
    boolean sameContent = measuredWidth == w && measuredHeight == hh
        && measuredRevision == contentRevision && measuredFontScale == fontScale && measuredDensity == density;
    boolean fits;
    if (sameContent) fits = measureMode(compact, w, hh);
    else {
      decorationsReduced = false;
      fits = measureMode(false, w, hh);
      if (!fits) { decorationsReduced = true; fits = measureMode(false, w, hh); }
      if (!fits) fits = measureMode(true, w, hh);
    }
    measuredWidth = w; measuredHeight = hh; measuredRevision = contentRevision;
    measuredFontScale = fontScale; measuredDensity = density;
    layoutFits = fits;
    setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
  }
  private void quantitySlot(View child, int width, int rightMargin) {
    LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) child.getLayoutParams();
    if (p.width != width || p.rightMargin != rightMargin || p.weight != 0) {
      p.width = width; p.rightMargin = rightMargin; p.weight = 0; child.setLayoutParams(p);
    }
  }
  private boolean measureMode(boolean useCompact, int w, int hh) {
    int gap = h.dp(8);
    int desiredHeadingSp = w >= h.dp(900) ? 40 : 32;
    if (headingSp != desiredHeadingSp) { headingSp = desiredHeadingSp; heading.setTextSize(headingSp); }
    int baseMaxWidth = Math.max(h.dp(48), naturalWidth(maximum));
    int quantityMin = h.dp(48) * 3 + baseMaxWidth + h.dp(1 + 12) + purchase.getPaddingLeft() + purchase.getPaddingRight();
    rightWidth = Math.max(quantityMin, Math.max(h.dp(224), Math.round(w * .28f)));
    int innerWidth = rightWidth - purchase.getPaddingLeft() - purchase.getPaddingRight();
    int maxWidth = Math.max(baseMaxWidth, Math.min(h.dp(65), innerWidth - h.dp(48) * 3 - h.dp(13)));
    int controlGap = Math.max(h.dp(4), Math.min(h.dp(10), (rightWidth - purchase.getPaddingLeft() - purchase.getPaddingRight() - h.dp(48) * 3 - maxWidth - h.dp(1)) / 3));
    quantitySlot(minus, h.dp(48), controlGap); quantitySlot(count, h.dp(48), controlGap);
    quantitySlot(plus, h.dp(48), controlGap / 2); quantitySlot(maximumDivider, h.dp(1), controlGap - controlGap / 2);
    quantitySlot(maximum, maxWidth, 0);
    boolean wasCompact = compact;
    compact = useCompact;
    heading.setVisibility(compact ? GONE : VISIBLE); ornament.setVisibility(compact || decorationsReduced ? GONE : VISIBLE); lowerDivider.setVisibility(compact ? GONE : VISIBLE); pity.setVisibility(GONE);
    String summary = RecruitmentProbabilityView.pityText(response);
    String shortPity = summary.contains(" 보장까지 ") ? summary.substring(0, summary.indexOf(" · ") >= 0 ? summary.indexOf(" · ") : summary.length()).replace(" 보장까지 ", " ")
        : response.has("pity") && response.isNull("pity") ? "보장 없음" : "보장 확인";
    String pityLabel = compact ? shortPity : summary.replace(" · ", "\n");
    if (!pityLabel.contentEquals(info.getText())) info.setText(pityLabel);
    if (wasCompact != compact) {
      if (selected != null) cost.setText(compact ? "1회 · 지원서 " + unitCost + "장" : "지원서 " + unitCost + "장 / 1회");
      setAmount(amount);
    }
    leftWidth = Math.max(h.dp(160), Math.min(h.dp(240), w * 26 / 100));
    for (Tier tier : choices) { tier.refresh(selected != null && selected.optString("kind").equals(tier.g.optString("kind"))); leftWidth = Math.max(leftWidth, naturalWidth(tier.name) + tier.getPaddingLeft() + tier.getPaddingRight() + (compact ? 0 : h.dp(94 + 14))); }
    if (leftWidth + rightWidth + gap * 2 + h.dp(48) > w) { leftWidth = Math.max(0, w - rightWidth - gap * 2 - h.dp(48)); stageWidth = h.dp(48); }
    else stageWidth = w - leftWidth - rightWidth - gap * 2;
    int supportWidth = leftWidth + (stageWidth > 0 ? stageWidth + gap : 0);
    int claimWidth = Math.min(supportWidth, naturalWidth(claim));
    stackedSupport = supportWidth - claimWidth - gap < naturalWidth(wait);
    exactWidth(claim, claimWidth);
    exactWidth(wait, Math.min(naturalWidth(wait), stackedSupport ? supportWidth : Math.max(0, supportWidth - claimWidth - gap)));
    footerHeight = stackedSupport ? claim.getMeasuredHeight() + wait.getMeasuredHeight() : Math.max(claim.getMeasuredHeight(), wait.getMeasuredHeight()); bodyHeight = Math.max(0, hh - footerHeight - gap);
    int desiredCardHeight = h.dp(w >= h.dp(900) ? 74 : 68);
    int fittingCardHeight = Math.max(h.dp(48), (bodyHeight - Math.max(0, choices.size() - 1) * h.dp(4)) / Math.max(1, choices.size()));
    for (Tier tier : choices) tier.setMinimumHeight(compact ? h.dp(48) : Math.min(desiredCardHeight, fittingCardHeight));
    int pillWidth = Math.min(rightWidth, compact ? Math.max(h.dp(184), naturalWidth(cost)) : h.dp(184));
    LinearLayout.LayoutParams pill = (LinearLayout.LayoutParams) cost.getLayoutParams();
    if (pill.width != pillWidth) { pill.width = pillWidth; cost.setLayoutParams(pill); }
    exactWidth(tiers, leftWidth); exactWidth(actions, rightWidth);
    // Keep 82% of the stage width for artwork: the approved composition needs breathing room.
    int heroInset = Math.round(stageWidth * .09f);
    if (stage.getPaddingLeft() != heroInset || stage.getPaddingRight() != heroInset || stage.getPaddingBottom() != h.dp(8))
      stage.setPadding(heroInset, 0, heroInset, h.dp(8));
    stage.measure(MeasureSpec.makeMeasureSpec(stageWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(bodyHeight, MeasureSpec.EXACTLY));
    // Surface unsupported constraints to tests, rather than claiming clipped controls passed.
    boolean fits = stageWidth >= h.dp(48) && bodyHeight >= h.dp(24) && leftWidth + rightWidth + stageWidth + gap * 2 <= w && tiers.getMeasuredHeight() <= bodyHeight && actions.getMeasuredHeight() <= hh && supportWidth >= naturalWidth(claim);
    if (fits) actions.measure(MeasureSpec.makeMeasureSpec(rightWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(hh, MeasureSpec.EXACTLY));
    return fits;
  }
  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int x = getPaddingLeft(), y = getPaddingTop(), gap = h.dp(8), hh = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
    tiers.layout(x, y, x + leftWidth, y + tiers.getMeasuredHeight());
    stage.layout(x + leftWidth + gap, y, x + leftWidth + gap + stageWidth, y + bodyHeight);
    int actionX = getMeasuredWidth() - getPaddingRight() - rightWidth;
    actions.layout(actionX, y, actionX + rightWidth, y + actions.getMeasuredHeight());
    int footerY = y + hh - footerHeight;
    claim.layout(x, footerY, x + claim.getMeasuredWidth(), footerY + claim.getMeasuredHeight());
    int waitX = stackedSupport ? x : x + claim.getMeasuredWidth() + gap;
    int waitY = stackedSupport ? footerY + claim.getMeasuredHeight() : footerY;
    wait.layout(waitX, waitY, waitX + wait.getMeasuredWidth(), waitY + wait.getMeasuredHeight());
  }
}

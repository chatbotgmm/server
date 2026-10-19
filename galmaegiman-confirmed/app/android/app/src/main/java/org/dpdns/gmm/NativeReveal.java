package org.dpdns.gmm;

import android.animation.*;
import android.graphics.*;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import org.json.*;

/** 서버가 확정한 지원서 봉투 → 등급 빛 → 등록증. 결과 순서·등급·수량은 서버 응답만 쓴다. */
final class NativeReveal extends FrameLayout {
  final MainActivity h;
  final JSONObject result;
  final Runnable next;
  final MenuKit k;
  final List<Animator> animations = new ArrayList<>();
  final JSONArray units;
  final NativeAcquisitionFlow flow;
  final int topColor, topRank;
  final Glow glow;
  boolean opening, detached, revealed, leaving;
  static final int RESULT_PAGE_SIZE = 3;
  int resultPage;
  Runnable repaintResults;
  RevealStrip resultStrip;
  final java.util.Map<String, JSONObject> reassessmentCandidates = new java.util.LinkedHashMap<>();
  NativeProgression reassessment;
  boolean reassessmentLoading, reassessmentLoaded, resultSyncing;
  String reassessmentNotice = "";
  long reassessmentNextAt, reassessmentServerNow;
  JSONObject pendingReassessment;
  int pendingReassessmentIndex;
  String pendingReassessmentKey;
  TextView reassessmentStatus;

  NativeUnitDossier dossier;
  android.app.AlertDialog resultPickerDialog;
  ValueAnimator animation;
  ApplicationEnvelopeView envelope;
  Button openButton;
  final int ownerEpoch;
  final String ownerAccount;
  boolean observing, attachedOnce, finishDelivered;
  private final Runnable lifecycle = this::refreshMotion;
  private final ViewTreeObserver.OnPreDrawListener visibility =
      () -> {
        refreshMotion();
        return true;
      };
  private final ContentObserver animationSetting =
      new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
          refreshMotion();
        }
      };

  NativeReveal(MainActivity h, JSONObject result, Runnable next, boolean again) {
    this(h, result, next, again, null);
  }

  NativeReveal(
      MainActivity h, JSONObject result, Runnable next, boolean again, NativeAcquisitionFlow flow) {
    super(h);
    this.h = h;
    this.result = result;
    this.next = next;
    this.flow = flow;
    if (flow != null && flow.account.equals(h.api.token())) {
      pendingReassessment = flow.pendingReassessment;
      pendingReassessmentIndex = flow.pendingReassessmentIndex;
      pendingReassessmentKey = flow.pendingReassessmentKey;
    }
    ownerEpoch = h.epoch;
    ownerAccount = h.api.token();
    k = new MenuKit(h);
    units = result == null ? new JSONArray() : h.list(result, "items");
    String best = "COMMON";
    for (int i = 0; i < units.length(); i++) {
      JSONObject unit = units.optJSONObject(i);
      if (unit != null && rank(unit.optString("tier")) > rank(best)) best = unit.optString("tier");
    }
    topColor = tier(best);
    topRank = rank(best);
    setBackgroundColor(JournalTheme.TEAL);
    setClickable(true);
    glow = new Glow();
    if (again && hasServerResult()) cards(false);
    else application();
  }

  static int rank(String name) {
    switch (name) {
      case "NEUTRAL":
        return 1;
      case "UNCOMMON":
        return 2;
      case "SPECIAL":
        return 3;
      case "RARE":
        return 4;
      case "EPIC":
        return 5;
      case "LEGEND":
        return 6;
      case "HIDDEN":
        return 7;
      case "LIMITED":
        return 8;
      case "TRANSCEND":
        return 9;
      case "ETERNAL":
        return 10;
      case "IMMORTAL":
        return 11;
      default:
        return 0;
    }
  }

  static int tier(String name) {
    switch (name) {
      case "COMMON":
        return 0xff8a8a8a;
      case "UNCOMMON":
        return 0xff2f8f5b;
      case "SPECIAL":
      case "NEUTRAL":
        return 0xff8a7a6a;
      case "RARE":
        return 0xff1f8fb0;
      case "EPIC":
        return 0xff7a4fd0;
      case "LEGEND":
      case "IMMORTAL":
        return 0xffd9a400;
      case "HIDDEN":
        return 0xff5b3d8f;
      case "LIMITED":
        return 0xffd9483b;
      case "TRANSCEND":
        return 0xff2a6fc0;
      case "ETERNAL":
        return 0xff0f8a7b;
      default:
        return 0xffc9b08a;
    }
  }

  // 원본 등급색은 카드 테두리에 유지하고, 작은 등급 글자는 종이 위 대비를 확보한다.
  static int tierInk(String name) {
    int original = tier(name);
    for (int step = 0; step <= 10; step++) {
      int ink = androidx.core.graphics.ColorUtils.blendARGB(original, JournalTheme.INK, step / 10f);
      if (androidx.core.graphics.ColorUtils.calculateContrast(ink, JournalTheme.PAPER) >= 4.5)
        return ink;
    }
    return JournalTheme.INK;
  }

  boolean motion() {
    return NativeMotion.enabled(h, h.motionEnabled)
        && NativeMotion.legacyEnabled(h, h.motionEnabled);
  }

  boolean current() {
    return !detached
        && !h.isFinishing()
        && !h.isDestroyed()
        && ownerEpoch == h.epoch
        && ownerAccount.equals(h.api.token())
        && (flow == null || flow.account.equals(ownerAccount));
  }

  boolean active() {
    return current()
        && h.motionResumed
        && isAttachedToWindow()
        && getWindowVisibility() == VISIBLE
        && isShown();
  }

  boolean hasServerResult() {
    return result != null && result.optJSONArray("items") != null && units.length() > 0;
  }

  void animate(ValueAnimator animator) {
    animations.add(animator);
    animator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            animations.remove(animation);
          }
        });
    animator.start();
    if (!active()) animator.pause();
  }

  void stopAnimations() {
    for (Animator animator : new ArrayList<>(animations)) animator.cancel();
    animations.clear();
  }

  void refreshMotion() {
    if (h == null) return;
    if (resultPickerDialog != null && !resultPickerDialog.isShowing()) resultPickerDialog = null;
    if (!current()) {
      if (resultPickerDialog != null) resultPickerDialog.dismiss();
      if (dossier != null) dossier.dismiss();
      stopAnimations();
      if (envelope != null) envelope.cancel();
      return;
    }
    // A result may have been reopened before the original POST completed.
    // Adopt its account-bound receipt once; failed reads remain explicit retries.
    if (revealed && flow != null && flow.pendingReassessment != null
        && pendingReassessment != flow.pendingReassessment) {
      pendingReassessment = flow.pendingReassessment;
      pendingReassessmentIndex = flow.pendingReassessmentIndex;
      pendingReassessmentKey = flow.pendingReassessmentKey;
      syncReassessment();
    }
    for (Animator animator : new ArrayList<>(animations)) {
      if (!active()) {
        if (animator.isStarted() && !animator.isPaused()) animator.pause();
      } else if (!motion()) {
        // 결과가 확정된 카드의 뒤집기와 fade만 끝낸다. pause 중에는 완료하지 않는다.
        animator.end();
      } else if (animator.isPaused()) animator.resume();
    }
  }

  void addGlow() {
    if (glow.getParent() instanceof ViewGroup) ((ViewGroup) glow.getParent()).removeView(glow);
    addView(glow, new LayoutParams(-1, -1));
  }

  void application() {
    addGlow();
    LinearLayout page = k.column();
    page.setGravity(Gravity.CENTER);
    page.setPadding(h.dp(16), h.dp(8), h.dp(16), h.dp(16));
    envelope = new ApplicationEnvelopeView(h);
    page.addView(envelope, new LinearLayout.LayoutParams(-1, 0, 1));
    openButton = h.button("지원서 열기", this::openApplication);
    openButton.setTag("application-open");
    openButton.setContentDescription("지원서를 열어 모집 결과 보기");
    page.addView(openButton, new LinearLayout.LayoutParams(h.dp(240), -2));
    addView(page, new LayoutParams(-1, -1));
  }

  void openApplication() {
    if (opening || revealed || leaving || !current()) return;
    opening = true;
    openButton.setEnabled(false);
    openButton.setText("지원서 읽는 중…");
    h.sound.effect("draw");
    // 정지 화면의 동기 표시도 서버 결과를 반드시 요구한다. 기존 비부착 검수 경로 유지.
    if (!motion() && hasServerResult() && (!attachedOnce || active())) {
      cards(true);
      return;
    }
    envelope.play(
        hasServerResult(),
        () -> {
          if (current() && active() && !revealed && !leaving && hasServerResult()) cards(true);
        });
  }

  /** 이전 검수/호출자를 위한 이름만 남긴다. 알 그림이나 별도 요청은 사용하지 않는다. */
  void openEgg() {
    openApplication();
  }

  void cards() {
    cards(false);
  }

  void cards(boolean playEffect) {
    if (!current() || leaving || revealed || !hasServerResult()) return;
    revealed = true;
    if (envelope != null) envelope.cancel();
    stopAnimations();
    removeAllViews();
    addGlow();
    glow.revealed = true;
    if (playEffect) h.sound.effect(topRank >= 6 ? "drawlegend" : topRank >= 4 ? "drawrare" : "aug");
    LinearLayout page = k.column();
    page.setGravity(Gravity.CENTER);
    page.setPadding(h.dp(12), h.dp(10), h.dp(12), h.dp(10));
    addView(page, new LayoutParams(-1, -1));
    final TextView selectedLabel = k.note("");
    if (flow != null && units.length() > 1) {
      selectedLabel.setTextColor(JournalTheme.PAPER);
      selectedLabel.setGravity(Gravity.CENTER);
      selectedLabel.setSingleLine(true);
      page.addView(selectedLabel);
    }
    RevealStrip scroll = new RevealStrip();
    resultStrip = scroll;
    LinearLayout row = scroll.cardsRow;
    scroll.setTag("acquisition-cards-scroll");
    page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    reassessmentStatus = h.text("", 12);
    reassessmentStatus.setTag("acquisition-reassessment-status");
    reassessmentStatus.setTextColor(JournalTheme.PAPER);
    reassessmentStatus.setGravity(Gravity.CENTER);
    reassessmentStatus.setPadding(0, 0, 0, 0);
    reassessmentStatus.setVisibility(GONE);
    page.addView(reassessmentStatus, new LinearLayout.LayoutParams(-1, -2));
    final LinearLayout footer = k.column();
    final boolean[] firstPaint = {true};
    final ArrayList<RevealCard> shown = new ArrayList<>();
    final Runnable[] paint = new Runnable[1], selection = new Runnable[1];
    selection[0] = () -> {
      footer.removeAllViews();
      if (flow != null && units.length() > 0) {
        flow.selected = Math.max(0, Math.min(flow.selected, units.length() - 1));
        selectedLabel.setText("모집 결과 " + speciesCount() + "종");
        for (int j = 0; j < shown.size(); j++) {
          int serverIndex = resultPage * RESULT_PAGE_SIZE + j;
          RevealCard card = shown.get(j);
          boolean selected = serverIndex == flow.selected;
          card.setSelected(selected);
          card.setForeground(RegistrationCardView.selection(h, selected));
          card.setContentDescription(RegistrationCardView.description(units.optJSONObject(serverIndex))
              + (selected ? " · 선택됨" : "") + " · 눌러 대원 기록 보기");
        }
      }
      LinearLayout finish = k.row();
      if (units.length() > RESULT_PAGE_SIZE) {
        Button previous = k.action(null, "‹", () -> {
          if (!canTurnResults() || resultPage <= 0) return;
          scroll.turn(-1);
        });
        previous.setTag("acquisition-previous-page");
        previous.setContentDescription("이전 모집 결과 페이지");
        previous.setEnabled(resultPage > 0);
        finish.addView(previous, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
        TextView pageNumber = RegistrationCardView.stateLabel(h,
            (resultPage + 1) + "/" + ((units.length() + RESULT_PAGE_SIZE - 1) / RESULT_PAGE_SIZE));
        finish.addView(pageNumber, new LinearLayout.LayoutParams(h.dp(52), h.dp(48)));
        Button nextPage = k.action(null, "›", () -> {
          if (!canTurnResults() || (resultPage + 1) * RESULT_PAGE_SIZE >= units.length()) return;
          scroll.turn(1);
        });
        nextPage.setTag("acquisition-next-page");
        nextPage.setContentDescription("다음 모집 결과 페이지");
        nextPage.setEnabled((resultPage + 1) * RESULT_PAGE_SIZE < units.length());
        finish.addView(nextPage, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
      }
      if ((!reassessmentNotice.isEmpty() && hasReceiptIdentity()) || !savedReassessmentKey().isEmpty() || pendingReassessment != null
          || (hasReceiptIdentity() && reassessmentNextAt > reassessmentServerNow)) {
        Button retry = k.action(null, "↻", () -> {
          if (pendingReassessment != null) syncReassessment();
          else if (!savedReassessmentKey().isEmpty()) {
            String key = savedReassessmentKey();
            for (int i = 0; i < units.length(); i++) if (receiptKeys(units.optJSONObject(i)).contains(key)) { openReassessment(i, key); break; }
          } else { reassessmentLoaded = false; loadReassessment(); }
        });
        retry.setTag("acquisition-reassessment-refresh"); retry.setContentDescription("재심사 서버 상태 다시 확인");
        retry.setPadding(0, 0, 0, 0); retry.setEnabled(!reassessmentLoading && !resultSyncing);
        finish.addView(retry, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
      }
      Button all = k.action(null, "목록 · " + units.length(), () -> resultPicker(index -> {
        if (flow != null) flow.selected = index;
        resultPage = index / RESULT_PAGE_SIZE;
        paint[0].run();
      }));
      all.setTag("acquisition-result-picker");
      all.setContentDescription("전체 결과 목록 · " + speciesCount() + "종");
      h.cell(finish, all);
      if (flow != null && units.length() > 0) {
        LinearLayout actions = NativeUnitDossier.actions(h, flow,
            units.optJSONObject(flow.selected), () -> current() && !h.busy && dossier == null);
        finish.addView(actions, new LinearLayout.LayoutParams(0, h.dp(48),
            NativeAcquisitionFlow.battleSpecies(units.optJSONObject(flow.selected)) ? 2 : 1));
      }
      Button ok = k.primary(null, "확인", this::finish);
      ok.setTag("acquisition-confirm");
      h.cell(finish, ok);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.topMargin = h.dp(4);
      footer.addView(finish, lp);
    };
    paint[0] = () -> {
      // 페이지 밖 FULL 뷰는 분리해 큰 비트맵 참조도 해제한다. 50회 결과도 동시에 최대 세 장이다.
      row.removeAllViews();
      shown.clear();
      int pages = Math.max(1, (units.length() + RESULT_PAGE_SIZE - 1) / RESULT_PAGE_SIZE);
      resultPage = Math.max(0, Math.min(resultPage, pages - 1));
      int start = resultPage * RESULT_PAGE_SIZE;
      for (int i = start; i < Math.min(units.length(), start + RESULT_PAGE_SIZE); i++) {
        JSONObject unit = units.optJSONObject(i);
        if (unit == null) continue;
        RevealCard card = new RevealCard(unit, 240);
        shown.add(card);
        int index = i;
        bindReassessment(card, unit, index);
        card.setTag("acquisition-card-" + unit.optString("id"));
        card.setOnClickListener(v -> {
          if (!canTurnResults() || scroll.suppressClick || card.front.getVisibility() != VISIBLE) return;
          if (flow != null) flow.selected = index;
          selection[0].run();
          openDossier(unit, card);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(h.dp(240), card.preferredHeight);
        lp.setMargins(h.dp(6), h.dp(3), h.dp(6), h.dp(3));
        row.addView(card, lp);
        if (firstPaint[0] && playEffect && motion()) {
          ValueAnimator flip = ValueAnimator.ofFloat(0, 1);
          flip.setDuration(500);
          flip.setStartDelay((i - start) * 120L);
          flip.addUpdateListener(a -> card.flip((Float) a.getAnimatedValue()));
          animate(flip);
        } else card.flip(1);
      }
      firstPaint[0] = false;
      updateReassessmentStatus();
      selection[0].run();
    };
    repaintResults = paint[0];
    page.addView(footer, new LinearLayout.LayoutParams(-1, -2));
    if (flow != null) resultPage = Math.max(0, flow.selected) / RESULT_PAGE_SIZE;
    paint[0].run();
    if (isAttachedToWindow()) loadReassessment();
    if (motion()) {
      ValueAnimator rays = ValueAnimator.ofFloat(0, 360);
      rays.setDuration(14000);
      rays.setInterpolator(new LinearInterpolator());
      rays.setRepeatCount(topRank >= 6 ? ValueAnimator.INFINITE : 0);
      rays.addUpdateListener(
          a -> {
            glow.rotation = (Float) a.getAnimatedValue();
            glow.invalidate();
          });
      if (topRank >= 6) animate(rays);
      if (playEffect) {
        ValueAnimator flash = ValueAnimator.ofFloat(1, 0);
        flash.setDuration(500);
        flash.addUpdateListener(
            a -> {
              glow.flash = (Float) a.getAnimatedValue();
              glow.invalidate();
            });
        animate(flash);
      }
    }
  }

  boolean canTurnResults() {
    return current() && !leaving && !h.busy && dossier == null && reassessment == null
        && !resultSyncing && !pickerShowing();
  }

  List<String> receiptKeys(JSONObject item) {
    ArrayList<String> keys = new ArrayList<>();
    if (item == null) return keys;
    JSONArray supplied = item.optJSONArray("ownedKeys");
    if (supplied == null || supplied.length() != Math.max(1, item.optInt("n", 1))) return keys;
    for (int i = 0; i < supplied.length(); i++) {
      String key = supplied.optString(i);
      if (!key.matches("o[1-9]\\d*") || keys.contains(key)) return new ArrayList<>();
      int appearances = 0;
      for (int n = 0; n < units.length(); n++) {
        JSONObject other = units.optJSONObject(n);
        JSONArray otherKeys = other == null ? null : other.optJSONArray("ownedKeys");
        for (int j = 0; otherKeys != null && j < otherKeys.length(); j++) if (key.equals(otherKeys.optString(j))) appearances++;
      }
      if (appearances != 1) return new ArrayList<>();
      keys.add(key);
    }
    return keys;
  }

  boolean hasReceiptIdentity() {
    for (int i = 0; i < units.length(); i++) {
      JSONObject item = units.optJSONObject(i);
      if (item != null && rank(item.optString("tier")) >= 4 && !receiptKeys(item).isEmpty()) return true;
    }
    return false;
  }

  boolean needsReassessment() {
    if (flow == null || !flow.origin.equals("draw")) return false;
    for (int i = 0; i < units.length(); i++) {
      JSONObject item = units.optJSONObject(i);
      if (item != null && rank(item.optString("tier")) >= 4) return true;
    }
    return false;
  }

  String savedReassessmentKey() {
    try {
      NativeProgression screen = new NativeProgression(h);
      String owner = NativeProgression.accountKey(ownerAccount.split("\\.")[0]);
      JSONObject pending = new JSONObject(screen.prefs.getString(owner, "{}"));
      if (!"native/progression/reassess".equals(pending.optString("path"))) return "";
      String key = NativeProgression.object(pending, "body").optString("key");
      for (int i = 0; i < units.length(); i++) if (receiptKeys(units.optJSONObject(i)).contains(key)) return key;
    } catch (JSONException ignored) { }
    return "";
  }

  void updateReassessmentStatus() {
    if (reassessmentStatus == null) return;
    String message = reassessmentNotice;
    if (message.isEmpty() && !savedReassessmentKey().isEmpty()) message = "재심사 처리 확인이 남아 있어요. 같은 요청 결과를 확인해 주세요.";
    if (message.isEmpty() && needsReassessment() && !hasReceiptIdentity())
      message = "재심사 잠김 · 서버가 획득 개체 번호를 보내지 않았어요.";
    if (message.isEmpty() && reassessmentLoading) message = "재심사 가능 개체 확인 중…";
    if (message.isEmpty() && reassessmentNextAt > reassessmentServerNow)
      message = "재심사 대기 · " + NativeProgression.duration(reassessmentNextAt - reassessmentServerNow);
    reassessmentStatus.setText(message);
    reassessmentStatus.setVisibility(message.isEmpty() ? GONE : VISIBLE);
  }

  void loadReassessment() {
    if (!current() || !revealed || !needsReassessment() || reassessmentLoading || resultSyncing) return;
    if (!hasReceiptIdentity()) { updateReassessmentStatus(); return; }
    if (reassessmentLoaded && reassessmentNotice.isEmpty()) return;
    reassessmentLoading = true; reassessmentNotice = "";
    updateReassessmentStatus();
    h.api.call("native/progression", null, (data, error) -> {
      if (ownerEpoch == h.epoch && error != null && h.api.token().isEmpty()) { h.login(); return; }
      if (!current()) return;
      reassessmentLoading = false; reassessmentCandidates.clear();
      if (error != null || !NativeProgression.validSnapshot(data)) {
        reassessmentLoaded = false;
        reassessmentNotice = "재심사 서버 지원을 확인하지 못했어요. 다시 확인해 주세요.";
      } else {
        reassessmentLoaded = true;
        JSONObject section = NativeProgression.object(data, "reassessment");
        reassessmentNextAt = section.optLong("nextAvailableAt"); reassessmentServerNow = data.optLong("serverNow");
        JSONArray candidates = section.optJSONArray("candidates");
        for (int i = 0; candidates != null && i < candidates.length(); i++) {
          JSONObject candidate = candidates.optJSONObject(i);
          if (candidate != null && candidate.optBoolean("eligible", true) && !candidate.optBoolean("locked")
              && candidate.optString("key").matches("o[1-9]\\d*"))
            reassessmentCandidates.put(candidate.optString("key"), candidate);
        }
      }
      if (repaintResults != null) repaintResults.run();
    });
  }

  List<JSONObject> eligibleInstances(JSONObject unit) {
    ArrayList<JSONObject> found = new ArrayList<>();
    if (unit == null || rank(unit.optString("tier")) < 4) return found;
    for (String key : receiptKeys(unit)) {
      JSONObject candidate = reassessmentCandidates.get(key);
      if (candidate != null && unit.optString("id").equals(candidate.optString("id"))
          && unit.optString("tier").equals(candidate.optString("rarity"))) found.add(candidate);
    }
    return found;
  }

  void bindReassessment(RevealCard card, JSONObject unit, int index) {
    List<JSONObject> candidates = eligibleInstances(unit);
    if (candidates.isEmpty()) return;
    Button button = k.action(null, "↻", () -> chooseReassessment(unit, index));
    button.setTag("acquisition-reassess-card-" + index);
    button.setTextSize(26); button.setPadding(0, 0, 0, h.dp(2));
    android.graphics.drawable.StateListDrawable circle = new android.graphics.drawable.StateListDrawable();
    android.graphics.drawable.GradientDrawable pressed = new android.graphics.drawable.GradientDrawable();
    pressed.setShape(android.graphics.drawable.GradientDrawable.OVAL); pressed.setColor(JournalTheme.TEAL);
    pressed.setStroke(h.dp(2), JournalTheme.BRASS);
    android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
    normal.setShape(android.graphics.drawable.GradientDrawable.OVAL); normal.setColor(JournalTheme.PAPER);
    normal.setStroke(h.dp(2), JournalTheme.BRASS);
    circle.addState(new int[] {android.R.attr.state_pressed}, pressed);
    circle.addState(new int[] {}, normal);
    button.setBackground(circle);
    button.setContentDescription(unit.optString("name") + " · 이번 획득 개체 재심사"
        + (reassessmentNextAt > reassessmentServerNow ? " · 대기 시간 남음" : ""));
    button.setEnabled(!resultSyncing && reassessmentNextAt <= reassessmentServerNow);
    LayoutParams slot = new LayoutParams(h.dp(48), h.dp(48), Gravity.TOP | Gravity.RIGHT);
    slot.setMargins(0, h.dp(4), h.dp(4), 0);
    card.photo.addView(button, slot);
    if (card.countBadge != null) {
      LayoutParams count = (LayoutParams) card.countBadge.getLayoutParams();
      count.topMargin = h.dp(56);
      card.countBadge.setLayoutParams(count);
    }
  }

  void chooseReassessment(JSONObject unit, int index) {
    if (!canTurnResults() || (resultStrip != null && resultStrip.suppressClick)) return;
    List<JSONObject> candidates = eligibleInstances(unit);
    if (candidates.isEmpty() || reassessmentNextAt > reassessmentServerNow) return;
    if (flow != null) flow.selected = index;
    if (candidates.size() == 1) { openReassessment(index, candidates.get(0).optString("key")); return; }
    String[] labels = new String[candidates.size()];
    for (int i = 0; i < labels.length; i++) labels[i] = candidates.get(i).optString("name") + " · 개체 " + candidates.get(i).optString("key");
    resultPickerDialog = new GameDialog(h).setTitle("이번에 얻은 재심사 대상 선택")
        .setItems(labels, (dialog, which) -> {
          resultPickerDialog = null;
          if (canTurnResults()) openReassessment(index, candidates.get(which).optString("key"));
        }).setNegativeButton("취소", null).show();
  }

  int speciesCount() {
    java.util.HashSet<String> ids = new java.util.HashSet<>();
    for (int i = 0; i < units.length(); i++) {
      JSONObject item = units.optJSONObject(i);
      if (item != null && !item.optString("id").isEmpty()) ids.add(item.optString("id"));
    }
    return ids.size();
  }

  void openReassessment(int index, String key) {
    if (!canTurnResults() || !receiptKeys(units.optJSONObject(index)).contains(key)) return;
    reassessment = NativeProgression.openReassessment(h, key, (sourceKey, receipt) -> {
      if (flow == null || !flow.account.equals(ownerAccount)) return;
      int actualIndex = -1;
      for (int i = 0; i < units.length(); i++) if (receiptKeys(units.optJSONObject(i)).contains(sourceKey)) actualIndex = i;
      if (actualIndex < 0) return;
      flow.pendingReassessment = receipt; flow.pendingReassessmentIndex = actualIndex; flow.pendingReassessmentKey = sourceKey;
      if (!current()) return;
      pendingReassessment = receipt; pendingReassessmentIndex = actualIndex; pendingReassessmentKey = sourceKey;
      syncReassessment();
    }, () -> {
      reassessment = null;
      if (current() && pendingReassessment == null) { reassessmentLoaded = false; loadReassessment(); }
    });
  }

  void syncReassessment() {
    if (!current() || pendingReassessment == null || resultSyncing) return;
    resultSyncing = true;
    h.api.call("native/state", null, (fresh, error) -> {
      if (ownerEpoch == h.epoch && error != null && h.api.token().isEmpty()) { h.login(); return; }
      if (!current()) return;
      resultSyncing = false;
      if (error != null || fresh == null || fresh.optJSONObject("me") == null) {
        reassessmentNotice = "재심사는 처리됐지만 새 보유 상태를 확인하지 못했어요. 다시 확인해 주세요.";
        if (reassessment != null) reassessment.dismiss();
        if (repaintResults != null) repaintResults.run();
        return;
      }
      JSONObject old = units.optJSONObject(pendingReassessmentIndex), receipt = pendingReassessment;
      List<String> remaining = receiptKeys(old);
      if (!remaining.remove(pendingReassessmentKey) || !old.optString("tier").equals(receipt.optString("rarity"))) {
        reassessmentNotice = "새 결과의 개체 정보를 확인해야 해요. 모집을 다시 실행하지 말고 진행을 확인해 주세요.";
        if (reassessment != null) reassessment.dismiss();
        if (repaintResults != null) repaintResults.run();
        return;
      }
      try {
        JSONObject replacement = MainActivity.obj("id", receipt.optString("id"), "img", receipt.optString("id"),
            "name", receipt.optString("name"), "tier", receipt.optString("rarity"), "tierName", old.optString("tierName"),
            "n", 1, "first", false, "ownedKeys", new JSONArray().put(receipt.optString("key")));
        JSONArray catalog = h.list(fresh.optJSONObject("me"), "units");
        for (int i = 0; i < catalog.length(); i++) {
          JSONObject entry = catalog.optJSONObject(i);
          if (entry != null && replacement.optString("id").equals(entry.optString("id"))) {
            replacement.put("name", entry.optString("name", replacement.optString("name")));
            replacement.put("tierName", entry.optString("tierName", replacement.optString("tierName")));
            break;
          }
        }
        ArrayList<JSONObject> updated = new ArrayList<>();
        for (int i = 0; i < units.length(); i++) {
          if (i != pendingReassessmentIndex) updated.add(units.optJSONObject(i));
          else {
            updated.add(replacement);
            if (!remaining.isEmpty()) updated.add(new JSONObject(old.toString())
                .put("n", remaining.size()).put("ownedKeys", new JSONArray(remaining)));
          }
        }
        while (units.length() > 0) units.remove(units.length() - 1);
        for (JSONObject item : updated) units.put(item);
        h.state = fresh; h.stateUpdated();
        // 사용자가 보고 있는 현재 페이지/선택 인덱스는 변환 후에도 유지한다.
        pendingReassessment = null; reassessmentNotice = ""; reassessmentLoaded = false;
        if (flow != null) flow.pendingReassessment = null;
        if (reassessment != null) reassessment.dismiss();
        if (repaintResults != null) repaintResults.run();
        loadReassessment();
      } catch (JSONException exception) {
        reassessmentNotice = "새 결과 표시를 확인하지 못했어요. 진행을 다시 확인해 주세요.";
        if (repaintResults != null) repaintResults.run();
      }
    });
  }

  boolean pickerShowing() {
    return resultPickerDialog != null && resultPickerDialog.isShowing();
  }

  void openDossier(JSONObject unit, View returnFocus) {
    if (!canTurnResults() || pickerShowing()) return;
    for (int i = 0; i < getChildCount(); i++)
      getChildAt(i).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    dossier = new NativeUnitDossier(h, unit, flow, this::current, () -> {
      NativeUnitDossier shown = dossier;
      dossier = null;
      if (shown != null) removeView(shown);
      for (int i = 0; i < getChildCount(); i++)
        getChildAt(i).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
      if (current() && returnFocus != null) returnFocus.requestFocus();
    });
    addView(dossier, new LayoutParams(-1, -1));
  }

  void resultPicker(java.util.function.IntConsumer select) {
    if (!current() || leaving || dossier != null || h.busy
        || pickerShowing()) return;
    android.app.AlertDialog[] dialog = {null};
    dialog[0] =
        k.openSheet(
            "획득 결과 · " + units.length(),
            () -> {
              LinearLayout list = k.column();
              for (int i = 0; i < units.length(); i++) {
                JSONObject unit = units.optJSONObject(i);
                if (unit == null) continue;
                int index = i;
                LinearLayout row = k.paper(), line = k.row(), words = k.column();
                line.addView(
                    RegistrationCardView.art(h, unit, RegistrationCardView.COMPACT),
                    new LinearLayout.LayoutParams(h.dp(64), h.dp(64)));
                TextView name = h.text(unit.optString("name"), 18);
                name.setTypeface(h.headline);
                words.addView(name);
                TextView grade =
                    k.note(
                        unit.optString("tierName")
                            + (unit.optInt("n") > 1 ? " · ×" + unit.optInt("n") : "")
                            + (unit.optBoolean("first") ? " · NEW" : ""));
                grade.setTextColor(tierInk(unit.optString("tier")));
                words.addView(grade);
                line.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(line);
                Button choose =
                    k.action(
                        null,
                        "이 갈매미 선택",
                        () -> {
                          if (!current() || leaving || dossier != null) return;
                          select.accept(index);
                          dialog[0].dismiss();
                        });
                row.addView(choose);
                list.addView(row);
              }
              return list;
            });
    // openSheet의 기존 dismiss 정리(guard·모달 크기 복구)를 덮어쓰지 않는다.
    resultPickerDialog = dialog[0];
  }

  void finish() {
    if (leaving || dossier != null || reassessment != null || resultSyncing || pickerShowing() || !revealed
        || !current() || (attachedOnce && !active())) return;
    leaving = true;
    stopAnimations();
    if (!motion()) {
      if (!attachedOnce || active()) {
        finishDelivered = true;
        next.run();
      }
      return;
    }
    animation = ValueAnimator.ofFloat(getAlpha(), 0);
    animation.setDuration(200);
    animation.addUpdateListener(a -> setAlpha((Float) a.getAnimatedValue()));
    animation.addListener(
        new AnimatorListenerAdapter() {
          boolean cancelled;

          @Override
          public void onAnimationCancel(Animator animator) {
            cancelled = true;
          }

          @Override
          public void onAnimationEnd(Animator animator) {
            if (!cancelled && !finishDelivered && current() && active()) {
              finishDelivered = true;
              next.run();
            }
          }
        });
    animate(animation);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    attachedOnce = true;
    if (detached) return;
    observing = true;
    if (h.journalTime != null) h.journalTime.addListener(lifecycle);
    getViewTreeObserver().addOnPreDrawListener(visibility);
    h.getContentResolver()
        .registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animationSetting);
    refreshMotion();
    if (revealed) { if (pendingReassessment != null) syncReassessment(); else loadReassessment(); }
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    refreshMotion();
  }

  @Override
  protected void onDetachedFromWindow() {
    detached = true;
    if (resultPickerDialog != null) resultPickerDialog.dismiss();
    if (dossier != null) dossier.dismiss();
    if (reassessment != null) reassessment.dismiss();
    stopAnimations();
    if (envelope != null) envelope.cancel();
    if (observing) {
      observing = false;
      if (h.journalTime != null) h.journalTime.removeListener(lifecycle);
      if (getViewTreeObserver().isAlive())
        getViewTreeObserver().removeOnPreDrawListener(visibility);
      h.getContentResolver().unregisterContentObserver(animationSetting);
    }
    animate().cancel();
    super.onDetachedFromWindow();
  }

  /** 하단 행동과 큰 글자가 먼저 차지한 뒤의 실제 카드 영역에 초상만 맞춘다. */
  final class RevealStrip extends HorizontalScrollView {
    final LinearLayout cardsRow = k.row();
    float downX, downY, dragX;
    boolean dragging, suppressClick, transitioning, verticalGesture;
    final int touchSlop = ViewConfiguration.get(h).getScaledTouchSlop();

    RevealStrip() {
      super(h);
      setFillViewport(true);
      setHorizontalScrollBarEnabled(false);
      cardsRow.setGravity(Gravity.CENTER);
      cardsRow.setPadding(h.dp(3), h.dp(3), h.dp(3), h.dp(3));
      addView(cardsRow, new HorizontalScrollView.LayoutParams(-2, -2, Gravity.CENTER));
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
        downX = event.getX(); downY = event.getY(); dragX = 0;
        dragging = false; verticalGesture = false;
        if (!transitioning) suppressClick = false;
        return transitioning;
      }
      if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !transitioning && canTurnResults()) {
        float dx = event.getX() - downX, dy = event.getY() - downY;
        if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
          dragging = true; suppressClick = true;
          getParent().requestDisallowInterceptTouchEvent(true);
          return true;
        }
        if (Math.abs(dy) > touchSlop) {
          verticalGesture = true; suppressClick = true;
          return true;
        }
      }
      return dragging || transitioning || verticalGesture;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
      if (transitioning) return true;
      if (!canTurnResults()) return false;
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downX = event.getX(); downY = event.getY(); dragX = 0; return true;
        case MotionEvent.ACTION_MOVE:
          if (verticalGesture) return true;
          dragX = event.getX() - downX;
          if (Math.abs(dragX) > touchSlop) { dragging = true; suppressClick = true; }
          boolean edge = dragX > 0 ? resultPage == 0 : (resultPage + 1) * RESULT_PAGE_SIZE >= units.length();
          cardsRow.setTranslationX(dragX * (edge ? .22f : 1f));
          return true;
        case MotionEvent.ACTION_UP:
          if (!verticalGesture && dragging && Math.abs(dragX) >= Math.max(h.dp(48), getWidth() * .12f)) turn(dragX < 0 ? 1 : -1);
          else settle();
          dragging = false; return true;
        case MotionEvent.ACTION_CANCEL:
          dragging = false; settle(); return true;
        default: return true;
      }
    }

    void settle() {
      if (motion()) cardsRow.animate().translationX(0).setDuration(140).start();
      else cardsRow.setTranslationX(0);
      postDelayed(() -> suppressClick = false, 200);
    }

    void turn(int direction) {
      if (!canTurnResults() || transitioning) { settle(); return; }
      int destination = resultPage + direction;
      if (destination < 0 || destination * RESULT_PAGE_SIZE >= units.length()) { settle(); return; }
      suppressClick = dragging || motion();
      Runnable change = () -> {
        if (!current()) return;
        resultPage = destination;
        if (flow != null) flow.selected = resultPage * RESULT_PAGE_SIZE;
        repaintResults.run();
        if (motion()) {
          cardsRow.setTranslationX(direction * getWidth() * .30f);
          cardsRow.animate().translationX(0).setDuration(160).withEndAction(() -> {
            transitioning = false; suppressClick = false;
          }).start();
        } else { cardsRow.setTranslationX(0); transitioning = false; postDelayed(() -> suppressClick = false, 200); }
      };
      transitioning = true;
      if (motion()) cardsRow.animate().translationX(-direction * getWidth()).setDuration(140).withEndAction(change).start();
      else change.run();
    }

    @Override protected void onDetachedFromWindow() {
      cardsRow.animate().cancel();
      super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
      super.onMeasure(widthSpec, heightSpec);
      if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) return;
      int room =
          getMeasuredHeight()
              - getPaddingTop()
              - getPaddingBottom()
              - cardsRow.getPaddingTop()
              - cardsRow.getPaddingBottom();
      if (room <= 0) return;
      for (int i = 0; i < cardsRow.getChildCount(); i++) {
        View child = cardsRow.getChildAt(i);
        if (!(child instanceof RevealCard)) continue;
        LinearLayout.LayoutParams slot = (LinearLayout.LayoutParams) child.getLayoutParams();
        int budget = Math.max(0, room - slot.topMargin - slot.bottomMargin);
        int columns = Math.max(1, cardsRow.getChildCount());
        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight()
            - cardsRow.getPaddingLeft() - cardsRow.getPaddingRight();
        int widthBudget = Math.max(1, availableWidth / columns - slot.leftMargin - slot.rightMargin);
        slot.width = Math.max(1, Math.min(widthBudget, (int) Math.floor(budget * .75f)));
        slot.height = ((RevealCard) child).fitToSize(slot.width, budget);
        child.forceLayout();
      }
      // 동일 사양의 측정 캐시를 재사용하지 않도록 실제 앞면 높이로 한 번 더 정렬한다.
      cardsRow.forceLayout();
      super.onMeasure(widthSpec, heightSpec);
    }
  }

  final class RevealCard extends FrameLayout {
    final View front, back;
    final int preferredHeight;
    final FrameLayout photo;
    TextView countBadge;
    int cardWidth, portraitHeight;

    RevealCard(JSONObject unit, int width) {
      this(unit, width, Integer.MAX_VALUE);
    }

    RevealCard(JSONObject unit, int width, int heightBudget) {
      super(h);
      cardWidth = h.dp(width);
      portraitHeight = Math.max(1, Math.min(Math.round(cardWidth * 4f / 3f), heightBudget));
      LinearLayout face = k.column();
      face.setGravity(Gravity.CENTER);
      // 원본에 종이·프레임·이름·등급이 모두 있으므로 외부 크림 판과 중복 문자를 두지 않는다.
      photo = new FrameLayout(h);
      View art = RegistrationCardView.art(h, unit, RegistrationCardView.FULL);
      art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      photo.addView(art, new LayoutParams(-1, -1));
      if (unit.optBoolean("first")) {
        TextView badge = h.text("NEW", 12);
        badge.setGravity(Gravity.CENTER);
        fitBadge(badge);
        badge.setBackground(new GamePaperDrawable(h, JournalTheme.BRASS));
        LayoutParams box = new LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        box.setMargins(h.dp(4), h.dp(4), 0, 0);
        photo.addView(badge, box);
      }
      if (unit.optInt("n") > 1) {
        TextView count = h.text("×" + unit.optInt("n"), 12);
        countBadge = count;
        count.setGravity(Gravity.CENTER);
        fitBadge(count);
        count.setBackground(new GamePaperDrawable(h));
        LayoutParams box = new LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        box.setMargins(0, h.dp(4), h.dp(4), 0);
        photo.addView(count, box);
      }
      face.addView(photo, new LinearLayout.LayoutParams(-1, portraitHeight));
      preferredHeight = portraitHeight;
      front = face;
      back =
          new View(h) {
            final Paint p = new Paint(3);

            @Override
            protected void onDraw(Canvas c) {
              RectF bounds = new RectF(0, 0, getWidth(), getHeight());
              GameButtonDrawable.drawSurface(c, bounds, JournalTheme.TEAL, h.dp(14), h.dp(3), 0, p);
              float w = getWidth() * .38f, x = getWidth() / 2f, y = getHeight() / 2f;
              p.setColor(JournalTheme.BRASS);
              p.setStyle(Paint.Style.STROKE);
              p.setStrokeWidth(h.dp(2));
              c.drawCircle(x, y, w * .6f, p);
              p.setColor(JournalTheme.PAPER);
              c.drawLine(x, y - w * .36f, x, y + w * .36f, p);
              c.drawLine(x - w * .22f, y - w * .1f, x + w * .22f, y - w * .1f, p);
              c.drawArc(
                  new RectF(x - w * .38f, y - w * .16f, x + w * .38f, y + w * .36f),
                  0,
                  180,
                  false,
                  p);
              c.drawCircle(x, y - w * .36f, w * .1f, p);
              p.setStyle(Paint.Style.FILL);
            }
          };
      setContentDescription(RegistrationCardView.description(unit));
      addView(front, new LayoutParams(-1, -1));
      addView(back, new LayoutParams(-1, -1));
      if (rank(unit.optString("tier")) >= 6) setElevation(h.dp(6));
      flip(0);
    }

    int fitToSize(int width, int budget) {
      cardWidth = width;
      portraitHeight = Math.max(1, Math.min(budget, Math.round(width * 4f / 3f)));
      LinearLayout face = (LinearLayout) front;
      View photo = face.getChildAt(0);
      photo.getLayoutParams().height = portraitHeight;
      photo.forceLayout();
      face.forceLayout();
      return portraitHeight;
    }

    void fitBadge(TextView label) {
      label.setPadding(0, 0, 0, 0);
      label.setSingleLine(true);
      label.setMinWidth(
          Math.max(
              h.dp(36),
              (int) Math.ceil(label.getPaint().measureText(label.getText().toString()))
                  + h.dp(12)));
      label.setMinHeight(Math.max(h.dp(20), label.getLineHeight() + h.dp(10)));
    }

    void flip(float progress) {
      front.setVisibility(progress >= .5f ? VISIBLE : INVISIBLE);
      back.setVisibility(progress < .5f ? VISIBLE : INVISIBLE);
      setScaleX(Math.max(.02f, Math.abs((float) Math.cos(progress * Math.PI))));
      setAlpha(.55f + .45f * Math.min(1, progress * 4));
    }
  }

  final class Glow extends View {
    final Paint p = new Paint(3);
    float flash, rotation;
    boolean revealed;

    Glow() {
      super(h);
    }

    @Override
    protected void onDraw(Canvas c) {
      float x = getWidth() / 2f,
          y = getHeight() * .45f,
          radius = Math.max(1, Math.max(getWidth(), getHeight()) * .55f);
      NativeResultBackdrop.draw(h, c, getWidth(), getHeight(), p);
      if (revealed && topRank >= 6) {
        c.save();
        c.rotate(rotation, x, y);
        p.setColor(JournalTheme.BRASS);
        p.setAlpha(30);
        for (int angle = 0; angle < 360; angle += 22) {
          Path ray = new Path();
          ray.moveTo(x, y);
          ray.lineTo(
              x + radius * 2 * (float) Math.cos(Math.toRadians(angle)),
              y + radius * 2 * (float) Math.sin(Math.toRadians(angle)));
          ray.lineTo(
              x + radius * 2 * (float) Math.cos(Math.toRadians(angle + 8)),
              y + radius * 2 * (float) Math.sin(Math.toRadians(angle + 8)));
          ray.close();
          c.drawPath(ray, p);
        }
        c.restore();
        p.setAlpha(255);
      }
      if (flash > 0) {
        p.setShader(
            new RadialGradient(
                x,
                y,
                radius * (1.6f - flash * 1.2f),
                new int[] {
                  JournalTheme.PAPER,
                  JournalTheme.BRASS & 0x00ffffff | 0x77000000,
                  Color.TRANSPARENT
                },
                new float[] {0, .4f, 1},
                Shader.TileMode.CLAMP));
        p.setAlpha((int) (flash * 255));
        c.drawRect(0, 0, getWidth(), getHeight(), p);
        p.setShader(null);
        p.setAlpha(255);
      }
    }
  }

}



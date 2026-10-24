package org.dpdns.gmm;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.json.*;

/** 서버가 허가한 개체와 보상만 실행한다. 미확인 요청은 저장된 동일 요청으로만 재시도한다. */
final class NativeProgression {
  private static final Set<String> IN_FLIGHT = new HashSet<>();
  final MainActivity h;
  final MenuKit k;
  final NativeProgressionState clock = new NativeProgressionState();
  final SharedPreferences prefs;
  String account, owner, tab = "pass", selectedKey = "", notice = "";
  int epoch;
  boolean loading;
  JSONObject snapshot, receipt;
  LinearLayout contents, overlay;
  OnBackPressedCallback back;
  Button refreshButton;
  java.util.function.BiConsumer<String, JSONObject> conversionReceipt;
  Runnable onDismiss;
  boolean dismissed, receiptDelivered;
  final java.util.Map<String, Integer> pages = new java.util.HashMap<>();

  NativeProgression(MainActivity host) {
    h = host; k = new MenuKit(h);
    prefs = h.getSharedPreferences("native_progression_requests", Context.MODE_PRIVATE);
  }

  static void openReassessment(MainActivity host, String key) {
    if (key == null || key.isEmpty()) return;
    NativeProgression screen = new NativeProgression(host);
    screen.tab = "reassessment"; screen.selectedKey = key; screen.openOverlay();
  }

  static NativeProgression openReassessment(MainActivity host, String key,
      java.util.function.BiConsumer<String, JSONObject> converted, Runnable closed) {
    NativeProgression screen = new NativeProgression(host);
    screen.tab = "reassessment"; screen.selectedKey = key;
    screen.conversionReceipt = converted; screen.onDismiss = closed;
    screen.openOverlay();
    return screen;
  }

  void open() {
    if (h.api.token().isEmpty()) { h.login(); return; }
    h.base("성장 · 플레이 패스", "progression");
    account = h.api.token(); owner = accountKey(account.split("\\.")[0]); epoch = h.epoch;
    LinearLayout tabs = k.row();
    String[][] options = {{"pass", "플레이 패스"}, {"reroll", "다시뽑기"},
        {"reincarnation", "환생"}, {"reassessment", "재심사"}};
    for (String[] option : options) {
      Button b = k.action(null, option[1], () -> { tab = option[0]; selectedKey = ""; receipt = null; notice = ""; if(roster60()){loading=false;refresh();}else render(); });
      fixedButton(b);
      b.setTag("progression-tab-" + option[0]);
      tabs.addView(b, new LinearLayout.LayoutParams(0, h.dp(48), 1));
    }
    refreshButton = k.action(null, "↻", this::refresh);
    fixedButton(refreshButton); refreshButton.setTag("progression-refresh"); refreshButton.setContentDescription("서버 진행 새로 확인");
    tabs.addView(refreshButton, new LinearLayout.LayoutParams(h.dp(48), h.dp(48)));
    // Fixed viewport: one server candidate per page, never a scrolling main panel.
    h.page.removeViewAt(h.page.getChildCount() - 1);
    LinearLayout panel = k.column(); panel.setTag("progression-panel");
    h.page.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
    panel.addView(tabs, new LinearLayout.LayoutParams(-1, h.dp(48)));
    contents = k.column(); contents.setTag("progression-content");
    panel.addView(contents, new LinearLayout.LayoutParams(-1, 0, 1));
    refresh();
  }

  void openOverlay() {
    if (h.api.token().isEmpty()) { h.message("다시 로그인한 뒤 결과에서 재심사를 열어 주세요."); return; }
    account = h.api.token(); owner = accountKey(account.split("\\.")[0]); epoch = h.epoch;
    overlay = k.column(); overlay.setTag("progression-reassessment-overlay");
    overlay.setBackground(new GamePaperDrawable(h)); overlay.setPadding(h.dp(16), h.dp(8), h.dp(16), h.dp(8));
    Button close = k.action(null, "‹ 결과로", this::dismiss);
    fixedButton(close); close.setTag("progression-close");
    LinearLayout rail = k.row(); rail.addView(close, new LinearLayout.LayoutParams(0, h.dp(48), 1));
    refreshButton = k.action(null, "새로 확인", this::refresh); fixedButton(refreshButton); refreshButton.setTag("progression-refresh");
    rail.addView(refreshButton, new LinearLayout.LayoutParams(h.dp(120), h.dp(48)));
    overlay.addView(rail, new LinearLayout.LayoutParams(-1, h.dp(48)));
    contents = k.column(); contents.setTag("progression-content");
    overlay.addView(contents, new LinearLayout.LayoutParams(-1, 0, 1));
    h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    back = new OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() { dismiss(); }
    };
    h.getOnBackPressedDispatcher().addCallback(h, back);
    overlay.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
      @Override public void onViewAttachedToWindow(android.view.View view) { }
      @Override public void onViewDetachedFromWindow(android.view.View view) { if (back != null) back.remove(); clock.invalidate(); }
    });
    refresh();
  }

  void dismiss() {
    if (dismissed) return;
    dismissed = true;
    clock.invalidate();
    if (back != null) back.remove();
    if (overlay != null) h.root.removeView(overlay);
    if (onDismiss != null) onDismiss.run();
  }

  boolean current() {
    return epoch == h.epoch && account.equals(h.api.token()) && (overlay == null ? "progression".equals(h.screen) : overlay.getParent() == h.root)
        && !h.isFinishing() && !h.isDestroyed();
  }

  boolean roster60(){JSONObject t=h.state.optJSONObject("tutorial");return t!=null&&"roster60-v1".equals(t.optString("flow"));}
  boolean newReincarnation(){return roster60()&&tab.equals("reincarnation");}

  void refresh() {
    if (!current() || loading) return;
    loading = true; snapshot = null; int ticket = clock.begin(); render();
    final boolean newFlow=newReincarnation();
    h.api.call(newFlow?"native/roster60/reincarnation/preview":"native/progression", null, (data, error) -> {
      if (epoch == h.epoch && h.api.token().isEmpty()) { h.login(); return; }
      if (!current() || !clock.current(ticket)) return;
      loading = false;
      if (error != null || (newFlow?(data==null||!"roster60-v1".equals(data.optString("flow"))):!validSnapshot(data)) || !clock.accept(ticket, data.optLong("serverNow"), SystemClock.elapsedRealtime())) {
        notice = "서버에서 이 기능의 진행 정보를 확인하지 못했어요. 아직 지원하지 않는 서버이거나 연결 문제일 수 있어요.\n"
            + (error == null ? "지급이나 변환은 실행하지 않았어요. 다시 조회해 주세요." : error);
        render(); return;
      }
      snapshot = data; render();
    });
  }

  JSONObject pending() {
    try { return new JSONObject(prefs.getString(owner, "{}")); }
    catch (JSONException e) { return new JSONObject(); }
  }

  boolean blocked() { return loading || pending().has("requestId"); }

  void render() {
    if (!current() || contents == null) return;
    contents.removeAllViews();
    if (refreshButton != null) refreshButton.setEnabled(!loading);
    JSONObject intent = pending();
    if (intent.has("requestId")) {
      summary(notice.isEmpty() ? "처리 결과 확인 중 · 다른 지급/변환은 확인 뒤 이용해요." : notice, 3);
      action("progression-retry", "같은 요청 결과 확인", !IN_FLIGHT.contains(intent.optString("requestId")), () -> send(intent));
      return;
    }
    if(newReincarnation()){renderNewReincarnation();return;}
    if (loading) { summary("서버 진행 확인 중…", 2); return; }
    if (receipt != null && !receipt.optString("key").isEmpty()) {
      summary("변환 완료 · " + receipt.optString("name") + " · " + receipt.optString("rarity")
          + (receipt.optInt("reincarnation") > 0 ? " · 환생 +" + receipt.optInt("reincarnation") : "")
          + "\n개체 번호 " + receipt.optString("key"), 3);
      action("progression-result-unit", "새 개체 육성 보기", true, () -> {
        h.currentKey = receipt.optString("key");
        if (overlay != null) dismiss();
        h.load("unit");
      });
      return;
    }
    if (snapshot == null) { summary(notice.isEmpty() ? "서버 진행을 새로 확인해 주세요." : notice, 4); return; }
    if (!notice.isEmpty()) {
      summary(notice, 4);
      action("progression-dismiss-notice", "진행 보기", true, () -> { notice = ""; render(); });
      return;
    }
    if (tab.equals("pass")) pass();
    else if (tab.equals("reroll")) reroll();
    else candidates(tab);
  }

  void renderNewReincarnation(){
    if(loading){summary("환생 정보 확인 중…",2);return;}
    if(snapshot==null){summary("새 환생 데이터 준비 중 · 아직 실행하지 않습니다.\n"+notice,4);return;}
    JSONArray list=snapshot.optJSONArray("candidates");
    if(list==null||list.length()==0){summary("환생 미리보기 대상이 없습니다.",2);return;}
    JSONObject unit=list.optJSONObject(page(list.length())),p=unit.optJSONObject("preview");
    if(p==null)summary(unit.optString("name")+" · 기본 능력치 또는 환생 정책 준비 중",3);
    else {
      JSONObject before=p.optJSONObject("baselineLevelOneStats"),after=p.optJSONObject("afterLevelOneStats");
      StringBuilder line=new StringBuilder(unit.optString("name")+" · Lv.1 / 99 · XP 0\n일반 1레벨 → 환생 "+object(p,"next").optInt("reincarnation")+"회\n");
      String[] keys={"hp","mp","durability","statusResistance","speed"},labels={"체력","마력(MP)","내구력","저항력","속도"};
      for(int i=0;i<keys.length;i++)line.append(labels[i]).append(" ").append(before.optString(keys[i])).append(" → ").append(after.optString(keys[i])).append(i%2==1?"\n":" · ");
      summary(line.toString(),6);
    }
    summary(!unit.has("costPeanut")?"비용 확인 준비 중":unit.optBoolean("accountFreeAvailable")?"계정 첫 성공 1회 무료":"비용: 땅콩 "+unit.optInt("costPeanut")+"개",1);
    action("roster60-reincarnation-confirm",unit.optBoolean("eligible")?"환생 확인":unit.has("requiredLevel")?"필요 Lv."+unit.optInt("requiredLevel")+" · 대상 조건 미충족":"환생 정보 준비 중",!blocked()&&unit.optBoolean("eligible"),()->{
      final int shownEpoch=epoch;
      android.app.AlertDialog dialog=new GameDialog(h).navy().setMessage(unit.optString("name")+" · Lv.1 / 99로 초기화\n"+(unit.optBoolean("accountFreeAvailable")?"계정 첫 성공 무료":"땅콩 "+unit.optInt("costPeanut")+"개 사용")+"\n환생할까요?").setNegativeButton("취소",null).setPositiveButton("확인",null).show();
      dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
        if(!current()||epoch!=shownEpoch||blocked())return;
        submit("roster60/reincarnation/confirm",h.obj("ownedId",unit.optString("ownedId"),"catalogVersion",snapshot.optString("catalogVersion"),"previewFingerprint",unit.optString("previewFingerprint")));
        dialog.dismiss();
      });
    });
    pagination(list.length());
  }

  void summary(String text, int lines) {
    TextView view = k.note(text); view.setSingleLine(false); view.setHorizontallyScrolling(false); view.setMaxLines(lines); view.setMinimumHeight(h.dp(48));
    view.setEllipsize(null);
    view.setTag("progression-summary");
    view.setTextColor(JournalTheme.INK);
    view.setBackground(new GamePaperDrawable(h));
    view.setPadding(h.dp(12), h.dp(6), h.dp(12), h.dp(6));
    view.setGravity(android.view.Gravity.CENTER_VERTICAL);
    view.setContentDescription(text); view.setOnClickListener(v -> h.message(text));
    contents.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  void featureCard(String headline, String detail, String iconName, JSONObject unit) {
    LinearLayout card = k.row(); card.setTag("progression-feature-card");
    card.setBaselineAligned(false); card.setGravity(android.view.Gravity.CENTER_VERTICAL);
    card.setBackground(new GamePaperDrawable(h));
    card.setPadding(h.dp(20), h.dp(6), h.dp(20), h.dp(6));
    android.graphics.Bitmap image = null;
    String species = unit == null ? "" : unit.optString("id");
    // Display identity is the actual species, never combatDefinitionId/inheritedFrom.
    if (species.matches("[A-Za-z][0-9]{1,3}")) {
      try (java.io.InputStream input = h.getAssets().open("game-art/thumbs/" + species + ".jpg")) {
        image = android.graphics.BitmapFactory.decodeStream(input);
      } catch (java.io.IOException ignored) { }
    }
    boolean canonicalPortrait = image != null;
    if (image == null) image = JournalIcons.create(h, iconName);
    ImageView picture = new ImageView(h); picture.setTag("progression-feature-art");
    picture.setScaleType(ImageView.ScaleType.FIT_CENTER); picture.setImageBitmap(image);
    picture.setContentDescription(canonicalPortrait ? species + " 정본 모습" : "보상 아이콘");
    card.addView(picture, new LinearLayout.LayoutParams(h.dp(64), h.dp(64)));
    LinearLayout words = k.column(); words.setGravity(android.view.Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams wordSlot = new LinearLayout.LayoutParams(0, -2, 1);
    wordSlot.leftMargin = h.dp(20); card.addView(words, wordSlot);
    TextView title = h.text(headline, 22); title.setTextSize(22); title.setTypeface(h.headline);
    title.setTag("progression-headline"); title.setTextColor(JournalTheme.INK);
    // Reserve real ink space for the headline font's negative bearings.
    title.setPadding(h.dp(2), h.dp(2), h.dp(2), h.dp(3)); title.setIncludeFontPadding(false);
    title.setSingleLine(false); title.setMaxLines(1); title.setHorizontallyScrolling(false); title.setEllipsize(null);
    words.addView(title, new LinearLayout.LayoutParams(-1, -2));
    TextView subtitle = h.text(detail, 16); subtitle.setTextSize(16); subtitle.setTypeface(h.prose);
    subtitle.setTag("progression-summary"); subtitle.setTextColor(JournalTheme.INK);
    subtitle.setPadding(0, 0, 0, 0); subtitle.setIncludeFontPadding(false);
    subtitle.setSingleLine(false); subtitle.setMaxLines(2); subtitle.setHorizontallyScrolling(false); subtitle.setEllipsize(null);
    words.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
    contents.addView(card, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  int page(int count) { return Math.max(0, Math.min(pages.getOrDefault(tab, 0), Math.max(0, count - 1))); }

  void pagination(int count) {
    if (count <= 1) return;
    int index = page(count);
    LinearLayout row = k.row(); row.setTag("progression-pagination");
    Button previous = k.action(null, "‹", () -> { pages.put(tab, index - 1); render(); });
    fixedButton(previous); previous.setTag("progression-previous"); previous.setContentDescription("이전 개체 또는 보상"); previous.setEnabled(index > 0);
    row.addView(previous, new LinearLayout.LayoutParams(h.dp(64), h.dp(48)));
    TextView label = k.note((index + 1) + " / " + count); label.setGravity(android.view.Gravity.CENTER);
    label.setTag("progression-page-label");
    label.setBackground(new GamePaperDrawable(h)); label.setTextColor(JournalTheme.INK);
    label.setSingleLine(false); label.setMaxLines(1); label.setHorizontallyScrolling(false);
    row.addView(label, new LinearLayout.LayoutParams(0, h.dp(48), 1));
    Button next = k.action(null, "›", () -> { pages.put(tab, index + 1); render(); });
    fixedButton(next); next.setTag("progression-next"); next.setContentDescription("다음 개체 또는 보상"); next.setEnabled(index + 1 < count);
    row.addView(next, new LinearLayout.LayoutParams(h.dp(64), h.dp(48)));
    contents.addView(row, new LinearLayout.LayoutParams(-1, h.dp(48)));
  }

  void fixedButton(Button button) {
    button.setTextSize(16);
    button.setSingleLine(false);
    button.setMaxLines(1);
    button.setHorizontallyScrolling(false);
    button.setEllipsize(null);
    button.setPadding(h.dp(8), h.dp(4), h.dp(8), h.dp(4));
    button.setMinHeight(h.dp(48));
    button.setMinimumHeight(h.dp(48));
  }

  void action(String tag, String label, boolean enabled, Runnable run) {
    Button button = k.action(null, label, run); fixedButton(button); button.setTag(tag); button.setEnabled(enabled);
    contents.addView(button, new LinearLayout.LayoutParams(-1, h.dp(48)));
  }

  void pass() {
    JSONObject pass = object(snapshot, "pass");
    JSONArray rewards = pass.optJSONArray("milestones");
    if (rewards == null || rewards.length() == 0) { summary("등록된 패스 보상이 없어요.", 2); return; }
    JSONObject reward = rewards.optJSONObject(page(rewards.length()));
    if (reward == null) return;
    String id = reward.optString("id");
    featureCard("지원서 " + reward.optInt("credits") + "장 · " + pass.optInt("points") + "점",
        reward.optInt("points") + "점 달성 보상\n" + (reward.optBoolean("claimed") ? "수령 완료" : reward.optBoolean("claimable") ? "수령 가능" : "점수 부족"),
        "u-ticket", null);
    String label = reward.optBoolean("claimed") ? "수령 완료" : reward.optBoolean("claimable") ? "단계 보상 받기" : "점수 부족";
    action("progression-pass-" + id, label, !blocked() && !id.isEmpty() && reward.optBoolean("claimable")
        && !reward.optBoolean("claimed"), () -> submit("pass/claim", h.obj("milestoneId", id)));
    pagination(rewards.length());
  }

  void reroll() {
    JSONObject r = object(snapshot, "reroll");
    long activeFor = clock.remaining(r.optLong("expiresAt"), SystemClock.elapsedRealtime());
    long deadline = r.optLong("nextClaimAt"); boolean ready = clock.ready(deadline);
    String active = r.optBoolean("available") && activeFor > 0 ? "사용 가능 · 남은 " + duration(activeFor) : "보유 추가 횟수 없음";
    featureCard("증강 다시뽑기 +1", active + " · 30분 유효\n"
        + (ready ? "5시간마다 · 지금 수령 가능" : "다음 수령까지 " + duration(clock.remaining(deadline, SystemClock.elapsedRealtime()))),
        "reverse", null);
    action("progression-reroll-claim", "+1 받기", !blocked() && ready, () -> submit("reroll/claim", new JSONObject()));
  }

  void candidates(String kind) {
    JSONObject section = object(snapshot, kind); boolean reincarnation = kind.equals("reincarnation");
    boolean cooldown = !reincarnation && !clock.ready(section.optLong("nextAvailableAt"));
    JSONArray all = section.optJSONArray("candidates");
    java.util.ArrayList<JSONObject> candidates = new java.util.ArrayList<>();
    if (all != null) for (int i = 0; i < all.length(); i++) {
      JSONObject u = all.optJSONObject(i); if (u == null || u.optString("key").isEmpty()) continue;
      if (selectedKey.isEmpty() || selectedKey.equals(u.optString("key"))) candidates.add(u);
    }
    if (candidates.isEmpty()) { summary("서버에서 확인된 대상 개체가 없어요.", 2); return; }
    JSONObject unit = candidates.get(page(candidates.size())); String key = unit.optString("key");
    boolean eligible = !unit.optBoolean("locked") && (reincarnation ? unit.optBoolean("eligible") : unit.optBoolean("eligible", true));
    String status = reincarnation ? "일반 등급 · 다음 환생 +" + (unit.optInt("reincarnation") + 1) : "고급 모집 · 같은 등급 재심사";
    if (!eligible) status = unit.optString("reason", "지금은 대상이 아니에요.");
    if (cooldown) status = "같은 등급 · 재심사 대기 " + duration(clock.remaining(section.optLong("nextAvailableAt"), SystemClock.elapsedRealtime()));
    featureCard(unit.optString("name"), unit.optString("rarity") + " · 개체 " + key + "\n" + status, "u-profile", unit);
    action("progression-" + kind + "-" + key, reincarnation ? "이 개체 환생 확인" : "이 개체 재심사 확인",
        !blocked() && eligible && !cooldown, () -> confirm(kind, unit));
    pagination(candidates.size());
  }

  void confirm(String kind, JSONObject unit) {
    if (!current() || blocked()) return;
    final int version = epoch;
    String verb = kind.equals("reincarnation") ? "환생" : "재심사";
    String consequence = kind.equals("reincarnation") ? "현재 개체를 소모해 일반 +" + (unit.optInt("reincarnation") + 1) + " 개체로 바꿉니다. 되돌릴 수 없어요." : "현재 개체를 같은 등급의 새 개체로 바꿉니다. 결과를 되돌릴 수 없어요.";
    h.confirm(unit.optString("name") + "\n개체 번호 " + unit.optString("key") + "\n" + consequence + "\n" + verb + "할까요?", () -> {
      if (!current() || version != h.epoch || blocked()) return;
      submit(kind.equals("reincarnation") ? "reincarnate" : "reassess", h.obj("key", unit.optString("key")));
    });
  }

  void submit(String endpoint, JSONObject body) {
    if (!current() || blocked()) return;
    String id = UUID.randomUUID().toString();
    JSONObject intent = h.obj("requestId", id, "path", endpoint.equals("roster60/reincarnation/confirm")?"native/"+endpoint:"native/progression/" + endpoint,
        "body", body);
    try { body.put("requestId", id); } catch (JSONException e) { return; }
    // commit before transmission; without a durable id no request is sent.
    if (!prefs.edit().putString(owner, intent.toString()).commit()) {
      notice = "요청을 안전하게 저장하지 못했어요. 다시 시도해 주세요."; render(); return;
    }
    send(intent);
  }

  void send(JSONObject intent) {
    if (!current()) return;
    String id = intent.optString("requestId"), path = intent.optString("path");
    JSONObject body = intent.optJSONObject("body");
    if (id.isEmpty() || body == null || (!path.startsWith("native/progression/")&&!path.equals("native/roster60/reincarnation/confirm")) || !IN_FLIGHT.add(id)) return;
    final String requestOwner = owner;
    render();
    h.api.call(path, body, (data, error) -> {
      IN_FLIGHT.remove(id);
      boolean conversionProof = !path.equals("native/progression/reassess") || (data != null
          && data.optString("key").matches("o[1-9]\\d*") && !data.optString("key").equals(body.optString("key"))
          && data.optString("id").matches("[A-Za-z][0-9]{1,3}") && !data.optString("rarity").isEmpty());
      boolean rebirth=path.equals("native/roster60/reincarnation/confirm");
      boolean success = error == null && data != null && (rebirth?data.optBoolean("completed"):data.optBoolean("ok")) && conversionProof;
      boolean rejected = error != null && data != null && data.has("retryable")
          && !data.optBoolean("retryable", true) && !data.optBoolean("ok", true)
          && !h.api.token().isEmpty();
      if(rebirth&&error!=null&&java.util.Arrays.asList("REINCARNATION_GRADE_NOT_ALLOWED","REINCARNATION_REQUIREMENTS_NOT_MET","REINCARNATION_LIMIT","UNIT_UNAVAILABLE","UNIT_NOT_FOUND","UNIT_CHANGED","BATTLE_ACTIVE","INSUFFICIENT_PEANUT","PREVIEW_CHANGED","REQUEST_ID_REUSED","ROSTER_CATALOG_PENDING","LEVEL_ONE_BASE_STATS_REQUIRED").contains(error))rejected=true;
      // Clear only this exact receipt; a delayed callback cannot erase a newer intent.
      if (success || rejected) {
        try {
          JSONObject saved = new JSONObject(prefs.getString(requestOwner, "{}"));
          if (id.equals(saved.optString("requestId"))) prefs.edit().remove(requestOwner).commit();
        } catch (JSONException ignored) { }
      }
      // 성공 영수증은 화면 수명과 독립적으로 원래 계정의 획득 흐름에 보존한다.
      // 다른 개체에서 미확인 요청을 재조회하더라도 선택한 카드 key로 바꿔치기하지 않는다.
      if (success && conversionReceipt != null && path.equals("native/progression/reassess")
          && !receiptDelivered && data.optString("key").matches("o[1-9]\\d*")
          && !data.optString("id").isEmpty()) {
        receiptDelivered = true;
        conversionReceipt.accept(body.optString("key"), data);
      }
      if (epoch == h.epoch && h.api.token().isEmpty()) { h.login(); return; }
      if (!current()) return;
      if (success) receipt = data;
      notice = success ? "서버에서 처리를 확인했어요. 최신 진행을 불러옵니다."
          : error == null ? "처리 결과를 확인하지 못했어요. 같은 요청 결과를 다시 확인해 주세요." : error;
      if (success || rejected) refresh(); else render();
    });
  }

  static boolean validSnapshot(JSONObject data) {
    if (data == null || data.optLong("serverNow") <= 0) return false;
    for (String name : new String[] {"pass", "reroll", "reincarnation", "reassessment"})
      if (data.optJSONObject(name) == null) return false;
    return object(data, "pass").optJSONArray("milestones") != null
        && object(data, "reroll").has("nextClaimAt")
        && object(data, "reincarnation").optJSONArray("candidates") != null
        && object(data, "reassessment").has("nextAvailableAt")
        && object(data, "reassessment").optJSONArray("candidates") != null;
  }

  static JSONObject object(JSONObject parent, String name) {
    JSONObject result = parent.optJSONObject(name); return result == null ? new JSONObject() : result;
  }
  static String duration(long ms) { return ms < 0 ? "확인 필요" : ((ms + 59999) / 60000) + "분"; }
  static String accountKey(String account) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8));
      StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format("%02x", b & 255));
      return s.toString();
    } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}

